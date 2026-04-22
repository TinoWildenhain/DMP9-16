# Yamaha DMP9 / DMP9-16 Firmware RE

Reverse engineering project for the Yamaha DMP9 and DMP9-16 digital mixing
processors (1991-1994).

## Hardware

| Component | Part |
|---|---|
| CPU | Toshiba TMP68301 (68000 core, 16MHz) |
| ROM | Texas Instruments TMS27C240 (512KB OTP EPROM) |
| SRAM | 64KB external SRAM |
| Effects EF1 | Yamaha YM3804 + YM6007 + YM3807 + YM6104 |
| Effects EF2 | Yamaha YM3804 + YM3807 |
| MIDI | TMP68301 integrated UART, 31250 baud |
| LCD | HD44780-compatible character LCD |

## ROM versions

| Label | Version | Date | Status |
|---|---|---|---|
| XN349G0 | v1.11 | 1994-03-10 | **Canonical** — primary analysis target |
| XN349F0 | v1.10 | 1994-01-20 | Secondary |
| XN349E0 | v1.02 | 1993-11-10 | Secondary |

ROM images are **not distributed** in this repository.
See `roms/README.md` for acquisition guidance.

## Repository structure

```
DMP9-16/
  docs/           Hardware reference, workflow, Ghidra guide, DSP notes
  src/hw/         Hardware abstraction headers (TMP68301, DSP, LCD, MIDI)
  src/firmware/   Recovered firmware C sources (work in progress)
  ghidra/         Ghidra project setup script (DMP9_Setup.java)
  analysis/       Version diffs, string tables, memory maps
  emulator/       Unicorn-based behavioral stub emulator
  build/          Makefile, linker script, toolchain setup
  roms/           ROM acquisition guidance and hash verification
```

## Quick start

### 1. Toolchain

```bash
sudo apt install gcc-m68k-linux-gnu binutils-m68k-linux-gnu radare2
pip3 install unicorn
```

See `build/toolchain-setup.md` for macOS and Ghidra setup.

### 2. Ghidra analysis

1. Import ROM as `68000:BE:32:default`, base `0x00000000`
2. Run `ghidra/scripts/DMP9_Setup.java`
3. Auto-analyze with **Decompiler Parameter ID** enabled
4. Start analysis at `reset_handler` (`0x0003B3F0` in v1.11)

See `docs/ghidra-guide.md` for full walkthrough.

### 3. Emulator

```bash
cd emulator/
pip3 install -r requirements.txt
python3 dmp9_emu.py --rom path/to/v1.11.bin --insn 500000
```

### 4. Build

```bash
cd build/
make
make verify
```

## References

- Toshiba TMP68301 datasheet
- Texas Instruments TMS27C240 datasheet
- Yamaha SPX-900 service manual (YM3804 write sequences)
- [TritonCore YM3413](https://github.com/michelgerritse/TritonCore) — YM3804-family sibling chip implementation
- Motorola MC68000 User's Manual

## Status

Early stage. Hardware headers and project scaffolding complete.
Ghidra setup script functional. Emulator harness skeleton ready.
Detailed function recovery ongoing.

## License

Original code in this repository: MIT License.  
ROM firmware: copyright Yamaha Corporation. Not distributed here.
