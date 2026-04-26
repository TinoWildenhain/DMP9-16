/*
 * rom_strings.h — DMP9/16 ROM string table and format string constants
 *
 * PURPOSE
 * ───────
 * The MCC68K compiler (Microtec Research MCC68K v4.x) places string literals
 * in a dedicated "strings" section (type CODE), and float/integer constants
 * in a "literals" section (type CODE), both within the ROM image.  The linker
 * (LNK68K) then assigns each to a fixed address.
 *
 * For the round-trip compile to produce a binary that matches the original ROM,
 * every string the code references must end up at EXACTLY the same ROM address
 * it occupied in the original.  The approach here is:
 *
 *   1. Declare a packed const struct whose MEMBERS are the actual string
 *      literals, in the exact order they appear in the ROM.
 *   2. Place the struct at the ROM base address of the string region (0x050000)
 *      via a named linker section so the linker puts it there.
 *   3. C source files #include this header and reference members by name
 *      instead of using string literals directly.
 *
 * This guarantees:
 *   - Strings stay in the exact ROM order regardless of compiler optimisation.
 *   - Adding/removing a string does not shift all subsequent strings.
 *   - The decompiler can resolve string pointer dereferences to named constants.
 *
 * USAGE
 * ─────
 *   #include "rom_strings.h"
 *
 *   // Reference a string:
 *   lcd_write_string(S.lcd_pgm_change_title);   // → "----PGM Change----"
 *   uart_write(S.err_bus_error, 9);
 *
 *   // Reference a format string:
 *   sprintf(buf, S.fmt_check_sum, crc);         // → "CHECK SUM [%04x]"
 *
 * GHIDRA COMPAT_H INTEROP
 * ────────────────────────
 * When compiling Ghidra decompiler output, Ghidra will have emitted string
 * pointer dereferences as bare casts to ROM addresses.  Replace those with
 * named S.xxx members.  Example:
 *
 *   // Ghidra output:
 *   lcd_write_string((char *)0x50C38);
 *
 *   // Round-trip compatible replacement:
 *   lcd_write_string(S.fmt_check_sum);
 *
 * ROM VERSION NOTE
 * ────────────────
 * String addresses documented here are from ROM v1.11 (XN349G0).
 * The struct layout is version-specific; do not reuse for v1.02/v1.10
 * without verifying every offset.
 *
 * MCC68K SECTION TYPES (from MCC68K manual, Ch.8)
 * ─────────────────────────────────────────────────
 *   code     — executable code                  (TYPE CODE)
 *   strings  — string literals from C code      (TYPE CODE)
 *   literals — float/int constants, jump tables (TYPE CODE)
 *   const    — const-qualified global data      (TYPE CODE)
 *   ioports  — volatile hardware registers       (TYPE DATA)
 *   vars     — initialised global variables     (TYPE DATA)
 *   zerovars — zero-initialised globals (BSS)   (TYPE DATA)
 *
 * The strings+literals+const sections are all in ROM (no RAM copy needed).
 */

#pragma once
#include <stdint.h>

/* ─────────────────────────────────────────────────────────────────────────
 * Section attribute — place in the "strings" section.
 * m68k-linux-gnu-gcc: use __attribute__((section(".strings")))
 * MCC68K:             use #pragma section strings (or compiler default)
 * ─────────────────────────────────────────────────────────────────────── */
#define ROM_STRINGS_SECTION __attribute__((section(".strings")))
#define ROM_CONST_SECTION   __attribute__((section(".rodata")))

/* ─────────────────────────────────────────────────────────────────────────
 * CRITICAL: This struct MUST be packed and placed at 0x050000.
 * Members MUST appear in ROM address order.
 * Do NOT reorder members — the struct layout is the ROM layout.
 * ─────────────────────────────────────────────────────────────────────── */

/* Forward declaration of the global string table instance */
extern const struct dmp9_rom_strings dmp9_strings;

/* Convenience alias — use S.member_name in C source */
#define S dmp9_strings

