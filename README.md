# Yamaha DMP9 / DMP9-16 Firmware RE

Reverse engineering project for the Yamaha DMP9 and DMP9-16 digital mixing
processors (1991–1994). Goal: produce a compilable, functionally equivalent
source tree for each of three known ROM versions, suitable for binary diffing
and release documentation.

## Hardware summary

| Component | Part | Notes |
|-----------|------|-------|
| CPU | Toshiba TMP68301AF-16 | 68000 core, 16 MHz; IC24 on DIG board |
| ROM | TMS27C240-10 (XN349A00) | 512 KB OTP EPROM; IC34 |
| DRAM | MSM511664-80 (IC35/36) | 64 KB at 0x400000; shared CPU stack + DSP delay |
| SRAM | M5M5256BFP-70LL (IC66–68, 93, 94) | 256K DSP section |
| Effects EF1 | YM3804 + YM6007 + YM3807 + YM6104 | IC19/29/46/70/71 + IC26–28/37–39 |
| Effects EF2 | YM3804 + YM3807 | |
| MIDI | TMP68301 integrated SC0 UART | 31250 baud, 8N1 |
| LCD | HD44780-compatible character LCD | 0x490000 CMD / 0x490001 DATA |
| Address decoding | PLD-TM1 (XA353A00) + PLD-TM2 (XA354A00) | External — not TMP68301 AMARO/AAMRO |

## Memory map

| Address | Name | Notes |
|---------|------|-------|
| `0x000000` | ROM / vector table | 96 vectors × 4 bytes = 0x180 bytes |
| `0x000400` | ISR stubs | MOVEM+JSR+STOP+RTE panic pattern |
| `0x000800` | Init / startup code | Reset handler chain |
| `0x005000` | Main firmware | Bulk of application code |
| `0x050000` | String / parameter tables | UI strings, effect names, MIDI params |
| `0x05059A` | Version string | `"Version 1.11  Date Mar.10.1994"` (v1.11) |
| `0x059B00` | DSP coefficient tables | Identical across all 3 ROMs |
| `0x07FF00` | Fault string | `"Fault from interrupt"` |
| `0x400000` | DRAM (64 KB) | SSP init `0x0040FED0`; stack grows down, DSP delay up |
| `0x460000` | DSP_EF1_BASE | YM3804+YM6007+YM3807+YM6104 |
| `0x470000` | DSP_EF2_BASE | YM3804+YM3807 |
| `0x490000` | LCD_CMD | HD44780 instruction register |
| `0x490001` | LCD_DATA | HD44780 data register |
| `0xFFFC00` | TMP68301 SFR | Internal peripheral base |

## ROM versions

| Label | Version | Date | Notes |
|-------|---------|------|-------|
| XN349G0 | v1.11 | 1994-03-10 | **Canonical** — primary analysis target |
| XN349F0 | v1.10 | 1994-01-20 | Patch predecessor |
| XN349E0 | v1.02 | 1993-11-10 | Earliest known |

ROM images are **not distributed** in this repository.
See `roms/README.md` for acquisition guidance.

### Diff summary

| Pair | Changed bytes | Changed regions | Nature |
|------|--------------|----------------|--------|
| v1.02 → v1.10 | 276,900 (52.8%) | 45 | Full recompile — all function addresses changed |
| v1.10 → v1.11 | 171,097 (32.6%) | 96 | Patch release — targeted bug fixes |
| v1.02 → v1.11 | 277,310 (52.9%) | cumulative | Cumulative of both |

DSP coefficient tables `0x059B00–0x06283F` are **byte-identical** in all three ROMs.
Extracted once as `src/shared/dsp_tables.S`.

## Calling conventions

Three conventions are present (defined in `ghidra/lang/tmp68301.cspec`):

| Name | Detection | Used for |
|------|-----------|---------|
| `__stdcall` | `LINK A6,#N` or `MOVEM.L ...,-(SP)` in first 6 insns | C-compiled functions |
| `yamaha_reg` | No LINK, no stack frame; args in D0/D1/D2/D3/A0/A1 | Register-call routines |
| `__interrupt` | Hardware dispatch, no args, RTE return | ISR handlers |

## Confirmed functions (v1.11)

| Address | Name | Signature |
|---------|------|-----------|
| `0x0000A5A4` | `report_bus_error` | `void() __stdcall` |
| `0x0000A5B8` | `report_address_error` | `void() __stdcall` |
| `0x0000A6A8` | `print_rjust16` | `void(char *s) __stdcall` |
| `0x00002AA6` | `uart_write` | `void(char *str, int len) __stdcall` |
| `0x0000238C` | `lcd_write_cmd` | HD44780 command |
| `0x000023DC` | `lcd_write_data` | HD44780 data byte |
| `0x0000274E` | `dsp_ef1_write` | Write EF1 register |
| `0x00002904` | `dsp_ef2_write` | Write EF2 register |
| `0x000122E0` | `midi_tx_byte` | Transmit one MIDI byte via SC0 |
| `0x000128EC` | `midi_process_rx` | MIDI message parsing core |
| `0x00013454` | `scene_recall` | Recall scene from memory |
| `0x00013186` | `scene_store` | Store scene to memory |

