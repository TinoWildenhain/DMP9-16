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
| CPU | Toshiba TMP68301A (68000 core, 16 MHz) |
| ROM | 512 KB (TMS27C240-10), mapped at 0x000000 |
| RAM | 64 KB DRAM at 0x400000 (CPU stack + DSP delay buffer) |
| TMP68301 regs | 0xFFFC00 (internal peripheral base) |
| LCD | HD44780 at 0x490000 (CMD) / 0x490001 (DATA) |
| DSP EF1 | YM3804+YM6007+YM3807+YM6104 at 0x460000 |
| DSP EF2 | YM3804+YM3807 at 0x470000 |
| Address decoding | External logic (not TMP68301 AMARO/AAMRO) |

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
| `0x000000–0x00017F` | Exception vector table | 96 vectors × 4 bytes |
| `0x000180–0x0003FF` | Vector table padding / reserved | |
| `0x000400–0x0007FF` | ISR stubs | MOVEM+JSR+STOP+RTE pattern |
| `0x000800–0x004FFF` | Init / startup code | Reset handler chain |
| `0x005000–0x04FFFF` | Main firmware | Bulk of application code |
| `0x050000–0x059A54` | String / parameter tables | UI strings, effect names, MIDI params |
| `0x059B00–0x06283F` | Data / coefficient tables | DSP coefficients, EQ tables |
| `0x062840–0x07FEFF` | Padding / unused | 0xFF filled |
| `0x07FF00–0x07FFFF` | Fault strings + end | "Fault from interrupt" etc. |

**Key finding:** The data/coefficient tables (0x059B00–0x06283F) are **byte-identical across
all three ROM versions**. They can be extracted once and shared.

### RAM layout (runtime)

| Address | Use |
|---------|-----|
| `0x400000–0x40FFFF` | 64 KB DRAM |
| `0x400000` | SSP reset value: `0x0040FED0` (grows downward) |
| `0x40FED0–0x40FFFF` | Stack space |
| `0x400000–0x40FEXX` | DSP delay buffer (grows from base) |

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
| Changed bytes | 276,900 (52.8 %) |
| Changed regions | 45 |
| Nature | **Major rewrite / full recompile** — different toolchain version or significant refactor; all function addresses changed |

Key: function boundary addresses differ throughout; code structure preserved at a logical level
but no address-level correspondence except fixed hardware I/O points.

### v1.10 → v1.11 (XN349F0 → XN349G0)

| Metric | Value |
|--------|-------|
| Changed bytes | 171,097 (32.6 %) |
| Changed regions | 96 |
| Nature | **Patch release** — targeted bug fixes, scattered 1–2 byte JSR/BSR offset fixups |

Notable patches:
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
| Changed bytes | 277,310 (52.9 %) |
| Changed regions | cumulative of above |
| Nature | Cumulative of both changes above |

---

## Calling Conventions

Three calling conventions are present in the firmware (defined in `tmp68301.cspec`):

| Name | Detection pattern | Used for |
|------|------------------|---------|
| `__stdcall` | `LINK A6,#N` or `MOVEM ...,-(SP)` in first 6 insns | C-compiled functions (default) |
| `yamaha_reg` | No LINK, no stack frame; args in D0/D1/D2/D3/A0/A1 | Register-calling routines |
| `__interrupt` | Hardware dispatch, no args, RTE return | ISR handlers from vector table |

Reset handler (`vec[1]` target) uses `yamaha_reg` with `noreturn`.

---

## Source Tree Structure

Each ROM version lives in its own directory `src/XN349xx/`. This ensures:

1. Per-version compilation without name collisions
2. Clean `diff -r src/XN349E0 src/XN349F0` for source-level release notes
3. Independent build targets via `make VERSION=XN349G0`

