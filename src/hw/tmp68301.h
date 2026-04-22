#ifndef TMP68301_H
#define TMP68301_H

/*
 * tmp68301.h - Toshiba TMP68301 integrated peripheral register map
 *
 * Yamaha DMP9/16 firmware reverse engineering project
 * RE_NOTE: Addresses confirmed from ROM disassembly and TMP68301 datasheet.
 */

#include <stdint.h>

/* SFR base address */
#define TMP68301_SFR_BASE   0x00FFFC00UL

/* GPIO */
#define MFP_GPDR  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x01)) /* GPIO data */
#define MFP_DDR   (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x03)) /* GPIO direction */

/* Interrupt enable */
#define MFP_IERA  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x05)) /* IRQ enable A */
#define MFP_IERB  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x07)) /* IRQ enable B */
#define MFP_IPRA  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x09)) /* IRQ pending A */
#define MFP_IPRB  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x0B)) /* IRQ pending B */
#define MFP_ISRA  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x0D)) /* IRQ in-service A */
#define MFP_ISRB  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x0F)) /* IRQ in-service B */
#define MFP_IMRA  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x11)) /* IRQ mask A */
#define MFP_IMRB  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x13)) /* IRQ mask B */

/* Timers */
#define MFP_TADR  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x1F)) /* Timer A data */
#define MFP_TBDR  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x21)) /* Timer B data */
#define MFP_TCDR  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x23)) /* Timer C data */
#define MFP_TDDR  (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x25)) /* Timer D data */

/* UART (MIDI at 31250 baud) */
#define MFP_UCR   (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x29)) /* UART control */
#define MFP_RSR   (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x2B)) /* RX status */
#define MFP_TSR   (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x2D)) /* TX status */
#define MFP_UDR   (*(volatile uint8_t *)(TMP68301_SFR_BASE + 0x2F)) /* UART data (MIDI byte) */

/* UART status bits */
#define MFP_RSR_BUFFER_FULL  0x80
#define MFP_RSR_OVERRUN      0x40
#define MFP_RSR_FRAME_ERR    0x08
#define MFP_TSR_BUFFER_EMPTY 0x80

#endif /* TMP68301_H */
