# Hardware Reference

## CPU: Toshiba TMP68301

The TMP68301 is a Toshiba ASSP integrating a Motorola 68000 CPU core with on-chip peripherals.

- CPU core: Motorola 68000, 16MHz
- Integrated UART (used for MIDI at 31250 baud)
- Integrated parallel I/O (panel scanning, LED drive)
- Integrated timer channels
- External bus interface for SRAM, DSP, LCD

Full register map: see `docs/tmp68301.md` and the Toshiba TMP68301 datasheet.

## Memory map

| Range | Size | Description |
|---|---|---|
| `0x00000000` | 512KB | ROM (TMS27C240) |
| `0x00400000` | 64KB | SRAM |
| `0x00460000` | 4KB | EF1 DSP registers |
| `0x00470000` | 4KB | EF2 DSP registers |
| `0x00490000` | 16B | LCD controller |
| `0x00FFFC00` | 64B | TMP68301 SFR (UART, GPIO, timers) |

## EPROM: TMS27C240-10

- Texas Instruments TMS27C240
- 4Mbit (512KB) OTP EPROM
- 10ns access time variant
- 32-pin DIP package
- 5V supply

## LCD

- Character LCD module, 2x40 or similar
- Hitachi HD44780-compatible controller
- Command register at `0x00490000`
- Data register at `0x00490001`

## MIDI interface

- Standard MIDI DIN-5 IN/OUT/THRU
- UART baud rate: 31250
- Handled via TMP68301 integrated UART
- MIDI RX triggers interrupt via MFP

## Panel

- Rotary encoders for level and parameter adjustment
- Pushbuttons for scene recall, function selection
- LED indicators for channel status, metering
- Scanned via TMP68301 GPIO

## Effects engine

See `docs/dsp-ym3804-family.md` for full details.

### EF1 block
- YM3804 — core reverb/delay engine
- YM6007 — enhanced delay with 28-bit precision, pitch shift
- YM3807 — LFO modulation source
- YM6104 (DEQ2) — programmable digital EQ

### EF2 block
- YM3804 — reverb/delay
- YM3807 — LFO modulation