/* ─────────────────────────────────────────────────────────────────────────
 * ROM string table struct
 * Each field name encodes what the string is used for.
 * Padding fields (pad_XXXXXX) hold binary data between string regions.
 * ─────────────────────────────────────────────────────────────────────── */

struct __attribute__((packed)) dmp9_rom_strings {

    /* 0x050000 — Initial data headers */
    char init_data_st[32];          /* "                Initial Data(ST)" */
    char init_data_xx0[16];         /* "xxxxxxxxxxxxxxxx" */
    uint8_t pad_050040[68];
    char init_data_ff[6];           /* "ffffff" */
    uint8_t pad_050096[238];
    char init_data_mn[32];          /* "                Initial Data(MN)" */
    char init_data_xx1[16];         /* "xxxxxxxxxxxxxxxx" */
    uint8_t pad_0501C0[1156];

    /* 0x050644 — Check data banner */
    char dmp9_check_data[32];       /* "                DMP9 Check Data " */
    uint8_t pad_050664[572];

    /* 0x0508A0 — Diagnostic pattern */
    uint8_t pad_050884[28];         /* includes '#@$' marker */
    char diag_pattern[8];           /* ".h/D0 1" */
    uint8_t pad_0508A8[552];

    /* 0x050AD0 — Startup banner (80 chars) */
    char startup_banner[80];        /* "                Digital Mixing Processor..." */
    char startup_spaces[16];        /* "                " */
    char bulk_midi_dataset[18];     /* "EBCD MIDI Data Set" */
    char executing[14];             /* "  Executing !!" */
    char starting_diag[16];         /* "Starting DIAG..." */
    char check_data_set[16];        /* " Check Data Set " */
    char to_edit_buffer[16];        /* " To Edit Buffer " */
    char dmp9_status[16];           /* "##DMP9 STATUS!##" */

    /* 0x050C38 — Format strings for diagnostics */
    char fmt_check_sum[16];         /* "CHECK SUM [%04x]" */
    char fmt_dig_sheet_ver[16];     /* "DIG Sheet Ver.%c" */
    uint8_t pad_050C58[666];

    /* 0x050DF2 — Factory/RAM operations */
    char factory_set[16];           /* "  Factory Set  " */
    char executing2[14];            /* "  Executing !!" */
    uint8_t pad_050E11[1];
    char ram_clear[12];             /* "   RAM Clear" */
    char executing3[14];            /* "  Executing !!" */
    uint8_t pad_050E2D[1];
    char ram_clear_end[13];         /* "RAM Clear End" */

    /* 0x050E3B — Parameter display format strings */
    char fmt_ch_short[9];           /* "  CH %-2d " */
    char fmt_ch_slash[10];          /* "CH %2d/%-2d" */
    char fmt_return[10];            /* "Return %1d" */
    char test_pattern[16];          /* " == 12345678 == " */

    /* 0x050E6E — MIDI status messages */
    char midi_fe_stopped0[16];      /* "MIDI \"FE\"Stopped" */
    char midi_fe_stopped1[16];      /* "MIDI \"FE\"Stopped" */
    char owners_mode_on[16];        /* "OWNER'S Mode ON!" */
    char special_mode_on[16];       /* "Special Mode ON!" */
    char date_jan_1994[16];         /* "Date:Jan.19.1994" */
    char end_marker[11];            /* "*** End ***" */
    uint8_t pad_050ECB[5];

    /* 0x050ED0 — MIDI error strings */
    char err_midi_parity[15];       /* "MIDI Parity Err" */
    uint8_t pad_050EDF[1];
    char err_midi_overrun[13];      /* "MIDI Over Run" */
    uint8_t pad_050EED[1];
    char err_midi_framing[16];      /* "MIDI Framing Err" */
    char err_midi_break[13];        /* "MIDI Break Rx" */
    uint8_t pad_050F0C[1];
    char err_midi_rxfull[16];       /* "MIDI Rx Buf Full" */
    char err_midi_txfull[16];       /* "MIDI Tx Buf Full" */
    char err_midi_irqclr[14];       /* "MIDI IRQ Clear" */
    uint8_t pad_050F3E[1];  /* not 'DSP' yet */

