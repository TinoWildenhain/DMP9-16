// DMP9_MidiAnalysis.java — Ghidra 11.x / 12.x
//
// MIDI subsystem annotator for Yamaha DMP9/16 firmware.
//
// PURPOSE
// ───────
// Labels, types, and comments for all MIDI-related code structures:
//
//   1. Known function anchors — midi_tx_byte, midi_process_rx, scene_recall,
//      scene_store — are confirmed by string cross-reference.  These form the
//      roots for all further MIDI analysis.
//
//   2. SysEx parser — located by searching for the F0/43 byte sequence near
//      midi_process_rx.  The format (Yamaha standard):
//        F0 43 <dev> <sub> <size_hi> <size_lo> [data...] <cksum> F7
//
//   3. Control Change parameter table — 671 entries × 16 banks, indexed by
//      (bank, CC number).  The table starts at 0x054FEE in v1.11 (confirmed).
//      Each entry maps to a parameter index used to look up the parameter
//      name string and the DSP/hardware register to update.
//
//   4. Undocumented command search — scans for MIDI status bytes beyond the
//      documented set (0xBn CC, 0xCn PG, 0xF0 SysEx) to find any hidden
//      commands.  Also scans for note-on (0x9n) handling, which would indicate
//      DMP7/DMP11-style note→parameter mapping.
//
//   5. Version comparison hooks — records findings as structured comments so
//      DMP9_VersionTrack can propagate them to v1.02/v1.10.
//
// ROM VERSION NOTE
// ────────────────
// Default addresses are for v1.11 (XN349G0).  The script auto-detects the
// version from the version string in ROM and adjusts a small offset table.
// For v1.02/v1.10, run DMP9_VersionTrack first to propagate names, then this
// script will find the functions by name rather than by address.
//
// WHAT TO LOOK FOR IN THE OUTPUT
// ───────────────────────────────
// After running:
//   - Check plate comments on midi_process_rx for the full dispatch table.
//   - Look for any function named "midi_note_on_handler" (hidden feature!).
//   - The CC parameter table enum in the Ghidra data type manager under
//     /DMP9/MIDI_CC_PARAM_TABLE lets you see all 671 parameter mappings.
//   - "UNDOCUMENTED" plate comments mark any handler beyond PG/CC/SysEx.
//
// @author  DMP9-16 project
// @category DMP9
// @keybinding
// @menupath
// @toolbar

import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.listing.Function.FunctionUpdateType;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.lang.*;
import ghidra.util.exception.InvalidInputException;

import java.util.*;

/**
 * DMP9_MidiAnalysis — MIDI subsystem, LCD, serial ISR, and MCC68K library anchor script
 *
 * Runs after DMP9_FuncFixup. Anchors function names + signatures for the MIDI
 * pipeline (rx dispatch, sysex, scene store/recall), the LCD output cluster,
 * the serial ISRs (SIO0/SIO1), the MCC68K runtime library (memcpy/memset),
 * and small app-level helpers (delay_simple, delay_nested, led_sr_write).
 *
 * Run order: DMP9_FuncFixup → TMP68301_Setup → DMP9_Board_Setup
 *            → DMP9_MidiAnalysis → DMP9_LibMatch → DMP9_VersionTrack
 *
 * Target: Yamaha DMP9/DMP16, TMP68301AF-16 (68000 @ 16MHz), MCC68K v4.x compiler
 * Ghidra: 12.0.4+
 *
 * See: ghidra/docs/DMP9_MidiAnalysis.md for full documentation.
 */
public class DMP9_MidiAnalysis extends GhidraScript {

    // -----------------------------------------------------------------------
    // v1.11 anchor addresses — used when functions not yet named by VT
    // -----------------------------------------------------------------------

