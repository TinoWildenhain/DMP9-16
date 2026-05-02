# DMP9_StringScan.java

**Purpose:** Auto-detects ROM string table clusters and creates Ghidra structs.

## What it does

1. Scans the ROM block for null-terminated printable ASCII strings (≥3 chars)
2. Clusters adjacent strings (within 4-byte alignment gap)
3. For clusters of ≥3 strings, creates a struct in the `/dmp9_strings` category
4. Applies the struct at the cluster start address and creates a label

## Configuration constants

| Constant | Default | Effect |
|---|---|---|
| `MIN_STRING_LEN` | 3 | Shorter strings are skipped |
| `MAX_CLUSTER_GAP` | 4 | Max alignment gap between adjacent strings |
| `MIN_CLUSTER_STRINGS` | 3 | Fewer strings in a run are not struct-ified |

Increase `MIN_STRING_LEN` or `MIN_CLUSTER_STRINGS` to reduce false positives.

## Duplicate strings

If the same string content appears at multiple addresses, each occurrence becomes
a separate struct field at its own address. This is expected — Yamaha reused short
strings (e.g. `" ON"`, `"OFF"`) across multiple tables. The struct captures the
physical layout; semantic deduplication is a manual analysis step.

## Run order

Run after `DMP9_FuncFixup` (so the ROM block is present). Safe to re-run (skips
already-created structs). Run on each ROM database separately.
