/**
 * dmp9_board_regs.h — Board-level peripheral address map for Yamaha DMP9/16
 *
 * Address decoding is performed by two PLDs on the DIG board:
 *   PLD-TM1 (XA353A00) — chip selects for RAM, ROM, encoder, LED
 *   PLD-TM2 (XA354A00) — chip selects for MEG, LCD, key matrix, AUX
 *
 * Both PLDs take: CS0, CS1, R/W, LDS, UDS, AS, A16, A17, A18, A19
 * and generate the individual chip enables below.
 *
 * Confirmed chip select outputs from PLD service manual (pages 35-36):
 *
 * PLD-TM1 outputs:
 *   /LC1     LED matrix line data chip enable 1
 *   /LC0     LED matrix line data chip enable 0
 *   /REC     Rotary encoder counter chip enable
 *   /RAMOEL  Lower RAM output enable
 *   /RAMWEL  Lower RAM write enable
 *   /RAMOEU  Upper RAM output enable
 *   /RAMWEU  Upper RAM write enable
 *   /ROMCE   ROM chip enable
 *
 * PLD-TM2 outputs:
 *   /AUXWR   AUX write chip enable
 *   /ROMAUX  AUX ROM chip enable
 *   /OP      External output port chip enable
 *   /MEGWF   MEG write enable
 *   /WMEGRD  MEG read enable
 *   /LCD     LCD chip enable  ← 0x490000 confirmed by ROM analysis
 *   /RC      LED matrix row data chip enable
 *   /KOC     Key matrix line data chip enable
 */

#ifndef DMP9_BOARD_REGS_H
#define DMP9_BOARD_REGS_H

#include <stdint.h>

/* ==========================================================================
 * ROM (IC34 — XN349A00 — EPROM 4M)
 * 512 KB mapped at 0x000000 via /ROMCE from PLD-TM1
 * ========================================================================== */
#define ROM_BASE            0x000000UL
#define ROM_SIZE            (512UL * 1024)
#define ROM_END             (ROM_BASE + ROM_SIZE)

/* ==========================================================================
 * DRAM (IC35,36 — MSM511664-80 — 1M DRAM)
 * 64 KB at 0x400000 — used as CPU stack + DSP delay line buffer
 * ========================================================================== */
#define DRAM_BASE           0x400000UL
#define DRAM_SIZE           (64UL * 1024)
#define DRAM_END            (DRAM_BASE + DRAM_SIZE)

/** CPU stack top — confirmed from ROM vec[0] (SSP initial value) */
#define STACK_TOP           0x0040FED0UL

/* ==========================================================================
 * DSP — EF1 Effects Processor Bank
 * ICs: YM3804 + YM6007(DSP2) + YM3807 + YM6104(DEQ2)
 * Enabled by MEG chip selects from PLD-TM2 (/MEGWF, /WMEGRD)
 * ========================================================================== */
#define DSP_EF1_BASE        0x460000UL

/** YM6007 (XF164A00) — DSP2 — digital signal processor for EF1 */
#define DSP_EF1_YM6007      ((volatile uint8_t *)0x460000UL)

/** YM6104 (XE788A00) — DEQ2 — digital equalizer for EF1 */
#define DSP_EF1_YM6104      ((volatile uint8_t *)0x460100UL)  /* offset TBD */

/* ==========================================================================
 * DSP — EF2 Effects Processor Bank
 * ICs: YM3804 + YM3807
 * ========================================================================== */
#define DSP_EF2_BASE        0x470000UL

/* ==========================================================================
 * MEG — Multiple Effect Generator
 * IC23 — HD62098 (XM309A00)
 * Connected to CPU data bus A0-A4, D0-D7 via PLD-TM2
 * ========================================================================== */
#define MEG_BASE            0x480000UL   /* tentative — verify from ROM */
#define MEG_REG(offset)     (*(volatile uint8_t *)(MEG_BASE + (offset)))

/* ==========================================================================
 * LCD — HD44780 Character Display
 * Chip enable: PLD-TM2 /LCD output
 * Confirmed: ROM initialises at 0x490000 (CMD) / 0x490001 (DATA)
 * ========================================================================== */
#define LCD_CMD_ADDR        0x490000UL
#define LCD_DATA_ADDR       0x490001UL

#define LCD_CMD             (*(volatile uint8_t *)LCD_CMD_ADDR)
#define LCD_DATA            (*(volatile uint8_t *)LCD_DATA_ADDR)

/* LCD combined control port — bit 8 = E (Enable strobe), bits 9-0 = data+RS+RW.
 * Exact bit layout TBD from hardware tracing. Accessed as word. */
#define LCD_CTRL    0x004A0000

/* ==========================================================================
 * Key matrix — front panel buttons
 * Chip enable: PLD-TM2 /KOC output
 * Exact address TBD from Ghidra analysis
 * ========================================================================== */
#define KEY_MATRIX_BASE     0x4A0000UL   /* tentative */

/* ==========================================================================
 * LED matrix — front panel indicators
 * Chip enables: PLD-TM1 /LC0, /LC1, PLD-TM2 /RC
 * ========================================================================== */
#define LED_MATRIX_BASE     0x4B0000UL   /* tentative */

/* ==========================================================================
 * Rotary encoder counter
 * Chip enable: PLD-TM1 /REC output
 * ========================================================================== */
#define ENCODER_BASE        0x4C0000UL   /* tentative */

/* ==========================================================================
 * TMP68301 internal peripherals
 * Base: 0xFFFC00 (hardwired in TMP68301)
 * See: ghidra/include/tmp68301_regs.h for full register map
 * ========================================================================== */
#define TMP68301_BASE       0xFFFC00UL

/* ==========================================================================
 * SRAM (IC66-68, 93, 94 — M5M5256BFP-70LL — 256K SRAM)
 * Used by DSP section — exact address TBD
 * ========================================================================== */
#define SRAM_BASE           0x500000UL   /* tentative */

/* ==========================================================================
 * AUX ROM (PLD-TM2 /ROMAUX)
 * Purpose unclear — possibly for microprogram control of DSP
 * ========================================================================== */
#define AUX_ROM_BASE        0x510000UL   /* tentative */

/* ==========================================================================
 * DSP coefficient / parameter tables (ROM region, read-only)
 * Identical across all three firmware versions (XN349E0/F0/G0)
 * ========================================================================== */
#define DSP_COEFF_BASE      0x059B00UL
#define DSP_COEFF_END       0x062840UL
#define DSP_COEFF_SIZE      (DSP_COEFF_END - DSP_COEFF_BASE)

/* ==========================================================================
 * Version string (ROM region)
 * ========================================================================== */
#define VERSION_STRING_ADDR 0x05059AUL

#endif /* DMP9_BOARD_REGS_H */
