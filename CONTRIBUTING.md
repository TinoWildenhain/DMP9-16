# Contributing

This is a reverse engineering research project. Contributions are welcome
if they advance the understanding of the Yamaha DMP9/16 firmware.

## Ground rules

- Do not commit actual ROM binary files. See `roms/README.md`.
- All uncertain items must be tagged `RE_NOTE:` in code and docs.
- Keep commit messages descriptive (`re: identify lcd_write_cmd at 0x238C`).
- Use the branch structure defined in `docs/workflow.md`.

## What to contribute

- Ghidra function annotations (exported as `.gzf` patches or documented in `analysis/`)
- Hardware register corrections or additions to `src/hw/`
- Confirmed function recoveries in `src/firmware/`
- Version diff findings in `analysis/diffs/`
- Emulator improvements in `emulator/`
- Documentation fixes

## RE_NOTE convention

Mark anything unverified:

```c
// RE_NOTE: parameter encoding unconfirmed, inferred from SPX-900 manual
uint16_t reverb_time_encode(uint16_t ms) { ... }
```

Remove the tag only after confirming against ROM disassembly.

## Pull requests

Open a PR against `main`. Describe:
1. What you found / fixed
2. How you verified it (Ghidra address, ROM version, reference source)
3. Any remaining uncertainty (RE_NOTE items)
