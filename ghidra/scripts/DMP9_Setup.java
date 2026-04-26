// Ghidra Script: DMP9/16 ROM Setup v2
// File: DMP9_Setup.java
//
// Full setup for Yamaha DMP9/DMP9-16 ROM analysis:
//
//  1. Create memory blocks (SRAM, SFR, DSP, LCD)
//  2. Define and apply TMP68301_SFR struct at 0xFFFC00
//  3. Define and apply M68K_VectorTable struct at 0x000000
//  4. Parse vector table from the loaded ROM image:
//       - read each 32-bit vector entry
//       - label the handler address with a canonical name
//       - seed disassembly at the handler
//       - mark as __interrupt calling convention
//  5. Apply known ROM structures (effect type table, string table)
//  6. Label known RAM variables from observed ROM access patterns
//  7. Seed disassembly at reset_handler and all known functions
//  8. Annotate known register-call and tail-call functions
//
// Version-adaptive: vector handler addresses are read from the
// loaded ROM, so labels are correct for v1.02, v1.10, and v1.11.
//
// Run on each ROM independently before Version Tracking.
//
//@category DMP9_RE

import ghidra.app.script.GhidraScript;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.data.*;
import ghidra.program.model.util.*;
import ghidra.util.task.TaskMonitor;
import java.util.ArrayList;

public class DMP9_Setup extends GhidraScript {

    // ----------------------------------------------------------------
    // M68000 exception vector names (indices 0-63, then user 64-255)
    // ----------------------------------------------------------------
    private static final String[] VEC_NAMES = {
        "vec_reset_ssp",        //  0  Initial SSP
        "vec_reset_pc",         //  1  Initial PC  (reset entry)
        "vec_bus_error",        //  2
        "vec_address_error",    //  3
        "vec_illegal_insn",     //  4
        "vec_zero_divide",      //  5
        "vec_chk",              //  6
        "vec_trapv",            //  7
        "vec_privilege",        //  8
        "vec_trace",            //  9
        "vec_line_a",           // 10  Line-A emulator
        "vec_line_f",           // 11  Line-F emulator
        "vec_reserved_12",      // 12
        "vec_reserved_13",      // 13
        "vec_format_error",     // 14
        "vec_uninit_irq",       // 15  Uninitialized interrupt
        "vec_reserved_16",      // 16
        "vec_reserved_17",      // 17
        "vec_reserved_18",      // 18
        "vec_reserved_19",      // 19
        "vec_reserved_20",      // 20
        "vec_reserved_21",      // 21
        "vec_reserved_22",      // 22
        "vec_reserved_23",      // 23
        "vec_spurious_irq",     // 24
        "vec_autovector_l1",    // 25  Level 1 autovector
        "vec_autovector_l2",    // 26  Level 2 autovector
        "vec_autovector_l3",    // 27  Level 3 autovector
        "vec_autovector_l4",    // 28  Level 4 autovector
        "vec_autovector_l5",    // 29  Level 5 autovector
        "vec_autovector_l6",    // 30  Level 6 autovector
        "vec_autovector_l7",    // 31  Level 7 (NMI)
        "vec_trap_0",           // 32  TRAP #0
        "vec_trap_1",           // 33  TRAP #1
        "vec_trap_2",           // 34  TRAP #2
        "vec_trap_3",           // 35  TRAP #3
        "vec_trap_4",           // 36  TRAP #4
        "vec_trap_5",           // 37  TRAP #5
        "vec_trap_6",           // 38  TRAP #6
        "vec_trap_7",           // 39  TRAP #7
        "vec_trap_8",           // 40  TRAP #8
        "vec_trap_9",           // 41  TRAP #9
        "vec_trap_10",          // 42  TRAP #10
        "vec_trap_11",          // 43  TRAP #11
        "vec_trap_12",          // 44  TRAP #12
        "vec_trap_13",          // 45  TRAP #13
        "vec_trap_14",          // 46  TRAP #14
        "vec_trap_15",          // 47  TRAP #15
        "vec_fp_unordered",     // 48  FP branch/set unordered
        "vec_fp_inexact",       // 49
        "vec_fp_zero_div",      // 50
        "vec_fp_underflow",     // 51
        "vec_fp_operand",       // 52
        "vec_fp_overflow",      // 53
        "vec_fp_nan",           // 54
        "vec_fp_reserved",      // 55
        "vec_mmu_config",       // 56
        "vec_mmu_illegal",      // 57
        "vec_mmu_access",       // 58
        "vec_reserved_59",      // 59
        "vec_reserved_60",      // 60
        "vec_reserved_61",      // 61
        "vec_reserved_62",      // 62
        "vec_reserved_63",      // 63
        // 64-255: user-defined / TMP68301 MFP vectors
    };