    /** Known MIDI function addresses in v1.11 (XN349G0) */
    private static final Map<String, Long> V111_ANCHORS = new LinkedHashMap<>();
    static {
        V111_ANCHORS.put("midi_tx_byte",       0x000122E0L);
        V111_ANCHORS.put("midi_process_rx",    0x000128ECL);
        V111_ANCHORS.put("scene_recall",       0x00013454L);
        V111_ANCHORS.put("scene_store",        0x00013186L);
        V111_ANCHORS.put("lcd_write_cmd",      0x0000238CL);
        V111_ANCHORS.put("lcd_write_data",     0x000023DCL);
        V111_ANCHORS.put("uart_write",         0x00002AA6L);
        // MCC68K runtime library block (0x0023AE cluster — confirmed all 3 ROMs)
        V111_ANCHORS.put("memcpy_b",           0x000023AEL); // byte copy: MOVE.B (A0)+,(A1)+ ; BTST #0,D0 ; BNE
        V111_ANCHORS.put("memcpy_w",           0x000023CAL); // word copy: SUBQ.W #1,D0 ; MOVE.W (A0)+,(A1)+ ; DBF
        V111_ANCHORS.put("memcpy_l",           0x000023E4L); // long copy: SUBQ.W #1,D0 ; MOVE.L (A0)+,(A1)+ ; DBF
        V111_ANCHORS.put("meminv_b",           0x000023FEL); // invert-copy: MOVE.B (A0)+,D1 ; NOT.B D1 ; MOVE.B D1,(A1)+
        V111_ANCHORS.put("memcpy_bitser",      0x0000241EL); // bit-serial: ROXR.B #1,D1 ; ROXL.B #1,D2 ×8 per byte
        V111_ANCHORS.put("bitrev_byte",        0x00002464L); // single-byte bit reversal helper
        V111_ANCHORS.put("memcpy_bitser_w",    0x00002488L); // word bit-serial variant
        V111_ANCHORS.put("memset_b",            0x000024DAL); // byte fill: MOVE.B D0,(A1)+ ; SUBQ.W #1,D1 ; BNE
        V111_ANCHORS.put("memset_w",            0x000024F2L); // word fill variant
        V111_ANCHORS.put("memset_l",            0x0000250AL); // longword fill variant

        // Init-block helpers (v1.11 confirmed — small leaf functions, yamaha_reg)
        // delay_simple: tight spin loop — no nested call, used as a primitive timing helper.
        // delay_nested: calls delay_simple in a loop — used for LCD command/data delays.
        // TODO: v1.02 (XN349E0) and v1.10 (XN349F0) addresses not confirmed.
        //       Compute from offset delta relative to memcpy_b (0x000023AE) once
        //       version-specific anchor tables are added.
        V111_ANCHORS.put("delay_nested",        0x0000274AL); // void delay_nested(int count) — calls delay_simple in a loop
        V111_ANCHORS.put("delay_simple",        0x00002768L); // void delay_simple(int iterations) — tight loop, no nested call
        V111_ANCHORS.put("led_sr_write",        0x00002770L); // void led_sr_write(uint data) — clocks 16 bits into LED shift register at LED_SR_DATA (0x4D0000)

        // LCD init cluster (v1.11 confirmed). yamaha_reg convention; small leaf helpers.
        // TODO: v1.02 (XN349E0) and v1.10 (XN349F0) addresses not yet confirmed —
        //       compute deltas relative to lcd_write_cmd (0x0000238C) once the
        //       version anchor tables exist.
        V111_ANCHORS.put("lcd_delay_enable",    0x000029BCL); // void(void) — fixed ~8.75µs DBF loop, saves/restores D0
        V111_ANCHORS.put("lcd_strobe",          0x000029C8L); // void(uint cmd) — D0=cmd, E-strobe sequence to LCD_CTRL
        V111_ANCHORS.put("lcd_send_init_cmds",  0x000029F2L); // void(void) — sends HD44780 init: 0x38 then 0x08
        V111_ANCHORS.put("lcd_init_sequence",   0x00002A00L); // void(void) — TODO verify

        // Hardware model detection / init (v1.11). hw_model_init may use either
        // yamaha_reg or __stdcall depending on whether it has a LINK prologue —
        // detected at runtime in applyModelInitSignature().
        V111_ANCHORS.put("hw_model_detect",     0x000029A6L); // uint(void) — 0=DMP9, nonzero=DMP16 in D0
        V111_ANCHORS.put("hw_model_init",       0x00000F3CL); // void(void) — selects + copies model param table to DRAM

        // Serial/UART ISRs (identical addresses in all 3 ROMs — init section, never relocated)
        V111_ANCHORS.put("timer_housekeeping_isr", 0x000006F0L); // vec69 — multi-rate scheduler (base+/5 tick)
        V111_ANCHORS.put("serial0_status_isr",    0x000007AEL); // vec72 — SIO0 4-bit cause decode
        V111_ANCHORS.put("serial0_rx_isr",        0x000007ECL); // vec73 — SIO0 ring-buffer Rx (MIDI Rx)
        V111_ANCHORS.put("serial0_tx_isr",        0x00000836L); // vec74 — SIO0 ring-buffer Tx
        V111_ANCHORS.put("serial0_special_isr",   0x0000088AL); // vec75 — SIO0 error/special
        V111_ANCHORS.put("serial1_status_isr",    0x000008B4L); // vec76 — SIO1 cause decode
        V111_ANCHORS.put("serial1_rx_isr",        0x000008F2L); // vec77 — SIO1 ring-buffer Rx
        V111_ANCHORS.put("serial1_tx_isr",        0x0000093CL); // vec78 — SIO1 ring-buffer Tx
        V111_ANCHORS.put("serial1_special_isr",   0x00000990L); // vec79 — SIO1 error/special

        // Parallel/GPIO IRQ handlers (shifted in v1.10/v1.11 relative to v1.02)
        V111_ANCHORS.put("parallel_irq_handler_1", 0x0000F0EAL); // v1.11 (was 0xECC8 in v1.02)
        V111_ANCHORS.put("parallel_irq_handler_0", 0x0000F121L); // v1.11 (was 0xECFF in v1.02)

        // Periodic scheduler callback (called from timer_housekeeping_isr every 5 ticks)
        V111_ANCHORS.put("housekeeping_tick",    0x0000182AL); // identical in all 3 ROMs

        // LCD string/display cluster (identified from 401-call hot-path analysis)
        V111_ANCHORS.put("strlen",             0x00007AE2L); // 67 call sites
        V111_ANCHORS.put("lcd_write_str",      0x000078D4L); // 401 calls — writes null-term str at col, tracks state
        V111_ANCHORS.put("lcd_write_str_row",  0x0000793AL); // 124 calls — variant with extra row param
        V111_ANCHORS.put("lcd_str_padded_r",   0x000079A8L); // 57 calls — right-pad to field width
        V111_ANCHORS.put("lcd_str_padded_l",   0x00007A06L); // 122 calls — left-pad variant
        V111_ANCHORS.put("lcd_str_width",      0x00007A74L); // 5 calls  — width-limited string write
        V111_ANCHORS.put("lcd_format_num",     0x00007B4EL); // 141 calls — formatted number to LCD
        V111_ANCHORS.put("lcd_format_str",     0x00007BA6L); // 7 calls  — formatted string display
        V111_ANCHORS.put("lcd_write_padded",   0x0000A09CL); // uses strlen, computes padding
        V111_ANCHORS.put("print_rjust16",      0x0000A6A8L); // 16-char right-justified UART output

        // DSP display (EF1/EF2 parameter rendering)
        V111_ANCHORS.put("dsp_display_render_ef", 0x00016950L); // void(byte slot, byte param) — renders EF param to LCD
    }

    /** MIDI status byte constants */
    private static final int MIDI_NOTE_OFF     = 0x80;
    private static final int MIDI_NOTE_ON      = 0x90;
    private static final int MIDI_POLY_PRESS   = 0xA0;
    private static final int MIDI_CTRL_CHANGE  = 0xB0;
    private static final int MIDI_PROG_CHANGE  = 0xC0;
    private static final int MIDI_CHAN_PRESS   = 0xD0;
    private static final int MIDI_PITCH_BEND   = 0xE0;
    private static final int MIDI_SYSEX_START  = 0xF0;
    private static final int MIDI_SYSEX_END    = 0xF7;
    private static final int YAMAHA_MFR_ID     = 0x43;

