# Emulator harness

A host-side behavioral stub emulator for the Yamaha DMP9/16 firmware.

This is **not** a cycle-accurate emulation. The goal is to run recovered
firmware control logic on a host machine and observe I/O behavior:

- LCD writes logged as readable text
- MIDI TX/RX bytes logged
- LED and panel register writes decoded and logged
- Knob and button events injectable from scripts

## Dependencies

- [Unicorn Engine](https://www.unicorn-engine.org/) for 68000 CPU emulation
- Python 3.8+ for the harness and scripting layer

## Usage

```bash
pip install unicorn
python3 dmp9_emu.py --rom path/to/v1.11.bin
```

## Memory map emulated

| Range | Role |
|---|---|
| `0x00000000` | ROM (512KB) |
| `0x00400000` | SRAM (64KB) |
| `0x00460000` | EF1 DSP stub |
| `0x00470000` | EF2 DSP stub |
| `0x00490000` | LCD stub |
| `0x00FFFC00` | TMP68301 SFR stub |

## Injecting input

See `scripts/inject_example.py` for examples of injecting button presses,
knob turns, and MIDI bytes into the running emulator.