```
src/
├── shared/
│   ├── dsp_tables.S        # Coefficient tables (identical all versions — extract once)
│   └── dsp_tables.h        # Extern declarations
├── XN349E0/                # v1.02
│   ├── Makefile
│   ├── vectors.S           # Exception vector table
│   ├── startup.c           # Reset handler, hardware init chain
│   ├── isr.c               # ISR stubs (MOVEM/JSR/STOP/RTE pattern)
│   ├── main.c              # Main firmware (bulk)
│   ├── lcd.c               # HD44780 LCD driver
│   ├── midi.c              # MIDI I/O
│   ├── dsp.c               # DSP EF1/EF2 control
│   └── strings.S           # UI strings, effect names (version-specific)
├── XN349F0/                # v1.10  (same layout)
└── XN349G0/                # v1.11  (same layout)
```

---

## Build System

### Requirements

- `m68k-linux-gnu-gcc` (or `m68k-elf-gcc`) — GCC cross-compiler for 68000
- GNU `make`
- `m68k-linux-gnu-objcopy` — for binary output
- `m68k-linux-gnu-size` — for section size verification

### Toolchain setup

See `build/toolchain-setup.md`. On Debian/Ubuntu:

```sh
sudo apt-get install gcc-m68k-linux-gnu binutils-m68k-linux-gnu
```

### Build

```sh
make VERSION=XN349G0          # Build v1.11
make VERSION=XN349F0          # Build v1.10
make VERSION=XN349E0          # Build v1.02
make all                      # Build all three
make verify VERSION=XN349G0   # Size/checksum verification
```

### Linker constraints

- Hardware I/O addresses (LCD, DSP, TMP68301 regs) are `volatile` pointers — not relocated
- All peripheral register addresses are kept at original hardware values
- CPU stack pointer `0x0040FED0` preserved as linker symbol
- Vector table at `0x000000` (ROM base)
- Data/BSS sections placed in DRAM `0x400000+`

---

## Ghidra Analysis Workflow

For each ROM:

1. Import binary as `68000:BE:32:default` with language `TMP68301 / Yamaha DMP9`
   (requires `tmp68301.cspec` installed — see `ghidra/INSTALL.md`)
2. Run `TMP68301_Setup.java` — labels all TMP68301 internal registers
3. Run `DMP9_Board_Setup.java` — labels LCD, DSP, RAM regions; decodes HD44780 commands
4. Run `DMP9_FuncFixup.java` — fixes calling conventions:
   - Pass 1: Tags vector table functions (`yamaha_reg`/`__interrupt`)
   - Pass 2: Heuristic prologue scan for `__stdcall` vs `yamaha_reg`
   - Pass 3: Protects ISR entries from Pass 2 overwrite
5. Run `DMP9_LibMatch.java` — matches known library functions by byte pattern
6. Manual review of auto-analysis results; refine calling conventions as needed
7. Export decompiler output per module

Scripts are in `ghidra/scripts/`. Headers for types are in `src/hw/`.

---

## Verification Strategy

A reconstructed ROM is considered **verified** when:

1. `make VERSION=XN349G0` builds without error
2. Binary size matches original (512 KB, with FF padding)
3. All hardware I/O addresses appear at expected locations in the binary
4. `build/verify.sh` passes checksum of data/coefficient table section
5. Vector table entries point to valid code within the ROM image
6. The version string at `0x05059A` matches the expected version

Bit-identical output is **not** required. Function addresses may differ from original
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

- [ ] Identify the Yamaha in-house C compiler/linker used (version, flags) — would explain
      calling convention mix and non-standard prologue patterns
- [ ] Map all MIDI SysEx command tables (suspected in 0x050000–0x059A54)
- [ ] Identify DSP coefficient table format (YM3804/YM3807 register dumps?)
- [ ] Determine whether `yamaha_reg` functions in main code are hand-written assembly
      or auto-generated by a custom compiler back-end
- [ ] Confirm DRAM layout: stack vs DSP delay buffer boundary
- [ ] Map effect parameter names from string table to DSP register writes

---

*Generated by Perplexity Computer — DMP9-16 ROM Reconstruction Project*