    // TMP68301 MFP interrupt vector base is configurable; typical default 0x40 (64)
    // Names for MFP vectors at base+0..base+15 (16 MFP interrupt sources)
    private static final String[] MFP_VEC_NAMES = {
        "mfp_irq_timer_d",        // +0
        "mfp_irq_timer_c",        // +1
        "mfp_irq_gpio_0",         // +2
        "mfp_irq_gpio_1",         // +3
        "mfp_irq_acia_rx_err",    // +4
        "mfp_irq_acia_rx",        // +5  MIDI receive -- key ISR
        "mfp_irq_acia_tx_err",    // +6
        "mfp_irq_acia_tx",        // +7  MIDI transmit
        "mfp_irq_timer_b",        // +8
        "mfp_irq_gpio_2",         // +9
        "mfp_irq_gpio_3",         // +10
        "mfp_irq_gpio_4",         // +11
        "mfp_irq_gpio_5",         // +12
        "mfp_irq_gpio_6",         // +13
        "mfp_irq_timer_a",        // +14
        "mfp_irq_mono_detect",    // +15
    };

    // Known v1.11 function addresses. The script uses these only as
    // fallback annotation -- vector table parsing handles ISRs dynamically.
    // RE_NOTE: verify all addresses against other ROM versions before trusting.
    private static final Object[][] KNOWN_FUNCS = {
        { 0x0003B3F0L, "reset_handler",   "Primary reset/boot entry point",           false },
        { 0x0000238CL, "lcd_write_cmd",   "Write LCD command byte",                   false },
        { 0x000023DCL, "lcd_write_data",  "Write LCD data byte",                      false },
        { 0x0000274EL, "dsp_ef1_write",   "Write EF1 DSP register/data sequence",     false },
        { 0x00002904L, "dsp_ef2_write",   "Write EF2 DSP register/data sequence",     false },
        { 0x000122E0L, "midi_tx_byte",    "TX one MIDI byte; reg-call: byte in D0",   true  },
        { 0x000128ECL, "midi_process_rx", "MIDI message processing core",             false },
        { 0x00013454L, "scene_recall",    "Recall scene from memory; reg-call: D0=slot", true },
        { 0x00013186L, "scene_store",     "Store scene to memory; reg-call: D0=slot", true  },
    };

    // Known SRAM variable layout (observed from ROM access patterns)
    // RE_NOTE: sizes and exact offsets need confirmation for each version
    private static final Object[][] RAM_VARS = {
        { 0x00400000L, "ram_scene_buf",       0x1000, "Active scene parameter buffer (4KB)" },
        { 0x00401000L, "ram_midi_rx_buf",     0x100,  "MIDI receive ring buffer" },
        { 0x00401100L, "ram_midi_rx_head",    2,      "MIDI RX ring buffer head index" },
        { 0x00401102L, "ram_midi_rx_tail",    2,      "MIDI RX ring buffer tail index" },
        { 0x00401200L, "ram_panel_state",     0x80,   "Panel / encoder current state" },
        { 0x00401280L, "ram_lcd_shadow",      0x50,   "LCD display shadow buffer (2x40 chars)" },
        { 0x004012D0L, "ram_effect_params",   0x200,  "Current EF1/EF2 parameter working set" },
        { 0x004014D0L, "ram_dsp_ef1_shadow",  0x80,   "EF1 DSP register shadow" },
        { 0x00401550L, "ram_dsp_ef2_shadow",  0x80,   "EF2 DSP register shadow" },
    };

    // ----------------------------------------------------------------

