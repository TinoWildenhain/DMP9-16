# src

Reconstructed C source code for the Yamaha DMP9/16 firmware.

## Structure

```
src/
  hw/          Hardware abstraction headers
  firmware/    Recovered firmware modules
  tables/      ROM data tables (effect params, strings, etc)
```

## Build status

Work in progress. Hardware headers are initial stubs derived from
ROM disassembly and TMP68301/YM3804 documentation.

## Compiler

Target: `m68k-linux-gnu-gcc -m68000 -O1 -fomit-frame-pointer`

Goal: functional equivalence first, binary identity later.

## RE_NOTE convention

All uncertain or unverified items are marked with `RE_NOTE:` in comments.
Do not remove these until the item has been confirmed against ROM disassembly.
