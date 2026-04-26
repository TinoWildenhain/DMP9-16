# DMP9/16 ROM Reconstruction Project Plan

## Overview

This document describes the methodology and goals for reverse-engineering and reconstructing
the firmware of the Yamaha DMP9/DMP16 rack digital mixer across three known ROM versions.

The objective is to produce, for each ROM version, a compilable C/assembly source tree that
links into a **functionally equivalent** binary — not necessarily bit-identical, but producing
the same runtime behaviour with hardware addresses (I/O registers, peripheral buffers) preserved
at their original locations.

---

## Hardware Platform

| Parameter | Value |
|-----------|-------|
| Device | Yamaha DMP9 / DMP16 |
| Board | DIG board (XN047A00), DIG16 variant confirmed from service manual |
| CPU | Toshiba TMP68301AF-16 (68000 core, 16 MHz) — IC24 |
| ROM | 512 KB (TMS27C240-10 / XN349A00), mapped at 0x000000 — IC34 |
| DRAM | 64 KB (MSM511664-80, IC35/36) at 0x400000 — dual-use: CPU stack + DSP delay |
| SRAM | M5M5256BFP-70LL (IC66–68, 93, 94) — 256K, DSP section |
| TMP68301 regs | 0xFFFC00 (internal peripheral base) |
| LCD | HD44780-compatible at 0x490000 (CMD) / 0x490001 (DATA) |
| DSP EF1 | YM3804+YM6007+YM3807+YM6104 at 0x460000 |
| DSP EF2 | YM3804+YM3807 at 0x470000 |
| Address decoding | **External logic PLDs** (PLD-TM1 / PLD-TM2) — not TMP68301 AMARO/AAMRO |

### PLD address decode (confirmed from service manual pp. 35–36)

| PLD | Part | Chip selects |
|-----|------|-------------|
| PLD-TM1 (IC33, XA353A00) | — | /ROMCE (ROM), /RAMOEL, /RAMOEU (DRAM), /LC0, /LC1 (LED), /REC (encoder) |
| PLD-TM2 (IC32, XA354A00) | — | /LCD (→0x490000), /MEGWF, /WMEGRD (DSP MEG), /KOC (key matrix), /AUXWR, /ROMAUX (AUX) |

Both PLDs decode A16–A19 plus AS/UDS/LDS/CS0/CS1/R_W from the TMP68301 bus.
The TMP68301's on-chip AMARO/AAMRO memory management registers are **not** used for
address decode on this hardware.

### Key ICs (DIG16 board)

| IC | Part | Function |
|----|------|----------|
| IC24 | TMP68301AF-16 | CPU |
| IC23 | HD62098 (XM309A00) | MEG — Multiple Effect Generator |
| IC19,29,46,70,71 | YM6007 (XF164A00) | DSP2 — main effects |
| IC26–28,37–39 | YM6104 (XE788A00) | DEQ2 — digital EQ |
| IC20–22 | YM3422B (XE862B00) | ES1 — format converter |
| IC30 | FMC9WSI (XL533A00) | PAL — AES/Yamaha format converter |
| IC31 | XN352A00 | PLD-VCO |
| IC32 | XN354A00 | PLD-TM2 |
| IC33 | XN353A00 | PLD-TM1 |
| IC34 | XN349A00 | EPROM 4M (ROM) |

---

## ROM Versions

| Label | Part | Date | File |
|-------|------|------|------|
| XN349E0 | v1.02 | 1993-11-10 | `TMS27C240-10-DMP9-16-XN349E0-v1.02-10.11.1993.bin` |
| XN349F0 | v1.10 | 1994-01-20 | `TMS27C240-10-DMP9-16-XN349F0-v1.10-20.01.1994.bin` |
| XN349G0 | v1.11 | 1994-03-10 | `TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin` |

All three are 512 KB (524,288 bytes). The last non-FF byte is at 0x07FFA3.

---

## ROM Memory Map

