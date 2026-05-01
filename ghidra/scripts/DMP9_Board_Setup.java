// DMP9_Board_Setup.java
// Ghidra script: Auto-annotate Yamaha DMP9 / DMP16 board-specific peripherals.
//
// Complements TMP68301_Setup.java by labelling the external peripherals
// whose address decoding is handled by off-chip logic circuits:
//
//   0x490000  LCD_CMD       HD44780 instruction register (RS=0)
//   0x490001  LCD_DATA      HD44780 data register (RS=1)
//   0x460000  DSP_EF1_BASE  Effects 1: YM3804 + YM6007 + YM3807 + YM6104
//   0x470000  DSP_EF2_BASE  Effects 2: YM3804 + YM3807
//   0x400000  DSP_DRAM      64 KB shared DRAM — CPU stack/RAM + DSP delay buffer
//
// Board architecture summary (from service manual N8-B7 block diagrams):
//   IC24  = TMP68301A (CPU)
//   IC34  = ROM (single firmware chip)
//   IC35/36 = Shared DRAM at 0x400000 — CPU stack (SP init confirmed from ROM)
//             + DSP delay/effects buffer (dual-purpose, time-division bus)
//   IC66-68 = DSP coefficient SRAM (within DSP section)
//   IC50    = PLD performing address decoding (CS, clock selects)
//   IC58/101 = DIR2 digital input receivers
//   IC43/100 = DIT2 digital output transmitters
//   IC45/49/96 + XTAL = PLL @ 24.576 MHz (512FS×48kHz)
//
// Additionally scans the disassembly for constant byte writes to LCD_CMD
// and appends a descriptive pre-comment explaining what each HD44780
// command does (e.g. "Clear Display", "Set DDRAM addr=0x40 [line 2]").
//
// Compatibility: Ghidra 11.x
//   Uses CommentType enum instead of deprecated CodeUnit int constants.
//   Uses getOpObjects(int) for operand inspection instead of removed methods.
//
// Sources:
//   dmp9_board_regs.h  (companion header, this project)
//   HD44780U datasheet, Hitachi Semiconductor
//   tmp68301_regs.h / TMP68301_Setup.java (base TMP68301 annotations)
//   Yamaha DMP9/DMP16 firmware analysis + service manual N8-B7
//
// Usage:
//   1. Open the DMP9 / DMP16 ROM image in Ghidra and auto-analyse it.
//   2. Optionally run TMP68301_Setup.java first to label CPU peripherals.
//   3. Run this script via Script Manager.
//   4. The script will:
//        a. Create / rename memory blocks for shared DRAM, DSP EF1/EF2, LCD
//        b. Place labels and plate/EOL comments at each peripheral address
//        c. Scan the entire code listing for writes to LCD_CMD and add
//           pre-comments decoding each constant command byte.
//
// @author   Derived from DMP9_Setup.java / TMP68301_Setup.java pattern
// @category Analysis.Hardware
// @keybinding
// @menupath
// @toolbar

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.scalar.*;
import ghidra.program.model.symbol.*;
import ghidra.util.exception.*;

/**
 * DMP9_Board_Setup — Labels Yamaha DMP9/DMP16 board-level peripherals
 *
 * Annotates the off-chip hardware mapped outside the TMP68301 SFR space:
 * shared DRAM at 0x400000 (CPU stack + DSP delay buffer), DSP effects
 * blocks EF1/EF2 at 0x460000/0x470000, and the HD44780 LCD controller at
 * 0x490000/0x490001. Also labels SIO ring-buffer variables, LCD shadow
 * RAM, and decodes constant LCD_CMD writes to readable HD44780 commands.
 *
 * Run order: DMP9_FuncFixup → TMP68301_Setup → DMP9_Board_Setup
 *            → DMP9_MidiAnalysis → DMP9_LibMatch → DMP9_VersionTrack
 *
 * Target: Yamaha DMP9/DMP16, TMP68301AF-16 (68000 @ 16MHz), MCC68K v4.x compiler
 * Ghidra: 12.0.4+
 *
 * See: ghidra/docs/DMP9_Board_Setup.md for full documentation.
 */
public class DMP9_Board_Setup extends GhidraScript {

