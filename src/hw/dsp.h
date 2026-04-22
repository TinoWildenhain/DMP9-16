#ifndef DSP_H
#define DSP_H

/*
 * dsp.h - Yamaha YM3804-family DSP register access
 *
 * EF1: YM3804 + YM6007 + YM3807 + YM6104 (full reverb/delay/EQ engine)
 * EF2: YM3804 + YM3807 (stripped reverb/delay engine)
 *
 * RE_NOTE: Register map partially inferred from SPX-900 service manual
 * and TritonCore YM3413 implementation. Treat with caution.
 */

#include <stdint.h>

#define DSP_EF1_BASE  0x00460000UL
#define DSP_EF2_BASE  0x00470000UL

#define DSP_EF1_REG   (*(volatile uint16_t *)(DSP_EF1_BASE + 0x00))
#define DSP_EF1_DATA  (*(volatile uint16_t *)(DSP_EF1_BASE + 0x02))
#define DSP_EF2_REG   (*(volatile uint16_t *)(DSP_EF2_BASE + 0x00))
#define DSP_EF2_DATA  (*(volatile uint16_t *)(DSP_EF2_BASE + 0x02))

/* Effect type identifiers (RE_NOTE: partial, verify against ROM tables) */
typedef enum {
    EFX_REVERB_HALL    = 0x00,
    EFX_REVERB_ROOM    = 0x01,
    EFX_REVERB_PLATE   = 0x02,
    EFX_REVERB_STAGE   = 0x03,
    EFX_DELAY_MONO     = 0x10,
    EFX_DELAY_STEREO   = 0x11,
    EFX_DELAY_PINGPONG = 0x12,
    EFX_CHORUS         = 0x20,
    EFX_FLANGER        = 0x21,
    EFX_PHASER         = 0x22,
    EFX_PITCH_SHIFT    = 0x30,  /* EF1 only, requires YM6007 */
    EFX_EQ_PARAMETRIC  = 0x40,  /* EF1 only, requires YM6104 */
} dsp_effect_type_t;

void dsp_ef1_write(uint16_t reg, uint16_t data);
void dsp_ef2_write(uint16_t reg, uint16_t data);
void dsp_ef1_set_effect(dsp_effect_type_t type);
void dsp_ef2_set_effect(dsp_effect_type_t type);

#endif /* DSP_H */
