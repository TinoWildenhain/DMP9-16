# DMP9_Board_Setup.java

**Purpose:** Labels board-level hardware registers and ring-buffer symbols.

## What it does

### Hardware register labels
Labels all board-mapped I/O registers defined in `dmp9_board_regs.h`:
- **LCD:** `LCD_CMD` at `0x490000`, `LCD_DATA` at `0x490001` (HD44780, 4×16 chars)
- **DSP:** `DSP_EF1_BASE` at `0x460000`, `DSP_EF2_BASE` at `0x470000`
- **RAM:** `SHARED_DRAM` block at `0x400000–0x40FFFF` (64KB DRAM)

### SIO ring buffer labels
Labels the MIDI TX/RX ring buffer variables in DRAM at version-specific addresses.
These are used by the serial ISRs and the MIDI processing loop.

### Memory block annotations
Creates descriptive memory blocks for DSP_EF1, DSP_EF2, and LCD_HD44780 regions
so they appear by name in the Program Trees pane.
