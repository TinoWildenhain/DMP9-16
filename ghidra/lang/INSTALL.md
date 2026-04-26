# TMP68301 / Yamaha DMP9 Ghidra Language Files — Installation

## Files
- `tmp68301.cspec` — Custom compiler spec (3 calling conventions: __stdcall, yamaha_reg, __interrupt)
- `68000.ldefs`    — Updated 68000 language definitions (adds TMP68301/Yamaha DMP9 compiler entry)

## Installation (non-snap Ghidra)

1. Copy `tmp68301.cspec` to:
   `$GHIDRA_HOME/Ghidra/Processors/68000/data/languages/`

2. In the SAME directory, open the existing `68000.ldefs` and add this line
   inside the `<language ...>` block for `68000:BE:32:default`:
   ```xml
   <compiler name="TMP68301 / Yamaha DMP9" spec="tmp68301.cspec" id="tmp68301"/>
   ```
   The `68000.ldefs` in this repo already has this line — you can diff it
   against the installed file to find where to add it.

3. Do NOT copy `tmp68301.ldefs` — it causes a duplicate language ID conflict.
   The compiler entry belongs in the existing `68000.ldefs`.

4. Restart Ghidra.

5. File → Set Language → `68000:BE:32:default` → compiler: `TMP68301 / Yamaha DMP9`

## Snap installs

Snap Ghidra files are read-only. Install a separate non-snap Ghidra to a
writable path (e.g. `~/opt/ghidra_12.0.4_PUBLIC`) and install there.