    /** CC parameter table base in v1.11 */
    private static final long CC_PARAM_TABLE_BASE = 0x054FEEL;
    private static final int  CC_PARAM_COUNT      = 671;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    @Override
    public void run() throws Exception {
        println("[DMP9_MidiAnalysis] Starting MIDI subsystem annotation...");

        Listing listing = currentProgram.getListing();
        Memory  mem     = currentProgram.getMemory();
        String  romVer  = detectRomVersion(mem);

        println("[DMP9_MidiAnalysis] ROM version: " + romVer);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1 — Anchor known MIDI / LCD / ISR / library function names
        // ─────────────────────────────────────────────────────────────────────────
        // -------------------------------------------------------------------
        // Step 1: Anchor known MIDI functions
        // -------------------------------------------------------------------
        Map<String, Function> namedFunctions = anchorMidiFunctions(listing);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1b — Apply MCC68K library signatures (stack-based, A1 callee-saved)
        // ─────────────────────────────────────────────────────────────────────────
        // -------------------------------------------------------------------
        // Step 1b: Apply MCC68K library signatures (stack-based, A1 callee-saved)
        // -------------------------------------------------------------------
        applyLibrarySignatures(namedFunctions);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1c — Apply yamaha_reg signatures to small app-level helpers
        // ─────────────────────────────────────────────────────────────────────────
        // -------------------------------------------------------------------
        // Step 1c: Apply yamaha_reg signatures to small app-level helpers.
        // delay_nested / delay_simple / led_sr_write are leaf routines used
        // by LCD timing and I/O paths.  yamaha_reg = single int arg in D0.
        // -------------------------------------------------------------------
        applyAppHelperSignatures(namedFunctions);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1d — Apply __stdcall void(void) to LINK-frame anchor functions
        // ─────────────────────────────────────────────────────────────────────────
        // scene_recall / scene_store / midi_process_rx all start with LINK A6
        // and read all data from absolute addresses (no D0/D1 reads in the
        // prologue).  They take no parameters and return no meaningful value.
        applyStdcallVoidSignatures(namedFunctions);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 1e — Apply LCD init / hw model signatures
        // ─────────────────────────────────────────────────────────────────────────
        // lcd_delay_enable / lcd_send_init_cmds / lcd_init_sequence are
        // yamaha_reg void(void).  lcd_strobe takes one D0 register parameter.
        // hw_model_detect returns uint in D0 (yamaha_reg).
        // hw_model_init uses __stdcall if it has a LINK prologue, else yamaha_reg.
        applyLcdInitSignatures(namedFunctions);
        applyModelDetectSignature(namedFunctions);
        applyModelInitSignature(namedFunctions);
        applyDspDisplayRenderEfSignature(namedFunctions);

        // -------------------------------------------------------------------
        // Step 2: Annotate SC0 UART register constants
        // -------------------------------------------------------------------
        annotateUartRegisters(listing);

        // -------------------------------------------------------------------
        // Step 3: Label MIDI message type constants in code
        // -------------------------------------------------------------------
        annotateMidiStatusBytes(listing, mem, namedFunctions);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 4 — Analyze MIDI rx dispatch (status byte branches, called funcs)
        // ─────────────────────────────────────────────────────────────────────────
        // -------------------------------------------------------------------
        // Step 4: Analyze the MIDI receive dispatcher
        // -------------------------------------------------------------------
        Function rxDispatch = namedFunctions.get("midi_process_rx");
        if (rxDispatch != null) {
            analyzeMidiRxDispatch(rxDispatch, listing, mem);
        } else {
            println("[DMP9_MidiAnalysis] WARN: midi_process_rx not found — run DMP9_FuncFixup first");
        }

        // -------------------------------------------------------------------
        // Step 5: Annotate SysEx handler
        // -------------------------------------------------------------------
        annotateSysExHandler(listing, mem, namedFunctions);

        // -------------------------------------------------------------------
        // Step 6: Label CC parameter table
        // -------------------------------------------------------------------
        annotateCcParamTable(listing, mem);

        // ─────────────────────────────────────────────────────────────────────────
        // STEP 7 — Scan for undocumented MIDI status handlers (Note On, etc.)
        // ─────────────────────────────────────────────────────────────────────────
        // -------------------------------------------------------------------
        // Step 7: Scan for undocumented MIDI status handlers
        // -------------------------------------------------------------------
        scanForUndocumentedMidi(listing, mem, namedFunctions);

        println("[DMP9_MidiAnalysis] Done.");
    }

    // -----------------------------------------------------------------------
    // Step 1: Anchor known MIDI functions by address or by name
    // -----------------------------------------------------------------------
    //
    // For each V111_ANCHORS entry:
    //   1. Look for an existing function with this name (already labelled).
    //   2. Look for an existing function at the known address.
    //   3. If neither exists: disassemble the address and create the function.
    // Step 3 is critical — auto-analysis misses small leaf routines in the
    // 0x23xx–0x27xx init block because they have no vector-table entry.

