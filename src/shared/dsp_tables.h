/**
 * dsp_tables.h — DSP coefficient / EQ table declarations
 *
 * The data/coefficient tables (ROM 0x059B00–0x06283F) are BYTE-IDENTICAL
 * across all three firmware versions (XN349E0/F0/G0).  They are extracted
 * once and shared via this translation unit.
 *
 * Do NOT modify this file per-version — it must compile identically for
 * XN349E0, XN349F0, and XN349G0.
 */

#ifndef DMP9_DSP_TABLES_H
#define DMP9_DSP_TABLES_H

#include <stdint.h>

/* -------------------------------------------------------------------------
 * Linker symbols — placed at fixed addresses by dmp9.ld
 * ------------------------------------------------------------------------- */

/** Base of shared coefficient table block (ROM 0x059B00) */
extern const uint8_t  dsp_coeff_table[];

/** End of shared data block (ROM 0x062840) */
extern const uint8_t  dsp_coeff_table_end[];

/* -------------------------------------------------------------------------
 * Table size constant
 * ------------------------------------------------------------------------- */
#define DSP_COEFF_TABLE_BASE  0x059B00U
#define DSP_COEFF_TABLE_END   0x062840U
#define DSP_COEFF_TABLE_SIZE  (DSP_COEFF_TABLE_END - DSP_COEFF_TABLE_BASE)

#endif /* DMP9_DSP_TABLES_H */
