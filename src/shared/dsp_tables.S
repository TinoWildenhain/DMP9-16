/**
 * dsp_tables.S — DSP coefficient / EQ tables
 *
 * Extracted from any of the three ROM images at offset 0x059B00, length
 * DSP_COEFF_TABLE_SIZE bytes.  Content is IDENTICAL across XN349E0/F0/G0.
 *
 * To regenerate:
 *   dd if=<rom.bin> bs=1 skip=$((0x059B00)) count=$((0x062840-0x059B00)) \
 *      of=dsp_raw.bin
 *   xxd -i dsp_raw.bin | sed '...' > dsp_tables.S   # or use objcopy
 *
 * For now this file is a placeholder.  The actual binary blob should be
 * included using .incbin once extracted.
 */

    .section .rodata.dsp_tables, "a", @progbits
    .global dsp_coeff_table
    .global dsp_coeff_table_end

dsp_coeff_table:
    /* TODO: .incbin "dsp_raw.bin" */
    .space (0x062840 - 0x059B00), 0xFF   /* placeholder — replace with .incbin */
dsp_coeff_table_end:
