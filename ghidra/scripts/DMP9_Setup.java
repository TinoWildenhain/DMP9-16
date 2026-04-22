// Ghidra Script: DMP9/16 ROM Setup
// File: DMP9_Setup.java
//
// Sets up memory map, labels, datatypes and comments for
// Yamaha DMP9/DMP9-16 ROM (TMS27C240, 512KB)
// CPU: Toshiba TMP68301 (68000 core + integrated peripherals)
//
// Run on v1.11 first (canonical), then apply Version Tracking
// to propagate to v1.10 and v1.02.
//
//@category DMP9_RE

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.data.*;

public class DMP9_Setup extends GhidraScript {
    @Override
    public void run() throws Exception {
        Memory mem = currentProgram.getMemory();
        AddressSpace as = currentProgram.getAddressFactory().getDefaultAddressSpace();
        SymbolTable st = currentProgram.getSymbolTable();
        Listing listing = currentProgram.getListing();

        println("=== Yamaha DMP9/16 setup ===");

        // Memory blocks
        createBlock(mem, as, "SRAM",         "0x00400000", 0x10000);
        createBlock(mem, as, "TMP68301_SFR", "0x00FFFC00", 0x40);
        createBlock(mem, as, "DSP_EF1",      "0x00460000", 0x1000);
        createBlock(mem, as, "DSP_EF2",      "0x00470000", 0x1000);
        createBlock(mem, as, "LCD",           "0x00490000", 0x10);

        // TMP68301 UART / MFP registers
        label(st, listing, as, "0x00FFFC01", "MFP_GPDR", "GPIO data register / panel I/O");
        label(st, listing, as, "0x00FFFC03", "MFP_DDR",  "GPIO direction register");
        label(st, listing, as, "0x00FFFC05", "MFP_IERA", "Interrupt enable register A");
        label(st, listing, as, "0x00FFFC07", "MFP_IERB", "Interrupt enable register B");
        label(st, listing, as, "0x00FFFC09", "MFP_UCR",  "UART control register (MIDI 31250 baud)");
        label(st, listing, as, "0x00FFFC0B", "MFP_RSR",  "UART receive status register");
        label(st, listing, as, "0x00FFFC0D", "MFP_TSR",  "UART transmit status register");
        label(st, listing, as, "0x00FFFC0F", "MFP_UDR",  "UART data register / MIDI byte I/O");

        // DSP and LCD
        label(st, listing, as, "0x00460000", "DSP_EF1_BASE", "EF1 Yamaha DSP block");
        label(st, listing, as, "0x00470000", "DSP_EF2_BASE", "EF2 Yamaha DSP block");
        label(st, listing, as, "0x00490000", "LCD_CMD",      "LCD command register");
        label(st, listing, as, "0x00490001", "LCD_DATA",     "LCD data register");

        // Known functions (v1.11 addresses)
        label(st, listing, as, "0x0003B3F0", "reset_handler",  "Primary reset/boot entry point");
        label(st, listing, as, "0x0000238C", "lcd_write_cmd",  "Write LCD command byte");
        label(st, listing, as, "0x000023DC", "lcd_write_data", "Write LCD data byte");
        label(st, listing, as, "0x0000274E", "dsp_ef1_write",  "Write EF1 DSP register/data sequence");
        label(st, listing, as, "0x00002904", "dsp_ef2_write",  "Write EF2 DSP register/data sequence");
        label(st, listing, as, "0x000122E0", "midi_tx_byte",   "Transmit one MIDI byte via UART");
        label(st, listing, as, "0x000128EC", "midi_process_rx","MIDI message processing core");
        label(st, listing, as, "0x00013454", "scene_recall",   "Recall scene from memory");
        label(st, listing, as, "0x00013186", "scene_store",    "Store scene to memory");

        println("Setup complete.");
        println("Next: Analysis -> Auto Analyze, enable Decompiler Parameter ID");
        println("Then: use Version Tracking to propagate labels to v1.10 and v1.02");
    }

    private void createBlock(Memory mem, AddressSpace as, String name, String addr, long size) {
        try {
            mem.createUninitializedBlock(name, as.getAddress(addr), size, false);
            println("  Block: " + name + " @ " + addr);
        } catch (Exception e) {
            println("  Skip block " + name + ": " + e.getMessage());
        }
    }

    private void label(SymbolTable st, Listing listing, AddressSpace as,
                       String addr, String name, String comment) {
        try {
            Address a = as.getAddress(addr);
            st.createLabel(a, name, SourceType.USER_DEFINED);
            listing.setComment(a, CodeUnit.PLATE_COMMENT, comment);
            println("  Label: " + name + " @ " + addr);
        } catch (Exception e) {
            println("  Skip label " + name + ": " + e.getMessage());
        }
    }
}