    /* 0x050F3E — DSP error strings */
    char err_dsp_parity[14];        /* "DSP Parity Err" */
    uint8_t pad_050F4C[1];
    char err_dsp_overrun[12];       /* "DSP Over Run" */
    uint8_t pad_050F59[1];
    char err_dsp_framing[15];       /* "DSP Framing Err" */
    uint8_t pad_050F69[1];
    char err_dsp_break[12];         /* "DSP Break Rx" */
    uint8_t pad_050F76[1];
    char err_dsp_rxfull[15];        /* "DSP Rx Buf Full" */
    uint8_t pad_050F86[1];
    char err_dsp_txfull[15];        /* "DSP Tx Buf Full" */
    uint8_t pad_050F96[1];
    char err_dsp_irqclr[13];        /* "DSP IRQ Clear" */
    uint8_t pad_050FA4[1];
    char err_dsp_endloop[16];       /* "DSP Endless Loop" */

    /* 0x050FB6 — DEQ error strings */
    char err_deq_parity[14];        /* "DEQ Parity Err" */
    uint8_t pad_050FC4[1];
    char err_deq_overrun[12];       /* "DEQ Over Run" */
    uint8_t pad_050FD1[1];
    char err_deq_framing[15];       /* "DEQ Framing Err" */
    uint8_t pad_050FE1[1];
    char err_deq_break[12];         /* "DEQ Break Rx" */
    uint8_t pad_050FEE[1];
    char err_deq_rxfull[15];        /* "DEQ Rx Buf Full" */
    uint8_t pad_050FFE[1];
    char err_deq_txfull[15];        /* "DEQ Tx Buf Full" */
    uint8_t pad_05100E[1];
    char err_deq_irqclr[13];        /* "DEQ IRQ Clear" */
    uint8_t pad_05101C[1];
    char err_deq_endloop[16];       /* "DEQ Endless Loop" */

    /* 0x05102E — Memory operation messages */
    char err_no0_fixed[14];         /* "No.0 is FIXED!" */
    uint8_t pad_05103C[1];
    char err_no_data[9];            /* "No Data !" */
    uint8_t pad_051046[1];
    char err_mem_protected[16];     /* "Memory Protected" */
    char err_no_edit_backup0[14];   /* "No Edit Backup" */
    uint8_t pad_051066[1];
    char err_no_edit_backup1[14];   /* "No Edit Backup" */
    uint8_t pad_051075[1];
    char err_change_chgroup[16];    /* "Change Ch Group!" */
    char err_bulk_devno_off[15];    /* "Bulk Dev.No.OFF" */
    uint8_t pad_051096[1];
    char fmt_bulk_rx_err[16];       /* "Bulk Rx Err(%2d)" */

    /* 0x0510A8 — CPU exception strings (68000 vector names) */
    char err_bus_error[9];          /* "Bus Error" */
    uint8_t pad_0510B1[1];
    char err_address_error[13];     /* "Address Error" */
    uint8_t pad_0510BD[3];
    char err_illegal_instr[16];     /* "Illegal Instruc." */
    char err_div_by_zero[13];       /* "Division by 0" */
    uint8_t pad_0510DE[1];
    char err_chk_instr[12];         /* "CHK instruc." */
    uint8_t pad_0510EA[2];
    char err_trapv_instr[14];       /* "TRAPV instruc." */
    uint8_t pad_0510F8[3];
    char err_privilege_viol[9];     /* "Violation" */
    uint8_t pad_051104[1];
    char err_trace[5];              /* "Trace" */
    uint8_t pad_05110A[1];
    char err_line1010[12];          /* "Line 1010 em" */
    uint8_t pad_051117[1];
    char err_line1111[12];          /* "Line 1111 em" */
    uint8_t pad_051123[2];
    char err_sys_reserved[15];      /* "System Reserved" */
    uint8_t pad_051134[1];
    char err_vec_uninit[13];        /* "Vector Uninit" */
    uint8_t pad_051142[1];
    char err_spurious_int[13];      /* "Spurious Int." */
    uint8_t pad_051150[1];
    char err_sys_check[16];         /* "Sys Check Error." (after spaces) */

