/**
 * main.c — Main firmware loop and application code
 * XN349G0 (v1.11, 1994-03-10)
 *
 * This file is a reconstruction skeleton.  The actual code must be
 * transcribed from Ghidra decompiler output for this ROM version.
 *
 * ROM region: 0x005000–0x04FFFF (main code)
 */

#include <stdint.h>
#include "../hw/tmp68301.h"
#include "../hw/dsp.h"
#include "../hw/lcd.h"
#include "../hw/midi.h"

/* -------------------------------------------------------------------------
 * Known confirmed functions (from Ghidra analysis of XN349G0)
 * Addresses below are from v1.11 (XN349G0) — update per-version.
 * ------------------------------------------------------------------------- */

/**
 * print_rjust16 — Right-justify string to 16 chars and send via UART
 * Measures strlen, pads with spaces, calls uart_write twice.
 * @param s  NUL-terminated string (in A0)
 */
void print_rjust16(const char *s);

/**
 * uart_write — Write byte string to UART (SC0 via TMP68301)
 * @param str  Pointer to data (in A0)
 * @param len  Length in bytes (in D0)
 */
void uart_write(const char *str, int len);

/* Error report functions — called from ISR stubs */
void report_bus_error(void)     { print_rjust16("Bus Error");     }
void report_address_error(void) { print_rjust16("Address Error"); }
void report_illegal_insn(void)  { print_rjust16("Illegal Insn"); }
void report_zero_divide(void)   { print_rjust16("Zero Divide");  }
/* TODO: remaining 9 report_* functions */

/* -------------------------------------------------------------------------
 * Main loop — noreturn
 * ------------------------------------------------------------------------- */
__attribute__((noreturn))
void main_loop(void)
{
    /*
     * TODO: Transcribe application logic from Ghidra decompiler output.
     *
     * Known top-level behaviour:
     *   - Reads front-panel controls (faders, buttons) via TMP68301 ports
     *   - Updates LCD display via HD44780 at 0x490000/0x490001
     *   - Processes incoming MIDI via SC0/SC1
     *   - Sends DSP parameter updates to EF1/EF2
     *   - Handles MIDI SysEx preset dump/load
     */
    for (;;);
}