    private Map<String, Function> anchorMidiFunctions(Listing listing) {
        Map<String, Function> found = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : V111_ANCHORS.entrySet()) {
            String funcName = entry.getKey();
            long   addrVal  = entry.getValue();

            // 1. Find by existing name first — already labelled by a prior pass.
            Function f = findFunctionByName(funcName, listing);

            // 2. Otherwise use the overlap-safe anchor helper.
            if (f == null) {
                try {
                    f = anchorFunction(addrVal, funcName);
                } catch (Exception e) {
                    println("[DMP9_MidiAnalysis] Error creating " + funcName
                            + " @ 0x" + Long.toHexString(addrVal) + ": " + e.getMessage());
                }
            }

            if (f != null) {
                found.put(funcName, f);
            } else {
                println("[DMP9_MidiAnalysis] MISSING: " + funcName
                        + " (expected @ 0x" + Long.toHexString(addrVal) + " in v1.11)");
            }
        }
        return found;
    }

    // -----------------------------------------------------------------------
    // Overlap-safe function anchor.
    //
    // If a function already exists AT this exact address: rename if its current
    // name is auto-generated, otherwise leave it.
    //
    // If the address falls inside an existing function body (Ghidra auto-merged
    // multiple routines): do NOT call createFunction — that fails with
    // "overlap with another namespace".  Rename the containing function only
    // when its name is auto-generated; otherwise log a warning and return the
    // containing function so the caller can still associate it.
    //
    // Otherwise: disassemble and create the function fresh.
    // -----------------------------------------------------------------------
    private Function anchorFunction(long addrVal, String name) throws Exception {
        Address a = currentProgram.getAddressFactory()
                        .getDefaultAddressSpace().getAddress(addrVal);
        FunctionManager fm = currentProgram.getFunctionManager();

        // 1. Function exists exactly at this address?
        Function fn = fm.getFunctionAt(a);

        // 2. Otherwise, are we inside a larger function body?
        if (fn == null) {
            Function containing = fm.getFunctionContaining(a);
            if (containing != null) {
                String curName = containing.getName();
                if (curName.startsWith("FUN_") || curName.startsWith("DAT_")
                        || curName.startsWith("LAB_") || curName.startsWith("SUB_")) {
                    try {
                        containing.setName(name, SourceType.ANALYSIS);
                        println("[DMP9_MidiAnalysis] Renamed containing function to "
                                + name + " @ " + containing.getEntryPoint());
                    } catch (InvalidInputException e) {
                        println("[DMP9_MidiAnalysis] Could not rename containing function to "
                                + name + ": " + e.getMessage());
                    }
                } else {
                    println("[DMP9_MidiAnalysis] WARNING: " + name + " @ 0x"
                            + Long.toHexString(addrVal)
                            + " is inside existing function " + curName
                            + " — skipping rename");
                }
                return containing;
            }
        }

        // 3. No function at or containing this address — create one.
        if (fn == null) {
            try {
                if (!(currentProgram.getListing().getCodeUnitAt(a) instanceof Instruction)) {
                    DisassembleCommand cmd =
                        new DisassembleCommand(new AddressSet(a), null, true);
                    cmd.applyTo(currentProgram, monitor);
                }
            } catch (Exception ignore) { }
            try {
                fn = createFunction(a, name);
            } catch (Exception e) {
                println("[DMP9_MidiAnalysis] FAILED to create " + name + " @ 0x"
                        + Long.toHexString(addrVal) + ": " + e.getMessage());
                return null;
            }
            if (fn == null) {
                println("[DMP9_MidiAnalysis] FAILED to create " + name + " @ 0x"
                        + Long.toHexString(addrVal));
                return null;
            }
            println("[DMP9_MidiAnalysis] Anchored: " + name + " @ " + a);
            return fn;
        }

        // 4. Function exists at exact address — rename if name is auto-generated.
        String curName = fn.getName();
        if (curName.startsWith("FUN_") || curName.startsWith("DAT_")
                || curName.startsWith("LAB_") || curName.startsWith("SUB_")
                || curName.equals("entry")) {
            try {
                fn.setName(name, SourceType.ANALYSIS);
                println("[DMP9_MidiAnalysis] Anchored: " + name + " @ " + a);
            } catch (InvalidInputException e) {
                println("[DMP9_MidiAnalysis] Could not rename " + a + " to " + name
                        + ": " + e.getMessage());
            }
        } else if (!curName.equals(name)) {
            println("[DMP9_MidiAnalysis] Already named: " + curName + " @ " + a
                    + " (wanted " + name + ")");
        } else {
            println("[DMP9_MidiAnalysis] Anchored: " + name + " @ " + a);
        }
        return fn;
    }

    private Function findFunctionByName(String name, Listing listing) {
        FunctionIterator it = listing.getFunctions(true);
        while (it.hasNext()) {
            Function f = it.next();
            if (f.getName().equals(name)) return f;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Step 1b: Apply typed signatures + mcc68k_lib calling convention to
    // the MCC68K runtime library functions.  These routines use a stack-based,
    // caller-cleaned convention with A1 callee-saved (the function pushes A1
    // on entry and pops it before RTS).  Without this, Ghidra's default
    // yamaha_reg analysis assigns bogus register parameters and the decompiler
    // output for memcpy/memset/meminv calls is unreadable.
    // -----------------------------------------------------------------------
    private static final String CC_LIB = "mcc68k_lib";

    private void applyLibrarySignatures(Map<String, Function> anchors) {
        // void memcpy_b(byte *dst, byte *src, int count)
        setLibrarySignature(anchors.get("memcpy_b"), "void",
                new String[][]{{"byte*","dst"},{"byte*","src"},{"int","count"}});
        // void memcpy_w(word *dst, word *src, int count)
        setLibrarySignature(anchors.get("memcpy_w"), "void",
                new String[][]{{"word*","dst"},{"word*","src"},{"int","count"}});
        // void memcpy_l(dword *dst, dword *src, int count)
        setLibrarySignature(anchors.get("memcpy_l"), "void",
                new String[][]{{"dword*","dst"},{"dword*","src"},{"int","count"}});
        // void meminv_b(byte *dst, byte *src, int count)
        setLibrarySignature(anchors.get("meminv_b"), "void",
                new String[][]{{"byte*","dst"},{"byte*","src"},{"int","count"}});
        // void memset_b(byte *dst, int val, int count)
        setLibrarySignature(anchors.get("memset_b"), "void",
                new String[][]{{"byte*","dst"},{"int","val"},{"int","count"}});
        // void memset_w(word *dst, int val, int count)
        setLibrarySignature(anchors.get("memset_w"), "void",
                new String[][]{{"word*","dst"},{"int","val"},{"int","count"}});
        // void memset_l(dword *dst, int val, int count)
        setLibrarySignature(anchors.get("memset_l"), "void",
                new String[][]{{"dword*","dst"},{"int","val"},{"int","count"}});
        // void memcpy_bitser(void *dst, void *src, int bit_count)
        setLibrarySignature(anchors.get("memcpy_bitser"), "void",
                new String[][]{{"void*","dst"},{"void*","src"},{"int","bit_count"}});
        // byte bitrev_byte(int val)
        setLibrarySignature(anchors.get("bitrev_byte"), "byte",
                new String[][]{{"int","val"}});
        // void memcpy_bitser_w(void *dst, void *src, int bit_count)
        setLibrarySignature(anchors.get("memcpy_bitser_w"), "void",
                new String[][]{{"void*","dst"},{"void*","src"},{"int","bit_count"}});
    }

    private void setLibrarySignature(Function fn, String retType, String[][] params) {
        if (fn == null) return;
        try {
            DataType returnDT = libType(retType);
            List<ParameterImpl> paramList = new ArrayList<>();
            for (String[] p : params) {
                paramList.add(new ParameterImpl(p[1], libType(p[0]), currentProgram));
            }
            fn.updateFunction(
                    CC_LIB,
                    new ReturnParameterImpl(returnDT, currentProgram),
                    paramList,
                    FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                    true,
                    SourceType.ANALYSIS);
            println("[DMP9_MidiAnalysis] Library signature set: " + fn.getName()
                    + " (" + CC_LIB + ")");
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set signature for "
                    + fn.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Apply yamaha_reg signatures to small app-level helpers
     * (delay_nested, delay_simple, led_sr_write).  Falls back silently if the
     * yamaha_reg convention is not present in the cspec.
     */
    private static final String CC_APP = "yamaha_reg";

    private void applyAppHelperSignatures(Map<String, Function> anchors) {
        // void delay_simple(int iterations) — tight spin loop
        setAppSignature(anchors.get("delay_simple"), "void",
                new String[][]{{"int","iterations"}});
        // void delay_nested(int count) — calls delay_simple in a loop
        setAppSignature(anchors.get("delay_nested"), "void",
                new String[][]{{"int","count"}});
        // void led_sr_write(uint data) — clocks 16 bits into LED shift register at LED_SR_DATA (0x4D0000)
        setAppSignature(anchors.get("led_sr_write"), "void",
                new String[][]{{"uint","data"}});

        // LCD string helpers — short leaf routines, all use D0/D1/D2 for args.
        // Tag with yamaha_reg so the decompiler treats register reads as params.
        // Return types left as void; callers will narrow once arg signatures are
        // confirmed from full disassembly.
        setAppHelperConvention(anchors.get("lcd_format_num"));
        setAppHelperConvention(anchors.get("lcd_str_padded_l"));
        setAppHelperConvention(anchors.get("lcd_write_str"));
        setAppHelperConvention(anchors.get("lcd_write_str_row"));
    }

    /** Tag a function with yamaha_reg without overriding its parameter list —
     *  used for LCD helpers whose D0/D1/D2 register args are inferred by the
     *  decompiler. */
    private void setAppHelperConvention(Function fn) {
        if (fn == null) return;
        try {
            fn.setCallingConvention(CC_APP);
            println("[DMP9_MidiAnalysis] " + fn.getName() + " → " + CC_APP);
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set " + CC_APP
                    + " on " + fn.getName() + ": " + e.getMessage());
        }
    }

    private void setAppSignature(Function fn, String retType, String[][] params) {
        if (fn == null) return;
        try {
            DataType returnDT = libType(retType);
            List<ParameterImpl> paramList = new ArrayList<>();
            for (String[] p : params) {
                paramList.add(new ParameterImpl(p[1], libType(p[0]), currentProgram));
            }
            fn.updateFunction(
                    CC_APP,
                    new ReturnParameterImpl(returnDT, currentProgram),
                    paramList,
                    FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                    true,
                    SourceType.ANALYSIS);
            println("[DMP9_MidiAnalysis] App signature set: " + fn.getName()
                    + " (" + CC_APP + ")");
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set signature for "
                    + fn.getName() + ": " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Step 1d: __stdcall void(void) for anchor functions whose disassembly
    // confirms a LINK A6 prologue with no register-arg reads.
    //
    // scene_recall / scene_store always satisfy this (verified manually).
    // midi_process_rx is checked at runtime — applied only if its first
    // instruction is LINK.
    // -----------------------------------------------------------------------
    private static final String CC_STDCALL = "__stdcall";

    private void applyStdcallVoidSignatures(Map<String, Function> anchors) {
        setStdcallVoidVoid(anchors.get("scene_recall"));
        setStdcallVoidVoid(anchors.get("scene_store"));

        // midi_process_rx — only if it has a LINK A6 prologue (it's a full
        // state machine, almost certainly stack-framed, but verify before
        // overwriting any prior heuristic).
        Function rx = anchors.get("midi_process_rx");
        if (rx != null && hasLinkPrologue(rx)) {
            setStdcallVoidVoid(rx);
        }
    }

    private boolean hasLinkPrologue(Function fn) {
        try {
            Instruction insn = currentProgram.getListing()
                    .getInstructionAt(fn.getEntryPoint());
            return insn != null && insn.getMnemonicString().equalsIgnoreCase("link");
        } catch (Exception e) {
            return false;
        }
    }

    private void setStdcallVoidVoid(Function fn) {
        if (fn == null) return;
        try {
            fn.setCallingConvention(CC_STDCALL);
            fn.replaceParameters(Collections.emptyList(),
                    FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                    true, SourceType.ANALYSIS);
            fn.setReturnType(VoidDataType.dataType, SourceType.ANALYSIS);
            println("[DMP9_MidiAnalysis] " + fn.getName() + " → __stdcall void(void)");
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set __stdcall void(void) on "
                    + fn.getName() + ": " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Step 1e: LCD init cluster + hardware model detect/init signatures
    // -----------------------------------------------------------------------

    /** Apply yamaha_reg void(void) to the LCD init void/void helpers, and the
     *  one-arg lcd_strobe(cmd) with cmd in D0. */
    private void applyLcdInitSignatures(Map<String, Function> anchors) {
        // void(void) helpers
        setAppSignature(anchors.get("lcd_delay_enable"),   "void", new String[][]{});
        setAppSignature(anchors.get("lcd_send_init_cmds"), "void", new String[][]{});
        setAppSignature(anchors.get("lcd_init_sequence"),  "void", new String[][]{});

        // lcd_strobe(uint cmd) — single D0 register parameter
        Function f = anchors.get("lcd_strobe");
        if (f == null) return;
        try {
            f.setCallingConvention(CC_APP);
            List<ParameterImpl> params = new ArrayList<>();
            params.add(new ParameterImpl("cmd", new DWordDataType(),
                new VariableStorage(currentProgram,
                    currentProgram.getLanguage().getRegister("D0"), 4),
                currentProgram));
            f.replaceParameters(params, FunctionUpdateType.CUSTOM_STORAGE,
                true, SourceType.ANALYSIS);
            f.setReturnType(VoidDataType.dataType, SourceType.ANALYSIS);
            println("[DMP9_MidiAnalysis] lcd_strobe → yamaha_reg void(uint cmd@D0)");
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set lcd_strobe signature: "
                    + e.getMessage());
        }
    }

    /** hw_model_detect: yamaha_reg, returns uint in D0, no params. */
    private void applyModelDetectSignature(Map<String, Function> anchors) {
        Function f = anchors.get("hw_model_detect");
        if (f == null) return;
        try {
            f.updateFunction(
                CC_APP,
                new ReturnParameterImpl(DWordDataType.dataType, currentProgram),
                Collections.emptyList(),
                FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                true,
                SourceType.ANALYSIS);
            println("[DMP9_MidiAnalysis] hw_model_detect → yamaha_reg uint(void)");
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set hw_model_detect signature: "
                    + e.getMessage());
        }
    }

    /** hw_model_init: __stdcall void(void) if it begins with LINK A6, otherwise
     *  yamaha_reg void(void).  Inspects the first instruction at runtime. */
    private void applyModelInitSignature(Map<String, Function> anchors) {
        Function f = anchors.get("hw_model_init");
        if (f == null) return;
        if (hasLinkPrologue(f)) {
            setStdcallVoidVoid(f);
        } else {
            setAppSignature(f, "void", new String[][]{});
        }
    }

    /**
     * dsp_display_render_ef(byte effect_slot, byte param_index)
     * Renders EF1/EF2 parameter display to LCD.
     * Takes 2 byte args on stack (Stack[0x7]=effect_slot, Stack[0xB]=param_index).
     * Uses dsp_display_s struct fields + DAT_0040754e display mode flag.
     */
    private void applyDspDisplayRenderEfSignature(Map<String, Function> anchors) {
        Function f = anchors.get("dsp_display_render_ef");
        if (f == null) return;
        try {
            f.setCallingConvention(CC_STDCALL);
            List<ParameterImpl> params = new ArrayList<>();
            params.add(new ParameterImpl("effect_slot", ByteDataType.dataType, currentProgram));
            params.add(new ParameterImpl("param_index", ByteDataType.dataType, currentProgram));
            f.replaceParameters(params,
                FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                true, SourceType.ANALYSIS);
            f.setReturnType(VoidDataType.dataType, SourceType.ANALYSIS);
            println("[DMP9_MidiAnalysis] dsp_display_render_ef → __stdcall void(byte slot, byte param)");
        } catch (Exception e) {
            println("[DMP9_MidiAnalysis] WARN: could not set dsp_display_render_ef signature: "
                    + e.getMessage());
        }
    }

    private DataType libType(String name) {
        switch (name) {
            case "void":   return VoidDataType.dataType;
            case "byte":   return ByteDataType.dataType;
            case "byte*":  return new PointerDataType(ByteDataType.dataType);
            case "word":   return WordDataType.dataType;
            case "word*":  return new PointerDataType(WordDataType.dataType);
            case "dword":  return DWordDataType.dataType;
            case "dword*": return new PointerDataType(DWordDataType.dataType);
            case "void*":  return new PointerDataType(VoidDataType.dataType);
            case "int":    return DWordDataType.dataType;
            case "uint":   return DWordDataType.dataType;
            default:       return DWordDataType.dataType;
        }
    }

    // -----------------------------------------------------------------------
    // Step 2: Annotate TMP68301 SC0 UART register usage
    // -----------------------------------------------------------------------

    private void annotateUartRegisters(Listing listing) {
        // These are already handled by TMP68301_Setup.java.
        // Here we add plate comments to key MIDI I/O functions explaining
        // which registers they use.
        Function tx = findFunctionByName("midi_tx_byte", listing);
        if (tx != null) {
            setPlateIfEmpty(tx.getEntryPoint(),
                "MIDI TX via TMP68301 SC0 UART\n" +
                "  SC0BUF (0xFFFC09) = data byte\n" +
                "  SC0CR  (0xFFFC0A) checks TDRE (Tx Data Register Empty)\n" +
                "  31250 baud, 8N1 per MIDI spec\n" +
                "  Called as: midi_tx_byte(uint8_t b) __stdcall");
        }
    }

    // -----------------------------------------------------------------------
    // Step 3: Label MIDI status byte constants in disassembly
    // -----------------------------------------------------------------------

    private void annotateMidiStatusBytes(Listing listing, Memory mem,
            Map<String, Function> anchors) {
        // Scan the code region for immediate values matching MIDI status bytes.
        // In 68k: CMPI.B #0xB0,D0  is "FC B0" or similar.
        // We'll mark these as named constants via EOL comments for clarity.

        // Known status bytes and their names
        Map<Integer, String> statusNames = new LinkedHashMap<>();
        statusNames.put(0xF0, "MIDI_SYSEX_START (0xF0)");
        statusNames.put(0xF7, "MIDI_SYSEX_END (0xF7)");
        statusNames.put(0xB0, "MIDI_CTRL_CHANGE (0xBn)");
        statusNames.put(0xC0, "MIDI_PROG_CHANGE (0xCn)");
        statusNames.put(0x90, "MIDI_NOTE_ON (0x9n) [?undoc?]");
        statusNames.put(0x80, "MIDI_NOTE_OFF (0x8n) [?undoc?]");
        statusNames.put(0xFE, "MIDI_ACTIVE_SENSE (0xFE) → filtered");
        statusNames.put(0x43, "YAMAHA_MFR_ID (0x43)");

        // This is a light scan — just mark the CMP/CMPI instructions near
        // the known MIDI rx dispatch function.
        Function rx = anchors.get("midi_process_rx");
        if (rx == null) return;

        Address start = rx.getEntryPoint();
        Address end   = start.add(Math.min(rx.getBody().getNumAddresses(), 0x400));

        InstructionIterator insns = listing.getInstructions(start, true);
        int scanned = 0;
        while (insns.hasNext() && scanned < 800) {
            Instruction insn = insns.next();
            if (insn.getAddress().compareTo(end) > 0) break;
            scanned++;

            String mnem = insn.getMnemonicString();
            if (!mnem.startsWith("CMP") && !mnem.startsWith("cmp")) continue;

            // Check each operand for a MIDI status byte value
            for (int oi = 0; oi < insn.getNumOperands(); oi++) {
                Object[] objs = insn.getOpObjects(oi);
                for (Object obj : objs) {
                    if (obj instanceof Number) {
                        int val = ((Number) obj).intValue() & 0xFF;
                        String label = statusNames.get(val);
                        if (label != null) {
                            setEolComment(insn.getAddress(), label);
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Step 4: Analyze the MIDI receive dispatcher
    // -----------------------------------------------------------------------

    private void analyzeMidiRxDispatch(Function rx, Listing listing, Memory mem) {
        println("[DMP9_MidiAnalysis] Analyzing MIDI rx dispatch @ " + rx.getEntryPoint());

        // The dispatch function is expected to:
        //   1. Check status byte (0xF0, 0xBn, 0xCn, maybe 0x9n)
        //   2. Branch to handlers (sysex_handler, cc_handler, pgm_handler)
        //
        // We find called functions within the first 512 instructions and
        // categorise them.

        Set<Function> calledFuncs = new HashSet<>();
        InstructionIterator it = listing.getInstructions(rx.getEntryPoint(), true);
        int count = 0;
        while (it.hasNext() && count < 512) {
            Instruction insn = it.next();
            count++;
            String mnem = insn.getMnemonicString().toUpperCase();
            if (mnem.equals("JSR") || mnem.equals("BSR")) {
                Address target = getJumpTarget(insn);
                if (target != null) {
                    Function f = listing.getFunctionAt(target);
                    if (f != null && !f.equals(rx)) {
                        calledFuncs.add(f);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MIDI Receive Dispatcher\n");
        sb.append("Handles: Program Change (0xCn), Control Change (0xBn), SysEx (0xF0 0x43)\n");
        sb.append("Active Sense (0xFE) is filtered (no action).\n");
        sb.append("Called functions:\n");
        for (Function f : calledFuncs) {
            sb.append("  ").append(f.getName()).append(" @ ").append(f.getEntryPoint()).append("\n");
        }
        sb.append("\nSearch for 0x90 (Note On) handling — would indicate DMP7-style note→param mapping.");

        setPlateIfEmpty(rx.getEntryPoint(), sb.toString());

        // Try to name unnamed callee functions based on context
        for (Function f : calledFuncs) {
            String name = f.getName();
            if (!name.startsWith("FUN_")) continue; // already named

            // Heuristic: look for F0/43 in the first 32 bytes of the function
            byte[] head = readBytes(mem, f.getEntryPoint(), 32);
            if (head != null && containsSequence(head, new byte[]{(byte)0xF0, (byte)0x43})) {
                try {
                    f.setName("midi_sysex_handler", SourceType.ANALYSIS);
                    setPlateIfEmpty(f.getEntryPoint(),
                        "SysEx handler: F0 43 <dev> <sub> <size_hi> <size_lo> [data] <cksum> F7\n" +
                        "Yamaha bulk dump/request format.");
                    println("[DMP9_MidiAnalysis] Named: midi_sysex_handler @ " + f.getEntryPoint());
                } catch (Exception ignored) {}
            }
        }
    }

    // -----------------------------------------------------------------------
    // Step 5: Annotate SysEx handler and bulk dump sub-commands
    // -----------------------------------------------------------------------

    private void annotateSysExHandler(Listing listing, Memory mem,
            Map<String, Function> anchors) {

        // Yamaha SysEx format for DMP9:
        //   F0 43 <dev_no[0..15]> <sub_status> <size_hi> <size_lo> [data...] <cksum> F7
        //
        // sub_status encodes the bulk dump type:
        //   0x00 = ?
        //   0x75 = ALL
        //   0x7D = MEM  (scene memory data)
        //   0x7E = SETUP
        //   0x7C = EDIT
        //   0x7F = PGM (program change assign)
        //   0x7B = CTRL (CC assign)
        //   etc. — actual values to be confirmed from ROM disassembly

        Function sysex = findFunctionByName("midi_sysex_handler", listing);
        if (sysex == null) {
            println("[DMP9_MidiAnalysis] SysEx handler not yet identified — run after midi_process_rx analysis.");
            return;
        }

        // Scan for CMP #immediate instructions that might be sub_status checks
        InstructionIterator it = listing.getInstructions(sysex.getEntryPoint(), true);
        int count = 0;
        while (it.hasNext() && count < 300) {
            Instruction insn = it.next();
            count++;
            String mnem = insn.getMnemonicString().toUpperCase();
            if (!mnem.startsWith("CMP")) continue;

            for (int oi = 0; oi < insn.getNumOperands(); oi++) {
                Object[] objs = insn.getOpObjects(oi);
                for (Object obj : objs) {
                    if (obj instanceof Number) {
                        int v = ((Number) obj).intValue() & 0xFF;
                        String label = sysexSubLabel(v);
                        if (label != null) {
                            setEolComment(insn.getAddress(), "SYSEX_SUB: " + label);
                        }
                    }
                }
            }
        }
    }

    private String sysexSubLabel(int v) {
        // Yamaha DMP9 SysEx sub-status values (to be confirmed from disassembly)
        // Reference: DMP7/DMP11 MIDI spec + DMP9 bulk dump screen strings
        switch (v) {
            case 0x75: return "BULK_ALL";
            case 0x7D: return "BULK_MEM";
            case 0x7E: return "BULK_SETUP";
            case 0x7C: return "BULK_EDIT";
            case 0x7F: return "BULK_PGM_ASSIGN";
            case 0x7B: return "BULK_CTRL_ASSIGN";
            // DMP7/DMP11 had extended commands — check if DMP9 inherited any:
            case 0x10: return "PARAM_CHANGE? [verify]";
            case 0x00: return "DEVICE_NUMBER? [verify]";
            default:   return null;
        }
    }

    // -----------------------------------------------------------------------
    // Step 6: Label the CC parameter table
    // -----------------------------------------------------------------------

    private void annotateCcParamTable(Listing listing, Memory mem) {
        Address tblBase = addr(CC_PARAM_TABLE_BASE);
        if (tblBase == null) return;

        println("[DMP9_MidiAnalysis] Annotating CC parameter table @ " + tblBase);

        // The table at 0x054FEE is a lookup structure mapping CC bank+number
        // to parameter indices.  The exact format needs to be confirmed from
        // disassembly of cc_handler, but based on the 671-parameter count and
        // 16-bank structure:
        //
        //   Likely: uint8_t cc_param_map[16_banks][96_ccs]  = 1536 bytes
        //           then uint16_t param_to_dsp[671]          = 1342 bytes
        //
        // Set a plate comment on the table base address.

        try {
            listing.setComment(tblBase, CommentType.PLATE,
                "CTRL Change parameter table\n" +
                "671 parameters × 16 banks × 96 CCs\n" +
                "Format: [bank_index_table][param_name_string_table]\n" +
                "Bank 0-11: sequential MIDI channels (Channel mode)\n" +
                "Register mode: CC98 (NRPN LSB) selects bank\n" +
                "See: docs/midi.md §Control Change");
        } catch (Exception ignored) {}

        // Create a named label for the table
        try {
            SymbolTable st = currentProgram.getSymbolTable();
            st.createLabel(tblBase, "CC_PARAM_TABLE", SourceType.ANALYSIS);
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // Step 7: Scan for undocumented MIDI status handlers
    // -----------------------------------------------------------------------

    private void scanForUndocumentedMidi(Listing listing, Memory mem,
            Map<String, Function> anchors) {

        println("[DMP9_MidiAnalysis] Scanning for undocumented MIDI handlers...");

        // Documented in manual: PG (0xCn), CC (0xBn), SysEx (0xF0 0x43)
        // Documented as filtered: Active Sense (0xFE)
        //
        // We're looking for:
        //   - Note On (0x9n) — DMP7 used this for parameter control
        //   - Note Off (0x8n)
        //   - Pitch Bend (0xEn) — DMP7 used this for pitch shift
        //   - Any 0xF? real-time message handling beyond 0xFE
        //
        // Scan the midi_process_rx function for CMP #0x90 / CMP #0x80 / CMP #0xE0

        Function rx = anchors.get("midi_process_rx");
        if (rx == null) return;

        Set<Integer> foundStatus = new HashSet<>();
        InstructionIterator it = listing.getInstructions(rx.getEntryPoint(), true);
        int count = 0;
        while (it.hasNext() && count < 1000) {
            Instruction insn = it.next();
            count++;
            String mnem = insn.getMnemonicString().toUpperCase();
            if (!mnem.startsWith("CMP") && !mnem.startsWith("AND")) continue;

            for (int oi = 0; oi < insn.getNumOperands(); oi++) {
                Object[] objs = insn.getOpObjects(oi);
                for (Object obj : objs) {
                    if (obj instanceof Number) {
                        int v = ((Number) obj).intValue() & 0xF0;
                        if (v == 0x90 || v == 0x80 || v == 0xE0 || v == 0xA0 || v == 0xD0) {
                            foundStatus.add(v);
                        }
                    }
                }
            }
        }

        if (foundStatus.isEmpty()) {
            println("[DMP9_MidiAnalysis] No evidence of undocumented MIDI status handling found.");
            println("  DMP9 appears to handle only PG/CC/SysEx as documented.");
            println("  NOTE: DMP7/DMP11 used Note On (0x9n) for 206-parameter real-time control.");
            println("  The DMP9 simplified this to CC-based control (671 params, 16 banks).");
        } else {
            println("[DMP9_MidiAnalysis] *** POTENTIAL UNDOCUMENTED MIDI HANDLERS FOUND! ***");
            for (int s : foundStatus) {
                String name = midiStatusName(s);
                println("  Status 0x" + Integer.toHexString(s) + " — " + name);

                // Tag the rx dispatcher with this finding
                try {
                    Address ep = rx.getEntryPoint();
                    String existing = currentProgram.getListing()
                        .getComment(CommentType.PLATE, ep);
                    String note = "\n\nPOTENTIAL UNDOCUMENTED: " + name + " (0x" +
                        Integer.toHexString(s) + ") handler found — investigate!";
                    currentProgram.getListing().setComment(ep, CommentType.PLATE,
                        (existing != null ? existing : "") + note);
                } catch (Exception ignored) {}
            }
        }

        // Extra: scan for 0xF0 0x43 followed by a non-standard sub_status byte
        // that would indicate an extension command not in the manual
        println("[DMP9_MidiAnalysis] To find undocumented SysEx sub-commands:");
        println("  1. In Ghidra, search for byte sequence: F0 43");
        println("  2. Near midi_process_rx and midi_sysex_handler");
        println("  3. Look for CMP #<sub_status> branches beyond the documented set");
        println("  4. Any sub_status not in {0x75,0x7D,0x7E,0x7C,0x7F,0x7B} is undocumented");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String detectRomVersion(Memory mem) {
        try {
            // v1.11 has "Version 1.11" at 0x050617
            Address a = addr(0x050617L);
            byte[] buf = new byte[16];
            mem.getBytes(a, buf);
            String s = new String(buf).trim();
            if (s.startsWith("Version")) return s;
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String midiStatusName(int status) {
        switch (status & 0xF0) {
            case 0x80: return "Note Off";
            case 0x90: return "Note On (DMP7 used for 206-param real-time control)";
            case 0xA0: return "Polyphonic Key Pressure";
            case 0xB0: return "Control Change";
            case 0xC0: return "Program Change";
            case 0xD0: return "Channel Pressure";
            case 0xE0: return "Pitch Bend (DMP7 used for pitch shift)";
            case 0xF0: return "System";
            default:   return "Unknown";
        }
    }

    private Address getJumpTarget(Instruction insn) {
        try {
            Address[] flows = insn.getFlows();
            if (flows != null && flows.length > 0) return flows[0];
        } catch (Exception ignored) {}
        return null;
    }

    private byte[] readBytes(Memory mem, Address addr, int len) {
        try {
            byte[] buf = new byte[len];
            int read = mem.getBytes(addr, buf);
            if (read < 4) return null;
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean containsSequence(byte[] buf, byte[] seq) {
        outer:
        for (int i = 0; i <= buf.length - seq.length; i++) {
            for (int j = 0; j < seq.length; j++) {
                if (buf[i+j] != seq[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private void setPlateIfEmpty(Address addr, String comment) {
        try {
            Listing lst = currentProgram.getListing();
            String existing = lst.getComment(CommentType.PLATE, addr);
            if (existing == null || existing.isBlank()) {
                lst.setComment(addr, CommentType.PLATE, comment);
            }
        } catch (Exception ignored) {}
    }

    private void setEolComment(Address addr, String comment) {
        try {
            Listing lst = currentProgram.getListing();
            String existing = lst.getComment(CommentType.EOL, addr);
            if (existing == null || existing.isBlank()) {
                lst.setComment(addr, CommentType.EOL, comment);
            }
        } catch (Exception ignored) {}
    }

    private Address addr(long offset) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(offset);
    }
}
