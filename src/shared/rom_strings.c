/*
 * rom_strings.c — Definition of the DMP9/16 ROM string table instance.
 *
 * This file defines dmp9_strings and places it in the ".strings" section.
 * The linker script (dmp9.ld) maps ".strings" to ROM address 0x050000.
 *
 * MCC68K compiler note:
 * ---------------------
 * MCC68K v4.x automatically emits all string literals into a section named
 * "strings" (type CODE).  Constant global data goes into "const" (type CODE).
 * Float/int constants go into "literals" (type CODE).
 *
 * When compiling with m68k-linux-gnu-gcc as a round-trip substitute:
 *   - ".strings"  → mapped to 0x050000 in dmp9.ld
 *   - ".rodata"   → mapped to 0x050000 + sizeof(strings) (or same region)
 *   - ".literal"  → handled by the compiler's constant pool
 *
 * IMPORTANT — filling strategy:
 * The struct members must produce exactly the bytes found in the ROM at each
 * position.  Gaps between strings (non-printable binary data: jump tables,
 * parameter structs, coefficient pointers, etc.) are represented as uint8_t
 * pad_XXXXXX[] arrays whose sizes keep members at the correct offsets.
 *
 * To verify alignment: compile with -g, then check symbol addresses in the
 * .map file or with m68k-linux-gnu-nm --numeric-sort dmp9_decompiled.elf.
 */

#include "rom_strings.h"

/*
 * The actual ROM string table.
 *
 * Initialiser values must match the ROM byte-for-byte.
 * Non-printable pad regions are zero-initialised here;
 * the linker/ROM image comparison will highlight any mismatch.
 *
 * __attribute__((section(".strings"))) causes m68k-linux-gnu-gcc to place
 * this object in the named output section, which the linker script then
 * positions at 0x050000.
 */