    // -------------------------------------------------------------------------
    // Board-specific fixed addresses (from dmp9_board_regs.h)
    // -------------------------------------------------------------------------
    private static final long LCD_CMD_ADDR      = 0x490000L;
    private static final long LCD_DATA_ADDR     = 0x490001L;
    private static final long LCD_CTRL_ADDR     = 0x4A0000L;  // word; bit 8 = E strobe
    private static final long LED_SR_DATA_ADDR  = 0x4D0000L;  // 16-bit LED shift register data port
    private static final long DSP_EF1_BASE_ADDR = 0x460000L;
    private static final long DSP_EF2_BASE_ADDR = 0x470000L;
    private static final long DSP_DRAM_BASE     = 0x400000L;  // IC35/IC36 — shared CPU+DSP DRAM
    private static final long DSP_DRAM_SIZE     = 0x010000L;  // 64 KB
    private static final long DSP_BLOCK_SIZE    = 0x010000L;  // 64 KB window per DSP block

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------
    @Override
    public void run() throws Exception {

        println("DMP9_Board_Setup: Annotating Yamaha DMP9/DMP16 board peripherals...");

        DataTypeManager dtm = currentProgram.getDataTypeManager();
        DataType byteType = dtm.getDataType("/byte");
        if (byteType == null) byteType = ByteDataType.dataType;

        // 1. Annotate shared DRAM block (CPU stack + DSP delay buffer)
        annotateSharedDRAM();

        // 1b. Name typed DRAM variables (scene state, MIDI ring buffers)
        nameDramVariables();

        // 2. Annotate DSP EF1 and EF2
        annotateDSP(byteType);

        // 3. Annotate LCD registers
        annotateLCD(byteType);

        // 3b. Annotate LED shift register port
        annotateLED();

        // 4. Scan listing for constant writes to LCD_CMD and decode them
        int cmdCount = decodeLcdCommandWrites();

        println("DMP9_Board_Setup: Done.");
        println("  SHARED_DRAM at 0x" + Long.toHexString(DSP_DRAM_BASE)
                + "  (IC35/IC36, 64 KB — CPU stack confirmed + DSP delay buffer)");
        println("  LCD_CMD  labelled at 0x" + Long.toHexString(LCD_CMD_ADDR));
        println("  LCD_DATA labelled at 0x" + Long.toHexString(LCD_DATA_ADDR));
        println("  LED_SR_DATA labelled at 0x" + Long.toHexString(LED_SR_DATA_ADDR));
        println("  DSP_EF1  labelled at 0x" + Long.toHexString(DSP_EF1_BASE_ADDR));
        println("  DSP_EF2  labelled at 0x" + Long.toHexString(DSP_EF2_BASE_ADDR));
        println("  LCD command decode: " + cmdCount + " write sites annotated.");
    }


