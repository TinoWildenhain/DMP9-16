# Contributing to DMP9-16 RE

Thank you for your interest in contributing to this preservation project.

## What contributions are welcome

- Improved Ghidra scripts and annotations
- Additional hardware documentation
- Emulator harness improvements
- Corrected or extended analysis notes
- Reconstructed C code improvements
- Version diff analysis
- Bug fixes in tooling

## What NOT to contribute

- Original Yamaha ROM binary files
- Any copyrighted Yamaha materials
- Speculative or unverified hardware claims without notes

## Branch model

- `main` — stable shared tooling, docs, scripts
- `rom/v1.02` — analysis specific to XN349E0 v1.02 (1993-11-10)
- `rom/v1.10` — analysis specific to XN349F0 v1.10 (1994-01-20)
- `rom/v1.11` — analysis specific to XN349G0 v1.11 (1994-03-10) — **canonical**
- `feature/*` — active development branches

## Commit messages

Keep them descriptive. Prefix with area:
- `ghidra:` for script changes
- `analysis:` for notes and diffs
- `emulator:` for emulator harness
- `src:` for reconstructed C code
- `docs:` for documentation
- `tools:` for helper scripts

## Questions

Open a GitHub Issue with the `question` label.
