# DMP9_VersionTrack.java

**Purpose:** Cross-ROM name propagation — propagates function names from a known
(canonical) ROM version to the other two ROM versions using byte-pattern matching.

## What it does

### Pattern matching
For each function named in the canonical ROM (v1.11 = XN349G0), extracts the first
N bytes of the function body and searches for an identical byte sequence in the other
two ROMs. If found at a unique location, names that function in the target ROM.

### Configurable thresholds
- `MIN_EXACT_BYTES`: minimum match length (default 16) — increase to reduce false positives
- `MAX_SEARCH_BYTES`: how many bytes of function prologue to use as the pattern

### Limitations
- Functions with very short prologues (< MIN_EXACT_BYTES) are skipped
- Functions that differ between ROM versions (bug fixes, new features) won't match
- The ISR stubs are identical across ROMs and will all match the same stub — name
  propagation for stubs is suppressed (stub names are set by DMP9_FuncFixup instead)

### Output
Prints a summary of matched/unmatched/skipped functions per target ROM.
