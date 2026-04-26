# Ghidra Headless Analysis — DMP9/16 ROM Reconstruction

This document describes how to run Ghidra analysis without the GUI, producing
a fully annotated project that can then be opened for decompiler output export.

## Prerequisites

- Ghidra **12.0.4** installed at a writable path (not snap) — set `$GHIDRAHOME`
- `ghidra/lang/tmp68301.cspec` and `ghidra/lang/68000.ldefs` installed (see `ghidra/lang/INSTALL.md`)
- `ghidra/scripts/` contains: `TMP68301_Setup.java`, `DMP9_Board_Setup.java`,
  `DMP9_FuncFixup.java`, `DMP9_LibMatch.java`

## Quick start (all three ROMs)

```bash
export GHIDRAHOME=/path/to/ghidra_12.0.4_PUBLIC
bash ghidra/scripts/run_analysis.sh /path/to/roms/ ~/ghidra_projects/
```

The script will:
1. Verify Ghidra installation and `analyzeHeadless` is accessible
2. Install `tmp68301.cspec` and `68000.ldefs` to the Ghidra language directory
3. Import + analyze all three ROMs into one project (`DMP9-16`)
4. Run annotation scripts: TMP68301_Setup → DMP9_Board_Setup → DMP9_FuncFixup → DMP9_LibMatch
5. Print a pass/fail summary with per-ROM timing

Logs land in `ghidra/logs/headless_<LABEL>.log`.

## Re-running scripts only (skip re-import)

If you've already imported the ROMs and just want to re-run the annotation scripts:

```bash
DMP9_MODE=rerun bash ghidra/scripts/run_analysis.sh /path/to/roms/ ~/ghidra_projects/
```

## One-shot: single ROM manual command

```bash
GHIDRA=$GHIDRAHOME
SCRIPTS=$PWD/ghidra/scripts
ROMS=/path/to/roms
PROJECTS=~/ghidra_projects

$GHIDRA/support/analyzeHeadless \
    $PROJECTS DMP9-16 \
    -import "$ROMS/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin" \
    -processor "68000:BE:32:default" \
    -cspec tmp68301 \
    -loader BinaryLoader \
    -loader-baseAddr 0x0 \
    -loader-blockName ROM \
    -loader-length 0x80000 \
    -analysisTimeoutPerFile 900 \
    -overwrite \
    -preScript DMP9_AnalysisOptions.java \
    -postScript TMP68301_Setup.java \
    -postScript DMP9_Board_Setup.java \
    -postScript DMP9_FuncFixup.java \
    -postScript DMP9_LibMatch.java \
    -scriptPath "$SCRIPTS"
```

## Export decompiler output for a function range

After headless import, either:

A) Open the project in the Ghidra GUI:
   - File → Open Project → select `~/ghidra_projects/DMP9-16.gpr`
   - Navigate to function, open Decompiler window

B) Re-run headlessly with an export script:

```bash
$GHIDRA/support/analyzeHeadless \
    $PROJECTS DMP9-16 \
    -process "TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin" \
    -noanalysis \
    -postScript DMP9_ExportDecompiled.java \
    -scriptPath "$SCRIPTS"
```

(`DMP9_ExportDecompiled.java` is planned — to be written once function boundaries
are stable from the first analysis pass.)

## Memory blocks created by scripts

`DMP9_Board_Setup.java` creates these blocks automatically if absent:

| Block | Start | Length | Permissions |
|-------|-------|--------|-------------|
| ROM   | 0x000000 | 512 KB (0x80000) | R-X |
| DRAM  | 0x400000 | 64 KB (0x10000)  | RWX |
| DSP_EF1 | 0x460000 | 4 KB (0x1000) | RW |
| DSP_EF2 | 0x470000 | 4 KB (0x1000) | RW |
| LCD   | 0x490000 | 2 B              | RW |
| TMP68301_SFR | 0xFFFC00 | 1 KB (0x400) | RW |

## Key analyzeHeadless flags

| Flag | Purpose |
|------|---------|
| `-processor 68000` | Select 68000 instruction set |
| `-cspec tmp68301` | Use custom calling convention spec (3 conventions) |
| `-loader BinaryLoader` | Raw binary, no file-format parsing |
| `-loader-baseAddr 0x0` | ROM mapped at physical address 0 |
| `-loader-length 0x80000` | 512 KB |
| `-loader-blockName ROM` | Names the memory block "ROM" |
| `-analysisTimeoutPerFile N` | Seconds before giving up on auto-analysis |
| `-overwrite` | Overwrite existing program in project |
| `-preScript <name>` | Script to run before analysis (sets options) |
| `-postScript <name>` | Script to run after analysis (annotation) |
| `-noanalysis` | Skip auto-analysis (use to re-run scripts only) |
| `-process <name>` | Re-process already-imported binary |
| `-scriptPath <dir>` | Where to find scripts |
| `-log <file>` | Write log to file |

## Stability notes

Auto-analysis on a raw 68000 ROM benefits from these settings (applied by
`DMP9_AnalysisOptions.java` pre-script):

```java
setAnalysisOption(currentProgram, "Disassemble Entry Points", "true");
setAnalysisOption(currentProgram, "Create Address Tables",    "true");
setAnalysisOption(currentProgram, "Decompiler Parameter ID", "true");
setAnalysisOption(currentProgram, "Stack",                   "true");
setAnalysisOption(currentProgram, "ARM Aggressive Instruction Finder", "false");
```

The most important manual step after auto-analysis: run `DMP9_FuncFixup.java` to
fix calling conventions. Auto-analysis defaults everything to the first compiler spec,
misidentifying `yamaha_reg` functions as `__stdcall`.

## Troubleshooting

**"analyzeHeadless: command not found"**
→ Check `$GHIDRAHOME/support/analyzeHeadless` exists and is executable.
→ Do not use a snap Ghidra install — snap restrictions prevent headless mode.

**"Unsupported language: 68000"**
→ The `-processor` flag requires the full language ID from the `.ldefs` file, not just
  the processor name. Use `-processor "68000:BE:32:default"` (not `-processor 68000`).

**"Language tmp68301 not found"**
→ Copy `ghidra/lang/tmp68301.cspec` and `ghidra/lang/68000.ldefs` to
  `$GHIDRAHOME/Ghidra/Processors/68000/data/languages/`

**"tag name 'void' is not allowed. Possible tag names are: `<group>`,`<pentry>`,`<rule>`"**
→ Your installed `tmp68301.cspec` has an older bug where the `__interrupt` prototype used
  `<input><void/></input>` and `<output><void/></output>`. Ghidra 12.x does not allow
  `<void/>` inside `<input>`/`<output>` — use empty self-closing elements instead:
  `<input/>` and `<output/>`. The file in this repo is already corrected; reinstall it:
  ```
  cp ghidra/lang/tmp68301.cspec $GHIDRAHOME/Ghidra/Processors/68000/data/languages/
  ```

**"Script not found: DMP9_Board_Setup"**
→ Ensure `-scriptPath` points to the `ghidra/scripts/` directory in this repo.

**Analysis times out after `N` seconds**
→ Increase `-analysisTimeoutPerFile`. 900 s (15 min) is usually sufficient.
→ On slow machines, try 1800 s.

**DMP9_FuncFixup overwrites ISR calling conventions**
→ Pass 3 of FuncFixup protects ISR entries. If this still happens, check that
  `DMP9_Board_Setup.java` ran first (it populates the vector table labels that
  FuncFixup uses to build its protected set).