| Address Range | Region | Notes |
|---------------|--------|-------|
| `0x000000–0x00017F` | Exception vector table | 96 vectors × 4 bytes (0x180 bytes) |
| `0x000180–0x0003FF` | Vector table padding | Reserved |
| `0x000400–0x0007FF` | ISR stubs | MOVEM.L+JSR+STOP #0x2700+RTE panic pattern |
| `0x000800–0x004FFF` | Init / startup code | Reset handler chain |
| `0x005000–0x04FFFF` | Main firmware | Bulk of application code |
| `0x050000–0x059A54` | String / parameter tables | UI strings, effect names, MIDI params |
| `0x05059A–0x05062D` | Version string area | `"Version 1.11  Date Mar.10.1994"` in v1.11 |
| `0x059B00–0x06283F` | DSP coefficient tables | **Identical across all 3 ROMs** |
| `0x062840–0x07FEFF` | Padding | 0xFF filled |
| `0x07FF00–0x07FFFF` | Fault strings + end | `"Fault from interrupt"` etc. |

**Key finding:** The data/coefficient tables (0x059B00–0x06283F) are **byte-identical across
all three ROM versions**. They are extracted once from any ROM and shared as
`src/shared/dsp_tables.S` (`.incbin`).

### RAM layout (runtime)

| Address | Use |
|---------|-----|
| `0x400000–0x40FFFF` | 64 KB DRAM |
| `0x0040FED0` | SSP reset value (stack top, grows downward) |
| `0x40FED0–0x40FFFF` | Stack space (~0x130 bytes headroom) |
| `0x400000–0x40FEXX` | DSP delay buffer (grows up from base) |

---

## Reset Vector (v1.11 reference)

| Vector | Value | Notes |
|--------|-------|-------|
| SSP (vec[0]) | `0x0040FED0` | Stack top — just below DRAM ceiling |
| PC (vec[1]) | `0x0003BF4E` | Actual reset handler (via JMP thunk at 0x000400) |

---

## ROM Diff Summary

### v1.02 → v1.10 (XN349E0 → XN349F0)

| Metric | Value |
|--------|-------|
| Changed bytes | 276,900 (52.8%) |
| Changed regions | 45 |
| Nature | **Major rewrite / full recompile** — different toolchain version or significant refactor; all function addresses changed |

### v1.10 → v1.11 (XN349F0 → XN349G0)

| Metric | Value |
|--------|-------|
| Changed bytes | 171,097 (32.6%) |
| Changed regions | 96 |
| Nature | **Patch release** — targeted bug fixes, scattered JSR/BSR offset fixups |

Notable patch regions:
- `0x000006–0x000007` — Reset PC minor offset change
- `0x000FE3–0x001053` (113 B) — Timing/init sequence modification
- `0x006699–0x006AD1` — Display/LCD handler area
- `0x00DF81–0x00E311` (913 B) — Large fix, likely MIDI or effect processing
- `0x00E505–0x00E8C9` (965 B) — Another large patch
- `0x012A8F–0x03F50D` (182 KB) — Re-linked block (function insert/remove shifted addresses)
- `0x05059A–0x05062E` (149 B) — **Version banner string** — clearest version identifier

### v1.02 → v1.11 (XN349E0 → XN349G0)

| Metric | Value |
|--------|-------|
| Changed bytes | 277,310 (52.9%) |
| Nature | Cumulative of both changes above |

---

## Calling Conventions

Three calling conventions detected in the firmware, defined in `ghidra/lang/tmp68301.cspec`:

| Name | Detection pattern | Used for |
|------|------------------|---------| 
| `__stdcall` | `LINK A6,#N` or `MOVEM.L ...,-(SP)` in first 6 insns | C-compiled functions (default) |
| `yamaha_reg` | No LINK, no stack frame; args in D0/D1/D2/D3/A0/A1 | Register-calling routines |
| `__interrupt` | Hardware dispatch, no args, RTE return | ISR handlers from vector table |

Reset handler (`vec[1]` target) uses `yamaha_reg` with `noreturn`.

The firmware uses both conventions concurrently — likely a mix of hand-written assembly
routines (`yamaha_reg`) and compiler-generated code (`__stdcall`). The Yamaha in-house
toolchain appears to have generated the `yamaha_reg` convention for performance-critical
paths.

