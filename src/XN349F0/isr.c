/**
 * isr.c — ISR stubs (exception / interrupt service routines)
 * XN349F0 (v1.10, 1994-01-20)
 *
 * Each stub follows the pattern observed at ROM 0x000400–0x0007FF:
 *   MOVEM.L <all regs>, -(SP)
 *   JSR     report_<error>
 *   MOVEM.L (SP)+, <all regs>
 *   STOP    #0x2700         ; deliberate halt — panic
 *   RTE
 *
 * The STOP+RTE combination is a firmware-level panic: the system halts with
 * interrupts fully masked.  This is intentional.
 *
 * Uses __interrupt calling convention (defined in tmp68301.cspec).
 */

#include <stdint.h>
#include "../hw/tmp68301.h"

/* -------------------------------------------------------------------------
 * Report functions (defined in main.c / reconstructed from Ghidra)
 * ------------------------------------------------------------------------- */
extern void report_bus_error(void);
extern void report_address_error(void);
extern void report_illegal_insn(void);
extern void report_zero_divide(void);
/* TODO: add remaining report_* functions as identified by Ghidra */

/* -------------------------------------------------------------------------
 * ISR stub macro
 * Each ISR saves all registers, calls report function, then halts.
 * ------------------------------------------------------------------------- */
#define PANIC_ISR(name, report_fn)              \
__attribute__((interrupt))                      \
void name(void) {                               \
    report_fn();                                \
    /* STOP #0x2700 — implemented via inline asm */ \
    __asm__ volatile ("stop #0x2700" ::: "memory"); \
}

PANIC_ISR(_isr_bus_error,     report_bus_error)
PANIC_ISR(_isr_address_error, report_address_error)
PANIC_ISR(_isr_illegal_insn,  report_illegal_insn)
PANIC_ISR(_isr_zero_divide,   report_zero_divide)

/* Generic stubs for remaining exception vectors */
__attribute__((interrupt))
void _isr_reserved(void) {
    __asm__ volatile ("stop #0x2700" ::: "memory");
}

__attribute__((interrupt))
void _isr_spurious(void)  { _isr_reserved(); }
__attribute__((interrupt))
void _isr_uninit(void)    { _isr_reserved(); }
__attribute__((interrupt))
void _isr_trap_generic(void) { _isr_reserved(); }
__attribute__((interrupt))
void _isr_chk(void)       { _isr_reserved(); }
__attribute__((interrupt))
void _isr_trapv(void)     { _isr_reserved(); }
__attribute__((interrupt))
void _isr_privilege(void) { _isr_reserved(); }
__attribute__((interrupt))
void _isr_trace(void)     { _isr_reserved(); }
__attribute__((interrupt))
void _isr_line_a(void)    { _isr_reserved(); }
__attribute__((interrupt))
void _isr_line_f(void)    { _isr_reserved(); }

/* Autovectors — TMP68301 routes hardware interrupts here */
__attribute__((interrupt))
void _isr_autovec1(void)  { /* TODO: MIDI SC0 interrupt? */ }
__attribute__((interrupt))
void _isr_autovec2(void)  { /* TODO: MIDI SC1 interrupt? */ }
__attribute__((interrupt))
void _isr_autovec3(void)  { /* TODO: Timer interrupt?   */ }
__attribute__((interrupt))
void _isr_autovec4(void)  { /* TODO: DSP interrupt?     */ }
__attribute__((interrupt))
void _isr_autovec5(void)  { /* TODO                     */ }
__attribute__((interrupt))
void _isr_autovec6(void)  { /* TODO                     */ }
__attribute__((interrupt))
void _isr_autovec7(void)  { /* TODO                     */ }
