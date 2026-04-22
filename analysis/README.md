# Analysis

This directory contains version diff reports, string tables, and memory maps
produced during reverse engineering of the Yamaha DMP9/16 firmware.

## Contents

- `diffs/` — version-to-version change reports
- `strings/` — extracted string tables per ROM version
- `maps/` — memory map and function address tables

## Generating diffs

See individual diff files in `diffs/` for exact commands.
All diffs use `radiff2` for binary-level comparison and
Ghidra Version Tracking for function-level correlation.
