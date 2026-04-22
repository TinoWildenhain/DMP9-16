# Workflow

This document defines the practical reverse-engineering workflow for the Yamaha DMP9 / DMP9-16 firmware project.

## Canonical ROM strategy

Use **XN349G0 v1.11 (1994-03-10)** as the canonical analysis target.

Why:
- it is the newest known firmware revision
- names and comments can be established once and propagated backwards
- older ROMs are easier to understand as diffs relative to the newest image

Older images:
- XN349F0 v1.10 (1994-01-20)
- XN349E0 v1.02 (1993-11-10)

## Project phases

### Phase 1: ROM intake

1. Acquire your own ROM dumps from hardware.
2. Verify their SHA256 hashes against `roms/hashes/sha256.txt`.
3. Record dump provenance, board revision, and extraction hardware.

### Phase 2: Initial triage

For each image:
- confirm file size is 524288 bytes
- extract printable strings
- locate vector table and reset handler
- note visible copyright strings and version markers

### Phase 3: Ghidra import

Import all three ROMs into one Ghidra project:
- language: `68000:BE:32:default`
- base address: `0x00000000`
- run `ghidra/scripts/DMP9_Setup.java`
- enable auto-analysis with decompiler parameter identification

### Phase 4: Establish markup on v1.11

Do the majority of manual work on v1.11 first:
- verify memory map
- label TMP68301 integrated peripheral registers
- recover LCD, MIDI, panel, scene, and DSP write routines
- identify strings, tables, and dispatcher functions

### Phase 5: Version tracking

Use Ghidra Version Tracking:
- source: v1.11
- destination: v1.10, then v1.02

Transfer only reviewed markup.
Do not trust automatic matches blindly; always confirm references and context.

### Phase 6: Binary diffing

Use external diff tooling alongside Ghidra:
- `radiff2` for raw binary and function-level deltas
- string extraction diffs for UI / message changes
- address range reports for changed regions

### Phase 7: Reconstructed source tree

Rebuild the firmware in layers:
1. hardware headers
2. low-level drivers
3. tables and enums
4. UI / menu logic
5. scene logic
6. MIDI handling
7. DSP parameter conversion and dispatch

Aim for **functional equivalence first**, not binary identity.

### Phase 8: Emulator harness

Build a host-side stub emulator that maps:
- ROM at `0x00000000`
- SRAM at `0x00400000`
- EF1 DSP registers at `0x00460000`
- EF2 DSP registers at `0x00470000`
- LCD at `0x00490000`
- TMP68301 SFR region at `0x00FFFC00`

Behavioral goals:
- log LCD writes
- log MIDI TX/RX bytes
- log LED and panel changes
- inject knob/button events from scripted input

## Branching guidance

- `main` — shared tools, docs, scripts, recovered common code
- `rom/v1.11` — canonical recovery work
- `rom/v1.10` — version-specific analysis
- `rom/v1.02` — version-specific analysis
- `feature/*` — focused tasks