    // =========================================================================
    // SHARED DRAM (0x400000) — IC35/IC36  [CONFIRMED dual-purpose]
    //
    // CONFIRMED: The ROM reset vector places the TMP68301 stack pointer just
    // above 0x400000.  This proves the CPU has direct read/write access.
    //
    // The same physical ICs are also used by the DSP SECTION (per service
    // manual block diagrams): DSP2 instances 3, 4, and 5 access them for
    // audio delay lines and effects buffers via a shared/time-division bus.
    //
    // Probable partition (verify from firmware):
    //   0x400000 – 0x40xxxx  : DSP delay buffer (lower portion, DSP-owned)
    //   0x40xxxx – 0x40FFFF  : CPU stack + working variables (SP near top)
    // =========================================================================
    private void annotateSharedDRAM() throws Exception {
        Address base = absAddr(DSP_DRAM_BASE);
        createMemoryBlockIfAbsent("SHARED_DRAM", base, DSP_DRAM_SIZE,
                "DMP9/DMP16 Shared DRAM (IC35+IC36), 64 KB — CONFIRMED dual-purpose:\n" +
                "  CPU: stack + working RAM  (SP initialised just above 0x400000 at reset)\n" +
                "  DSP: delay/effects buffer (DSP2 instances 3/4/5 for reverb, chorus)\n" +
                "  Shared via time-division bus.  DSP uses lower portion; CPU stack at top.");

        createLabelWithComments(base, "SHARED_DRAM_BASE",
                "Shared DRAM base (IC35/IC36) — CPU + DSP dual-purpose\n" +
                "\n" +
                "CONFIRMED: ROM reset vector sets SP just above 0x400000.\n" +
                "The TMP68301 CPU uses the upper portion of this 64 KB window\n" +
                "as its stack and working variable area.\n" +
                "\n" +
                "The DSP2 section also uses this same DRAM for audio delay lines\n" +
                "(reverb tail, chorus, etc.) via a shared/time-division bus.\n" +
                "\n" +
                "Probable layout:\n" +
                "  0x400000 – boundary  DSP delay buffer (size depends on effects config)\n" +
                "  boundary – 0x40FFFF  CPU stack and working variables\n" +
                "Trace the first MOVE.L writes here to find the boundary.",
                "IC35+IC36 Shared DRAM — CPU stack/RAM + DSP delay buffer");

        // --- LCD display RAM variables (identified from code analysis of 0x0078D4 cluster) ---
        // The hot LCD write function (0x0078D4, 401 call sites) references these addresses directly.
        // Layout hypothesis: two 64-byte tables (4 rows × 16 cols = 64 cells each)
        labelRamVar(0x00407982L, "lcd_col_table",
                "LCD column-position lookup table (4×16 LCD = 64 entries)\n" +
                "Written by lcd_write_str (0x0078D4) when char==0x10 (cursor position cmd).\n" +
                "Maps display cell index to HD44780 DDRAM address.");
        labelRamVar(0x004079C2L, "lcd_col_table_2",
                "LCD column table second half (offset +0x40 from lcd_col_table).\n" +
                "May be row-2/row-3 section or alternate display mode.");
        labelRamVar(0x00407A02L, "lcd_shadow_buf",
                "LCD shadow buffer — 64 bytes, mirrors the physical HD44780 DDRAM.\n" +
                "Written by lcd_write_str (0x0078D4) on every character output.\n" +
                "Allows the CPU to read back displayed content without querying the LCD.");
        labelRamVar(0x0040B6EAL, "lcd_mode_flag",
                "LCD mode/control flag.\n" +
                "Checked by lcd_write_str: if negative (bit7 set), character output is suppressed.\n" +
                "May control display-on/off, update pending, or blink state.");
        labelRamVar(0x0040B8A2L, "lcd_state_flag1",
                "LCD state flag 1 (busy/update state).\n" +
                "Checked before writing chars < ASCII 0x30 (control characters).\n" +
                "Non-zero: suppress output of control chars.");
        labelRamVar(0x0040B8A3L, "lcd_state_flag2",
                "LCD state flag 2 (paired with lcd_state_flag1).\n" +
                "Both flags zero: allow all characters through to LCD hardware.");

        // ---------------------------------------------------------------
        // Timer ISR software tick counters
        // ---------------------------------------------------------------
        labelRamVar(0x0040781EL, "sw_tick",
                "Free-running software tick counter.\n" +
                "Incremented every timer_housekeeping_isr (Timer0/1 driven).\n" +
                "Used with DIVUW #5 + SWAP to derive slower /5 tick rate.");
        labelRamVar(0x00407822L, "sw_tick_slow",
                "Slow software tick counter.\n" +
                "Incremented every 5 base ticks (every 5th timer_housekeeping_isr call).");
        labelRamVar(0x0040788CL, "sw_status",
                "Software status word.\n" +
                "Bit 0x0800: when set, calls housekeeping_tick() from the timer ISR\n" +
                "(full MOVEM.L save/restore around the call).");
        labelRamVar(0x00407AACL, "tick_mod2",
                "2-state modulo counter (incremented each tick, wraps at 2).");
        labelRamVar(0x00407AADL, "countdown_0",
                "Software countdown timer 0 (decremented to zero each tick).");
        labelRamVar(0x00407AB1L, "countdown_1",
                "Software countdown timer 1.");
        labelRamVar(0x00407AB2L, "countdown_2",
                "Software countdown timer 2.");
        labelRamVar(0x00407AAAL, "tick_gate",
                "Gates tick_mod2 increment: non-zero enables tick_mod2_count to grow.");
        labelRamVar(0x00407AABL, "tick_mod2_count",
                "Incremented when tick_gate is non-zero.");

        // ---------------------------------------------------------------
        // SIO0 (MIDI) ISR flag bytes and ring buffers
        // ---------------------------------------------------------------
        labelRamVar(0x0040B681L, "sio0_flags",
                "SIO0 (MIDI) event/cause register.\n" +
                "bits[3:0] = 4-bit cause code read from 0xFFFD87 (shift right 3, mask 0x0F).\n" +
                "bit4 = Rx ring buffer overflow.\n" +
                "bit6 = special condition / error (set by serial0_special_isr).");
        labelRamVar(0x0040B682L, "sio1_flags",
                "SIO1 event/cause register (same layout as sio0_flags).");
        labelRamVar(0x0040B684L, "sio_event_src",
                "Last serial event source: 1=SIO0, 2=SIO1.\n" +
                "Written by status-decode and Rx/special ISRs.");
        labelRamVar(0x0040B685L, "sio0_tx_busy",
                "Non-zero while SIO0 Tx ring buffer is draining (Tx ISR active).");
        labelRamVar(0x0040B686L, "sio1_tx_busy",
                "Non-zero while SIO1 Tx ring buffer is draining.");
        labelRamVar(0x0040ABE8L, "sio0_last_tx_byte",
                "Last byte written to SIO0 data register (0xFFFD89) by serial0_tx_isr.");
        labelRamVar(0x0040ABE9L, "sio1_last_tx_byte",
                "Last byte written to SIO1 data register (0xFFFD99) by serial1_tx_isr.");

        // SIO0 ring buffers
        labelRamVar(0x00407D9EL, "sio0_rx_buf_start",
                "SIO0 Rx ring buffer start (0x1004 bytes, ~4 KB).\n" +
                "Filled by serial0_rx_isr; drained by MIDI parser (midi_process_rx).");
        labelRamVar(0x00408DA2L, "sio0_rx_buf_end",    "SIO0 Rx ring buffer end.");
        labelRamVar(0x00408D9EL, "sio0_rx_wr_ptr",
                "SIO0 Rx ring buffer write pointer (4-byte pointer, ISR-maintained).\n" +
                "Points to next slot to write; wraps to sio0_rx_buf_start at sio0_rx_buf_end.");
        labelRamVar(0x00408FB6L, "sio0_tx_buf_start",
                "SIO0 Tx ring buffer start (0x1000 bytes, 4 KB).\n" +
                "Filled by mainline MIDI send; drained by serial0_tx_isr.");
        labelRamVar(0x00409FB6L, "sio0_tx_buf_end",    "SIO0 Tx ring buffer end.");
        labelRamVar(0x00409FBAL, "sio0_tx_rd_ptr",
                "SIO0 Tx ring buffer read pointer (4-byte pointer, Tx ISR-maintained).");

        // SIO1 ring buffers
        labelRamVar(0x00408DA6L, "sio1_rx_buf_start",  "SIO1 Rx ring buffer start (0x104 bytes).");
        labelRamVar(0x00408EAAL, "sio1_rx_buf_end",    "SIO1 Rx ring buffer end.");
        labelRamVar(0x00408EA6L, "sio1_rx_wr_ptr",     "SIO1 Rx ring buffer write pointer.");
        labelRamVar(0x00409FBEL, "sio1_tx_buf_start",  "SIO1 Tx ring buffer start (0x600 bytes).");
        labelRamVar(0x0040A5BEL, "sio1_tx_buf_end",    "SIO1 Tx ring buffer end.");
        labelRamVar(0x0040A5C2L, "sio1_tx_rd_ptr",     "SIO1 Tx ring buffer read pointer.");
    }

