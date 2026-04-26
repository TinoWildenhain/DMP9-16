/**
 * dsp.c — DSP EF1/EF2 control
 * XN349F0 (v1.10, 1994-01-20)
 *
 * EF1: YM3804 + YM6007 + YM3807 + YM6104 at 0x460000
 * EF2: YM3804 + YM3807 at 0x470000
 *
 * Address decoding is done by external logic circuits.
 */

#include <stdint.h>
#include "../hw/dsp.h"

#define DSP_EF1_BASE  ((volatile uint8_t *)0x00460000UL)
#define DSP_EF2_BASE  ((volatile uint8_t *)0x00470000UL)

void dsp_init(void)
{
    /*
     * TODO: Transcribe DSP initialisation from Ghidra analysis.
     * Known: coefficient tables at 0x059B00 are written to DSP registers
     * during init.
     */
}

void dsp_write_ef1(uint16_t reg, uint8_t val)
{
    DSP_EF1_BASE[reg] = val;
}

void dsp_write_ef2(uint16_t reg, uint8_t val)
{
    DSP_EF2_BASE[reg] = val;
}