---

## ISR Pattern

All hardware exception stubs follow the same pattern (deliberate panic halt):

```asm
MOVEM.L {D0-D7/A0-A6},-(SP)  ; save all registers
JSR     report_<error>        ; print error string via UART
MOVEM.L (SP)+,{D0-D7/A0-A6}  ; restore (never returns in practice)
STOP    #0x2700               ; disable all interrupts, halt
RTE                           ; never reached
```

This maps to the ISR stubs at `0x000400–0x0007FF`.

---

## Confirmed Functions (v1.11)

| Address | Name | Signature | Notes |
|---------|------|-----------|-------|
| `0x0003BF4E` | `reset_handler` | `void() yamaha_reg noreturn` | Entry point (via JMP at 0x000400) |
| `0x0000A5A4` | `report_bus_error` | `void() __stdcall` | Prints "Bus Error" via UART |
| `0x0000A5B8` | `report_address_error` | `void() __stdcall` | |
| `0x0000A6A8` | `print_rjust16` | `void(char *s) __stdcall` | 16-char right-justified UART output |
| `0x00002AA6` | `uart_write` | `void(char *str, int len) __stdcall` | Raw UART TX |
| `0x0000238C` | `lcd_write_cmd` | `void(uint8_t cmd)` | HD44780 instruction write |
| `0x000023DC` | `lcd_write_data` | `void(uint8_t data)` | HD44780 data byte write |
| `0x0000274E` | `dsp_ef1_write` | `void(...)` | Write EF1 DSP register |
| `0x00002904` | `dsp_ef2_write` | `void(...)` | Write EF2 DSP register |
| `0x000122E0` | `midi_tx_byte` | `void(uint8_t b) __stdcall` | SC0 UART transmit |
| `0x000128EC` | `midi_process_rx` | `void(...)` | MIDI message dispatch |
| `0x00013454` | `scene_recall` | `void(int n)` | Recall scene memory n |
| `0x00013186` | `scene_store` | `void(int n)` | Store scene memory n |

---

## MIDI Protocol (summary — see `docs/midi.md` for full catalog)

- **Interface:** TMP68301 SC0 UART at 31250 baud, 8N1
- **Message types:** Program Change, Control Change, System Exclusive (Yamaha ID `0x43`)
- **671 controllable parameters**, 16 banks × 96 CCs
- **CC mode:** Channel (bank = MIDI channel offset) or Register (CC98 selects bank)
- **Scene memories 1–50** default to Program Change 1–50

Key LCD strings used to identify MIDI code blocks:
`"--MIDI Setting--"`, `"- CTRL Change -"`, `"---PGM Change---"`, `"------ Bulk ------"`,
`"Sure?"`, `"Memory RECALL"`

---

## Source Tree Structure

Each ROM version lives in its own directory `src/XN349xx/`:

```
src/
├── shared/
│   ├── dsp_tables.S        # Coefficient tables (identical — extracted once)
│   └── dsp_tables.h
├── hw/
│   ├── tmp68301_regs.h     # TMP68301 internal register map (847 lines)
│   ├── dmp9_board_regs.h   # Board-level addresses (LCD, DSP, RAM)
│   ├── vectors.h           # m68k_vector_table_t C struct + _Static_assert
│   ├── isr.h               # ISR stub declarations
│   └── startup.h           # Reset handler prototype
├── XN349E0/                # v1.02
│   ├── vectors.c           # Vector table (C struct, placed at 0x000000)
│   ├── vectors.h
│   ├── isr.c               # ISR stubs
│   ├── startup.c           # Reset handler, hardware init
│   ├── main.c              # Main firmware (bulk — placeholder)
│   ├── lcd.c               # HD44780 LCD driver
│   ├── midi.c              # MIDI I/O (Program Change, Control Change, SysEx)
│   ├── dsp.c               # DSP EF1/EF2 control
│   └── strings.S           # UI strings, effect names (version-specific)
├── XN349F0/                # v1.10 (same layout)
└── XN349G0/                # v1.11 (same layout — canonical)
```

---

## Build System

### Requirements

