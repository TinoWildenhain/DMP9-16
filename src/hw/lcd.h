#ifndef LCD_H
#define LCD_H

/*
 * lcd.h - HD44780-compatible LCD controller interface
 *
 * Command register: 0x00490000
 * Data register:    0x00490001
 */

#include <stdint.h>

#define LCD_CMD_BASE  0x00490000UL
#define LCD_DAT_BASE  0x00490001UL

#define LCD_CMD  (*(volatile uint8_t *)(LCD_CMD_BASE))
#define LCD_DAT  (*(volatile uint8_t *)(LCD_DAT_BASE))

/* HD44780 commands */
#define LCD_CMD_CLEAR        0x01
#define LCD_CMD_HOME         0x02
#define LCD_CMD_ENTRY_MODE   0x06  /* increment, no shift */
#define LCD_CMD_DISPLAY_ON   0x0C  /* display on, cursor off, blink off */
#define LCD_CMD_FUNCTION_SET 0x38  /* 8-bit, 2 lines, 5x8 font */
#define LCD_CMD_DDRAM_ADDR   0x80  /* OR with address */

/* Row DDRAM offsets */
#define LCD_ROW0  0x00
#define LCD_ROW1  0x40

void lcd_init(void);
void lcd_write_cmd(uint8_t cmd);
void lcd_write_data(uint8_t data);
void lcd_set_cursor(uint8_t row, uint8_t col);
void lcd_write_string(const char *s);
void lcd_clear(void);

#endif /* LCD_H */
