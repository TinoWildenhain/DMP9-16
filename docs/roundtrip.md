# Round-Trip Decompile/Compile Workflow

The "round-trip" workflow lets you measure how much of the DMP9/16 firmware
has been reconstructed: Ghidra exports decompiled C → `m68k-linux-gnu-gcc`
attempts to compile it → the output binary is diffed against the original ROM.

```
┌─────────────────────────────────────────────────────────┐
│  Ghidra project (persistent, at ~/ghidra_projects/)     │
│                                                          │
│  GUI work: rename FUN_ → midi_note_on                   │
│            retype args, add comments                     │
└────────────────────────┬────────────────────────────────┘
                         │ bash run_analysis.sh --export-only
                         ▼
┌─────────────────────────────────────────────────────────┐
│  DMP9_ExportC.java dumps to ~/dmp9_export/XN349G0/       │
│    all_functions.c       — combined decompile            │
│    all_functions.h       — forward decls + MMIO macros   │
│    functions/<name>.c    — one file per named function   │
│    index.tsv             — address, CC, size, ok?        │
└────────────────────────┬────────────────────────────────┘
                         │ bash compile.sh ~/dmp9_export/XN349G0 roms/dumps/XN349G0.bin
                         ▼
┌─────────────────────────────────────────────────────────┐
│  m68k-linux-gnu-gcc compiles                             │
│    Per-function: how many .c files compile cleanly?      │
│    Combined: does all_functions.c link?                  │
│    ROM diff: byte match % vs. original                   │
└─────────────────────────────────────────────────────────┘
```

## Quick start

```bash
# 1. Install cross-toolchain (once)
sudo apt install gcc-m68k-linux-gnu binutils-m68k-linux-gnu

# 2. Export decompiled C from existing Ghidra project
bash ghidra/scripts/run_analysis.sh --export-only

# 3. Attempt compile + ROM diff
bash ghidra/scripts/compile.sh \
    ~/dmp9_export/XN349G0 \
    roms/dumps/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin
```

The compile command is:

```bash
m68k-linux-gnu-gcc \
    -m68000 -Os -ffreestanding -fno-builtin \
    -fomit-frame-pointer -mshort -nostdlib \
    -Wall -Wno-implicit-function-declaration \
    -Wno-int-conversion -Wno-pointer-to-int-cast \
    -I~/dmp9_export/XN349G0 \
    -include ~/dmp9_export/XN349G0/build/ghidra_compat.h \
    -T ~/dmp9_export/XN349G0/build/dmp9.ld \
    -o ~/dmp9_export/XN349G0/build/dmp9_decompiled.elf \
    ~/dmp9_export/XN349G0/all_functions.c
```

Key flags explained:

| Flag | Why |
|------|-----|
| `-m68000` | Target original MC68000 — no 68010+ instructions |
| `-Os` | Optimise for size — closest to the era's compilers |
| `-mshort` | `int` = 16 bits — standard for late-80s/early-90s 68k embedded |
| `-ffreestanding` | No libc, no startup code assumed |
| `-fomit-frame-pointer` | Yamaha mixes stack-frame and register-call conventions |
| `-nostdlib` | No default runtime; we're targeting bare metal |

## MCC68K compiler identification

The original firmware was almost certainly compiled with **Microtec Research MCC68K v4.x** (later acquired by Mentor Graphics). Evidence:

- The section layout in the ROM (`code` → `strings` at 0x050000 → `literals`/`const`) exactly matches MCC68K's documented output format (Ch.8 of the compiler manual)
- The `strings` section always lands at a clean 0x050000 boundary, consistent with LNK68K's default section ordering
- `int` behaves as 16-bit throughout (MCC68K default, matches the `mshort` flag)
- `char` appears to be unsigned (MCC68K default, matches `-funsigned-char`)
- Function prologues use `LINK A6, #-N` for stack-frame functions — MCC68K always uses A6 as frame pointer
- Calling conventions: D0-D2/A0-A1 caller-saves, D3-D7/A2-A5 callee-saves — matches MCC68K ABI exactly
- The runtime library contains `memcpy_b`, `memcpy_w`, `memcpy_l`, `memset` — consistent with MCC68K's libc68k
- Error/fault strings placed in a separate `fault` section at 0x07FF00 — typical LNK68K scatter file pattern

