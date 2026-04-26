# Ghidra Headless Analysis — DMP9/16 ROM Reconstruction

This document describes how to run Ghidra analysis without the GUI, producing
a fully annotated project that can then be opened for decompiler output export.

## Prerequisites

- Ghidra 11.x or 12.x installed at a writable path (not snap)
- `tmp68301.cspec` installed — see `ghidra/lang/INSTALL.md`
- `ghidra/scripts/` on the Ghidra script path

## One-shot: import + analyse + annotate (single ROM)

```sh
GHIDRA=$HOME/opt/ghidra_12.0.4_PUBLIC
SCRIPTS=$HOME/DMP9-16/ghidra/scripts
ROMS=$HOME/DMP9-16/roms
PROJECTS=$HOME/ghidra_projects

$GHIDRA/support/analyzeHeadless \
    $PROJECTS DMP9-16 \
    -import "$ROMS/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin" \
    -processor 68000 \
    -cspec tmp68301 \
    -loader BinaryLoader \
    -loader-baseAddr 0x0 \
    -loader-blockName ROM \
    -loader-length 0x80000 \
    -analysisTimeoutPerFile 600 \
    -postScript DMP9_HeadlessSetup.java \
    -scriptPath "$SCRIPTS"
```

## All three ROMs in one project

```sh
for ROM in XN349E0 XN349F0 XN349G0; do
    BIN=$(ls "$ROMS/"*${ROM}*.bin)
    $GHIDRA/support/analyzeHeadless \
        $PROJECTS DMP9-16 \
        -import "$BIN" \
        -processor 68000 \
        -cspec tmp68301 \
        -loader BinaryLoader \
        -loader-baseAddr 0x0 \
        -loader-blockName ROM \
        -loader-length 0x80000 \
        -analysisTimeoutPerFile 600 \
        -postScript DMP9_HeadlessSetup.java \
        -scriptPath "$SCRIPTS"
done
```

## Export decompiler output for a function range

After headless import, re-open the project in the Ghidra GUI and use:

```
Tools → Decompiler → Export → C/C++ Headers
```

Or run a custom export script headlessly:

```sh
$GHIDRA/support/analyzeHeadless \
    $PROJECTS DMP9-16 \
    -process "XN349G0-v1.11-10.03.1994.bin" \
    -noanalysis \
    -postScript DMP9_ExportDecompiled.java \
    -scriptPath "$SCRIPTS"
```

(See `DMP9_ExportDecompiled.java` — to be written once function boundaries
are stable from the first analysis pass.)

## Loader notes

`BinaryLoader` imports raw binary with no file-format parsing.
Critical options:
- `-loader-baseAddr 0x0` — ROM is mapped at physical address 0
- `-loader-length 0x80000` — 512 KB (0x80000 hex)
- `-loader-blockName ROM` — names the memory block "ROM"

The loader does NOT create a RAM block automatically.
After import, add memory blocks manually (or via `DMP9_Board_Setup.java`):

| Block | Start | Length | Type |
|-------|-------|--------|------|
| ROM   | 0x000000 | 512K  | R-X  |
| DRAM  | 0x400000 | 64K   | RWX  |
| TMP68301 | 0xFFFC00 | 0x400 | RW |
| DSP_EF1  | 0x460000 | 0x1000 | RW |
| DSP_EF2  | 0x470000 | 0x1000 | RW |
| LCD  | 0x490000 | 2 | RW |

`DMP9_Board_Setup.java` creates DRAM, TMP68301, DSP, and LCD blocks
automatically if they don't already exist.

## Key analyzeHeadless flags

| Flag | Purpose |
|------|---------|
| `-processor 68000` | Select 68000 instruction set |
| `-cspec tmp68301` | Use custom calling convention spec |
| `-noanalysis` | Skip auto-analysis (use for re-running scripts only) |
| `-analysisTimeoutPerFile N` | Seconds before giving up on auto-analysis |
| `-postScript <name>` | Script to run after analysis |
| `-prescript <name>` | Script to run before analysis (e.g. to set options) |
| `-scriptPath <dir>` | Where to find scripts |
| `-process <name>` | Re-process already-imported binary (skip re-import) |
| `-readOnly` | Open existing project without modifying |

## Stability notes

Auto-analysis on a raw 68000 ROM needs these analyser settings for best results.
Set via a `-preScript` that calls `setAnalysisOption()`:

```java
// In a preScript:
setAnalysisOption(currentProgram, "Disassemble Entry Points", "true");
setAnalysisOption(currentProgram, "Create Address Tables", "true");
setAnalysisOption(currentProgram, "ARM Aggressive Instruction Finder", "false");
// Increase recursion depth for 68k function boundary detection:
setAnalysisOption(currentProgram, "Stack", "true");
```

The most important manual step: after auto-analysis, run `DMP9_FuncFixup.java`
to fix calling conventions — auto-analysis defaults everything to the first
compiler spec, which misidentifies `yamaha_reg` functions as `__stdcall`.