- `m68k-linux-gnu-gcc` (or `m68k-elf-gcc`) — GCC cross-compiler for 68000
- GNU `make`
- `m68k-linux-gnu-objcopy` — binary output

```sh
sudo apt-get install gcc-m68k-linux-gnu binutils-m68k-linux-gnu
```

### Build targets

```sh
make VERSION=XN349G0          # Build v1.11
make VERSION=XN349F0          # Build v1.10
make VERSION=XN349E0          # Build v1.02
make all                      # Build all three
make verify VERSION=XN349G0   # Size/checksum verification
```

### Linker constraints

- Hardware I/O addresses are `volatile` pointers — never relocated
- CPU stack `0x0040FED0` preserved as linker symbol
- Vector table at `0x000000` (ROM base), via `m68k_vector_table_t` C struct
- Version string at `0x05059A` (fixed section placement)
- DSP coefficient tables at `0x059B00` (fixed, `.incbin` from binary extract)
- Data/BSS in DRAM `0x400000+`

---

## Ghidra Analysis Workflow

For each ROM:

1. Install `ghidra/lang/tmp68301.cspec` and `ghidra/lang/68000.ldefs`
2. Import ROM as `68000:BE:32:TMP68301`, base `0x00000000`
3. Run `TMP68301_Setup.java` — labels TMP68301 internal registers with comments
4. Run `DMP9_Board_Setup.java` — creates memory blocks, labels LCD/DSP/RAM, decodes HD44780 commands
5. Run `DMP9_FuncFixup.java` — 3-pass calling convention fixer:
   - Pass 1: Tags vector table targets (`yamaha_reg` / `__interrupt`)
   - Pass 2: Heuristic prologue scan (`__stdcall` vs `yamaha_reg`)
   - Pass 3: Protects ISR entries from Pass 2 overwrite
6. Run `DMP9_LibMatch.java` — byte-pattern matching for known library functions
7. Enable auto-analysis with **Decompiler Parameter ID**
8. Manual review; refine conventions as needed
9. Export decompiler output per module

For headless (CI) operation: see `ghidra/HEADLESS.md` and `ghidra/scripts/run_analysis.sh`.

---

## Verification Strategy

A reconstructed ROM is considered **verified** when:

1. `make VERSION=XN349G0` builds without error
2. Binary size matches original (512 KB, with FF padding)
3. All hardware I/O addresses appear at expected locations in the binary
4. `build/verify.sh` passes checksum of data/coefficient table section
5. Vector table entries point to valid code within the ROM image
6. Version string at `0x05059A` matches the expected version banner

Bit-identical output is **not** required. Function addresses may differ from the original
as long as vector table and hardware references remain correct.

---

## Release Document Generation

The diff markdown files in `analysis/diffs/` serve as the primary release notes:

| File | Content |
|------|---------| 
| `analysis/diffs/v102-v110.md` | v1.02 → v1.10 change summary |
| `analysis/diffs/v110-v111.md` | v1.10 → v1.11 change summary |
| `analysis/diffs/v102-v111.md` | v1.02 → v1.11 cumulative diff |

For source-level release notes (once source trees are established):

```sh
diff -rq src/XN349F0 src/XN349G0   # Which files changed
diff -ru src/XN349F0 src/XN349G0   # Full source diff
```

---

## Open Questions / Future Work

- [ ] Identify Yamaha in-house C compiler/linker (version, flags) — explains calling convention mix
- [ ] Map all MIDI SysEx command tables (suspected in `0x050000–0x059A54`)
- [ ] Identify DSP coefficient table format (YM3804/YM3807 register dumps?)
- [ ] Determine whether `yamaha_reg` functions are hand-written assembly or custom compiler output
- [ ] Confirm DRAM layout: precise stack vs DSP delay buffer boundary at runtime
- [ ] Map effect parameter names from string table to DSP EF1/EF2 register write sequences
- [ ] Run Ghidra Version Tracking from v1.11 → v1.10 → v1.02 to propagate markup
- [ ] Write `DMP9_ExportDecompiled.java` for automated per-function C output

---

*Generated by Perplexity Computer — DMP9-16 ROM Reconstruction Project*