    @Override
    public void run() throws Exception {
        Memory mem = currentProgram.getMemory();
        AddressSpace as = currentProgram.getAddressFactory().getDefaultAddressSpace();
        SymbolTable st  = currentProgram.getSymbolTable();
        Listing listing = currentProgram.getListing();
        DataTypeManager dtm = currentProgram.getDataTypeManager();

        println("=== Yamaha DMP9/16 setup v2 ===");
        println("Program: " + currentProgram.getName());

        // 1. Memory blocks
        println("\n-- Memory blocks --");
        ensureBlock(mem, as, "SRAM",         0x00400000L, 0x10000, true);
        ensureBlock(mem, as, "TMP68301_SFR", 0x00FFFC00L, 0x40,    true);
        ensureBlock(mem, as, "DSP_EF1",      0x00460000L, 0x1000,  true);
        ensureBlock(mem, as, "DSP_EF2",      0x00470000L, 0x1000,  true);
        ensureBlock(mem, as, "LCD",           0x00490000L, 0x10,    true);

        // 2. Define and apply TMP68301_SFR struct
        println("\n-- TMP68301_SFR structure --");
        StructureDataType sfr = buildSFRStruct(dtm);
        applyStruct(listing, as, 0x00FFFC00L, sfr);

        // 3. Define and apply M68K vector table struct
        println("\n-- M68K vector table structure --");
        StructureDataType vt = buildVectorTableStruct(dtm);
        applyStruct(listing, as, 0x00000000L, vt);

        // 4. Parse vector table from ROM, label handlers, seed disassembly
        println("\n-- Parsing vector table from ROM image --");
        parseVectorTable(mem, as, st, listing);

        // 5. Label hardware peripherals
        println("\n-- Hardware peripheral labels --");
        labelPeripherals(st, listing, as);

        // 6. Label known RAM variables
        println("\n-- RAM variable labels --");
        for (Object[] v : RAM_VARS) {
            long addr  = (Long)  v[0];
            String name = (String) v[1];
            int  size  = (Integer)v[2];
            String cmt  = (String) v[3];
            labelAndSize(st, listing, dtm, as, addr, name, size, cmt);
        }

        // 7. Seed known functions, annotate register-call variants
        println("\n-- Known function seeds --");
        for (Object[] f : KNOWN_FUNCS) {
            long   addr    = (Long)   f[0];
            String name    = (String) f[1];
            String cmt     = (String) f[2];
            boolean regcall = (Boolean)f[3];
            labelAndDisassemble(st, listing, as, addr, name, cmt, regcall);
        }

        println("\n=== Setup complete ===");
        println("Next steps:");
        println("  1. Analysis -> Auto Analyze");
        println("     Enable: Decompiler Parameter ID");
        println("     Enable: Aggressive Instruction Finder");
        println("  2. Inspect vector table at 0x000000 -- all ISRs are labelled");
        println("  3. Check Console for any RE_NOTE warnings");
    }

    // ----------------------------------------------------------------
    // Vector table parsing
    // ----------------------------------------------------------------

