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

### Phase 3–6 — LED Chain Sliding Window Animation (~4–5 seconds)

A 16-bit wide window of set bits slides continuously through the entire shift
register chain from the leftmost encoder ring to the rightmost panel element.
At any instant exactly 16 consecutive LEDs/segments appear lit simultaneously
(no flicker — the chain is continuously refreshed by `timer_housekeeping_isr`).

The window advances one bit position per ISR tick, snaking left-to-right:
  encoder rings ch1→16 → ON/MUTE buttons ch1→16 → SEL buttons ch1→16
  → right panel buttons → 7-segment segments

**7-segment appearance during animation:** The `L`, `8.E`, `1.8` patterns visible
in the animation are NOT a deliberate segment test. They are the 16-bit window
passing through the 8 segment bits in the chain:
- Window entering: only segment `d` (bottom bar) covered → shows `L`
- Window fully overlapping all 8 segment bits → shows `8.` (all segments on)
- Window exiting: trailing partial overlap → shows partial patterns like `1.8`

**ISR-driven refresh:** `timer_housekeeping_isr` (0x000006F0) clocks the DRAM LED
state buffer into `LED_SR_DATA` (0x4D0000) at high frequency (~hundreds of Hz).
The animation code simply advances the window position in the DRAM buffer each tick.

### Phase 7 — DSP Initialisation Pause (~5–6 seconds)

After the LED sweep completes, all LEDs extinguish and the display holds blank
for 5–6 seconds. This is **DSP program load time**:
- EF1 DSP (0x460000) and EF2 DSP (0x470000) load their effect programs
- DSPs stabilise before audio processing can begin
- Implemented as a timed wait (counter loop or ISR-counted delay) in firmware

### Phase 8 — Scene Recall and Normal Operation
All LEDs extinguish. Firmware calls `scene_recall()` to load the last stored scene.
7-segment displays the scene number (e.g. `50.`).
LCD switches to scene parameter display (4×16 chars, scrolling parameter names).
Channel 1 selected and blinking (SEL button + encoder ring flash).

## Hardware Architecture

### LED + 7-Segment Shift Register Chain

Single serial shift register chain driving ALL front-panel indicators.
Port: `LED_SR_DATA` = 0x4D0000, function `led_sr_write(uint data)`.

**Refresh architecture:**
- `timer_housekeeping_isr` (0x6F0) clocks the DRAM LED state buffer into the
  chain at high frequency (no flicker at normal viewing distance)
- DRAM LED buffer: address TBD (find via xrefs to `led_sr_write` in ISR)
- Normal operation: buffer reflects actual UI state (selected channel, active
  scenes, parameter values, etc.)
- Animation: firmware writes a sliding 16-bit all-ones window into the buffer

**Chain structure (estimated from animation):**
```
Position:  0        ~32      ~48       ~64         ~80  ~88
           |         |        |         |            |    |
Chain:  [enc rings][ON btns][SEL btns][right panel][7-seg][?]
        ch1→ch16   ch1→16   ch1→16    ~16 bits     8 bits
```
Total chain length: ~90–100 bits = 6–7 × 16-bit words per full refresh.

**Key analysis target:** Find `led_update()` (the ISR's chain-refresh subroutine)
via xrefs to `led_sr_write`. The loop body and iteration count will confirm the
exact chain length and DRAM buffer address.

### LCD
- HD44780 controller, 4×16 characters
- CMD port: 0x490000 (RS=0)
- DATA port: 0x490001 (RS=1)  
- CTRL port: 0x4A0000 (E+RS+RW combined latch, bit 8 = Enable strobe)

### Model Detection
`hw_model_detect()` at 0x29A6 reads hardware strap → 0=DMP9 (8ch), nonzero=DMP16 (16ch).
Result selects parameter table: `model_params_dmp9` (ROM 0x5052E) or `model_params_dmp16`
(ROM 0x505B6), copied to `model_params_active` (DRAM 0x407500).

## Key Analysis Target: `led_update()`

The most important function to find next. It:
1. Reads the DRAM LED state buffer (address TBD — likely near 0x407xxx)
2. Packs LED states into 16-bit shift register words
3. Calls `led_sr_write()` in a loop to refresh the full chain

**How to find it:** Check xrefs to `led_sr_write` (anchored at 0x2770). The caller
that uses a loop/counter and writes multiple consecutive words is `led_update()`.
Once found, the DRAM buffer address and the chain bit→LED mapping become known.

## Functions to Find (next analysis targets)
- `led_init()` — initialises shift register, called early in `reset_handler`
- `led_update()` — full chain refresh from DRAM LED state buffer
- `led_set_channel(ch, mask)` — sets individual channel LED bits
- `seg7_write(value)` — writes to 7-segment display
- `seg7_self_test()` — the lamp test sequence (L → 8.E → 1.8)
- `lcd_display_scene(scene_num)` — switches LCD to scene parameter view
- `lcd_scroll_version()` — scrolls version string to centre
