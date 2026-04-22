# DSP: Yamaha YM3804 family

## Overview

The DMP9/16 effects engine uses a Yamaha YM3804-family DSP chipset,
identical to the chipset used in the Yamaha SPX-900.

These are Yamaha proprietary reprogrammable delay/reverb DSPs.

## Chips in the EF1 block

| Chip | Alias | Role |
|---|---|---|
| YM3804 | DSP | Core reverb/delay engine; up to 192KB delay DRAM |
| YM6007 | DSP2 | Enhanced delay with 28-bit precision; pitch shift |
| YM3807 | MOD | LFO modulation source for chorus/flanger/phaser |
| YM6104 | DEQ2 | Programmable digital EQ (128-word coefficient RAM) |

## Chips in the EF2 block

| Chip | Role |
|---|---|
| YM3804 | Reverb/delay |
| YM3807 | LFO modulation |

EF2 is a stripped version of EF1 without YM6007 or YM6104.

## Register access

DSP registers are accessed via memory-mapped I/O at:
- EF1: `0x00460000`
- EF2: `0x00470000`

Access patterns follow a write-address then write-data sequence.

## Reference sources

- SPX-900 service manual: documents YM3804 write sequences directly
- TritonCore YM3413 implementation: `github.com/michelgerritse/TritonCore`
  The YM3413 is an architectural sibling of the YM3804 (same era, same Yamaha
  reprogrammable delay/reverb DSP family). Register access patterns are
  near-identical and useful for cross-referencing.

## Effect types

Effect type selection is written to the DSP via an init sequence.
Known effect categories:
- Reverb (Hall, Room, Plate, Stage)
- Delay (Mono, Stereo, Ping-Pong)
- Chorus
- Flanger
- Phaser
- Pitch shift (via YM6007 on EF1)
- Parametric EQ (via YM6104 on EF1)

Parameter encoding for reverb time, delay time, EQ frequency etc.
uses 16-bit DSP integer format with soft-float conversion routines
located in the ROM at approximately `0x3E000+`.

## Analysis notes

- RE_NOTE: YM3804 register map not fully public. Cross-reference SPX-900
  service manual write sequences with ROM disassembly to recover.
- RE_NOTE: YM6007 pitch-shift registers are least documented.
  Focus on YM3804 + YM3807 first for basic effect type coverage.
