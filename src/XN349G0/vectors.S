/**
 * vectors.S — Exception vector table
 * XN349G0 (v1.11, 1994-03-10)
 *
 * The 68000 vector table occupies ROM 0x000000–0x00017F (96 vectors × 4 bytes).
 * Hardware I/O addresses are NOT in the vector table — this file only defines
 * the CPU exception dispatch entries.
 *
 * NOTE: Actual target addresses for ISR stubs must be verified against the
 *       original ROM disassembly produced by Ghidra.  Placeholders are used
 *       where Ghidra analysis is still pending.
 */

    .section .vectors, "a", @progbits
    .global _vector_table

/* Stack pointer initial value (SSP) and reset PC */
_vector_table:
    .long   0x0040FED0    /* Vec[0]  — Initial SSP */
    .long   _reset_handler     /* Vec[1]  — Reset PC    */

    /* Vec[2]–Vec[5]: Bus/Address/Illegal/ZeroDivide */
    .long   _isr_bus_error
    .long   _isr_address_error
    .long   _isr_illegal_insn
    .long   _isr_zero_divide

    /* Vec[6]–Vec[7]: CHK/TRAPV */
    .long   _isr_chk
    .long   _isr_trapv

    /* Vec[8]–Vec[10]: Privilege/Trace/Line-A */
    .long   _isr_privilege
    .long   _isr_trace
    .long   _isr_line_a

    /* Vec[11]: Line-F */
    .long   _isr_line_f

    /* Vec[12]–Vec[14]: Reserved (hardware errors) */
    .long   _isr_reserved
    .long   _isr_reserved
    .long   _isr_reserved

    /* Vec[15]: Uninitialised interrupt */
    .long   _isr_uninit

    /* Vec[16]–Vec[23]: Reserved */
    .rept   8
    .long   _isr_reserved
    .endr

    /* Vec[24]–Vec[31]: Spurious + Autovectors 1–7 */
    .long   _isr_spurious
    .long   _isr_autovec1
    .long   _isr_autovec2
    .long   _isr_autovec3
    .long   _isr_autovec4
    .long   _isr_autovec5
    .long   _isr_autovec6
    .long   _isr_autovec7

    /* Vec[32]–Vec[47]: TRAP #0–#15 */
    .rept   16
    .long   _isr_trap_generic
    .endr

    /* Vec[48]–Vec[63]: Reserved */
    .rept   16
    .long   _isr_reserved
    .endr

    /* Vec[64]–Vec[95]: User interrupt vectors (TMP68301 IRQ routing) */
    /* TODO: Fill in from Ghidra disassembly of original ROM */
    .rept   32
    .long   _isr_reserved
    .endr
