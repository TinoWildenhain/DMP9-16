/**
 * dsp.h — DSP EF1/EF2 definitions for Yamaha DMP9/16
 *
 * EF1 (Effects processor 1): YM3804 + YM6007 + YM3807 + YM6104
 *   Base address: 0x460000
 *
 * EF2 (Effects processor 2): YM3804 + YM3807
 *   Base address: 0x470000
 *
 * Address decoding for both DSP banks is done by external logic circuits.
 *
 * TODO: Map individual chip register offsets within each bank.
 *       This requires further Ghidra analysis of DSP init sequences.
 */

#ifndef DMP9_DSP_H
#define DMP9_DSP_H

#include <stdint.h>

/* -------------------------------------------------------------------------
 * Base addresses
 * ------------------------------------------------------------------------- */
#define DSP_EF1_BASE_ADDR  0x00460000UL
#define DSP_EF2_BASE_ADDR  0x00470000UL

/* -------------------------------------------------------------------------
 * Chip offsets within EF1 bank (tentative — verify from ROM analysis)
 * EF1: YM3804(0) + YM6007(?) + YM3807(?) + YM6104(?)
 * ------------------------------------------------------------------------- */
#define DSP_EF1_YM3804_OFFSET   0x0000   /**< YM3804 base in EF1 */
#define DSP_EF1_YM6007_OFFSET   0x0100   /**< YM6007 base in EF1 — TBD */
#define DSP_EF1_YM3807_OFFSET   0x0200   /**< YM3807 base in EF1 — TBD */
#define DSP_EF1_YM6104_OFFSET   0x0300   /**< YM6104 base in EF1 — TBD */

/* -------------------------------------------------------------------------
 * Chip offsets within EF2 bank (tentative)
 * EF2: YM3804(0) + YM3807(?)
 * ------------------------------------------------------------------------- */
#define DSP_EF2_YM3804_OFFSET   0x0000   /**< YM3804 base in EF2 */
#define DSP_EF2_YM3807_OFFSET   0x0200   /**< YM3807 base in EF2 — TBD */

/* -------------------------------------------------------------------------
 * Coefficient / preset table (ROM, identical all versions)
 * Loaded into DSP registers during init.
 * ------------------------------------------------------------------------- */
#define DSP_COEFF_ROM_BASE  0x059B00U
#define DSP_COEFF_ROM_END   0x062840U
#define DSP_COEFF_ROM_SIZE  (DSP_COEFF_ROM_END - DSP_COEFF_ROM_BASE)

/* -------------------------------------------------------------------------
 * Inline accessors
 * ------------------------------------------------------------------------- */
static inline void dsp_ef1_write(uint16_t reg, uint8_t val)
{
    ((volatile uint8_t *)DSP_EF1_BASE_ADDR)[reg] = val;
}

static inline uint8_t dsp_ef1_read(uint16_t reg)
{
    return ((volatile uint8_t *)DSP_EF1_BASE_ADDR)[reg];
}

static inline void dsp_ef2_write(uint16_t reg, uint8_t val)
{
    ((volatile uint8_t *)DSP_EF2_BASE_ADDR)[reg] = val;
}

static inline uint8_t dsp_ef2_read(uint16_t reg)
{
    return ((volatile uint8_t *)DSP_EF2_BASE_ADDR)[reg];
}

/* -------------------------------------------------------------------------
 * Driver API
 * ------------------------------------------------------------------------- */
void dsp_init(void);

#endif /* DMP9_DSP_H */
