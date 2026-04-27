# DMP9_FuncFixup.java

**Purpose:** Primary setup script — runs first in the analysis pipeline. Establishes the foundation for all other scripts.

## What it does

### Pass 0 — Calling conventions
Applies calling conventions to all already-known functions:
- `yamaha_reg` for application-level functions (args in D0/D1/D2/A0/A1)
- `__interrupt` for ISR functions (RTE return, no args)
- `mcc68k_lib` for MCC68K runtime library functions (stack-based args, A1 callee-saved)

### Pass 1 — C header parsing
Parses `ghidra/include/dmp9_types.h` (M68K_VectorTable struct, SceneParams),
`ghidra/include/tmp68301_regs.h` → `src/hw/tmp68301.h` (TMP68301 SFR definitions),
`ghidra/include/dmp9_board_regs.h` → `src/hw/dmp9_board_regs.h` (board I/O addresses),
`src/shared/vectors.h`, `src/shared/rom_strings.h`.

### Pass 1b — Vector table struct overlay
Applies `M68K_VectorTable` struct at address `0x000000`. After this pass, each vector
slot shows its field name (e.g. `bus_error`, `serial0_rx_isr`) with the pointer resolved
to the function it points to.

### Pass 1c — Stub function naming
Reads each vector slot pointer. Pointers into `0x400–0x9FF` point to panic/exception stubs
and are named `stub_<slot_name>`. Pointers elsewhere point to real ISR bodies and are named
`<slot_name>` directly. Vec 1 (`initial_pc`) always names its target `reset_handler`.
First-match wins for shared stubs (all autovectors sharing one stub get one name).

### Pass 2 — ISR stub force-disassembly
Iterates `0x400–0x9FF` in steps, disassembles any undefined bytes, creates functions.
The stub pattern: `MOVEM.L + JSR panic_target + MOVEM.L + STOP #0x2700 + RTE`.

### Pass 3 — Force disassembly of known function addresses
Ensures auto-analysis didn't miss any confirmed function start addresses by explicitly
calling `disassemble()` and `createFunction()` at each known address.

## Calling convention detection heuristic
- First 6 instructions contain `LINK A6,#n` or `MOVEM.L` save → `__stdcall` / `yamaha_reg`
- First instruction is `RTE` → `__interrupt`
- First instruction is `MOVE.L A1,-(SP)` → `mcc68k_lib`
- Otherwise → `yamaha_reg` default

## Dependencies
Must run before `DMP9_MidiAnalysis.java`, `DMP9_Board_Setup.java`, `TMP68301_Setup.java`.
