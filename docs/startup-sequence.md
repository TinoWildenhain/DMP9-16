# DMP9/DMP16 Boot Startup Sequence

Analysed from video frames of power-on sequence (25 frames, ~2 seconds total).

## Sequence Timeline

### Phase 1 — CPU Init (no LEDs)
CPU executes `reset_handler`, performs RAM/stack init, hardware detection.
LCD backlight powers on immediately (hardware, not firmware controlled).

### Phase 2 — LCD Version Display
Before LED controller is initialised, firmware writes version string to LCD:
- Line 1: `DMP9-16` (or `DMP9` for 8-channel variant — set by `hw_model_detect()`)
- Line 2: `YAMAHA Corp.`
- Line 3: `Copyright 1994`
Position: top-left (column 0, row 0). Later scrolled to centre during scene load.

### Phase 3 — Encoder Ring LED Sweep
Red LEDs around the 8 rotary encoder pots sweep left-to-right in groups of 16 bits.
Each group write goes to `LED_SR_DATA` (0x4D0000). Sweep covers ch1→ch16 (8 channel strips,
2 encoders per strip = 16 encoder rings total). Duration: ~200ms.

### Phase 4 — Channel ON/MUTE Button Sweep  
Orange channel ON/MUTE buttons sweep left-to-right ch1→ch16.
MASTER encoder ring also lights during this phase.

### Phase 5 — SEL Button Sweep + Right Panel
Green channel SEL (select) buttons sweep left-to-right ch1→ch16.
Simultaneously, right-panel buttons flash:
- SCENE MEMORY bank (EF1, EF2, SOLO) — pink/magenta
- SETUP MEMORY bank (UTILITY, DIO, MIDI) — green
- SEND1, SEND2 select buttons

### Phase 6 — 7-Segment Self-Test
The 2-digit 7-segment display (MEMORY section, scene number) performs a lamp test:
- `L` — single segment
- `8.E` — all segments + decimal point
- `1.8` — further pattern
- Counts through segment patterns then goes blank

### Phase 7 — Scene Recall
All LEDs extinguish. Firmware calls `scene_recall()` to load the last stored scene.
7-segment displays the scene number (e.g. `50.`).
LCD switches to scene parameter display (4×16 chars, scrolling parameter names).
Channel 1 selected and blinking (SEL button + encoder ring flash).

## Hardware Architecture

### LED Shift Register Chain
All front-panel LEDs are driven by a **16-bit serial shift register chain**
clocked via writes to `LED_SR_DATA` (0x4D0000). The function `led_sr_write(uint data)`
shifts one 16-bit word into the chain per call.

Chain order (approximate, pending disassembly confirmation):
```
[ch1 enc ring] [ch2 enc ring] ... [ch16 enc ring]  ← 8+ words
[ch1–16 ON buttons]                                 ← 1 word (16 channels)
[ch1–16 SEL buttons]                               ← 1 word (16 channels)
[right panel buttons]                               ← 1–2 words
```

### LCD
- HD44780 controller, 4×16 characters
- CMD port: 0x490000 (RS=0)
- DATA port: 0x490001 (RS=1)  
- CTRL port: 0x4A0000 (E+RS+RW combined latch, bit 8 = Enable strobe)

### 7-Segment Display
- 2 digits, MEMORY section (top-left panel area, scene number)
- Port address TBD from disassembly
- Driven separately from shift register chain

### Model Detection
`hw_model_detect()` at 0x29A6 reads hardware strap → 0=DMP9 (8ch), nonzero=DMP16 (16ch).
Result selects parameter table: `model_params_dmp9` (ROM 0x5052E) or `model_params_dmp16`
(ROM 0x505B6), copied to `model_params_active` (DRAM 0x407500).

## Functions to Find (next analysis targets)
- `led_init()` — initialises shift register, called early in `reset_handler`
- `led_update()` — full chain refresh from DRAM LED state buffer
- `led_set_channel(ch, mask)` — sets individual channel LED bits
- `seg7_write(value)` — writes to 7-segment display
- `seg7_self_test()` — the lamp test sequence (L → 8.E → 1.8)
- `lcd_display_scene(scene_num)` — switches LCD to scene parameter view
- `lcd_scroll_version()` — scrolls version string to centre
