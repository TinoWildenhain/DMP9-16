/**
 * startup.c — Reset handler and hardware initialisation chain
 * XN349G0 (v1.11, 1994-03-10)
 *
 * Called via vector table vec[1].  Uses yamaha_reg calling convention
 * (no stack frame, register-based), marked noreturn.
 *
 * NOTE: This is a reconstruction skeleton.  The actual init sequence must be
 *       transcribed from the Ghidra decompiler output for this ROM version.
 */

#include <stdint.h>
#include "../hw/tmp68301.h"
#include "../hw/dsp.h"
#include "../hw/lcd.h"
#include "../hw/midi.h"

/* -------------------------------------------------------------------------
 * Version identification
 * Placed at fixed ROM offset 0x05059A by linker (see dmp9.ld)
 * ------------------------------------------------------------------------- */
__attribute__((section(".version_string")))
const char firmware_version[] = "Version 1.11  Date Mar.10.1994";

/* -------------------------------------------------------------------------
 * Forward declarations
 * ------------------------------------------------------------------------- */
extern void main_loop(void) __attribute__((noreturn));

/* -------------------------------------------------------------------------
 * Reset handler
 * yamaha_reg CC: no LINK, no stack frame; args in D0–D3/A0–A1
 * ------------------------------------------------------------------------- */
__attribute__((noreturn))
void _reset_handler(void)
{
    /*
     * TODO: Transcribe init sequence from Ghidra decompiler output.
     *
     * Typical sequence observed in v1.11:
     *   1. Disable all interrupts (SR = 0x2700)
     *   2. Initialise TMP68301 internal registers (SC0CR, SC1CR, timers)
     *   3. Clear DRAM (0x400000–0x40FECF)
     *   4. Initialise LCD (HD44780 command sequence)
     *   5. Initialise DSP EF1/EF2 (YM3804 etc.)
     *   6. Initialise MIDI I/O via TMP68301 SC0/SC1
     *   7. Jump to main_loop()
     */

    /* Placeholder — replace with actual init transcription */
    for (;;);
}
