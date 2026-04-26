// TMP68301_Setup.java
// Ghidra script: Auto-annotate Toshiba TMP68301 / TMP68303 internal peripheral registers.
//
// Extends the register-labelling approach of DMP9_Setup.java:
//   - Creates named memory regions for each peripheral block
//   - Defines all internal register addresses as labelled symbols
//   - Applies appropriate data sizes (BYTE / WORD) to each register
//   - Adds plate comments with register descriptions
//   - Adds end-of-line (EOL) comments summarising bitfields
//   - Defines Ghidra structures for IMR/IPR/IISR, TCR, SMR, SCMR, SSR
//
// Sources:
//   Toshiba TMP68301AK datasheet (TMP68301AKF-8)
//   MAME src/devices/cpu/m68000/tmp68301.cpp (Olivier Galibert, BSD-3-Clause)
//   tmp68301_regs.h (derived header, this project)
//
// Usage:
//   1. Open a TMP68301-based binary in Ghidra (e.g. Kurzweil K2000, Yamaha VL70-m)
//   2. Run this script via Script Manager or keybinding
//   3. When prompted, enter the peripheral base address (default 0xFFFC00)
//      -- leave blank to use 0xFFFC00
//   4. Optionally enter a board name prefix for symbols (e.g. "KRZ" or "YM")
//
// @author   Derived from DMP9_Setup.java pattern
// @category Analysis.Hardware
// @keybinding
// @menupath
// @toolbar

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import ghidra.util.exception.*;
// Ghidra 11.x: CommentType replaces deprecated CodeUnit.{PLATE,EOL,PRE}_COMMENT int constants

public class TMP68301_Setup extends GhidraScript {

    // -------------------------------------------------------------------------
    // Peripheral base address (default 0xFFFC00; read from ARELR at runtime)
    // -------------------------------------------------------------------------
    private long BASE = 0xFFFC00L;
    private String PREFIX = "TMP68301";

    // Convenience address helpers
    private Address addr(long offset) {
        return currentProgram.getAddressFactory()
                             .getDefaultAddressSpace()
                             .getAddress(BASE + offset);
    }