__attribute__((section(".strings")))
const struct dmp9_rom_strings dmp9_strings = {

    /* 0x050000 */
    .init_data_st           = "                Initial Data(ST)",
    .init_data_xx0          = "xxxxxxxxxxxxxxxx",
    /* pad_050040: 68 bytes of binary data — zero here, ROM image will differ */
    .init_data_ff           = "ffffff",
    /* pad_050096: 238 bytes */
    .init_data_mn           = "                Initial Data(MN)",
    .init_data_xx1          = "xxxxxxxxxxxxxxxx",
    /* pad_0501C0: 1156 bytes */

    /* 0x050644 */
    .dmp9_check_data        = "                DMP9 Check Data ",
    /* pad_050664: 572 bytes */

    /* 0x0508A0 */
    .diag_pattern           = ".h/D0 1",
    /* pad_0508A8: 552 bytes */

    /* 0x050AD0 */
    .startup_banner         = "                Digital Mixing Processor"
                              "                                        ",
    .startup_spaces         = "                ",
    .bulk_midi_dataset      = "EBCD MIDI Data Set",
    .executing              = "  Executing !!",
    .starting_diag          = "Starting DIAG...",
    .check_data_set         = " Check Data Set ",
    .to_edit_buffer         = " To Edit Buffer ",
    .dmp9_status            = "##DMP9 STATUS!##",

    /* 0x050C38 */
    .fmt_check_sum          = "CHECK SUM [%04x]",
    .fmt_dig_sheet_ver      = "DIG Sheet Ver.%c",
    /* pad_050C58: 666 bytes */

    /* 0x050DF2 */
    .factory_set            = "  Factory Set  ",
    .executing2             = "  Executing !!",
    .ram_clear              = "   RAM Clear",
    .executing3             = "  Executing !!",
    .ram_clear_end          = "RAM Clear End",

    /* 0x050E3B */
    .fmt_ch_short           = "  CH %-2d ",
    .fmt_ch_slash           = "CH %2d/%-2d",
    .fmt_return             = "Return %1d",
    .test_pattern           = " == 12345678 == ",

    /* 0x050E6E */
    .midi_fe_stopped0       = "MIDI \"FE\"Stopped",
    .midi_fe_stopped1       = "MIDI \"FE\"Stopped",
    .owners_mode_on         = "OWNER'S Mode ON!",
    .special_mode_on        = "Special Mode ON!",
    .date_jan_1994          = "Date:Jan.19.1994",
    .end_marker             = "*** End ***",

    /* 0x050ED0 — MIDI errors */
    .err_midi_parity        = "MIDI Parity Err",
    .err_midi_overrun       = "MIDI Over Run",
    .err_midi_framing       = "MIDI Framing Err",
    .err_midi_break         = "MIDI Break Rx",
    .err_midi_rxfull        = "MIDI Rx Buf Full",
    .err_midi_txfull        = "MIDI Tx Buf Full",
    .err_midi_irqclr        = "MIDI IRQ Clear",

    /* 0x050F3E — DSP errors */
    .err_dsp_parity         = "DSP Parity Err",
    .err_dsp_overrun        = "DSP Over Run",
    .err_dsp_framing        = "DSP Framing Err",
    .err_dsp_break          = "DSP Break Rx",
    .err_dsp_rxfull         = "DSP Rx Buf Full",
    .err_dsp_txfull         = "DSP Tx Buf Full",
    .err_dsp_irqclr         = "DSP IRQ Clear",
    .err_dsp_endloop        = "DSP Endless Loop",

    /* 0x050FB6 — DEQ errors */
    .err_deq_parity         = "DEQ Parity Err",
    .err_deq_overrun        = "DEQ Over Run",
    .err_deq_framing        = "DEQ Framing Err",
    .err_deq_break          = "DEQ Break Rx",
    .err_deq_rxfull         = "DEQ Rx Buf Full",
    .err_deq_txfull         = "DEQ Tx Buf Full",
    .err_deq_irqclr         = "DEQ IRQ Clear",
    .err_deq_endloop        = "DEQ Endless Loop",

    /* 0x05102E — Memory ops */
    .err_no0_fixed          = "No.0 is FIXED!",
    .err_no_data            = "No Data !",
    .err_mem_protected      = "Memory Protected",
    .err_no_edit_backup0    = "No Edit Backup",
    .err_no_edit_backup1    = "No Edit Backup",
    .err_change_chgroup     = "Change Ch Group!",
    .err_bulk_devno_off     = "Bulk Dev.No.OFF",
    .fmt_bulk_rx_err        = "Bulk Rx Err(%2d)",

    /* 0x0510A8 — 68000 exception strings */
    .err_bus_error          = "Bus Error",
    .err_address_error      = "Address Error",
    .err_illegal_instr      = "Illegal Instruc.",
    .err_div_by_zero        = "Division by 0",
    .err_chk_instr          = "CHK instruc.",
    .err_trapv_instr        = "TRAPV instruc.",
    .err_privilege_viol     = "Violation",
    .err_trace              = "Trace",
    .err_line1010           = "Line 1010 em",
    .err_line1111           = "Line 1111 em",
    .err_sys_reserved       = "System Reserved",
    .err_vec_uninit         = "Vector Uninit",
    .err_spurious_int       = "Spurious Int.",
    .err_sys_check          = "Sys Check Error.",

    /* 0x051173 — Sample rates */
    .fs_off                 = "Off",
    .fs_32khz               = "32KHz",
    .fs_44khz               = "44.1KHz",
    .fs_48khz               = "48KHz",

    /* 0x0512D4 — Scene memory strings */
    .fmt_stored_to_mem      = "Stored to MEM%2d!",
    .stored_to_mem0         = "Stored to MEM0!!",
    .lbl_scene_memory       = "-Scene Memory-",
    .lbl_memory_store       = "--Memory Store--",
    .fmt_memory_no          = "Memory No.%-2d",
    .prompt_sure_store      = "Sure?Push[STORE]",
    .lbl_remote_buffer      = "Remote Buffer to",
    .lbl_edit_buffer        = " Edit Buffer to ",
    .msg_store_cancelled    = "Store Cancelled!!",
    .lbl_vacant             = "   * Vacant *   ",
    .lbl_no_title           = "## No Title!! ##",
    .fmt_memory_no2         = "  Memory No.%d",
    .msg_backup_recalled    = "Backup Recalled",

    /* Remainder of region: binary data, zero-initialised.
     * The ROM diff will show mismatches in pad regions — that is expected
     * until those regions are reverse-engineered and populated. */
};