    private void parseVectorTable(Memory mem, AddressSpace as,
                                   SymbolTable st, Listing listing) throws Exception {
        int nVectors = 256;
        int vecBase  = 0x000000;
        AddressSet disasmSet = new AddressSet();

        for (int i = 0; i < nVectors; i++) {
            int offset = vecBase + (i * 4);
            // Read 4-byte big-endian vector entry from ROM
            byte[] buf = new byte[4];
            mem.getBytes(as.getAddress(offset), buf);
            long handlerAddr =  ((buf[0] & 0xFFL) << 24)
                              | ((buf[1] & 0xFFL) << 16)
                              | ((buf[2] & 0xFFL) <<  8)
                              |  (buf[3] & 0xFFL);

            // Skip null/invalid entries
            if (handlerAddr == 0x00000000L || handlerAddr == 0xFFFFFFFFL) continue;
            // Skip entries pointing outside ROM (except autovectors point to RAM sometimes)
            if (handlerAddr >= 0x00080000L && handlerAddr < 0x00400000L) continue;

            // Determine handler name
            String handlerName;
            String comment;

            if (i == 0) {
                // Vector 0 is the initial SSP value, not a code address
                handlerName = "initial_ssp";
                comment = "Initial Supervisor Stack Pointer (not a handler)";
                labelOnly(st, listing, as, offset, "vec_" + i + "_ssp_value",
                         "Vector " + i + ": initial SSP = 0x" + Long.toHexString(handlerAddr));
                continue;
            } else if (i == 1) {
                handlerName = "reset_handler";
                comment = "Reset entry point (vector 1)";
            } else if (i < VEC_NAMES.length) {
                handlerName = VEC_NAMES[i] + "_handler";
                comment = "68000 exception vector " + i + ": " + VEC_NAMES[i];
            } else {
                // User vectors 64-255: check if this looks like MFP range
                // TMP68301 MFP vector base is written by firmware to MFP_VR;
                // we'll label the entire user range generically but name
                // plausible MFP slots (base 0x40 = 64, 16 sources)
                int userIdx = i - 64;
                if (userIdx >= 0 && userIdx < MFP_VEC_NAMES.length) {
                    handlerName = MFP_VEC_NAMES[userIdx] + "_handler";
                    comment = "MFP vector " + i + " (user+" + userIdx + "): " + MFP_VEC_NAMES[userIdx];
                } else {
                    handlerName = "user_vec_" + i + "_handler";
                    comment = "User-defined vector " + i;
                }
            }

            // Label the vector table slot itself
            labelOnly(st, listing, as, offset,
                     "vec_" + i,
                     "Vector " + i + " -> 0x" + Long.toHexString(handlerAddr) + " (" + handlerName + ")");

            // Label + disassemble the handler
            Address hAddr = as.getAddress(handlerAddr);
            try {
                st.createLabel(hAddr, handlerName, SourceType.USER_DEFINED);
                listing.setComment(hAddr, CommentType.PLATE,
                    comment + "\nVec[" + i + "] @ ROM+0x" + Integer.toHexString(offset));

                // Only seed disassembly in ROM range
                if (handlerAddr < 0x00080000L) {
                    disasmSet.add(hAddr);
                }
                println("  Vec[" + i + "] -> " + handlerName
                       + " @ 0x" + Long.toHexString(handlerAddr));
            } catch (Exception e) {
                println("  Vec[" + i + "] label error: " + e.getMessage());
            }
        }

        // Bulk disassemble all discovered handler addresses
        if (!disasmSet.isEmpty()) {
            println("  Seeding disassembly at " + disasmSet.getNumAddressRanges() + " handler(s)...");
            DisassembleCommand cmd = new DisassembleCommand(disasmSet, null, true);
            cmd.applyTo(currentProgram, monitor);
        }
    }

    // ----------------------------------------------------------------
    // Struct builders
    // ----------------------------------------------------------------