    private Address absAddr(long absolute) {
        return currentProgram.getAddressFactory()
                             .getDefaultAddressSpace()
                             .getAddress(absolute);
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------
    @Override
    public void run() throws Exception {

        // Ask user for base address override
        String baseStr = askString("TMP68301 Peripheral Base",
                "Peripheral register base address (hex, default FFFC00):", "FFFC00");
        if (baseStr != null && !baseStr.trim().isEmpty()) {
            try {
                BASE = Long.parseLong(baseStr.trim(), 16);
            } catch (NumberFormatException e) {
                printerr("Invalid base address '" + baseStr + "', using 0xFFFC00");
                BASE = 0xFFFC00L;
            }
        }

        String pfx = askString("Symbol Prefix",
                "Symbol name prefix (e.g. TMP68301, KRZ, YM):", "TMP68301");
        if (pfx != null && !pfx.trim().isEmpty()) {
            PREFIX = pfx.trim();
        }

        println("TMP68301_Setup: BASE=0x" + Long.toHexString(BASE) + "  PREFIX=" + PREFIX);

        // Build Ghidra data types once
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        DataType byteType  = dtm.getDataType("/byte");
        DataType wordType  = dtm.getDataType("/word");
        if (byteType == null)  byteType  = ByteDataType.dataType;
        if (wordType == null)  wordType  = WordDataType.dataType;

        // Create composite structures and add them to the DTM
        createStructures(dtm);

        // Annotate each peripheral block
        annotateAddressDecoder(byteType, wordType);
        annotateInterruptController(byteType, wordType);
        annotateParallel(byteType, wordType);
        annotateSerial(byteType, wordType);
        annotateTimers(byteType, wordType);
        annotateIACKSpace();

        println("TMP68301_Setup: Done. " + PREFIX + " registers labelled at base 0x"
                + Long.toHexString(BASE));
    }


    // =========================================================================
    // SECTION 1 – ADDRESS DECODER  (offsets 0x000–0x00D)
    // =========================================================================
    private void annotateAddressDecoder(DataType bt, DataType wt) throws Exception {
        createMemoryBlockIfAbsent("TMP68301_ADDRDEC",
                addr(0x000), 0x10, "TMP68301 Address Decoder Registers");

        defineReg(addr(0x000), bt, "AMAR0",
                "Memory Area Address Register 0 – CS0 base address bits[23:16]",
                "Write A[23:16] for CS0 window base; AAMR0 selects mask granularity");

        defineReg(addr(0x001), bt, "AAMR0",
                "Address Area Mask Register 0 – CS0 address mask",
                "Bits[7:2]=256K mask; bit[1]=512B sub-range; bit[0]=256B sub-range");

        defineReg(addr(0x003), bt, "AACR0",
                "Address Area Control Register 0 – CS0 enable/DTACK/wait",
                "bit[5]=CS_EN; bits[4:3]=DTACK(01=int,10=ext,11=both); bits[2:0]=waits; Reset=0x3D");

        defineReg(addr(0x004), bt, "AMAR1",
                "Memory Area Address Register 1 – CS1 base address bits[23:16]",
                "Same encoding as AMAR0, for CS1 chip-select output");

        defineReg(addr(0x005), bt, "AAMR1",
                "Address Area Mask Register 1 – CS1 address mask",
                "Same encoding as AAMR0; Reset=0xFF");

        defineReg(addr(0x007), bt, "AACR1",
                "Address Area Control Register 1 – CS1 enable/DTACK/wait",
                "bit[5]=CS_EN; bits[4:3]=DTACK; bits[2:0]=waits; Reset=0x18 (disabled)");

        defineReg(addr(0x009), bt, "AACR2",
                "Address Area Control Register 2 – IACK cycle DTACK/wait",
                "bits[4:3]=DTACK for interrupt-acknowledge cycles; bits[2:0]=waits; Reset=0x18");

        defineReg(addr(0x00B), bt, "ATOR",
                "Address Timeout Register – BERR bus monitor timeout",
                "Bits[3:0]: 0x0=disabled, 0x1=32clk, 0x2=64, 0x4=128, 0x8=256; Reset=0x08");

        defineReg(addr(0x00C), wt, "ARELR",
                "Address RELocation Register – peripheral block base address",
                "Bits[15:2]=new_base>>8 (bits[1:0] always 0); default 0xFFFC → regs at 0xFFFC00");
    }


    // =========================================================================
    // SECTION 2 – INTERRUPT CONTROLLER  (offsets 0x080–0x09D)
    // =========================================================================
    private void annotateInterruptController(DataType bt, DataType wt) throws Exception {
        createMemoryBlockIfAbsent("TMP68301_INTC",
                addr(0x080), 0x20, "TMP68301 Interrupt Controller Registers");

        // ICR0–ICR9: array at 0xfffc81..0xfffc93 (odd bytes of 16-bit words)
        String[] icrDesc = {
            "External INT0: bit[6]=auto-vector; bits[5:4]=mode(00=fall,01=lo,10=rise,11=hi); bits[2:0]=IPL",
            "External INT1: same encoding as ICR0",
            "External INT2: same encoding as ICR0",
            "Serial ch0 IRQ priority: bits[2:0]=IPL level (0=disable); writable only when masked",
            "Serial ch1 IRQ priority: bits[2:0]=IPL level",
            "Serial ch2 IRQ priority: bits[2:0]=IPL level",
            "Parallel INT6 IRQ priority: bits[2:0]=IPL level (reserved in AK variant)",
            "Timer ch0 IRQ priority: bits[2:0]=IPL level; writable only when masked",
            "Timer ch1 IRQ priority: bits[2:0]=IPL level",
            "Timer ch2 IRQ priority: bits[2:0]=IPL level",
        };
        int[] icrOff = { 0x81, 0x83, 0x85, 0x87, 0x89, 0x8B, 0x8D, 0x8F, 0x91, 0x93 };
        String[] icrNames = { "ICR0","ICR1","ICR2","ICR3","ICR4","ICR5","ICR6","ICR7","ICR8","ICR9" };
        for (int i = 0; i < 10; i++) {
            defineReg(addr(icrOff[i]), bt, icrNames[i],
                    "Interrupt Control Register " + i + ": " + (i < 3 ? "External INT" + i : icrDesc[i]),
                    icrDesc[i] + "; Reset=0x07");
        }

        DataType irqRegType = getOrCreateIrqRegType();

        defineReg(addr(0x094), irqRegType, "IMR",
                "Interrupt Mask Register (16-bit) – 1=masked, 0=active",
                "Bits: [12]=T2,[11]=T1,[10]=T0,[9]=P,[8]=S2,[7]=S1,[6]=S0,[2]=E2,[1]=E1,[0]=E0; Reset=0x07F7");

        defineReg(addr(0x096), irqRegType, "IPR",
                "Interrupt Pending Register (16-bit) – 1=request pending",
                "Same bit layout as IMR; write 0 to clear edge-triggered; Reset=0x0000");

        defineReg(addr(0x098), irqRegType, "IISR",
                "Interrupt In-Service Register (16-bit) – 1=being processed",
                "Clear each bit at end of ISR; Reset=0x0000");

        defineReg(addr(0x09B), bt, "IVNR",
                "Interrupt Vector Number Register (8-bit) – upper 3 bits of auto vector",
                "Bits[7:3]=vector_base; lower 5 bits auto-assigned by channel; set ≥0x40 after reset");

        defineReg(addr(0x09D), bt, "IEIR",
                "Expansion Interrupt Enable Register – redirect channels to external pins",
                "bit[6]=T2→IO14/DSR0, bit[5]=T1→TIN, bit[4]=T0→IO12, bit[3]=S2→RxD2, bit[2]=S1→RxD1, bit[1]=S0→RxD0");
    }


    // =========================================================================
    // SECTION 3 – PARALLEL INTERFACE  (offsets 0x100–0x10F)
    // =========================================================================
    private void annotateParallel(DataType bt, DataType wt) throws Exception {
        createMemoryBlockIfAbsent("TMP68301_PAR",
                addr(0x100), 0x10, "TMP68301 Parallel Interface Registers");

        defineReg(addr(0x100), wt, "PDIR",
                "Parallel Direction Register (16-bit) – bit=1→output, bit=0→input",
                "Each bit controls one IO pin (IO0–IO15); Reset=0x0000 (all inputs)");

        defineReg(addr(0x103), bt, "PCR",
                "Parallel Control Register (8-bit) – selects parallel operating mode",
                "Bits[3:0]=mode: 0=GPIO(mode0), others=Centronics/handshake; Reset=0x00");

        defineReg(addr(0x105), bt, "PSR",
                "Parallel Status Register (8-bit) – bit[7]=IF interrupt flag; bit[6]=busy/status",
                "Write 0 to bit[7] to clear IF; Reset=0x40");

        defineReg(addr(0x107), bt, "PCMR",
                "Parallel Command Mask Register (8-bit)",
                "bit[4]=CMD_COMPLETE_mask; bit[3]=CHANGE_mask; bit[2]=TXRDY_mask; bit[1]=FAULT; bit[0]=PRIME");

        defineReg(addr(0x108), bt, "PMR",
                "Parallel Mode Register (8-bit, lower byte of word at 0x108)",
                "bit[3]=DSTB_pol(1=falling); bit[2]=ACK(1=auto); bit[1]=busy/ACK; bit[0]=transfer(1=ext)");

        defineReg(addr(0x10A), wt, "PDR",
                "Parallel Data Register (16-bit) – read=input; write=output (direction gated by PDIR)",
                "IO15–IO0; input bits read external pin; output bits set via internal latch");

        defineReg(addr(0x10D), bt, "PPR1",
                "Parallel Pulse Register 1 (8-bit) – output pulse delay parameter",
                "Bits[6:0]=delay value; used in Centronics/handshake modes");

        defineReg(addr(0x10F), bt, "PPR2",
                "Parallel Pulse Register 2 (8-bit) – output pulse width parameter",
                "Bits[6:0]=width value");
    }


    // =========================================================================
    // SECTION 4 – SERIAL INTERFACE  (Ch0: 0x181..0x18F, Ch1: 0x191, Ch2: 0x1A1)
    // =========================================================================
    private void annotateSerial(DataType bt, DataType wt) throws Exception {
        createMemoryBlockIfAbsent("TMP68301_SER",
                addr(0x180), 0x30, "TMP68301 Serial Interface Registers (Ch0–Ch2)");

        // Global registers (in ch0 block)
        defineReg(addr(0x18D), bt, "SPR",
                "Serial Prescaler Register (global, 8-bit) – clock prescaler for all channels",
                "Value 0=divide by 256; 1–255=divide by that value; Reset=0x00 (÷256)");

        defineReg(addr(0x18F), bt, "SCR",
                "Serial Control Register (global, 8-bit) – master serial control",
                "bit[7]=CKSE(1=internal clk); bit[5]=RES(1=hold in reset); bit[0]=INTM(1=mask all IRQs); Reset=0xA1");

        // Per-channel registers
        int[] smrOff  = { 0x181, 0x191, 0x1A1 };
        int[] scmrOff = { 0x183, 0x193, 0x1A3 };
        int[] sbrrOff = { 0x185, 0x195, 0x1A5 };
        int[] ssrOff  = { 0x187, 0x197, 0x1A7 };
        int[] sdrOff  = { 0x189, 0x199, 0x1A9 };

        DataType smrType  = getOrCreateSmrType();
        DataType scmrType = getOrCreateScmrType();
        DataType ssrType  = getOrCreateSsrType();

        for (int ch = 0; ch < 3; ch++) {
            defineReg(addr(smrOff[ch]), smrType, "SMR" + ch,
                    "Serial Mode Register ch" + ch + " (8-bit) – frame format control",
                    "bit[7]=RXINTM; bit[6]=ERINTM; bits[3:2]=CL(00=5b…11=8b); bit[4]=PEN; bit[5]=PEO; bit[1]=TXINTM; bit[0]=ST(2-stop)");

            String scmrExtra = (ch == 0) ? "; bit[5]=RTS; bit[1]=DTR" : " (no RTS/DTR on ch" + ch + ")";
            defineReg(addr(scmrOff[ch]), scmrType, "SCMR" + ch,
                    "Serial Command Register ch" + ch + " (8-bit) – TX/RX enable and control",
                    "bit[4]=ERS(error_reset); bit[3]=SBRK; bit[2]=RXEN; bit[0]=TXEN" + scmrExtra + "; Reset=0x10");

            defineReg(addr(sbrrOff[ch]), bt, "SBRR" + ch,
                    "Serial Baud Rate Register ch" + ch + " (8-bit) – baud rate divisor",
                    "Divisor = highest-set-bit-rounded value; actual_baud = clk/(SPR_val*SBRR_div*8); Reset=0x01");

            defineReg(addr(ssrOff[ch]), ssrType, "SSR" + ch,
                    "Serial Status Register ch" + ch + " (8-bit, READ-ONLY)",
                    "bit[7]=DSR(ch0); bit[6]=RBRK; bit[5]=FE; bit[4]=OE; bit[3]=PE; bit[2]=TXE; bit[1]=RXRDY; bit[0]=TXRDY; Reset=0x04");

            defineReg(addr(sdrOff[ch]), bt, "SDR" + ch,
                    "Serial Data Register ch" + ch + " (8-bit) – TX write / RX read buffer",
                    "Write=load TX shift register (sets TXE=0,TXRDY=0); Read=get RX byte (clears RXRDY)");
        }
    }


    // =========================================================================
    // SECTION 5 – 16-BIT TIMER/COUNTER  (Ch0: 0x200, Ch1: 0x220, Ch2: 0x240)
    // =========================================================================
    private void annotateTimers(DataType bt, DataType wt) throws Exception {
        createMemoryBlockIfAbsent("TMP68301_TMR",
                addr(0x200), 0x60, "TMP68301 16-bit Timer/Counter Registers (Ch0–Ch2)");

        DataType tcrType = getOrCreateTcrType();

        int[] tcrOff  = { 0x200, 0x220, 0x240 };
        int[] tmcr1Off = { 0x204, 0x224, 0x244 };
        int[] tmcr2Off = { 0x208, 0x228, 0x248 };  // ch1/ch2 only
        int[] tctrOff = { 0x20C, 0x22C, 0x24C };

        for (int ch = 0; ch < 3; ch++) {
            defineReg(addr(tcrOff[ch]), tcrType, "TCR" + ch,
                    "Timer Control Register ch" + ch + " (16-bit) – mode, clock, interrupt",
                    "bits[15:14]=CK(00=sysclk,01=TIN,10=ch" + (ch == 0 ? "1" : ch == 1 ? "0" : "0") + ",11=ch" + (ch < 2 ? "2" : "1") + "); " +
                    "bits[13:10]=P(prescaler 2^(P+1)); bits[9:8]=T(00=freerun,01=match,10=capture,11=PWM); " +
                    "bit[7]=N1(output_en); bit[6]=RP(polarity); bits[5:4]=MR(00=0xffff,01=TMCR1,10=TMCR2,11=dual); " +
                    "bit[2]=INT; bit[1]=CS(stop); bit[0]=TS(start/clear); Reset ch0=0x0052 ch1/2=0x0012");

            defineReg(addr(tmcr1Off[ch]), wt, "TMCR" + ch + "1",
                    "Timer Match Control Register 1 ch" + ch + " (16-bit) – compare value",
                    "When TCR.MR=01: counter reloads to 0 on match. When MR=10/11: dual-match low value");

            if (ch > 0) {
                defineReg(addr(tmcr2Off[ch]), wt, "TMCR" + ch + "2",
                        "Timer Match Control Register 2 ch" + ch + " (16-bit) – second compare value",
                        "Used in MR=10 (TMCR2 as max) or MR=11 (alternating match); ch0 has no TMCR2");
            }

            defineReg(addr(tctrOff[ch]), wt, "TCTR" + ch,
                    "Timer Counter Register ch" + ch + " (16-bit) – current count (read) or reset (write)",
                    "Read=current counter value (live snapshot); Write=reset counter to 0");
        }
    }


    // =========================================================================
    // SECTION 6 – CPU SPACE / INTERRUPT ACKNOWLEDGE VECTORS  (0xFFFFF0–0xFFFFFF)
    // =========================================================================
    private void annotateIACKSpace() throws Exception {
        // These are in cpu-space, not regular memory, but annotate if present in program
        long iackBase = 0xFFFFF0L;
        createMemoryBlockIfAbsent("TMP68301_IACK",
                absAddr(iackBase), 0x10,
                "TMP68301 CPU Space – interrupt vector fetch region (auto-vector response)");

        writePreComment(absAddr(iackBase),
                "TMP68301 Auto-vector region: reads here during IACK cycle return (IVNR | channel_vector).\n" +
                "Vector byte = (IVNR & 0xE0) | lower_5_bits.\n" +
                "Set IVNR >= 0x40 to avoid collision with 68000 system vectors.");
    }


    // =========================================================================
    // DATA TYPE CONSTRUCTION
    // =========================================================================

    /** Shared 16-bit interrupt register structure (IMR / IPR / IISR layout) */
    private DataType getOrCreateIrqRegType() throws Exception {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        CategoryPath cp = new CategoryPath("/TMP68301");
        String name = "TMP68301_IRQ_REG";
        DataType existing = dtm.getDataType(cp, name);
        if (existing != null) return existing;

        StructureDataType s = new StructureDataType(cp, name, 0);
        s.setPackingEnabled(false);
        // We model as a plain word with field comments via a union
        UnionDataType u = new UnionDataType(cp, name);
        u.add(WordDataType.dataType, 2, "raw", "Raw 16-bit interrupt status word");
        // Add a struct overlay for documentation
        StructureDataType bits = new StructureDataType(cp, name + "_bits", 0);
        bits.addBitField(ByteDataType.dataType, 1, "E0",  "External INT0 channel");
        bits.addBitField(ByteDataType.dataType, 1, "E1",  "External INT1 channel");
        bits.addBitField(ByteDataType.dataType, 1, "E2",  "External INT2 channel");
        bits.addBitField(ByteDataType.dataType, 3, "_r1", "Reserved");
        bits.addBitField(ByteDataType.dataType, 1, "S0",  "Serial ch0");
        bits.addBitField(ByteDataType.dataType, 1, "S1",  "Serial ch1");
        bits.addBitField(ByteDataType.dataType, 1, "S2",  "Serial ch2");
        bits.addBitField(ByteDataType.dataType, 1, "P",   "Parallel INT6");
        bits.addBitField(ByteDataType.dataType, 1, "T0",  "Timer ch0");
        bits.addBitField(ByteDataType.dataType, 1, "T1",  "Timer ch1");
        bits.addBitField(ByteDataType.dataType, 1, "T2",  "Timer ch2");
        bits.addBitField(ByteDataType.dataType, 3, "_r2", "Reserved");
        u.add(bits, 2, "bits", "Bitfield breakdown");
        return dtm.addDataType(u, DataTypeConflictHandler.REPLACE_HANDLER);
    }

    /** SMR – Serial Mode Register bitfield struct */
    private DataType getOrCreateSmrType() throws Exception {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        CategoryPath cp = new CategoryPath("/TMP68301");
        String name = "TMP68301_SMR";
        DataType existing = dtm.getDataType(cp, name);
        if (existing != null) return existing;
        StructureDataType s = new StructureDataType(cp, name, 0);
        s.addBitField(ByteDataType.dataType, 1, "ST",     "Stop bits: 1=2 stop bits, 0=1 stop bit");
        s.addBitField(ByteDataType.dataType, 1, "TXINTM", "TX interrupt mask (1=masked)");
        s.addBitField(ByteDataType.dataType, 2, "CL",     "Char length: 00=5bit,01=6bit,10=7bit,11=8bit");
        s.addBitField(ByteDataType.dataType, 1, "PEN",    "Parity enable");
        s.addBitField(ByteDataType.dataType, 1, "PEO",    "Parity: 0=even, 1=odd");
        s.addBitField(ByteDataType.dataType, 1, "ERINTM", "Error interrupt mask");
        s.addBitField(ByteDataType.dataType, 1, "RXINTM", "RX interrupt mask");
        return dtm.addDataType(s, DataTypeConflictHandler.REPLACE_HANDLER);
    }

    /** SCMR – Serial Command Register bitfield struct */
    private DataType getOrCreateScmrType() throws Exception {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        CategoryPath cp = new CategoryPath("/TMP68301");
        String name = "TMP68301_SCMR";
        DataType existing = dtm.getDataType(cp, name);
        if (existing != null) return existing;
        StructureDataType s = new StructureDataType(cp, name, 0);
        s.addBitField(ByteDataType.dataType, 1, "TXEN",  "Transmitter enable");
        s.addBitField(ByteDataType.dataType, 1, "DTR",   "DTR output low (ch0 only)");
        s.addBitField(ByteDataType.dataType, 1, "RXEN",  "Receiver enable");
        s.addBitField(ByteDataType.dataType, 1, "SBRK",  "Send break (continuous mark)");
        s.addBitField(ByteDataType.dataType, 1, "ERS",   "Error status reset (write 1 to clear SSR errors)");
        s.addBitField(ByteDataType.dataType, 1, "RTS",   "RTS output low (ch0 only)");
        s.addBitField(ByteDataType.dataType, 2, "_res",  "Reserved");
        return dtm.addDataType(s, DataTypeConflictHandler.REPLACE_HANDLER);
    }

    /** SSR – Serial Status Register bitfield struct */
    private DataType getOrCreateSsrType() throws Exception {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        CategoryPath cp = new CategoryPath("/TMP68301");
        String name = "TMP68301_SSR";
        DataType existing = dtm.getDataType(cp, name);
        if (existing != null) return existing;
        StructureDataType s = new StructureDataType(cp, name, 0);
        s.addBitField(ByteDataType.dataType, 1, "TXRDY", "TX data register ready for new byte");
        s.addBitField(ByteDataType.dataType, 1, "RXRDY", "RX byte available in SDR");
        s.addBitField(ByteDataType.dataType, 1, "TXE",   "TX shift register empty");
        s.addBitField(ByteDataType.dataType, 1, "PE",    "Parity error");
        s.addBitField(ByteDataType.dataType, 1, "OE",    "Overrun error");
        s.addBitField(ByteDataType.dataType, 1, "FE",    "Framing error");
        s.addBitField(ByteDataType.dataType, 1, "RBRK",  "Break received");
        s.addBitField(ByteDataType.dataType, 1, "DSR",   "DSR input state (ch0 only)");
        return dtm.addDataType(s, DataTypeConflictHandler.REPLACE_HANDLER);
    }

    /** TCR – Timer Control Register bitfield struct */
    private DataType getOrCreateTcrType() throws Exception {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        CategoryPath cp = new CategoryPath("/TMP68301");
        String name = "TMP68301_TCR";
        DataType existing = dtm.getDataType(cp, name);
        if (existing != null) return existing;
        StructureDataType s = new StructureDataType(cp, name, 0);
        s.addBitField(ShortDataType.dataType, 1, "TS",   "Timer start/clear (write 0 to clear counter)");
        s.addBitField(ShortDataType.dataType, 1, "CS",   "Counter stop (1=halted)");
        s.addBitField(ShortDataType.dataType, 1, "INT",  "Interrupt enable on compare match");
        s.addBitField(ShortDataType.dataType, 1, "_r1",  "Reserved");
        s.addBitField(ShortDataType.dataType, 2, "MR",   "Match/max mode: 00=0xFFFF,01=TMCR1,10=TMCR2,11=dual");
        s.addBitField(ShortDataType.dataType, 1, "RP",   "Output polarity invert");
        s.addBitField(ShortDataType.dataType, 1, "N1",   "Output waveform enable");
        s.addBitField(ShortDataType.dataType, 2, "T",    "Count mode: 00=freerun,01=match,10=capture,11=PWM");
        s.addBitField(ShortDataType.dataType, 4, "P",    "Prescaler: divide by 2^(P+1)");
        s.addBitField(ShortDataType.dataType, 2, "CK",   "Clock source: 00=sysclk,01=TIN,10=chN,11=chM");
        return dtm.addDataType(s, DataTypeConflictHandler.REPLACE_HANDLER);
    }

    /** Stub to create remaining structures if needed */
    private void createStructures(DataTypeManager dtm) throws Exception {
        getOrCreateIrqRegType();
        getOrCreateSmrType();
        getOrCreateScmrType();
        getOrCreateSsrType();
        getOrCreateTcrType();
    }


    // =========================================================================
    // UTILITY HELPERS
    // =========================================================================

    /**
     * Define a labelled register: clears existing code units, applies a data type,
     * sets a symbol, and adds plate + EOL comments.
     */
    private void defineReg(Address a, DataType dt, String name,
                           String plateComment, String eolComment) throws Exception {
        // Clear any existing code units at this address
        Listing listing = currentProgram.getListing();
        listing.clearCodeUnits(a, a.add(dt.getLength() - 1), false);

        // Apply data type
        try {
            listing.createData(a, dt);
        } catch (Exception e) {
            // If data creation fails (e.g. not in a mapped region), skip silently
            println("  WARN: could not apply data type at " + a + " (" + e.getMessage() + ")");
        }

        // Create symbol
        SymbolTable st = currentProgram.getSymbolTable();
        // Remove duplicate symbols at this address first
        for (Symbol s : st.getSymbols(a)) {
            if (!s.isPrimary()) s.delete();
        }
        String symName = PREFIX + "_" + name;
        try {
            st.createLabel(a, symName, SourceType.USER_DEFINED);
        } catch (Exception e) {
            println("  WARN: could not create symbol " + symName + " at " + a);
        }

        // Plate comment (above the address)
        writePlateComment(a, "=== " + name + " ===\n" + plateComment);

        // EOL comment
        writeEOLComment(a, eolComment);
    }

    /** Overload: use WordDataType for 16-bit register without explicit type arg */
    private void defineReg16(Address a, String name,
                              String plateComment, String eolComment) throws Exception {
        defineReg(a, WordDataType.dataType, name, plateComment, eolComment);
    }

    /** Overload: use ByteDataType for 8-bit register */
    private void defineReg8(Address a, String name,
                             String plateComment, String eolComment) throws Exception {
        defineReg(a, ByteDataType.dataType, name, plateComment, eolComment);
    }

    /** Set a plate comment — Ghidra 11.x: CommentType.PLATE */
    private void writePlateComment(Address a, String text) {
        currentProgram.getListing().setComment(a, CommentType.PLATE, text);
    }

    /** Set an EOL comment — Ghidra 11.x: CommentType.EOL */
    private void writeEOLComment(Address a, String text) {
        currentProgram.getListing().setComment(a, CommentType.EOL, text);
    }

    /** Set a pre-comment (above instruction) — Ghidra 11.x: CommentType.PRE */
    private void writePreComment(Address a, String text) {
        currentProgram.getListing().setComment(a, CommentType.PRE, text);
    }

    /**
     * Create a named memory block if one does not already exist at this address.
     * Skips silently if the address is already mapped (common when the image
     * already maps all of 0xFF0000–0xFFFFFF as ROM/RAM overlay).
     */
    private void createMemoryBlockIfAbsent(String blockName, Address start,
                                            long size, String comment) {
        Memory mem = currentProgram.getMemory();
        // Check if already mapped
        MemoryBlock existing = mem.getBlock(start);
        if (existing != null) {
            // Rename if it was auto-named
            if (existing.getName().startsWith("MEM_") || existing.getName().startsWith("ram") ||
                existing.getName().startsWith("ROM")) {
                try { existing.setName(blockName); } catch (Exception ignored) {}
            }
            existing.setComment(comment);
            return;
        }
        // Try to create a new overlay block
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