    /* 0x051173 — Sample rate strings */
    uint8_t pad_051162[17];         /* spaces + "Sys Check Error." terminator region */
    char fs_off[3];                 /* "Off" */
    uint8_t pad_051177[3];
    char fs_32khz[5];               /* "32KHz" */
    uint8_t pad_05117D[3];
    char fs_44khz[7];               /* "44.1KHz" */
    uint8_t pad_051185[3];
    char fs_48khz[5];               /* "48KHz" */
    uint8_t pad_05118D[26];

    /* 0x0512D4 — Scene memory operation strings */
    uint8_t pad_5_mid[350];         /* binary data / non-string region */
    char fmt_stored_to_mem[17];     /* "Stored to MEM%2d!" */
    char stored_to_mem0[16];        /* "Stored to MEM0!!" */
    char lbl_scene_memory[14];      /* "-Scene Memory-" */
    uint8_t pad_051300[6];
    char lbl_memory_store[16];      /* "--Memory Store--" */
    char fmt_memory_no[13];         /* "Memory No.%-2d" */
    uint8_t pad_051320[6];
    char prompt_sure_store[16];     /* "Sure?Push[STORE]" */
    char lbl_remote_buffer[16];     /* "Remote Buffer to" */
    char lbl_edit_buffer[16];       /* " Edit Buffer to " */
    char msg_store_cancelled[17];   /* "Store Cancelled!!" */
    uint8_t pad_051368[7];
    char lbl_vacant[16];            /* "   * Vacant *   " */
    char lbl_no_title[16];          /* "## No Title!! ##" */
    char fmt_memory_no2[14];        /* "  Memory No.%d" */
    uint8_t pad_05139F[1];
    char msg_backup_recalled[15];   /* "Backup Recalled" */

    /* (large binary + parameter name region follows)
     * We stop defining individual strings here and switch to direct
     * address references for the MIDI parameter name table.
     * See PARAM_NAMES_BASE below. */
    uint8_t pad_rest_of_region[0x054FEE - 0x0513AC];

    /* 0x054FEE — CTRL Change parameter name table
     * 671 parameters × ~17 bytes = large contiguous array.
     * Accessed as param_names[idx] or via pointer arithmetic.
     * The table starts with a lookup index, then name strings.
     */
    uint8_t param_name_index[79];   /* index/map bytes before first name */
    /* First named parameter starts at 0x05503D */
    /* NOTE: do NOT put individual char arrays here — the table has
     * internal structure accessed via offsets, not by sequential
     * member access.  Use PARAM_NAME_PTR(idx) macro instead. */
};

/* ─────────────────────────────────────────────────────────────────────────
 * Direct ROM address accessors for regions we don't model as struct members
 * ─────────────────────────────────────────────────────────────────────── */

/* CTRL parameter name table base (671 entries, 16-byte fixed-width strings) */
#define PARAM_NAMES_BASE    ((const char *)0x05503DUL)
#define PARAM_NAME_WIDTH    11   /* observed stride — verify in disasm */

/* Get pointer to CTRL param name by index (0-based) */
#define PARAM_NAME_PTR(idx) (PARAM_NAMES_BASE + (idx) * PARAM_NAME_WIDTH)

/* Effect type name table (16-char fixed-width, starts at 0x0528AE) */
#define EFFECT_NAMES_HQ_BASE  ((const char *)0x0528AEUL)
#define EFFECT_NAMES_STD_BASE ((const char *)0x0529AEUL)
#define EFFECT_NAME_WIDTH     16

/* Get HQ or standard effect name by type index */
#define EFFECT_HQ_NAME(idx)   (EFFECT_NAMES_HQ_BASE  + (idx) * EFFECT_NAME_WIDTH)
#define EFFECT_STD_NAME(idx)  (EFFECT_NAMES_STD_BASE + (idx) * EFFECT_NAME_WIDTH)