    // =========================================================================
    // DRAM variable naming pass
    //
    // Creates typed labels in SHARED_DRAM (0x400000-0x40FFFF) for variables
    // identified by static analysis but not yet named.  The SHARED_DRAM block
    // is created by annotateSharedDRAM() above; this pass only adds labels
    // and applies data types — it does not create memory blocks.
    //
    // Scene state vars come from disassembly of scene_recall:
    //   0x40042C..0x40042E  live (current) panel state
    //   0x40BD86..0x40BD88  shadow copy compared against on recall
    // =========================================================================
    private void nameDramVariables() {
        // Scene control state (live + shadow copy)
        nameDramVar(absAddr(0x40042CL), "scene_cur_flags",     ByteDataType.dataType);
        nameDramVar(absAddr(0x40042DL), "scene_cur_param_b",   ByteDataType.dataType);
        nameDramVar(absAddr(0x40042EL), "scene_cur_param_c",   ByteDataType.dataType);
        nameDramVar(absAddr(0x40BD86L), "scene_shadow_flags",  ByteDataType.dataType);
        nameDramVar(absAddr(0x40BD87L), "scene_shadow_param_b",ByteDataType.dataType);
        nameDramVar(absAddr(0x40BD88L), "scene_shadow_param_c",ByteDataType.dataType);

        // DRAM base + MIDI ring-buffer head/tail pointers (top of 64 KB DRAM)
        nameDramVar(absAddr(0x400000L), "dram_base",           null);
        nameDramVar(absAddr(0x40FFE0L), "midi_tx_ring_head",   DWordDataType.dataType);
        nameDramVar(absAddr(0x40FFE4L), "midi_tx_ring_tail",   DWordDataType.dataType);
        nameDramVar(absAddr(0x40FFE8L), "midi_rx_ring_head",   DWordDataType.dataType);
        nameDramVar(absAddr(0x40FFECL), "midi_rx_ring_tail",   DWordDataType.dataType);

        // ROM model param tables (labels only — already in ROM block, no memory
        // block creation needed).  Selected by hw_model_init based on
        // hw_model_detect's return value.
        try {
            SymbolTable st = currentProgram.getSymbolTable();
            st.createLabel(absAddr(0x5052EL), "model_params_dmp9",  SourceType.ANALYSIS);
            st.createLabel(absAddr(0x505B6L), "model_params_dmp16", SourceType.ANALYSIS);
        } catch (Exception e) {
            println("Warning: could not label model param tables: " + e.getMessage());
        }

        // DRAM destination for the active model params (copied by hw_model_init)
        nameDramVar(absAddr(0x407500L), "model_params_active",
                new ArrayDataType(ByteDataType.dataType, 0x88, 1));
    }

    /**
     * Create a named, typed label at a DRAM address.  Removes any existing
     * auto-generated DAT_ symbol first so the new name becomes primary.
     */
    private void nameDramVar(Address addr, String name, DataType dt) {
        try {
            SymbolTable st = currentProgram.getSymbolTable();
            Symbol existing = st.getPrimarySymbol(addr);
            if (existing != null && existing.getName().startsWith("DAT_")) {
                existing.delete();
            }
            st.createLabel(addr, name, SourceType.ANALYSIS);
            if (dt != null) {
                Listing listing = currentProgram.getListing();
                listing.clearCodeUnits(addr, addr.add(dt.getLength() - 1L), false);
                listing.createData(addr, dt);
            }
        } catch (Exception e) {
            println("Warning: could not label " + name + " @ " + addr + ": " + e.getMessage());
        }
    }