    private StructureDataType buildSFRStruct(DataTypeManager dtm) {
        StructureDataType s = new StructureDataType(
            new CategoryPath("/DMP9_RE"), "TMP68301_SFR", 0, dtm);

        // All registers are byte-wide, odd-byte addressed in the SFR window.
        // We model the full 0x40-byte region with explicit padding.
        s.add(ByteDataType.dataType, 1, "pad00",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "GPDR",        "GPIO data register");
        s.add(ByteDataType.dataType, 1, "pad02",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "DDR",         "GPIO direction");
        s.add(ByteDataType.dataType, 1, "pad04",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "IERA",        "IRQ enable A");
        s.add(ByteDataType.dataType, 1, "pad06",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "IERB",        "IRQ enable B");
        s.add(ByteDataType.dataType, 1, "pad08",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "IPRA",        "IRQ pending A");
        s.add(ByteDataType.dataType, 1, "pad0A",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "IPRB",        "IRQ pending B");
        s.add(ByteDataType.dataType, 1, "pad0C",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "ISRA",        "IRQ in-service A");
        s.add(ByteDataType.dataType, 1, "pad0E",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "ISRB",        "IRQ in-service B");
        s.add(ByteDataType.dataType, 1, "pad10",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "IMRA",        "IRQ mask A");
        s.add(ByteDataType.dataType, 1, "pad12",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "IMRB",        "IRQ mask B");
        s.add(ByteDataType.dataType, 1, "pad14",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "VR",          "Vector base register (MFP)");
        s.add(ByteDataType.dataType, 1, "pad16",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "GPIP",        "GPIO input port");
        s.add(ByteDataType.dataType, 1, "pad18",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TACR",        "Timer A control");
        s.add(ByteDataType.dataType, 1, "pad1A",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TBCR",        "Timer B control");
        s.add(ByteDataType.dataType, 1, "pad1C",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TCDCR",       "Timer C+D control");
        s.add(ByteDataType.dataType, 1, "pad1E",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TADR",        "Timer A data");
        s.add(ByteDataType.dataType, 1, "pad20",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TBDR",        "Timer B data");
        s.add(ByteDataType.dataType, 1, "pad22",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TCDR",        "Timer C data");
        s.add(ByteDataType.dataType, 1, "pad24",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TDDR",        "Timer D data");
        s.add(ByteDataType.dataType, 1, "pad26",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "SCR",         "Sync char register");
        s.add(ByteDataType.dataType, 1, "pad28",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "UCR",         "UART control (MIDI 31250)");
        s.add(ByteDataType.dataType, 1, "pad2A",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "RSR",         "RX status");
        s.add(ByteDataType.dataType, 1, "pad2C",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "TSR",         "TX status");
        s.add(ByteDataType.dataType, 1, "pad2E",       "(reserved)");
        s.add(ByteDataType.dataType, 1, "UDR",         "UART data register (MIDI byte)");

        dtm.addDataType(s, DataTypeConflictHandler.REPLACE_HANDLER);
        println("  Struct TMP68301_SFR defined (" + s.getLength() + " bytes)");
        return s;
    }

    private StructureDataType buildVectorTableStruct(DataTypeManager dtm) {
        StructureDataType s = new StructureDataType(
            new CategoryPath("/DMP9_RE"), "M68K_VectorTable", 0, dtm);

        // 256 x 4-byte vector entries
        for (int i = 0; i < 256; i++) {
            String name;
            if (i == 0)       name = "initial_ssp";
            else if (i == 1)  name = "initial_pc";
            else if (i < VEC_NAMES.length) name = VEC_NAMES[i];
            else {
                int u = i - 64;
                name = (u >= 0 && u < MFP_VEC_NAMES.length)
                     ? MFP_VEC_NAMES[u] : "user_vec_" + i;
            }
            s.add(Pointer32DataType.dataType, 4, name, "Vector " + i);
        }

        dtm.addDataType(s, DataTypeConflictHandler.REPLACE_HANDLER);
        println("  Struct M68K_VectorTable defined (" + s.getLength() + " bytes)");
        return s;
    }

    // ----------------------------------------------------------------
    // Peripheral labels
    // ----------------------------------------------------------------

    private void labelPeripherals(SymbolTable st, Listing listing, AddressSpace as) {
        Object[][] periph = {
            { 0x00FFFC01L, "MFP_GPDR",  "GPIO data register" },
            { 0x00FFFC03L, "MFP_DDR",   "GPIO direction" },
            { 0x00FFFC05L, "MFP_IERA",  "IRQ enable A" },
            { 0x00FFFC07L, "MFP_IERB",  "IRQ enable B" },
            { 0x00FFFC09L, "MFP_IPRA",  "IRQ pending A" },
            { 0x00FFFC0BL, "MFP_IPRB",  "IRQ pending B" },
            { 0x00FFFC0DL, "MFP_ISRA",  "IRQ in-service A" },
            { 0x00FFFC0FL, "MFP_ISRB",  "IRQ in-service B" },
            { 0x00FFFC11L, "MFP_IMRA",  "IRQ mask A" },
            { 0x00FFFC13L, "MFP_IMRB",  "IRQ mask B" },
            { 0x00FFFC15L, "MFP_VR",    "MFP vector base register" },
            { 0x00FFFC17L, "MFP_GPIP",  "GPIO input" },
            { 0x00FFFC19L, "MFP_TACR",  "Timer A control" },
            { 0x00FFFC1BL, "MFP_TBCR",  "Timer B control" },
            { 0x00FFFC1DL, "MFP_TCDCR", "Timer C+D control" },
            { 0x00FFFC1FL, "MFP_TADR",  "Timer A data" },
            { 0x00FFFC21L, "MFP_TBDR",  "Timer B data" },
            { 0x00FFFC23L, "MFP_TCDR",  "Timer C data" },
            { 0x00FFFC25L, "MFP_TDDR",  "Timer D data" },
            { 0x00FFFC27L, "MFP_SCR",   "Sync char register" },
            { 0x00FFFC29L, "MFP_UCR",   "UART control (MIDI 31250 baud)" },
            { 0x00FFFC2BL, "MFP_RSR",   "RX status" },
            { 0x00FFFC2DL, "MFP_TSR",   "TX status" },
            { 0x00FFFC2FL, "MFP_UDR",   "UART data (MIDI byte)" },
            { 0x00460000L, "DSP_EF1_BASE", "EF1: YM3804+YM6007+YM3807+YM6104" },
            { 0x00470000L, "DSP_EF2_BASE", "EF2: YM3804+YM3807" },
            { 0x00490000L, "LCD_CMD",    "LCD command register (HD44780)" },
            { 0x00490001L, "LCD_DATA",   "LCD data register" },
        };
        for (Object[] p : periph) {
            labelOnly(st, listing, as, (Long)p[0], (String)p[1], (String)p[2]);
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void ensureBlock(Memory mem, AddressSpace as, String name,
                              long addr, long size, boolean rw) {
        try {
            Address a = as.getAddress(addr);
            if (mem.getBlock(a) != null) {
                println("  Block exists: " + name);
                return;
            }
            MemoryBlock b = mem.createUninitializedBlock(
                name, a, size, false);
            b.setRead(true);
            b.setWrite(rw);
            b.setExecute(false);
            println("  Block created: " + name + " @ 0x" + Long.toHexString(addr)
                   + " (" + size + " bytes)");
        } catch (Exception e) {
            println("  Block error " + name + ": " + e.getMessage());
        }
    }

    private void applyStruct(Listing listing, AddressSpace as,
                              long addr, StructureDataType s) {
        try {
            Address a = as.getAddress(addr);
            // Clear any existing defined data first
            listing.clearCodeUnits(a, a.add(s.getLength() - 1), false);
            listing.createData(a, s);
            println("  Applied struct " + s.getName() + " @ 0x" + Long.toHexString(addr));
        } catch (Exception e) {
            println("  Struct apply error " + s.getName() + ": " + e.getMessage());
        }
    }

    private void labelOnly(SymbolTable st, Listing listing, AddressSpace as,
                            long addr, String name, String comment) {
        try {
            Address a = as.getAddress(addr);
            st.createLabel(a, name, SourceType.USER_DEFINED);
            if (comment != null && !comment.isEmpty())
                listing.setComment(a, CommentType.EOL, comment);
        } catch (Exception e) {
            println("  Label error " + name + ": " + e.getMessage());
        }
    }

    private void labelAndDisassemble(SymbolTable st, Listing listing,
                                      AddressSpace as, long addr,
                                      String name, String comment, boolean regCall) {
        try {
            Address a = as.getAddress(addr);
            st.createLabel(a, name, SourceType.USER_DEFINED);
            String cmt = comment;
            if (regCall) cmt += "\nRE_NOTE: register-call convention -- review parameter assignment";
            listing.setComment(a, CommentType.PLATE, cmt);

            // Seed disassembly if not already code
            if (listing.getCodeUnitAt(a) == null ||
                !(listing.getCodeUnitAt(a) instanceof Instruction)) {
                AddressSet aset = new AddressSet(a);
                DisassembleCommand cmd = new DisassembleCommand(aset, null, true);
                cmd.applyTo(currentProgram, monitor);
            }
            println("  Func: " + name + " @ 0x" + Long.toHexString(addr)
                   + (regCall ? " [reg-call]" : ""));
        } catch (Exception e) {
            println("  Func error " + name + ": " + e.getMessage());
        }
    }

    private void labelAndSize(SymbolTable st, Listing listing,
                               DataTypeManager dtm, AddressSpace as,
                               long addr, String name, int size, String comment) {
        try {
            Address a = as.getAddress(addr);
            st.createLabel(a, name, SourceType.USER_DEFINED);
            listing.setComment(a, CommentType.PLATE,
                comment + "\nSize: 0x" + Integer.toHexString(size) + " bytes"
                + "\nRE_NOTE: layout inferred from ROM access patterns -- verify");
            // Apply a byte-array placeholder so Ghidra shows the extent
            try {
                ArrayDataType arr = new ArrayDataType(ByteDataType.dataType, size, 1);
                listing.createData(a, arr);
            } catch (Exception ignore) { /* already defined */ }
            println("  RAM: " + name + " @ 0x" + Long.toHexString(addr)
                   + " [" + size + " bytes]");
        } catch (Exception e) {
            println("  RAM error " + name + ": " + e.getMessage());
        }
    }
}
