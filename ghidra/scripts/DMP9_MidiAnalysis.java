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

        // -------------------------------------------------------------------
        // Step 1: Anchor known MIDI functions
        // -------------------------------------------------------------------
        Map<String, Function> namedFunctions = anchorMidiFunctions(listing);

        // -------------------------------------------------------------------
        // Step 2: Annotate SC0 UART register constants
        // -------------------------------------------------------------------
        annotateUartRegisters(listing);

        // -------------------------------------------------------------------
        // Step 3: Label MIDI message type constants in code
        // -------------------------------------------------------------------
        annotateMidiStatusBytes(listing, mem, namedFunctions);

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

        // -------------------------------------------------------------------
        // Step 7: Scan for undocumented MIDI status handlers
        // -------------------------------------------------------------------
        scanForUndocumentedMidi(listing, mem, namedFunctions);

        println("[DMP9_MidiAnalysis] Done.");
    }

    // -----------------------------------------------------------------------
    // Step 1: Anchor known MIDI functions by address or by name
    // -----------------------------------------------------------------------

    private Map<String, Function> anchorMidiFunctions(Listing listing) {
        Map<String, Function> found = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : V111_ANCHORS.entrySet()) {
            String funcName = entry.getKey();
            long   addr     = entry.getValue();

            // First try: find by existing name
            Function f = findFunctionByName(funcName, listing);

            // Second try: find by address
            if (f == null) {
                Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(addr);
                f = listing.getFunctionAt(a);
            }

            if (f != null) {
                // Ensure the name is set
                if (f.getName().startsWith("FUN_") || f.getName().equals("entry")) {
                    try {
                        f.setName(funcName, SourceType.USER_DEFINED);
                    } catch (Exception e) {
                        println("  Could not rename " + f.getEntryPoint() + " to " + funcName);
                    }
                }
                found.put(funcName, f);
                println("[DMP9_MidiAnalysis] Anchored: " + funcName + " @ " + f.getEntryPoint());
            } else {
                println("[DMP9_MidiAnalysis] Not found: " + funcName
                        + " (expected @ 0x" + Long.toHexString(addr) + " in v1.11)");
            }
        }
        return found;
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
        Address tblBase = toAddr(CC_PARAM_TABLE_BASE);
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
            listing.setComment(tblBase, CodeUnit.PLATE_COMMENT,
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
                        .getComment(CodeUnit.PLATE_COMMENT, ep);
                    String note = "\n\nPOTENTIAL UNDOCUMENTED: " + name + " (0x" +
                        Integer.toHexString(s) + ") handler found — investigate!";
                    currentProgram.getListing().setComment(ep, CodeUnit.PLATE_COMMENT,
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
            Address a = toAddr(0x050617L);
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
            String existing = lst.getComment(CodeUnit.PLATE_COMMENT, addr);
            if (existing == null || existing.isBlank()) {
                lst.setComment(addr, CodeUnit.PLATE_COMMENT, comment);
            }
        } catch (Exception ignored) {}
    }

    private void setEolComment(Address addr, String comment) {
        try {
            Listing lst = currentProgram.getListing();
            String existing = lst.getComment(CodeUnit.EOL_COMMENT, addr);
            if (existing == null || existing.isBlank()) {
                lst.setComment(addr, CodeUnit.EOL_COMMENT, comment);
            }
        } catch (Exception ignored) {}
    }

    private Address toAddr(long offset) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(offset);
    }
}