    /** Create a label + plate comment at a RAM variable address in SHARED_DRAM. */
    private void labelRamVar(long absAddr, String name, String comment) throws Exception {
        Address a = absAddr(absAddr);
        SymbolTable st = currentProgram.getSymbolTable();
        Listing listing = currentProgram.getListing();
        try { st.createLabel(a, name, SourceType.USER_DEFINED); }
        catch (Exception e) { println("  WARN: could not label " + name + " @ " + a); }
        if (comment != null && !comment.isEmpty()) {
            listing.setComment(a, CommentType.PLATE, comment);
        }
    }

    // =========================================================================
    // DSP EF1 and EF2
    // =========================================================================
    private void annotateDSP(DataType bt) throws Exception {

        // --- EF1 ---
        Address ef1 = absAddr(DSP_EF1_BASE_ADDR);
        createMemoryBlockIfAbsent("DSP_EF1", ef1, DSP_BLOCK_SIZE,
                "DMP9 DSP Effects Block 1 — YM3804 + YM6007 + YM3807 + YM6104\n" +
                "Address decoding by external logic; internal chip offsets TBD from firmware.");

        createLabelWithComments(ef1, "DSP_EF1_BASE",
                "DSP Effects Block 1 base\n" +
                "Chip composition:\n" +
                "  YM3804  — programmable DSP core (effects processor)\n" +
                "  YM6007  — EF1-only; suspected reverb/chorus engine\n" +
                "  YM3807  — DSP companion (coefficient RAM / sub-processor)\n" +
                "  YM6104  — EF1-only; additional effects processing\n" +
                "NOTE: No public register documentation exists for these chips.\n" +
                "      Sub-chip address offsets must be derived from firmware analysis.",
                "YM3804+YM6007+YM3807+YM6104  [Yamaha proprietary DSP, no public datasheet]");

        // --- EF2 ---
        Address ef2 = absAddr(DSP_EF2_BASE_ADDR);
        createMemoryBlockIfAbsent("DSP_EF2", ef2, DSP_BLOCK_SIZE,
                "DMP9 DSP Effects Block 2 — YM3804 + YM3807\n" +
                "Address decoding by external logic; internal chip offsets TBD from firmware.");

        createLabelWithComments(ef2, "DSP_EF2_BASE",
                "DSP Effects Block 2 base\n" +
                "Chip composition:\n" +
                "  YM3804  — programmable DSP core (effects processor)\n" +
                "  YM3807  — DSP companion (coefficient RAM / sub-processor)\n" +
                "NOTE: No public register documentation. Offsets from firmware analysis.",
                "YM3804+YM3807  [Yamaha proprietary DSP, no public datasheet]");

        // Annotate data type at base (byte placeholder)
        applyByteType(ef1, bt);
        applyByteType(ef2, bt);
    }


