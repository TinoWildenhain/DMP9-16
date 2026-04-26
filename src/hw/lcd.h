/**
 * lcd.h — HD44780 LCD controller definitions for Yamaha DMP9/16
 *
 * Hardware addresses (confirmed from ROM analysis):
 *   LCD_CMD   = 0x490000  — instruction register
 *   LCD_DATA  = 0x490001  — data register
 *
 * Address decoding is done by external logic circuits (not TMP68301 AMARO).
 * The TMP68301 drives the address bus; chip-select for 0x490000/1 comes
 * from a PAL/GAL outside the TMP.
 */

#ifndef DMP9_LCD_H
#define DMP9_LCD_H

#include <stdint.h>

/* -------------------------------------------------------------------------
 * Memory-mapped register addresses
 * ------------------------------------------------------------------------- */
#define LCD_CMD_ADDR   0x00490000UL
#define LCD_DATA_ADDR  0x00490001UL

#define LCD_CMD_REG    (*(volatile uint8_t *)LCD_CMD_ADDR)
#define LCD_DATA_REG   (*(volatile uint8_t *)LCD_DATA_ADDR)

/* -------------------------------------------------------------------------
 * HD44780 Command bytes (instruction register)
 * ------------------------------------------------------------------------- */

/** Clear display — writes spaces to all DDRAM, resets AC to 0 */
#define LCD_CMD_CLEAR              0x01

/** Return home — resets AC to 0, returns display to original position */
#define LCD_CMD_HOME               0x02

/** Entry mode set */
#define LCD_CMD_ENTRY_MODE         0x04
#define LCD_ENTRY_INCREMENT        0x02  /**< Cursor moves right after write */
#define LCD_ENTRY_DECREMENT        0x00  /**< Cursor moves left after write  */
#define LCD_ENTRY_SHIFT            0x01  /**< Shift display instead of cursor */

/** Display on/off control */
#define LCD_CMD_DISPLAY_CTRL       0x08
#define LCD_DISPLAY_ON             0x04
#define LCD_DISPLAY_OFF            0x00
#define LCD_CURSOR_ON              0x02
#define LCD_CURSOR_OFF             0x00
#define LCD_BLINK_ON               0x01
#define LCD_BLINK_OFF              0x00

/** Cursor/display shift */
#define LCD_CMD_SHIFT              0x10
#define LCD_SHIFT_DISPLAY          0x08  /**< Shift display             */
#define LCD_SHIFT_CURSOR           0x00  /**< Move cursor               */
#define LCD_SHIFT_RIGHT            0x04  /**< Direction: right          */
#define LCD_SHIFT_LEFT             0x00  /**< Direction: left           */

/** Function set */
#define LCD_CMD_FUNCTION_SET       0x20
#define LCD_FUNC_8BIT              0x10  /**< 8-bit interface           */
#define LCD_FUNC_4BIT              0x00  /**< 4-bit interface           */
#define LCD_FUNC_2LINE             0x08  /**< 2-line display            */
#define LCD_FUNC_1LINE             0x00  /**< 1-line display            */
#define LCD_FUNC_5X10              0x04  /**< 5×10 dot font             */
#define LCD_FUNC_5X8               0x00  /**< 5×8 dot font              */

/** Set CGRAM address (bits[5:0] = address) */
#define LCD_CMD_SET_CGRAM(addr)    (0x40 | ((addr) & 0x3F))

/** Set DDRAM address (bits[6:0] = address) */
#define LCD_CMD_SET_DDRAM(addr)    (0x80 | ((addr) & 0x7F))

/* -------------------------------------------------------------------------
 * DDRAM address map (2-line display)
 * Line 0: 0x00–0x27, Line 1: 0x40–0x67
 * ------------------------------------------------------------------------- */
#define LCD_LINE0_ADDR  0x00
#define LCD_LINE1_ADDR  0x40
#define LCD_COLS        16   /**< DMP9/16 displays 2×16 characters */

/* -------------------------------------------------------------------------
 * Driver API
 * ------------------------------------------------------------------------- */
void lcd_init(void);
void lcd_cmd(uint8_t cmd);
void lcd_putchar(uint8_t c);
void lcd_puts(const char *s);
void lcd_goto(uint8_t line, uint8_t col);

#endif /* DMP9_LCD_H */
