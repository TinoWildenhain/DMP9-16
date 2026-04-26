# Workflow — DMP9/16 ROM Reconstruction

This document defines the practical reverse-engineering workflow for the Yamaha DMP9 /
DMP9-16 firmware project.

## Canonical ROM strategy

Use **XN349G0 v1.11 (1994-03-10)** as the canonical analysis target.

Rationale:
- Newest known firmware revision
- Names and comments established once, then propagated backwards via Ghidra Version Tracking
- Older ROMs are easier to understand as diffs relative to the newest image
- Version string at `0x05059A` is the clearest version identifier

Older images:
- XN349F0 v1.10 (1994-01-20) — patch predecessor
- XN349E0 v1.02 (1993-11-10) — earliest known

## Project phases

### Phase 1: ROM intake

1. Acquire your own ROM dumps from hardware
2. Verify SHA256 hashes against `roms/hashes/sha256.txt`
3. Record dump provenance, board revision, extraction hardware

### Phase 2: Initial triage

For each image:
- Confirm file size is 524288 bytes
- Extract printable strings (`strings -n 4` or Ghidra's string search)
- Locate vector table (offset 0) and reset handler (`vec[1]` value)
- Confirm version string near `0x050000` region
- Note visible copyright strings and version markers

### Phase 3: Language file installation

```bash
cp ghidra/lang/tmp68301.cspec  $GHIDRAHOME/Ghidra/Processors/68000/data/languages/
cp ghidra/lang/68000.ldefs     $GHIDRAHOME/Ghidra/Processors/68000/data/languages/
```

### Phase 4: Ghidra import (all three ROMs, one project)

Option A — GUI:
- Language: `68000:BE:32:TMP68301`
- Base address: `0x00000000`

Option B — headless (recommended for CI):
```bash
bash ghidra/scripts/run_analysis.sh /path/to/roms/ ~/ghidra_projects/
```

See `ghidra/HEADLESS.md` for full headless documentation.

### Phase 5: Script annotation (run in order for each ROM)

1. `TMP68301_Setup.java` — TMP68301 SFR labels
2. `DMP9_Board_Setup.java` — Board I/O blocks, LCD, DSP, RAM
3. `DMP9_FuncFixup.java` — Calling convention fix (3-pass)
4. `DMP9_LibMatch.java` — Library pattern matching
5. Auto-analysis with Decompiler Parameter ID

### Phase 6: Establish markup on v1.11

Do the majority of manual work on v1.11 first:
- Verify memory map (use `docs/hardware.md` as reference)
- Confirm LCD driver, MIDI handler, scene recall/store
- Identify string table structure (`docs/midi.md` LCD string catalog as anchors)
- Trace DSP EF1/EF2 write sequences
- Identify effect parameter dispatch table

### Phase 7: Version Tracking (propagate to older ROMs)

- Source: v1.11
- Destination: v1.10, then v1.02
- Run: Exact Function Instructions + Bulk Basic Block correlators
- Accept only high-confidence matches; always confirm manually

Note: v1.02 → v1.10 is a full recompile. Do not trust automated matches blindly.

### Phase 8: Binary diffing

Use alongside Ghidra for coarser-grained analysis:
```bash
# Function-level delta between v1.10 and v1.11
radiff2 -A XN349F0.bin XN349G0.bin

# String extraction diff
strings XN349F0.bin > str_f0.txt && strings XN349G0.bin > str_g0.txt
diff str_f0.txt str_g0.txt
```

See `analysis/diffs/` for pre-generated diff reports.

### Phase 9: Reconstructed source tree

Rebuild in layers:
1. Hardware headers (`src/hw/`)
2. Vector table (`src/XN349xx/vectors.c` — C struct, placed at 0x000000)
3. ISR stubs (`src/XN349xx/isr.c`)
4. Reset / startup (`src/XN349xx/startup.c`)
5. Low-level drivers: `lcd.c`, `midi.c`, `dsp.c`
6. String tables / enums (`src/XN349xx/strings.S`)
7. Scene and UI logic
8. Main firmware (`src/XN349xx/main.c`)

Goal: **functional equivalence first**, not binary identity.
See `docs/reconstruction-plan.md` for linker constraints.

### Phase 10: Build and verify

```bash
make VERSION=XN349G0
make verify VERSION=XN349G0
```

### Phase 11: Emulator harness (optional)

Build a host-side stub emulator that maps:
- ROM at `0x00000000`
- DRAM at `0x00400000`
- EF1 DSP at `0x00460000`, EF2 at `0x00470000`
- LCD at `0x00490000`
- TMP68301 SFR at `0x00FFFC00`

Behavioural goals:
- Log LCD writes (correlate with UI strings catalog in `docs/midi.md`)
- Log MIDI TX/RX bytes
- Log LED and panel changes
- Inject knob/button events from scripted input

## Branching guidance

- `main` — shared tools, docs, scripts, recovered common code
- `rom/v1.11` — canonical recovery work
- `rom/v1.10` — version-specific analysis
- `rom/v1.02` — version-specific analysis
- `feature/*` — focused tasks

## Key reference files

| File | Purpose |
|------|---------|
| `docs/hardware.md` | IC map, memory map, PLD decode, all peripherals |
| `docs/midi.md` | MIDI protocol, LCD string catalog, effect type names |
| `docs/reconstruction-plan.md` | Full methodology, diff summary, open questions |
| `docs/ghidra-guide.md` | Ghidra import, script order, API notes |
| `ghidra/HEADLESS.md` | Headless CI analysis guide |
| `ghidra/scripts/run_analysis.sh` | Full CI script for all 3 ROMs |
| `src/hw/tmp68301_regs.h` | TMP68301 register C header |
| `src/hw/dmp9_board_regs.h` | Board-level address constants |
| `analysis/diffs/` | Version diff reports |