    // =========================================================================
    // LCD (HD44780)
    // =========================================================================
    private void annotateLCD(DataType bt) throws Exception {

        // Create a single memory block spanning both LCD registers
        Address lcdBase = absAddr(LCD_CMD_ADDR);
        createMemoryBlockIfAbsent("LCD_HD44780", lcdBase, 2,
                "DMP9/DMP16 HD44780 LCD controller — 2 byte registers\n" +
                "RS=0 (0x490000): instruction register / busy flag\n" +
                "RS=1 (0x490001): data register (DDRAM/CGRAM read/write)\n" +
                "Address decoding by external logic (NOT TMP68301 CS0/CS1).");

        // LCD_CMD (0x490000)
        createLabelWithComments(absAddr(LCD_CMD_ADDR), "LCD_CMD",
                "HD44780 Instruction Register (RS=0)\n" +
                "WRITE: send command opcode (see HD44780 instruction set below)\n" +
                "READ:  bit[7]=BF (Busy Flag, 1=busy), bits[6:0]=AC (Address Counter)\n" +
                "\n" +
                "=== HD44780 Instruction Set Summary ===\n" +
                "  0x01        Clear Display            (all DDRAM cleared, AC=0)    ~1.52 ms\n" +
                "  0x02/0x03   Return Home              (AC=0, display unshifted)    ~1.52 ms\n" +
                "  0x04        Entry Mode: dec, no shift\n" +
                "  0x05        Entry Mode: dec + shift\n" +
                "  0x06        Entry Mode: inc, no shift                              typical\n" +
                "  0x07        Entry Mode: inc + shift\n" +
                "  0x08        Display OFF\n" +
                "  0x0C        Display ON, cursor off\n" +
                "  0x0E        Display ON, cursor on\n" +
                "  0x0F        Display ON, cursor on + blink\n" +
                "  0x10        Shift cursor left\n" +
                "  0x14        Shift cursor right\n" +
                "  0x18        Shift display left\n" +
                "  0x1C        Shift display right\n" +
                "  0x20        Function Set: 4-bit, 1-line, 5x8\n" +
                "  0x28        Function Set: 4-bit, 2/4-line, 5x8\n" +
                "  0x30        Function Set: 8-bit, 1-line, 5x8\n" +
                "  0x38        Function Set: 8-bit, 2/4-line, 5x8    (standard init)\n" +
                "  0x40-0x7F   Set CGRAM addr = bits[5:0]            (8 chars x 8 rows)\n" +
                "  0x80-0xFF   Set DDRAM addr = bits[6:0]\n" +
                "              Line1=0x00-0x13, Line2=0x40-0x53\n" +
                "              Line3=0x14-0x27, Line4=0x54-0x67  (DMP9 4x20 display)",
                "HD44780 CMD register — write command, read BF+AC");

        applyByteType(absAddr(LCD_CMD_ADDR), bt);

        // LCD_DATA (0x490001)
        createLabelWithComments(absAddr(LCD_DATA_ADDR), "LCD_DATA",
                "HD44780 Data Register (RS=1)\n" +
                "WRITE: stores data byte at current DDRAM or CGRAM address, then\n" +
                "       auto-increments or decrements AC per Entry Mode setting.\n" +
                "READ:  reads data byte from current DDRAM or CGRAM address.\n" +
                "Ensure BF=0 (or wait >43 us) before each write.",
                "HD44780 DATA register — write char to DDRAM/CGRAM, read back");

        applyByteType(absAddr(LCD_DATA_ADDR), bt);

        // LCD_CTRL (0x4A0000) — combined word-wide control port used by lcd_strobe
        // for the E-strobe sequence.  Address decoding is by external logic; the
        // port is not physically inside any existing memory block, so create a
        // 2-byte volatile uninitialized block here (similar to how
        // TMP68301_Setup creates the SFR block).
        Address lcdCtrl = absAddr(LCD_CTRL_ADDR);
        createMemoryBlockIfAbsent("LCD_HD44780_CTRL", lcdCtrl, 2,
                "DMP9/DMP16 HD44780 combined control port — word-wide.\n" +
                "Written by lcd_strobe (0x29C8) to drive the E-strobe sequence.\n" +
                "  bit 8     = E (Enable strobe)\n" +
                "  bits 9-0  = data + RS + RW (exact layout TBD from hardware tracing)\n" +
                "Address decoding by external logic (NOT TMP68301 CS0/CS1).");
        createLabelWithComments(lcdCtrl, "LCD_CTRL",
                "HD44780 combined control port (word access).\n" +
                "bit 8 = E strobe; bits 9-0 = data + RS + RW (layout TBD).\n" +
                "Used by lcd_strobe to clock commands into the LCD controller.",
                "HD44780 combined CTRL port — word write");
        applyWordType(lcdCtrl);
    }


    // =========================================================================
    // LED Shift Register Controller (0x4D0000)
    //
    // 16-bit shift register chain driving all front-panel LEDs.  Each write
    // clocks 16 bits into the chain.  Driven by led_sr_write (formerly
    // write_4D0000 in DMP9_MidiAnalysis anchor table).
    //
    // Chain order (approximate, pending disassembly confirmation):
    //   - Encoder ring LEDs ch1..ch16 (red, around rotary pots)
    //   - Channel ON/MUTE buttons (orange)
    //   - Channel SEL buttons (green)
    //   - Right panel (SCENE MEMORY, SETUP MEMORY, SEND1/2)
    // =========================================================================
    private void annotateLED() throws Exception {
        Address ledSr = absAddr(LED_SR_DATA_ADDR);
        createMemoryBlockIfAbsent("LED_SR", ledSr, 2,
                "DMP9/DMP16 LED shift register data port — 16-bit serial chain.\n" +
                "Each write clocks 16 bits into a chain that drives all front-panel\n" +
                "LEDs: encoder rings, channel ON/MUTE, channel SEL, right-panel\n" +
                "buttons (SCENE MEMORY, SETUP MEMORY, SEND1/2).\n" +
                "Address decoding by external logic (NOT TMP68301 CS0/CS1).");
        createLabelWithComments(ledSr, "LED_SR_DATA",
                "LED shift register data port (word write).\n" +
                "Driven by led_sr_write — each call shifts a 16-bit word into the chain.\n" +
                "All-zero clears all LEDs; bit=1 lights an LED.\n" +
                "\n" +
                "Chain order (approximate, pending disassembly confirmation):\n" +
                "  Words 0..7: Encoder ring LEDs ch1..ch16 (2 enc per channel strip)\n" +
                "  Then:       Channel ON/MUTE buttons (orange)\n" +
                "  Then:       Channel SEL buttons (green)\n" +
                "  Then:       Right panel (SCENE MEMORY, SETUP MEMORY, SEND1/2)\n" +
                "\n" +
                "Boot self-test (from video frame analysis): sweeps left-to-right\n" +
                "across encoder rings, then ON buttons, then SEL buttons, then\n" +
                "right-panel buttons.",
                "LED shift register — word write, 16-bit chain");
        applyWordType(ledSr);
    }