## Repository structure

```
DMP9-16/
  docs/             Hardware reference, MIDI catalog, workflow, Ghidra guide
  src/
    shared/         dsp_tables.S — coefficient tables (identical across versions)
    XN349E0/        v1.02 source skeleton
    XN349F0/        v1.10 source skeleton
    XN349G0/        v1.11 source skeleton
  src/hw/           Hardware abstraction headers (TMP68301, DSP, LCD, MIDI)
  ghidra/
    scripts/        Ghidra Java analysis scripts
    lang/           Custom tmp68301.cspec + 68000.ldefs
    HEADLESS.md     Headless analysis guide
  analysis/
    diffs/          Version diff reports
    strings/        Extracted string tables per ROM
    maps/           Memory map and function address tables
  build/            Makefile, linker script, toolchain setup
  roms/             ROM acquisition guidance and hash verification
```

## Quick start

### 1. Toolchain

```bash
sudo apt install gcc-m68k-linux-gnu binutils-m68k-linux-gnu
```

See `build/toolchain-setup.md` for macOS.

### 2. Install custom Ghidra language files

```bash
cp ghidra/lang/tmp68301.cspec  $GHIDRAHOME/Ghidra/Processors/68000/data/languages/
cp ghidra/lang/68000.ldefs     $GHIDRAHOME/Ghidra/Processors/68000/data/languages/
```

See `ghidra/lang/INSTALL.md` for details.

### 3. Ghidra headless analysis (all 3 ROMs)

```bash
export GHIDRAHOME=/path/to/ghidra_12.0.4_PUBLIC
bash ghidra/scripts/run_analysis.sh /path/to/roms/ ~/ghidra_projects/
```

See `ghidra/HEADLESS.md` for full walkthrough.

### 4. Manual Ghidra analysis

1. Import ROM as `68000:BE:32:TMP68301` (or `default`), base `0x00000000`
2. Run `TMP68301_Setup.java` — labels all TMP68301 internal registers
3. Run `DMP9_Board_Setup.java` — labels LCD, DSP, RAM regions
4. Run `DMP9_FuncFixup.java` — fixes calling conventions (3-pass)
5. Run `DMP9_LibMatch.java` — matches library byte patterns
6. Enable auto-analysis with **Decompiler Parameter ID**
7. Start review at `reset_handler` (`0x0003BF4E` in v1.11)

See `docs/ghidra-guide.md` for full walkthrough.

### 5. Build

```bash
make VERSION=XN349G0          # v1.11
make all                      # all three versions
make verify VERSION=XN349G0   # size/checksum verification
```

## Current analysis status

| Area | Status |
|------|--------|
| Hardware memory map | Complete (confirmed from service manual) |
| PLD address decode | Complete (PLD-TM1/TM2 chip-select map confirmed) |
| Vector table (96 entries) | Labeled via DMP9_Board_Setup.java |
| TMP68301 register map | Complete (tmp68301_regs.h, 847 lines) |
| Calling conventions | 3 detected; cspec installed |
| ISR stubs | Identified (MOVEM+JSR+STOP+RTE panic pattern) |
| LCD driver | `lcd_write_cmd` / `lcd_write_data` confirmed |
| MIDI handler | `midi_tx_byte` / `midi_process_rx` confirmed |
| Scene recall/store | `scene_recall` / `scene_store` confirmed |
| DSP EF1/EF2 writes | `dsp_ef1_write` / `dsp_ef2_write` confirmed |
| DSP coefficient tables | Extracted; identical across all ROMs |
| String table | Located `0x050000–0x059A54`; MIDI/LCD strings catalogued |
| Source skeletons | Per-version `src/XN349E0/F0/G0/` scaffolding in place |
| Build system | Makefile + linker script; compiles skeletal sources |
| Version tracking | Not yet run (requires Ghidra GUI) |
| Function recovery | In progress on v1.11; most functions not yet decompiled |

## References

- Toshiba TMP68301AF datasheet
- Texas Instruments TMS27C240 datasheet
- DMP9 Owner's Guide (DMP9E_2_ocr.pdf)
- DMP9/DMP9-16 Owner's Manual (yamaha_dmp9-8-dmp9-16_mixer_user_manual.pdf)
- DMP9 Service Manual (schematic, board diagram, PLD chip-select map)
- Motorola MC68000 User's Manual
- [TritonCore YM3413](https://github.com/michelgerritse/TritonCore) — YM3804-family implementation

## License

Original code in this repository: MIT License.
ROM firmware: copyright Yamaha Corporation. Not distributed here.