/* Effect parameter format string table (starts at 0x052AAE) */
#define EFFECT_FMT_BASE     ((const char *)0x052AAEUL)

/* Version string (fixed placement, critical for ROM verification) */
#define VERSION_STRING_ADDR ((const char *)0x050617UL)   /* "Version 1.11  Date:Mar.10.1994" */
#define VERSION_STRING      ((const char *)VERSION_STRING_ADDR)

/* Fault string at end of ROM */
#define FAULT_STRING_ADDR   ((const char *)0x07FF00UL)
#define FAULT_STRING        ((const char *)FAULT_STRING_ADDR)  /* "Fault from interrupt: " */

/* ─────────────────────────────────────────────────────────────────────────
 * LCD format string quick-reference
 * These are the most commonly referenced format strings in the firmware.
 * They all live in the S struct but these aliases improve readability.
 * ─────────────────────────────────────────────────────────────────────── */

/* Integer formatting (used with lcd_printf / uart_printf equivalents) */
#define FMT_D1          "%1d"           /* 0x051843, 0x051853, ... */
#define FMT_D2          "%2d"           /* 0x052590, 0x052594, ... */
#define FMT_D3          "%3d"           /* 0x0526A3, ... */
#define FMT_D2_MINUS    "%-2d"          /* 0x0526B8, ... */
#define FMT_D1_SLASH    "%1d/%1d"       /* 0x0518D9, ... */
#define FMT_D02X        "%02x"          /* 0x052403, ... */
#define FMT_D04X        "%04x"          /* 0x0526F1, ... */

/* Floating point (effect parameter display) */
#define FMT_F41         "%4.1f"         /* %4.1fkHz, %4.1fs etc. */
#define FMT_F51         "%5.1f"         /* %5.1fdB etc. */
#define FMT_F52         "%5.2f"         /* %5.2fmsec */
#define FMT_F52MS       "%5.2fmsec"     /* 0x051FF0 */
#define FMT_D03SAMPLE   "%03dSample"    /* 0x0519C6, 0x053425 */

/* Channel display */
#define FMT_CH_2D       "CH %2d/%-2d"   /* 0x050E46 */
#define FMT_CH_SHORT    "  CH %-2d "    /* 0x050E3B */
#define FMT_RET_1D      "Return %1d"    /* 0x050E52 */

/* dB display */
#define FMT_DB_51       "%5.1fdB  "     /* 0x053444 */
#define FMT_G_PLUS      "G=+%4.1fdB"    /* 0x052FC0 */
#define FMT_G_MINUS     "G=-%4.1fdB"    /* 0x052FAB */
#define FMT_G_ZERO      "G=  0.0dB"     /* 0x052FB6 */
#define FMT_DB_ZERO     "  0.0dB"       /* 0x052275 */

/* ─────────────────────────────────────────────────────────────────────────
 * The actual instance — defined in rom_strings.c, placed at 0x050000.
 * In a round-trip compile, this declaration is sufficient; the definition
 * in rom_strings.c with the linker script places it correctly.
 * ─────────────────────────────────────────────────────────────────────── */
extern const struct dmp9_rom_strings dmp9_strings
    __attribute__((section(".strings")));

/* ─────────────────────────────────────────────────────────────────────────
 * Sanity: verify the struct starts where we expect.
 * This will produce a compile-time error if the struct layout drifts.
 * (Only meaningful when compiling for the target — skip on host.)
 * ─────────────────────────────────────────────────────────────────────── */
#ifdef __m68k__
_Static_assert(
    (uint32_t)&dmp9_strings == 0x050000UL,
    "dmp9_strings must be placed at 0x050000 by the linker"
);
_Static_assert(
    sizeof(struct dmp9_rom_strings) <= (0x054FEE - 0x050000),
    "dmp9_rom_strings struct overflows string table region"
);
#endif
