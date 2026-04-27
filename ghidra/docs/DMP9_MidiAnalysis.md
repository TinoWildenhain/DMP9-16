# DMP9_MidiAnalysis.java

**Purpose:** Anchors MIDI subsystem, LCD, serial ISR, and MCC68K library functions.
Runs after DMP9_FuncFixup.

## What it does

### ROM version detection
Reads a version string or checksum from the ROM to select the correct address table
(v1.02 = XN349E0, v1.10 = XN349F0, v1.11 = XN349G0).

### MIDI function anchors
Names `midi_tx_byte`, `midi_process_rx`, `scene_recall`, `scene_store` at their
version-specific addresses. Uses `getFunctionContaining()` to handle cases where
auto-analysis has merged functions.

### LCD function anchors
Names `lcd_write_cmd`, `lcd_write_data` at their version-specific addresses.
Note: auto-analysis may merge these two adjacent functions into one body — if so,
a warning is logged and the containing function is renamed. Manual split in Ghidra UI
may be needed.

### Serial ISR anchors
Names `timer_housekeeping_isr`, `serial0_status/rx/tx/special_isr`,
`serial1_status/rx/tx/special_isr` across all ROM versions.

### MCC68K runtime library anchors
Names and applies full typed signatures (via `mcc68k_lib` calling convention) to:
`memcpy_b/w/l`, `meminv_b`, `memset_b/w/l`, `memcpy_bitser`, `memcpy_bitser_w`,
`bitrev_byte`.

### Delay function anchors
Names `delay_nested` and `delay_simple` — used for LCD timing and general delays.
Names `write_4D0000` — I/O write helper.

### Anchor error handling
If a function can't be created (overlap), `anchorFunction()` calls `getFunctionContaining()`
and renames the enclosing function if it has an auto-generated name. Never crashes.

## Address tables
All version-specific addresses are defined as `private static final long` constants
grouped by ROM version near the top of the class.
