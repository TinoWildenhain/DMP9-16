# DMP9_StringScan.java

**Purpose:** Auto-detects ROM string table clusters and creates Ghidra structs.

## What it does

1. Scans the ROM block for null-terminated strings
2. Clusters adjacent strings (packed with no alignment, gap ≤ 1 stray byte)
3. For clusters of ≥3 strings, creates a struct in the `/dmp9_strings` category
4. Applies the struct at the cluster start address
5. Creates a label only if the cluster has at least one function XREF

## What counts as a string character

| Range | Meaning |
|---|---|
| `0x00` | Terminator (ends the string, never part of it) |
| `0x01`–`0x07` | HD44780 CGRAM custom chars — valid inside strings |
| `0x08`–`0x1F` | Other control/extended bytes — valid inside strings |
| `0x20`–`0x7E` | Printable ASCII — valid, counts toward `MIN_PRINTABLE_CHARS` |
| `0x80`–`0xFF` | LCD extended char ROM — valid inside strings |

A string body must satisfy one of:
- total length ≥ 3, **or**
- total length ≥ 2 **and** ≥ 2 printable ASCII chars

This admits e.g. `"EQ\x01"` (2 printable + 1 custom char) and rejects single-byte-plus-null noise.

## Cluster packing

Strings in ROM are packed back-to-back with **no alignment padding** between
them — the next string starts at the byte immediately after the previous
`\x00` terminator. `MAX_CLUSTER_GAP = 1` allows a single stray byte (e.g. a
length prefix) to remain in the same cluster; any actual gap bytes are emitted
as `pad_XXXX` fields so the struct mirrors memory exactly. A gap > 1 starts a
new cluster.

## XRef-based label promotion

The struct datatype is **always** created and applied (useful in the data type
browser even for unreferenced data). The named label is only created when the
cluster has at least one cross-reference from a function — preferring a known
LCD string function (`lcd_write_str`, `lcd_write_str_row`, `lcd_format_num`,
`lcd_str_padded_l`, `lcd_write_cmd`, `lcd_write_data`), and accepting any
function-resident XREF as a "live data" signal. Strings without any function
XREF are likely dead data and are not promoted to named labels.

## Configuration constants

| Constant | Default | Effect |
|---|---|---|
| `MIN_PRINTABLE_CHARS` | 2 | Minimum printable ASCII chars in a 2-byte string |
| `MIN_TOTAL_LEN` | 2 | Minimum total body length before the null |
| `MAX_CLUSTER_GAP` | 1 | Max stray bytes between adjacent strings in a cluster |
| `MIN_CLUSTER_STRINGS` | 3 | Fewer strings in a run are not struct-ified |

Increase `MIN_CLUSTER_STRINGS` to reduce false positives. Add LCD function
addresses to `LCD_STRING_FUNCS` as more are confirmed.

## Duplicate strings

If the same string content appears at multiple addresses, each occurrence
becomes a separate struct field at its own address. This is expected — Yamaha
reused short strings (e.g. `" ON"`, `"OFF"`) across multiple tables. The struct
captures the physical layout; semantic deduplication is a manual analysis step.

## Run order

Run after `DMP9_FuncFixup` (so the ROM block is present and functions are
defined — the XREF filter relies on `getFunctionContaining`). Safe to re-run
(skips already-created structs). Run on each ROM database separately.
