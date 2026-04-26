/**
 * vectors.c — Exception vector table initialiser (XN349G0, v1.11, 1994-03-10)
 *
 * Defines the const struct placed at ROM 0x000000 by the linker.
 *
 * KEY CONSTRAINT: hardware I/O addresses (LCD 0x490000, DSP 0x460000/470000,
 * TMP68301 0xFFFC00) are accessed as volatile pointers in the C code — they
 * are NOT represented in the vector table.  Only CPU exception vectors go here.
 *
 * CONFIRMED VALUES (from ROM analysis of XN349G0):
 *   Vec[0] SSP = 0x0040FED0  (just below 64 KB DRAM ceiling)
 *   Vec[1] PC  = 0x0003BF4E  (reset handler, via JMP thunk at 0x000400)
 *   Vec[2..5]  = ISR stubs in isr.c (MOVEM+JSR+STOP+RTE pattern)
 *   Vec[6..23] = reserved / panic stubs
 *   Vec[24]    = spurious stub
 *   Vec[25–31] = autovector stubs (TMP68301 HW interrupts)
 *   Vec[32–47] = TRAP stubs
 *   Vec[48–95] = reserved / to be filled from Ghidra analysis
 *
 * AUTOVECTOR INTERRUPT MAPPING (from TMP68301 + PLD documentation):
 *   INT0 → SC0 (MIDI serial)      → autovec level determined by TMP config
 *   INT1 → SC1 (second UART?)     → autovec level TBD
 *   INT2 → Timer TOUT0/TOUT1      → autovec level TBD
 *   The three TMP68301 interrupt inputs (INTO/INT1/INT2) are routed to
 *   autovector levels via TMP68301 interrupt control register.
 */

#include "../shared/vectors.h"
#include "isr.h"
#include "startup.h"

/* -------------------------------------------------------------------------
 * Vector table — placed at ROM 0x000000
 * ------------------------------------------------------------------------- */
__attribute__((section(".vectors")))
const m68k_vector_table_t _vector_table = {

    /* Vec[0]: Initial SSP — top of 64 KB DRAM (grows downward) */
    .initial_ssp    = 0x0040FED0UL,

    /* Vec[1]: Reset PC — actual reset_handler (via JMP thunk for XN349G0) */
    .reset_pc       = _reset_handler,

    /* Vec[2–5]: Exception handlers (PANIC: print message, STOP, RTE) */
    .bus_error      = _isr_bus_error,
    .address_error  = _isr_address_error,
    .illegal_insn   = _isr_illegal_insn,
    .zero_divide    = _isr_zero_divide,

    /* Vec[6–11]: Further synchronous exceptions */
    .chk            = _isr_reserved,
    .trapv          = _isr_reserved,
    .privilege_viol = _isr_reserved,
    .trace          = _isr_reserved,
    .line_a         = _isr_reserved,
    .line_f         = _isr_reserved,

    /* Vec[12–23]: Reserved */
    .reserved_12    = _isr_reserved,
    .reserved_13    = _isr_reserved,
    .reserved_14    = _isr_reserved,
    .uninit_interrupt = _isr_reserved,
    .reserved_16    = _isr_reserved,
    .reserved_17    = _isr_reserved,
    .reserved_18    = _isr_reserved,
    .reserved_19    = _isr_reserved,
    .reserved_20    = _isr_reserved,
    .reserved_21    = _isr_reserved,
    .reserved_22    = _isr_reserved,
    .reserved_23    = _isr_reserved,

    /* Vec[24–31]: Hardware interrupt autovectors */
    .spurious       = _isr_spurious,
    .autovec_1      = _isr_autovec1,   /* TMP68301 INTO (MIDI SC0?) */
    .autovec_2      = _isr_autovec2,   /* TMP68301 INT1 (SC1?)      */
    .autovec_3      = _isr_autovec3,   /* TMP68301 INT2 (Timer?)    */
    .autovec_4      = _isr_reserved,
    .autovec_5      = _isr_reserved,
    .autovec_6      = _isr_reserved,
    .autovec_7      = _isr_reserved,   /* Level 7 / NMI             */

    /* Vec[32–47]: TRAP #0–#15 */
    .trap = {
        [0]  = _isr_reserved,
        [1]  = _isr_reserved,
        [2]  = _isr_reserved,
        [3]  = _isr_reserved,
        [4]  = _isr_reserved,
        [5]  = _isr_reserved,
        [6]  = _isr_reserved,
        [7]  = _isr_reserved,
        [8]  = _isr_reserved,
        [9]  = _isr_reserved,
        [10] = _isr_reserved,
        [11] = _isr_reserved,
        [12] = _isr_reserved,
        [13] = _isr_reserved,
        [14] = _isr_reserved,
        [15] = _isr_reserved,
    },

    /* Vec[48–63]: Reserved */
    .reserved_48_63 = {
        [0 ... 15] = _isr_reserved,
    },

    /* Vec[64–95]: User/peripheral interrupt vectors
     * TODO: Fill from Ghidra analysis of TMP68301 interrupt init sequence.
     * The TMP68301 IACK0/IACK1/IACK2 pins drive the vector number placed
     * on D0–D7 during the interrupt acknowledge cycle.
     */
    .user = {
        [0 ... 31] = _isr_reserved,
    },
};