The [MCC68K v4.3K documentation](https://archive.org/details/microtec-68-k-asm-link-lib) is available on the Internet Archive.

## GCC flag equivalents for MCC68K

| MCC68K default | GCC equivalent | Importance |
|---|---|---|
| `int` = 16 bits | `-mshort` | **Critical** — shifts all struct layouts |
| `char` = unsigned | `-funsigned-char` | High — affects string/byte comparisons |
| A6 frame pointer | `-fno-omit-frame-pointer` (for `__stdcall`) | Medium |
| Optimise for space | `-Os` | Medium — instruction selection |
| No ANSI extensions | `-ffreestanding` | Low |
| `code`/`strings`/`literals` sections | linker script `.strings 0x050000` | Critical |

## ROM string table (`src/shared/rom_strings.h`)

The 1521 strings in `0x050000–0x059A54` correspond to MCC68K's `strings` section. For the compiled binary to match the ROM, every string must land at exactly the same address.

The approach: a single packed `const struct dmp9_rom_strings` instance (`dmp9_strings`) is placed at 0x050000 via a named linker section. C source files include `rom_strings.h` and reference strings by name:

```c
#include "rom_strings.h"

// Instead of a bare string literal (which GCC may relocate):
lcd_write_string(S.lbl_scene_memory);       // → "-Scene Memory-"
uart_printf(S.fmt_check_sum, crc);          // → "CHECK SUM [%04x]"
lcd_write_string(S.err_bus_error);          // → "Bus Error"

// For the parameter name table (indexed):
const char *name = PARAM_NAME_PTR(ctrl_idx); // → "CH 1 Level" etc.

// For effect names:
const char *eff = EFFECT_HQ_NAME(type_idx); // → "HQ-REV 1 HALL  "
```

Key format string categories found in the ROM:

| Pattern | Count | Example usage |
|---|---|---|
| `%2d` | 34 | Channel numbers, memory numbers |
| `%1d` | 30 | Single-digit parameters |
| `%d` | 28 | Generic integers |
| `%-2d` | 17 | Left-justified channel IDs |
| `%4.1f` | 11 | Effect parameters (Hz, seconds) |
| `%c` | 14 | Version char, format chars |
| `%02x` | 9 | Hex monitor displays |
| `%5.1f` | 6 | dB values |
| `%5.2fmsec` | 4 | Delay times |

## What to expect (and not to expect)

Ghidra's decompiled C is **analysis output**, not source code. The original
was compiled with an era-specific 68k cross-compiler (likely Tasking/COSMIC/
Microtec or Yamaha's in-house toolchain) that no longer exists. Therefore:

| What you get | What you don't get |
|---|---|
| Correct logic / control flow | Bit-identical binary output |
| Named functions and their call graph | Original compiler register allocation |
| MMIO access patterns | Original instruction scheduling |
| Calling convention correctness | Identical code size |

**The byte-match % is a progress metric, not a success criterion.**
A function is "round-trip verified" when its compiled bytes match the ROM
exactly at the same address. That's a strong signal the decompile is correct.

## Ghidra pseudo-type mapping

Ghidra emits pseudo-types that need mapping before the C compiles.
`compile.sh` auto-generates `build/ghidra_compat.h`:

| Ghidra type | C equivalent | Notes |
|---|---|---|
| `undefined` | `uint8_t` | Unknown-type byte |
| `undefined1` | `uint8_t` | |
| `undefined2` | `uint16_t` | |
| `undefined4` | `uint32_t` | |
| `undefined8` | `uint64_t` | |
| `BADSPACEBASE` | `uint32_t` | Ghidra lost track of address space base |
| `code` | `void *` | Function pointer |

Add additional mappings to `build/ghidra_compat.h` as you encounter them.
Do not commit the build directory — it is generated.

## Iterative refinement loop

```
1. In Ghidra GUI: rename FUN_xxx → real name, retype arguments
2. bash run_analysis.sh --export-only      # fast, ~5s per ROM
3. bash compile.sh ~/dmp9_export/XN349G0 roms/dumps/XN349G0.bin
4. Look at compile errors → fix types / add extern declarations
5. ROM diff % increases → commit naming progress to git
6. Repeat
```

For script changes (not just renaming):

```
1. Edit DMP9_FuncFixup.java / DMP9_LibMatch.java
2. bash run_analysis.sh --incremental   # re-applies scripts, skips auto-analysis
3. bash run_analysis.sh --export-only   # then export
```

## Incremental mode vs. full mode

| Command | What it does | Typical time |
|---|---|---|
| `--full` | Import ROM + full Ghidra auto-analysis + scripts | ~40 s/ROM |
| `--incremental` | Re-run scripts on existing project, skip analysis | ~8 s/ROM |
| `--export-only` | Export decompiled C only, no script re-run | ~10 s/ROM |
| `--export` | `--incremental` + `--export-only` combined | ~18 s/ROM |

Only use `--full` after changing `tmp68301.cspec`, `68000.ldefs`, or when
importing a ROM for the first time.

## Filtering to a single ROM

```bash
DMP9_ROM_FILTER=XN349G0 bash ghidra/scripts/run_analysis.sh --export-only
```

## Known compilation issues

**`undefined reference to ...`**
→ Globals that Ghidra decompiled as bare pointer dereferences don't have
  symbols in the output. Add `extern uint32_t <name>;` declarations to
  `all_functions.h`.

**`BADSPACEBASE` errors**
→ Ghidra lost track of address space context. Replace with explicit MMIO
  macros: `MMIO8(0x490000)` for LCD, etc.

**`implicit declaration of function`**
→ Functions Ghidra calls but hasn't decompiled yet (still named `FUN_`).
  Either name them in Ghidra and re-export, or add a stub extern declaration.

**`tag name 'void' is not allowed` (in cspec)**
→ See [HEADLESS.md troubleshooting](../ghidra/HEADLESS.md). This is a
  `tmp68301.cspec` bug, not a C compile issue.