    // =========================================================================
    // LCD Command Write Decoder
    //
    // Walks the entire code listing looking for 68000 MOVE.B instructions
    // that write a constant (immediate) byte value to LCD_CMD.  When found,
    // a pre-comment is inserted above the instruction explaining what the
    // HD44780 command does.
    //
    // Operand inspection uses getOpObjects(int) which returns an Object[]
    // containing Address, Scalar, Register, etc. — compatible with Ghidra 11.x.
    // =========================================================================
    private int decodeLcdCommandWrites() {
        int count = 0;
        Listing listing = currentProgram.getListing();
        InstructionIterator iter = listing.getInstructions(true);

        while (iter.hasNext() && !monitor.isCancelled()) {
            Instruction instr = iter.next();

            // Only MOVE.B instructions (68k mnemonic includes size suffix)
            if (!instr.toString().toUpperCase().contains("MOVE.B")) continue;

            // Check if the instruction writes to LCD_CMD address
            if (!writesToAddress(instr, LCD_CMD_ADDR)) continue;

            // Extract the immediate source value (operand 0 = source for 68k MOVE)
            Long imm = getImmediateSource(instr);
            if (imm == null) continue;

            // Decode and write a pre-comment above the instruction
            String decode = decodeLcdCommand((int)(imm & 0xFF));
            String existing = listing.getComment(CommentType.PRE, instr.getAddress());
            String newComment = "HD44780 CMD: " + decode;
            if (existing != null && !existing.isEmpty()) {
                newComment = existing + "\n" + newComment;
            }
            listing.setComment(instr.getAddress(), CommentType.PRE, newComment);
            count++;
        }
        return count;
    }

