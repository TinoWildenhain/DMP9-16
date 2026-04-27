# DMP9_LibMatch.java

**Purpose:** Pattern-matches MCC68K compiler runtime library functions across all
three ROM versions.

## What it does
Uses the confirmed MCC68K library function byte patterns (from v1.11 canonical addresses)
to locate and name the same functions in v1.02 and v1.10 ROMs. This is separate from
DMP9_VersionTrack because library functions may have identical byte patterns but different
surrounding code offsets.

## Tip
Add patterns from newly confirmed functions and re-run. Increase `MIN_EXACT_BYTES` if
false positives appear.
