#ifndef DMP9_ROM_ADDRS_XN349G0_H
#define DMP9_ROM_ADDRS_XN349G0_H
/*
 * XN349G0 — Yamaha DMP9/DMP16 ROM v1.11 (1994-03-10)
 * Confirmed function and data addresses for Ghidra analysis scripts.
 * Do NOT hardcode these in scripts — include this file or use the
 * DMP9_MidiAnalysis.java version-switch block.
 */

/* ── Reset / Init ─────────────────────────────────────────────── */
#define ADDR_reset_handler          0x0003BF4E
#define ADDR_hw_model_init          0x00000F3C
#define ADDR_hw_model_detect        0x000029A6

/* ── LCD ──────────────────────────────────────────────────────── */
#define ADDR_lcd_write_cmd          0x0000238C
#define ADDR_lcd_write_data         0x000023DC
#define ADDR_lcd_delay_enable       0x000029BC
#define ADDR_lcd_strobe             0x000029C8
#define ADDR_lcd_send_init_cmds     0x000029F2
#define ADDR_lcd_init_sequence      0x00002A00

/* ── Delay / I/O helpers ──────────────────────────────────────── */
#define ADDR_delay_nested           0x0000274A
#define ADDR_delay_simple           0x00002768
#define ADDR_led_sr_write           0x00002770

/* ── MIDI ─────────────────────────────────────────────────────── */
#define ADDR_midi_tx_byte           0x000122E0
#define ADDR_midi_process_rx        0x000128EC

/* ── Scene ────────────────────────────────────────────────────── */
#define ADDR_scene_recall           0x00013454
#define ADDR_scene_store            0x00013186

/* ── Serial ISRs ──────────────────────────────────────────────── */
#define ADDR_timer_housekeeping_isr 0x000006F0
#define ADDR_serial0_status_isr     0x000007AE
#define ADDR_serial0_rx_isr         0x000007EC
#define ADDR_serial0_tx_isr         0x00000836
#define ADDR_serial0_special_isr    0x0000088A
#define ADDR_serial1_status_isr     0x000008B4
#define ADDR_serial1_rx_isr         0x000008F2
#define ADDR_serial1_tx_isr         0x0000093C
#define ADDR_serial1_special_isr    0x00000990

/* ── MCC68K Runtime Library ───────────────────────────────────── */
#define ADDR_memcpy_b               0x000023AE
#define ADDR_memcpy_w               0x000023CA
#define ADDR_memcpy_l               0x000023E4
#define ADDR_meminv_b               0x000023FE
#define ADDR_memcpy_bitser          0x0000241E
#define ADDR_bitrev_byte            0x00002464
#define ADDR_memcpy_bitser_w        0x00002488
#define ADDR_memset_b               0x000024DA
#define ADDR_memset_w               0x000024F2
#define ADDR_memset_l               0x0000250A

/* ── Housekeeping ─────────────────────────────────────────────── */
#define ADDR_housekeeping_tick      0x0000182A

/* ── Data addresses ───────────────────────────────────────────── */
#define DATA_model_params_dmp9      0x0005052E
#define DATA_model_params_dmp16     0x000505B6
#define DATA_dsp_display_s          0x000521F4   /* EF display string table */

#endif /* DMP9_ROM_ADDRS_XN349G0_H */