    /**
     * Returns true if the instruction writes to the given absolute address.
     *
     * Strategy (two passes, either is sufficient):
     *   1. Iterate getOpObjects(i) for each operand and check for Address matches.
     *   2. Check Ghidra's recorded write-references from this instruction.
     */
    private boolean writesToAddress(Instruction instr, long targetAddr) {
        int numOps = instr.getNumOperands();
        for (int i = 0; i < numOps; i++) {
            Object[] objs = instr.getOpObjects(i);
            if (objs == null) continue;
            for (Object obj : objs) {
                if (obj instanceof Address) {
                    if (((Address) obj).getOffset() == targetAddr) return true;
                }
            }
        }
        // Fallback: check reference manager for write references
        for (ghidra.program.model.symbol.Reference ref :
                currentProgram.getReferenceManager().getReferencesFrom(instr.getAddress())) {
            if (ref.getToAddress().getOffset() == targetAddr &&
                ref.getReferenceType().isWrite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts a Scalar (immediate) value from the source operand (operand 0)
     * of a MOVE instruction.  Returns null if operand 0 contains no Scalar.
     *
     * Uses getOpObjects(int) which returns Address, Scalar, Register, etc.
     */
    private Long getImmediateSource(Instruction instr) {
        if (instr.getNumOperands() >= 1) {
            Object[] objs = instr.getOpObjects(0);
            if (objs != null) {
                for (Object obj : objs) {
                    if (obj instanceof Scalar) {
                        return ((Scalar) obj).getUnsignedValue();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Decodes an 8-bit HD44780 command byte into a human-readable string.
     * Rules applied from most-specific to least-specific.
     */
    private String decodeLcdCommand(int cmd) {
        // Set DDRAM address (0x80-0xFF)
        if ((cmd & 0x80) != 0) {
            int ddramAddr = cmd & 0x7F;
            return String.format("Set DDRAM addr=0x%02X  %s", ddramAddr, ddramLineInfo(ddramAddr));
        }
        // Set CGRAM address (0x40-0x7F)
        if ((cmd & 0xC0) == 0x40) {
            int cgramAddr = cmd & 0x3F;
            return String.format("Set CGRAM addr=0x%02X  [custom char %d, row %d]",
                    cgramAddr, cgramAddr >> 3, cgramAddr & 0x07);
        }
        // Function Set (0x20-0x3F)
        if ((cmd & 0xE0) == 0x20) {
            return String.format("Function Set: %s-bit, %s, 5x%s",
                    (cmd & 0x10) != 0 ? "8" : "4",
                    (cmd & 0x08) != 0 ? "2/4-line" : "1-line",
                    (cmd & 0x04) != 0 ? "10" : "8");
        }
        // Cursor/Display Shift (0x10-0x1F)
        if ((cmd & 0xF0) == 0x10) {
            return String.format("%s %s",
                    (cmd & 0x08) != 0 ? "Shift Display" : "Move Cursor",
                    (cmd & 0x04) != 0 ? "Right" : "Left");
        }
        // Display ON/OFF (0x08-0x0F)
        if ((cmd & 0xF8) == 0x08) {
            if ((cmd & 0x04) == 0) return "Display OFF";
            StringBuilder sb = new StringBuilder("Display ON");
            sb.append((cmd & 0x02) != 0 ? ", cursor on" : ", cursor off");
            if ((cmd & 0x01) != 0) sb.append(", blink");
            return sb.toString();
        }
        // Entry Mode Set (0x04-0x07)
        if ((cmd & 0xFC) == 0x04) {
            return String.format("Entry Mode: AC %s%s",
                    (cmd & 0x02) != 0 ? "increment (cursor right)" : "decrement (cursor left)",
                    (cmd & 0x01) != 0 ? " + display shift" : ", no shift");
        }
        if (cmd == 0x02 || cmd == 0x03) return "Return Home  [AC=0, display unshifted]  (wait >=1.52 ms)";
        if (cmd == 0x01)                return "Clear Display  [all DDRAM cleared, AC=0]  (wait >=1.52 ms)";
        if (cmd == 0x00)                return "NOP / reserved write (0x00)";
        return String.format("Unknown HD44780 command byte: 0x%02X", cmd);
    }

    /** Maps a DDRAM address to a display line/column string for the DMP9 4x20 LCD. */
    private String ddramLineInfo(int ddram) {
        if (ddram <= 0x13)                       return String.format("[line 1, col %d]", ddram + 1);
        if (ddram >= 0x14 && ddram <= 0x27)      return String.format("[line 3, col %d -- verify L3 offset]", ddram - 0x14 + 1);
        if (ddram >= 0x40 && ddram <= 0x53)      return String.format("[line 2, col %d]", ddram - 0x40 + 1);
        if (ddram >= 0x54 && ddram <= 0x67)      return String.format("[line 4, col %d -- verify L4 offset]", ddram - 0x54 + 1);
        return String.format("[DDRAM addr 0x%02X -- outside known line ranges, verify mapping]", ddram);
    }


    // =========================================================================
    // UTILITY HELPERS
    // =========================================================================

    /** Resolve an absolute (long) address to a Ghidra Address object. */
    private Address absAddr(long absolute) {
        return currentProgram.getAddressFactory()
                             .getDefaultAddressSpace()
                             .getAddress(absolute);
    }

    /** Apply a byte data type at the given address (best-effort). */
    private void applyByteType(Address a, DataType bt) {
        Listing listing = currentProgram.getListing();
        listing.clearCodeUnits(a, a, false);
        try {
            listing.createData(a, bt);
        } catch (Exception e) {
            println("  WARN: could not apply byte type at " + a + " (" + e.getMessage() + ")");
        }
    }

    /** Apply a word (2-byte) data type at the given address (best-effort). */
    private void applyWordType(Address a) {
        Listing listing = currentProgram.getListing();
        try {
            listing.clearCodeUnits(a, a.add(1L), false);
            listing.createData(a, WordDataType.dataType);
        } catch (Exception e) {
            println("  WARN: could not apply word type at " + a + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Create a named label at the given address with plate and EOL comments.
     * Uses CommentType enum (Ghidra 11.x API).
     * Skips creation if the symbol name already exists at this address.
     */
    private void createLabelWithComments(Address a, String name,
                                          String plateComment, String eolComment)
            throws Exception {
        SymbolTable st = currentProgram.getSymbolTable();
        for (Symbol s : st.getSymbols(a)) {
            if (s.getName().equals(name)) return;
        }
        try {
            st.createLabel(a, name, SourceType.USER_DEFINED);
        } catch (Exception e) {
            println("  WARN: could not create label " + name + " at " + a);
        }

        Listing listing = currentProgram.getListing();

        // Plate comment — only write if not already set
        String full = "=== " + name + " ===\n" + plateComment;
        String existingPlate = listing.getComment(CommentType.PLATE, a);
        if (existingPlate == null || existingPlate.isEmpty()) {
            listing.setComment(a, CommentType.PLATE, full);
        }

        // EOL comment — only write if not already set
        String existingEol = listing.getComment(CommentType.EOL, a);
        if (existingEol == null || existingEol.isEmpty()) {
            listing.setComment(a, CommentType.EOL, eolComment);
        }
    }

    /**
     * Create a named memory block at the given address if no block already
     * covers that address.  If the address is already mapped, renames it
     * (if it has a generic auto-name) and updates its comment.
     */
    private void createMemoryBlockIfAbsent(String blockName, Address start,
                                            long size, String comment) {
        Memory mem = currentProgram.getMemory();
        MemoryBlock existing = mem.getBlock(start);
        if (existing != null) {
            if (existing.getName().startsWith("MEM_") ||
                existing.getName().startsWith("ram")  ||
                existing.getName().startsWith("ROM")  ||
                existing.getName().startsWith("UNK")) {
                try { existing.setName(blockName); } catch (Exception ignored) {}
            }
            existing.setComment(comment);
            return;
        }
        try {
            MemoryBlock mb = mem.createUninitializedBlock(blockName, start, size, true /*overlay*/);
            mb.setRead(true);
            mb.setWrite(true);
            mb.setComment(comment);
        } catch (Exception e) {
            println("  INFO: could not create block '" + blockName + "' at " + start +
                    " (may already be mapped): " + e.getMessage());
        }
    }
}
