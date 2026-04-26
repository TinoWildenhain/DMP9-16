/**
 * lcd.c — HD44780 LCD driver
 * XN349E0 (v1.02, 1993-11-10)
 *
 * Hardware:
 *   LCD_CMD  = 0x490000  (instruction register)
 *   LCD_DATA = 0x490001  (data register)
 *
 * Address decoding is done by external logic circuits (not TMP68301 AMARO).
 */

#include <stdint.h>
#include "../hw/lcd.h"

/* Memory-mapped LCD registers */
#define LCD_CMD_REG   (*(volatile uint8_t *)0x00490000UL)
#define LCD_DATA_REG  (*(volatile uint8_t *)0x00490001UL)

void lcd_cmd(uint8_t cmd)
{
    LCD_CMD_REG = cmd;
    /* TODO: busy-wait or fixed delay matching original timing */
}

void lcd_putchar(uint8_t c)
{
    LCD_DATA_REG = c;
    /* TODO: busy-wait */
}

void lcd_puts(const char *s)
{
    while (*s)
        lcd_putchar((uint8_t)*s++);
}

void lcd_init(void)
{
    /*
     * HD44780 initialisation sequence.
     * TODO: Transcribe exact timing and command sequence from Ghidra
     *       analysis of init_code region (0x000800–0x004FFF).
     */
    lcd_cmd(LCD_CMD_FUNCTION_SET | LCD_FUNC_8BIT | LCD_FUNC_2LINE | LCD_FUNC_5X8);
    lcd_cmd(LCD_CMD_DISPLAY_CTRL | LCD_DISPLAY_ON);
    lcd_cmd(LCD_CMD_CLEAR);
    lcd_cmd(LCD_CMD_ENTRY_MODE | LCD_ENTRY_INCREMENT);
}
