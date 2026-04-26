/**
 * isr.h — ISR stub declarations (XN349G0)
 * Forward declarations so vectors.c can reference them.
 */

#ifndef DMP9_ISR_H
#define DMP9_ISR_H

/* Exception handlers */
void _isr_bus_error(void)     __attribute__((interrupt));
void _isr_address_error(void) __attribute__((interrupt));
void _isr_illegal_insn(void)  __attribute__((interrupt));
void _isr_zero_divide(void)   __attribute__((interrupt));
void _isr_reserved(void)      __attribute__((interrupt));
void _isr_spurious(void)      __attribute__((interrupt));
void _isr_autovec1(void)      __attribute__((interrupt));
void _isr_autovec2(void)      __attribute__((interrupt));
void _isr_autovec3(void)      __attribute__((interrupt));

#endif /* DMP9_ISR_H */
