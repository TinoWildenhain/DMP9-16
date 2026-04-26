# DMP9/16 ROM Reconstruction Project Plan

## Overview

This document describes the methodology and goals for reverse-engineering and reconstructing
the firmware of the Yamaha DMP9/DMP16 rack digital mixer across three known ROM versions.

The objective is to produce, for each ROM version, a compilable C/assembly source tree that
links into a **functionally equivalent** binary — not necessarily bit-identical, but producing
the same runtime behaviour with hardware addresses (I/O registers, peripheral buffers) preserved
at their original locations.

A secondary goal is to discover MIDI controls and SysEx sub-commands that are **undocumented
in the service manuals**, and potentially to extend the firmware with new MIDI functionality.
The DMP9's predecessor family (DMP7/DMP11) provides important architectural context for
understanding what may have survived or evolved in the DMP9's MIDI implementation.

---

## Hardware Platform

| Parameter | Value |
|-----------|-------|
| Device | Yamaha DMP9 / DMP16 |
| Board | DIG board (XN047A00), DIG16 variant confirmed from service manual |
| CPU | Toshiba TMP68301AF-16 (68000 core, 16 MHz) — IC24 |
| ROM | 512 KB (TMS27C240-10 / XN349A00), mapped at 0x000000 — IC34 |
| DRAM | 64 KB (MSM511664-80, IC35/36) at 0x400000 — dual-use: CPU stack + DSP delay |
| SRAM | M5M5256BFP-70LL (IC66–68, 93, 94) — 256K, DSP section |
| TMP68301 regs | 0xFFFC00 (internal peripheral base) |
| LCD | HD44780-compatible, 4×16 chars, at 0x490000 (CMD) / 0x490001 (DATA) |
| DSP EF1 | YM3804+YM6007+YM3807+YM6104 at 0x460000 |
| DSP EF2 | YM3804+YM3807 at 0x470000 |
| Address decoding | **External logic PLDs** (PLD-TM1 / PLD-TM2) — not TMP68301 AMARO/AAMRO |

### PLD address decode (confirmed from service manual pp. 35–36)

| PLD | Part | Chip selects |
|-----|------|-------------|
| PLD-TM1 (IC33, XA353A00) | — | /ROMCE (ROM), /RAMOEL, /RAMOEU (DRAM), /LC0, /LC1 (LED), /REC (encoder) |
| PLD-TM2 (IC32, XA354A00) | — | /LCD (→0x490000), /MEGWF, /WMEGRD (DSP MEG), /KOC (key matrix), /AUXWR, /ROMAUX (AUX) |

Both PLDs decode A16–A19 plus AS/UDS/LDS/CS0/CS1/R_W from the TMP68301 bus.
The TMP68301's on-chip AMARO/AAMRO memory management registers are **not** used for
address decode on this hardware.

### Key ICs (DIG16 board)

| IC | Part | Function |
|----|------|----------|
| IC24 | TMP68301AF-16 | CPU |
| IC23 | HD62098 (XM309A00) | MEG — Multiple Effect Generator |
| IC19,29,46,70,71 | YM6007 (XF164A00) | DSP2 — main effects |
| IC26–28,37–39 | YM6104 (XE788A00) | DEQ2 — digital EQ |
| IC20–22 | YM3422B (XE862B00) | ES1 — format converter |
| IC30 | FMC9WSI (XL533A00) | PAL — AES/Yamaha format converter |
| IC31 | XN352A00 | PLD-VCO |
| IC32 | XN354A00 | PLD-TM2 |
| IC33 | XN353A00 | PLD-TM1 |
| IC34 | XN349A00 | EPROM 4M (ROM) |

---

## ROM Versions

| Label | Part | Date | File |
|-------|------|------|------|
| XN349E0 | v1.02 | 1993-11-10 | `TMS27C240-10-DMP9-16-XN349E0-v1.02-10.11.1993.bin` |
| XN349F0 | v1.10 | 1994-01-20 | `TMS27C240-10-DMP9-16-XN349F0-v1.10-20.01.1994.bin` |
| XN349G0 | v1.11 | 1994-03-10 | `TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin` |

All three are 512 KB (524,288 bytes). The last non-FF byte is at 0x07FFA3.

---

## ROM Memory Map

| Address Range | Region | Notes |
|---------------|--------|-------|
| `0x000000–0x00017F` | Exception vector table | 96 vectors × 4 bytes (0x180 bytes) |
| `0x000180–0x0003FF` | Vector table padding | Reserved |
| `0x000400–0x0007FF` | ISR stubs | MOVEM.L+JSR+STOP #0x2700+RTE panic pattern |
| `0x000800–0x004FFF` | Init / startup code | Reset handler chain |
| `0x005000–0x04FFFF` | Main firmware | Bulk of application code |
| `0x050000–0x059A54` | String / parameter tables | UI strings, effect names, MIDI params |
| `0x05059A–0x05062D` | Version string area | `"Version 1.11  Date Mar.10.1994"` in v1.11 |
| `0x059B00–0x06283F` | DSP coefficient tables | **Identical across all 3 ROMs** |
| `0x062840–0x07FEFF` | Padding | 0xFF filled |
| `0x07FF00–0x07FFFF` | Fault strings + end | `"Fault from interrupt"` etc. |

**Key finding:** The data/coefficient tables (0x059B00–0x06283F) are **byte-identical across
all three ROM versions**. They are extracted once from any ROM and shared as
`src/shared/dsp_tables.S` (`.incbin`).

### RAM layout (runtime)

| Address | Use |
|---------|-----|
| `0x400000–0x40FFFF` | 64 KB DRAM |
| `0x0040FED0` | SSP reset value (stack top, grows downward) |
| `0x40FED0–0x40FFFF` | Stack space (~0x130 bytes headroom) |
| `0x400000–0x40FEXX` | DSP delay buffer (grows up from base) |

---

## Reset Vector (v1.11 reference)

| Vector | Value | Notes |
|--------|-------|-------|
| SSP (vec[0]) | `0x0040FED0` | Stack top — just below DRAM ceiling |
| PC (vec[1]) | `0x0003BF4E` | Actual reset handler (via JMP thunk at 0x000400) |

---

## ROM Diff Summary

### v1.02 → v1.10 (XN349E0 → XN349F0)

| Metric | Value |
|--------|-------|
| Changed bytes | 276,900 (52.8%) |
| Changed regions | 45 |
| Nature | **Major rewrite / full recompile** — different toolchain version or significant refactor; all function addresses changed |

### v1.10 → v1.11 (XN349F0 → XN349G0)

| Metric | Value |
|--------|-------|
| Changed bytes | 171,097 (32.6%) |
| Changed regions | 96 |
| Nature | **Patch release** — targeted bug fixes, scattered JSR/BSR offset fixups |

Notable patch regions:
- `0x000006–0x000007` — Reset PC minor offset change
- `0x000FE3–0x001053` (113 B) — Timing/init sequence modification
- `0x006699–0x006Ad1` — Display/LCD handler area
- `0x00DF81–0x00E311` (913 B) — Large fix, likely MIDI or effect processing
- `0x00E505–0x00E8C9` (965 B) — Another large patch
- `0x012A8F–0x03F50D` (182 KB) — Re-linked block (function insert/remove shifted addresses)
- `0x05059A–0x05062E` (149 B) — **Version banner string** — clearest version identifier

### v1.02 → v1.11 (XN349E0 → XN349G0)

| Metric | Value |
|--------|-------|
| Changed bytes | 277,310 (52.9%) |
| Nature | Cumulative of both changes above |

---

## Calling Conventions

Three calling conventions detected in the firmware, defined in `ghidra/lang/tmp68301.cspec`:

| Name | Detection pattern | Used for |
|------|------------------|---------| 
| `__stdcall` | `LINK A6,#N` or `MOVEM.L ...,-(SP)` in first 6 insns | C-compiled functions (default) |
| `yamaha_reg` | No LINK, no stack frame; args in D0/D1/D2/D3/A0/A1 | Register-calling routines |
| `__interrupt` | Hardware dispatch, no args, RTE return | ISR handlers from vector table |

Reset handler (`vec[1]` target) uses `yamaha_reg` with `noreturn`.

The firmware uses both conventions concurrently — likely a mix of hand-written assembly
routines (`yamaha_reg`) and compiler-generated code (`__stdcall`). The Yamaha in-house
toolchain appears to have generated the `yamaha_reg` convention for performance-critical
paths.

---

## ISR Pattern

All hardware exception stubs follow the same pattern (deliberate panic halt):

```asm
MOVEM.L {D0-D7/A0-A6},-(SP)  ; save all registers
JSR     report_<error>        ; print error string via UART
MOVEM.L (SP)+,{D0-D7/A0-A6}  ; restore (never returns in practice)
STOP    #0x2700               ; disable all interrupts, halt
RTE                           ; never reached
```

This maps to the ISR stubs at `0x000400–0x0007FF`.

---

## Confirmed Functions (v1.11)

### Low-level I/O (hardware drivers)

| Address | Name | Signature | Notes |
|---------|------|-----------|-------|
| `0x0003BF4E` | `reset_handler` | `void() yamaha_reg noreturn` | Entry point (via JMP at 0x000400) |
| `0x0000A5A4` | `report_bus_error` | `void() __stdcall` | Prints "Bus Error" via UART |
| `0x0000A5B8` | `report_address_error` | `void() __stdcall` | |
| `0x00002AA6` | `uart_write` | `void(char *str, int len) __stdcall` | Raw UART TX (SC0) |
| `0x0000238C` | `lcd_write_cmd` | `void(uint8_t cmd)` | HD44780 instruction write to 0x490000 |
| `0x000023DC` | `lcd_write_data` | `void(uint8_t data)` | HD44780 data byte write to 0x490001 |
| `0x0000274E` | `dsp_ef1_write` | `void(...)` | Write EF1 DSP register (0x460000) |
| `0x00002904` | `dsp_ef2_write` | `void(...)` | Write EF2 DSP register (0x470000) |
| `0x000122E0` | `midi_tx_byte` | `void(uint8_t b) __stdcall` | SC0 UART transmit (MIDI out) |

### MCC68K runtime library block (0x0023xx cluster)

A contiguous block of Microtec MCC68K compiler runtime routines at 0x0023AE–0x0024C0,
confirmed present at matching relative offsets in all three ROM versions.  These are
identified by their characteristic byte patterns (ROXR/ROXL ×8, NOT.B invert loop,
DBF copy loops) and by cross-referencing addresses from the old analysis session which
correctly identified 0x23FC (bitser) and 0x243E (bitrev_byte) in v1.02.

All functions use the `MOVE.L A1,-(SP)` register-save prologue (no LINK A6 frame) —
consistent with Microtec's register-argument ABI for small utility functions.

| Address (v1.11) | Address (v1.02) | Address (v1.10) | Name | Description |
|-----------------|-----------------|-----------------|------|-------------|
| `0x0023AE` | `0x00238C` | `0x0023AE` | `memcpy_b` | Byte copy: `MOVE.B (A0)+,(A1)+` ; `BTST #0,D0` ; `BNE` |
| `0x0023CA` | `0x0023A8` | `0x0023CA` | `memcpy_w` | Word copy: `SUBQ.W #1,D0` ; `MOVE.W (A0)+,(A1)+` ; `DBF` |
| `0x0023E4` | `0x0023C2` | `0x0023E4` | `memcpy_l` | Longword copy: `SUBQ.W #1,D0` ; `MOVE.L (A0)+,(A1)+` ; `DBF` |
| `0x0023FE` | `0x0023DC` | `0x0023FE` | `meminv_b` | Invert-copy: `MOVE.B (A0)+,D1` ; `NOT.B D1` ; `MOVE.B D1,(A1)+` |
| `0x00241E` | `0x0023FC` | `0x00241E` | `memcpy_bitser` | Bit-serial copy: `ROXR.B #1,D1` ; `ROXL.B #1,D2` ×8 per byte |
| `0x002464` | `0x002442` | `0x002464` | `bitrev_byte` | Single-byte bit reversal helper |
| `0x002488` | `0x002468` | `0x002488` | `memcpy_bitser_w` | Word bit-serial variant |
| `0x0024DA` | `0x0024B8` | `0x0024DA` | `memset_b` | Byte fill: `MOVE.B D0,(A1)+` ; `SUBQ.W #1,D1` ; `BNE` |
| `0x0024F2` | `0x0024D0` | `0x0024F2` | `memset_w` | Word fill variant |
| `0x00250A` | `0x0024E8` | `0x00250A` | `memset_l` | Longword fill variant |

The `meminv_b` and `memcpy_bitser` functions are used for DSP coefficient / delay-line
DMA — the inversion and bit-serialisation patterns match the YM6007 DSP load protocol
identified in the old analysis session.  `memcpy_b/w/l` are standard memory utilities;
`bitrev_byte` is called by the bit-serial copiers to reverse bit order within each byte
before transmitting to the DSP.

### LCD string/display subsystem (hot cluster at 0x007800–0x007C00)

Identified by call-count analysis: the cluster at 0x007800–0x007C00 contains the
most-called functions in the firmware (401 / 141 / 124 / 122 / 57 call sites).
All share the same LINK A6 / MOVEM.L prologue and reference LCD shadow-RAM tables.

| Address | Name | Calls | Notes |
|---------|------|-------|-------|
| `0x000078D4` | `lcd_write_str` | 401 | Writes null-terminated string at column; checks `lcd_state_flag1/2` |
| `0x0000793A` | `lcd_write_str_row` | 124 | Variant with extra row parameter (row+col addressing) |
| `0x000079A8` | `lcd_str_padded_r` | 57 | Right-padded string to fixed field width |
| `0x00007A06` | `lcd_str_padded_l` | 122 | Left-padded string variant |
| `0x00007A74` | `lcd_str_width` | 5 | Width-limited string write |
| `0x00007AE2` | `strlen` | 67 | MCC68K `strlen` — tight `TST.B (A0)+ ; BNE` loop |
| `0x00007B4E` | `lcd_format_num` | 141 | Formatted decimal/hex number → LCD |
| `0x00007BA6` | `lcd_format_str` | 7 | Formatted string display variant |
| `0x0000A09C` | `lcd_write_padded` | — | Uses `strlen`, computes padding for alignment |
| `0x0000A6A8` | `print_rjust16` | — | 16-char right-justified UART output |

### LCD shadow RAM (confirmed addresses in SHARED_DRAM)

| Address | Name | Notes |
|---------|------|-------|
| `0x00407982` | `lcd_col_table` | Column→DDRAM lookup, 64 bytes (4×16 LCD) |
| `0x004079C2` | `lcd_col_table_2` | Second half of column table (+0x40 offset) |
| `0x00407A02` | `lcd_shadow_buf` | LCD display shadow buffer, 64 bytes |
| `0x0040B6EA` | `lcd_mode_flag` | If bit7 set: suppress character output |
| `0x0040B8A2` | `lcd_state_flag1` | State flag 1 (busy/blink); non-zero blocks control chars |
| `0x0040B8A3` | `lcd_state_flag2` | State flag 2 (paired with flag1) |

### MIDI / scene (confirmed)

| Address | Name | Signature | Notes |
|---------|------|-----------|-------|
| `0x000128EC` | `midi_process_rx` | `void(...)` | MIDI message parsing core |
| `0x00013454` | `scene_recall` | `void(int n)` | Recall scene memory n |
| `0x00013186` | `scene_store` | `void(int n)` | Store scene memory n |

### Serial / MIDI ISRs (init section — identical in all 3 ROMs)

All ISR entry points live in the low init block and are hard-wired in the vector table.
They are identical in v1.02, v1.10, and v1.11 — the init section is never relocated.
Vector numbers are TMP68301 internal autovectors (68-series numbering).

| Address | Vec# | Name | Notes |
|---------|------|------|-------|
| `0x000006F0` | 69 | `timer_housekeeping_isr` | Multi-rate scheduler: increments `sw_tick`, /5 modulo, decrements countdown timers, conditionally calls `housekeeping_tick` |
| `0x000007AE` | 72 | `serial0_status_isr` | SIO0: reads 0xFFFD87, extracts 4-bit cause code → `sio0_flags`; sets `sio_event_src=1` |
| `0x000007EC` | 73 | `serial0_rx_isr` | SIO0 MIDI Rx: ring-buffer byte from 0xFFFD89; overflow → bit4 of `sio0_flags` |
| `0x00000836` | 74 | `serial0_tx_isr` | SIO0 Tx: feeds ring buffer to 0xFFFD89; disables Tx IRQ when empty |
| `0x0000088A` | 75 | `serial0_special_isr` | SIO0: sets bit6 of `sio0_flags` (error/break) |
| `0x000008B4` | 76 | `serial1_status_isr` | SIO1: same as 0x7AE but for 0xFFFD97 → `sio1_flags` |
| `0x000008F2` | 77 | `serial1_rx_isr` | SIO1 Rx: ring-buffer byte from 0xFFFD99 |
| `0x0000093C` | 78 | `serial1_tx_isr` | SIO1 Tx: feeds from Tx buffer to 0xFFFD99 |
| `0x00000990` | 79 | `serial1_special_isr` | SIO1: sets bit6 of `sio1_flags` |

Parallel/GPIO handlers (shifted in v1.10/v1.11):

| Address (v1.11) | Address (v1.02) | Name | Notes |
|-----------------|-----------------|------|-------|
| `0x0000F0EA` | `0x0000ECC8` | `parallel_irq_handler_1` | GPIO parallel interrupt, handler 1 |
| `0x0000F121` | `0x0000ECFF` | `parallel_irq_handler_0` | GPIO parallel interrupt, handler 0 |

Scheduler callback (called every 5 timer ticks when `sw_status & 0x0800`):

| Address | Name | Notes |
|---------|------|-------|
| `0x0000182A` | `housekeeping_tick` | High-level service routine: scans UI, issues DSP commands, etc. Identical in all 3 ROMs |

### Init-block helpers (0x274A cluster)

Short utility routines in the boot/init section, confirmed identical in all 3 ROMs.

| Address (v1.11) | Address (v1.02) | Name | Signature | Notes |
|-----------------|-----------------|------|-----------|-------|
| `0x0000274A` | `0x00002728` | `delay_nested` | `void(u_int16_t loops)` | Nested busy-wait loop |
| `0x00002768` | `0x00002746` | `delay_simple` | `void(u_int32_t count)` | Simple countdown spin delay |
| `0x00002770` | `0x0000274E` | `write_4D0000` | `void(u_int16_t val)` | Writes to 0x004D0000 (unknown peripheral) |

### Timer and serial configuration (v1.02, from init block 0x2588–0x27AE)

Confirmed from disassembly of the init block; confirmed present in all 3 ROMs at same relative offset.

**Timer channels (TMP68301 internal timers at 0x00FFFE0x):**

| Channel | Base | TCR value | Period (counts) | Derived period @ 16 MHz | Use |
|---------|------|-----------|-----------------|--------------------------|-----|
| Timer0 | `0xFFFE00` | `0x10D2` | 1000 | 62.5 µs (16 kHz) | OS tick / ISR rate |
| Timer1 | `0xFFFE20` | `0x20D2` | 6250 | 390.6 µs (~2.56 kHz) | UI / housekeeping |
| Timer2 | `0xFFFE40` | `0x08D2` | 2 | Sync / watchdog pulse |

**Serial channels (TMP68301 SIO at 0x00FFFFDxx):**

| Channel | Regs (status/data/ctrl) | SSR | SDR | SPR | Rate | Use |
|---------|------------------------|-----|-----|-----|------|-----|
| SIO0 | 0xFFFD87 / 89 / 83 | 0xCC | 0x10 | 0x20 | **31 250 bps** | MIDI in/out |
| SIO1 | 0xFFFD97 / 99 / 93 | 0xCC | 0x10 | 0x01 | (non-MIDI) | Panel/debug UART |
| SIO2 | 0xFFFDA7 / A9 / A3 | 0xCC | 0x10 | 0x01 | (non-MIDI) | Aux UART |

Baud rate derivation: `f_clk / (prescale × divider) = 16 MHz / 512 = 31 250 bps` for SIO0 (SPR=0x20 selects prescale×divider=512).

### Serial ring buffers and ISR flag RAM (SHARED_DRAM)

**SIO0 (MIDI) buffers:**

| Address | Name | Size | Notes |
|---------|------|------|-------|
| `0x00407D9E` | `sio0_rx_buf_start` | 0x1004 B | Rx ring buffer start |
| `0x00408DA2` | `sio0_rx_buf_end` | — | Rx ring buffer end |
| `0x00408D9E` | `sio0_rx_wr_ptr` | 4 B | Current write pointer (ISR-maintained) |
| `0x00408FB6` | `sio0_tx_buf_start` | 0x1000 B | Tx ring buffer start |
| `0x00409FB6` | `sio0_tx_buf_end` | — | Tx ring buffer end |
| `0x00409FBA` | `sio0_tx_rd_ptr` | 4 B | Current read pointer (Tx ISR) |
| `0x0040ABE8` | `sio0_last_tx_byte` | 1 B | Last byte sent on SIO0 |

**SIO1 buffers:**

| Address | Name | Size | Notes |
|---------|------|------|-------|
| `0x00408DA6` | `sio1_rx_buf_start` | 0x0104 B | Rx ring buffer start |
| `0x00408EAA` | `sio1_rx_buf_end` | — | |
| `0x00408EA6` | `sio1_rx_wr_ptr` | 4 B | |
| `0x00409FBE` | `sio1_tx_buf_start` | 0x0600 B | Tx ring buffer start |
| `0x0040A5BE` | `sio1_tx_buf_end` | — | |
| `0x0040A5C2` | `sio1_tx_rd_ptr` | 4 B | |
| `0x0040ABE9` | `sio1_last_tx_byte` | 1 B | Last byte sent on SIO1 |

**ISR flag bytes:**

| Address | Name | Notes |
|---------|------|-------|
| `0x0040781E` | `sw_tick` | Free-running software tick counter (incremented every timer IRQ) |
| `0x00407822` | `sw_tick_slow` | Slow tick counter (every 5 base ticks) |
| `0x00407AAC` | `tick_mod2` | 2-state modulo counter (wraps at 2) |
| `0x00407AAD` | `countdown_0` | Countdown timer 0 (decremented to zero) |
| `0x00407AB1` | `countdown_1` | Countdown timer 1 |
| `0x00407AB2` | `countdown_2` | Countdown timer 2 |
| `0x00407AAA` | `tick_gate` | Gates increment of `tick_mod2` (non-zero = active) |
| `0x00407AAB` | `tick_mod2_count` | Incremented when `tick_gate` non-zero |
| `0x0040788C` | `sw_status` | Status word; bit 0x0800 triggers `housekeeping_tick` call from timer ISR |
| `0x0040B681` | `sio0_flags` | SIO0 event/cause register: bits[3:0]=cause code, bit4=Rx overflow, bit6=special |
| `0x0040B682` | `sio1_flags` | SIO1 event/cause register (same layout) |
| `0x0040B684` | `sio_event_src` | Last event source: 1=SIO0, 2=SIO1 |
| `0x0040B685` | `sio0_tx_busy` | Non-zero while SIO0 Tx ring buffer is active |
| `0x0040B686` | `sio1_tx_busy` | Non-zero while SIO1 Tx ring buffer is active |

---

## Analysis Strategy

### Guiding principle: gradual naming, no address pinning

Because all three ROM versions represent the same firmware at different stages of development,
the goal is to build up function names and argument types **incrementally** using Ghidra's
tooling, rather than hard-coding addresses into the source or linker.

Only addresses that are **physically fixed by the hardware** should be pinned:

| What | How pinned | Rationale |
|------|-----------|-----------|
| Exception vector table | Linker: `.vectors AT(0x000000)` | Processor mandates 0x000000 |
| DSP coefficient tables | `.incbin` from extracted binary | Identical across all 3 ROMs |
| Hardware I/O (LCD, DSP, DRAM) | `#define` macros in `dmp9_board_regs.h` | PLD-decoded fixed bus addresses |
| TMP68301 SFR base | `#define TMP68301_BASE 0xFFFC00` | On-chip, processor-fixed |

Everything else — code sections, string tables, library functions — is allowed to float
and is placed by the compiler/linker. The output only needs to fit within 512 KB ROM space
and maintain correct vector-to-code references.

This strategy allows:
- The same source tree to compile for all three ROM versions
- Gradual recovery of function names and signatures without breaking the build
- Accurate comparison of functional differences between versions (rather than address differences)

### Cross-ROM Version Tracking workflow

`DMP9_VersionTrack.java` (in `ghidra/scripts/`) implements byte-pattern cross-ROM function
matching. The typical workflow:

1. Run a full analysis on v1.11 (canonical) and manually name functions as you identify them.
2. Run `--version-track` mode in `run_analysis.sh` to propagate names, data types, and
   comments from v1.11 into v1.10 and v1.02 projects using byte-pattern matching.
3. Review the match report (`analysis/version_track/match_report.md`) for false positives
   (functions that changed significantly between versions may match at the wrong address).
4. Iterate: as more functions are named in v1.11, re-run version tracking to propagate.

```bash
# Run version tracking (propagates v1.11 → v1.10, v1.11 → v1.02)
bash ghidra/scripts/run_analysis.sh --version-track

# Match report location
cat analysis/version_track/match_report.md
cat analysis/version_track/match_data.json
```

### MIDI undocumented command investigation

The primary research goal — beyond reconstruction — is to discover MIDI controls and SysEx
sub-commands that are **not documented in the DMP9 service or owner's manuals**. The approach:

1. `DMP9_MidiAnalysis.java` labels all known MIDI anchor points (from confirmed function
   addresses) and annotates the CC parameter table and SysEx dispatch table with comments.
2. The script also scans for **Note On** (`0x9n`) and **Pitch Bend** (`0xEn`) handlers —
   these are undocumented in the DMP9 manual but are suspected to have survived from the
   DMP7/DMP11 era (see next section).
3. After running the script, review the plate comments on `midi_process_rx` and any
   newly labeled `midi_note_handler` / `midi_pitchbend_handler` functions in Ghidra.
4. Findings from all three ROM versions can be diffed to see whether undocumented handlers
   were added, modified, or removed across the v1.02 → v1.10 → v1.11 progression.

---

## DMP7 / DMP11 Context

The DMP9 is the third generation of Yamaha's digital rack mixer line. Understanding its
predecessors helps interpret the DMP9's MIDI implementation, especially for undocumented
features that may have carried over.

### DMP7 and DMP11 architecture

| Parameter | DMP7 / DMP11 | DMP9 / DMP16 |
|-----------|-------------|-------------|
| CPU | Motorola MC6809 (8-bit, ~1 MHz) | Toshiba TMP68301 (68000 core, 16 MHz) |
| Architecture | 8-bit, Harvard-ish | 32-bit, 68000 |
| MIDI control method | **Note On (0x9n)** for 206-parameter real-time control | **Control Change (0xBn)**, 671 params, 16 banks |
| Pitch shift MIDI | **Pitch Bend (0xEn)** | CC-based effect parameter |
| Parameter count | 206 | 671 |
| Scene memories | Yes | 50 scenes |

### DMP7 MIDI implementation (for comparison)

The DMP7 used Note On events (`0x9n`) as a general-purpose parameter control mechanism:
- Note number selected the parameter index (0–127, covering 206 parameters)
- Velocity set the parameter value
- This was documented in the DMP7 owner's guide but is **completely non-standard** MIDI usage

Pitch shift (on the DMP7's effects section) was controlled via Pitch Bend events (`0xEn`),
making real-time pitch control possible from a keyboard controller.

The DMP11 shared the same MC6809 architecture and similar MIDI approach.

### What may have survived in the DMP9

When Yamaha redesigned the DMP9 on the TMP68301 platform, they expanded the parameter
count from 206 to 671 and switched to a CC-based bank/select system. However:

- The `DMP9_MidiAnalysis.java` scan checks whether `0x9n` (Note On) and `0xEn` (Pitch Bend)
  dispatch cases exist anywhere in the MIDI parser — these would indicate undocumented
  backward-compatibility handlers
- The `0x9n` path is particularly interesting: if the DMP9 retained Note On → parameter
  control, it would allow a DMP7-style control workflow on DMP9 hardware
- Any such handlers would appear as branches from the `midi_process_rx` dispatch table
  that are not accounted for by the documented CC/PGM/SysEx handlers

The version-to-version diff (v1.02 → v1.10 → v1.11) of the MIDI dispatcher region
(`0x00DF81–0x00E311` is a known large patch in v1.10→v1.11) may reveal when/whether
these were added or removed.

---

## MIDI Protocol (summary — see `docs/midi.md` for full catalog)

- **Interface:** TMP68301 SC0 UART at 31250 baud, 8N1
- **Message types:** Program Change, Control Change, System Exclusive (Yamaha ID `0x43`)
- **671 controllable parameters**, 16 banks × 96 CCs
- **CC mode:** Channel (bank = MIDI channel offset) or Register (CC98 selects bank)
- **Scene memories 1–50** default to Program Change 1–50

Key LCD strings used to identify MIDI code blocks:
`"--MIDI Setting--"`, `"- CTRL Change -"`, `"---PGM Change---"`, `"------ Bulk ------"`,
`"Sure?"`, `"Memory RECALL"`

---


## MCC68K Toolchain — Confirmed Headers

Two original MCC68K header files from the Yamaha build environment have been recovered:

| File | SCCS version | Date | Copyright |
|------|-------------|------|-----------|
| `MRIEXT.H` | 1.33 | Oct 30, 1992 | 1988–1991, 1992 |
| `STRING.H` | 1.14 | Dec 21, 1992 | 1988–1991 |

Both date from **late 1992**, approximately 13 months before the earliest ROM (v1.02, Nov 1993).
This is consistent with Yamaha acquiring the standard MCC68K v4.1/4.2 package in 1992 and
developing the DMP9 firmware over the following year.

### What these headers tell us

**No OEM variant.** The headers are structurally identical to the standard commercial
MCC68K v4.x distribution. `stdaux`/`stdprn` (MS-DOS host streams), the `_FPU`/`_68040`
dispatch macros in `eprintf`/`ftoa`, and the `NULLPTR`/`TRUE`/`FALSE` convenience macros
are all present — they are simply unused dead code in the embedded target build.

**Library functions actually used in the ROM:**

| Function | Status | Notes |
|----------|--------|-------|
| `strlen` | **Present** @ `0x007AE2` | MCC68K `TST.B (A0)+ ; BNE` loop; 67 call sites |
| `memcpy_b/w/l` | **Present** @ `0x0023AE`/`CA`/`E4` | MCC68K runtime variants (byte/word/long); `MOVE.x (A0)+,(A1)+` ; `DBF` |
| `meminv_b` | **Present** @ `0x0023FE` | MRI extension: invert-copy (`NOT.B` on each byte); used for DSP loading |
| `memcpy_bitser` | **Present** @ `0x00241E` | Bit-serial copy (`ROXR/ROXL ×8`); used for YM6007 DSP DMA |
| `bitrev_byte` | **Present** @ `0x002464` | Helper: reverse bit order in a single byte |
| `memset_b/w/l` | **Present** @ `0x0024DA`/`F2`/`250A` | Byte/word/longword fill variants |
| `strcpy` | **Not found** | Short strings copied with explicit loops; LCD is only 16 chars wide |
| `strcmp` | **Not found** | Comparisons done with `CMPI` against string literals |
| `memset` | see `memset_b/w/l` above | Present as typed variants in the MCC68K library block |
| `zalloc` | **Not present** | No heap; firmware is fully stack/static |
| `memclr` | **Not present** | Uses inline clear loops |
| `itostr`/`itoa` | **Not present** | Yamaha wrote custom decimal formatter: `DIVU.W #10,D0` loop found at `0x008832`, `0x0088A8`, `0x00F44C`, `0x013DC6` |
| `ftoa`, `eprintf` | **Not applicable** | No FPU on TMP68301; `_FPU`/`_68040` macros never defined |
| `strtok`, `strerror` | **Not applicable** | No OS, no `errno` |

**`STRING.H` vs `MRIEXT.H` relationship:**
- `string.h` v1.14 is the standard ANSI C `<string.h>` — defines `strlen`, `strcpy`, `memcpy`, etc.
- `mriext.h` v1.33 extends the standard library with MRI-specific functions (`zalloc`, `memclr`,
  `itostr`, `ftoa`) and convenience macros (`min`/`max`, `TRUE`/`FALSE`, `NULLPTR`)
- The firmware uses `strlen` and the `memcpy_b/w/l` / `meminv_b` / `memcpy_bitser` runtime
  routines from the standard library; none of the MRI extensions (`zalloc`, `memclr`, `itostr`)
  are present — consistent with a bare-metal build that links against `libmcc68k.a` minimally

**Arithmetic profile (all 3 ROMs):**
- 243 `DIVU.W` instructions in code (÷2: 38×, ÷10: 4×, ÷16: 2×, ÷56: 1×)
- 486 `MULU.W` instructions — heavy fixed-point DSP coefficient arithmetic
- 65 `DIVS.W` — signed division for parameter range mapping
- No `DIVU.W #96` — CC count multiplication done via `MULU.W` in the other direction

### Reconstruction implications

The GCC flags in `compile.sh` already approximate the MCC68K output:
```
-m68000 -Os -mshort -funsigned-char -ffreestanding -fno-builtin -fno-common -nostdlib
```
Key points:
- `-mshort` (`int` = 16-bit) is confirmed critical — `DIVU.W` (16-bit quotient) is used everywhere
- The `strlen` implementation is a simple loop — GCC with `-Os` generates the same pattern
- No `libmcc68k.a` link needed for the string functions actually used

## Source Tree Structure

Each ROM version lives in its own directory `src/XN349xx/`:

```
src/
├── shared/
│   ├── dsp_tables.S        # Coefficient tables (identical — extracted once)
│   ├── dsp_tables.h
│   ├── rom_strings.h       # Packed const struct dmp9_rom_strings at 0x050000
│   └── rom_strings.c       # Designated initializer instance
├── hw/
│   ├── tmp68301_regs.h     # TMP68301 internal register map (847 lines)
│   ├── dmp9_board_regs.h   # Board-level addresses (LCD, DSP, RAM) — all #define macros
│   ├── vectors.h           # m68k_vector_table_t C struct + _Static_assert
│   ├── isr.h               # ISR stub declarations
│   └── startup.h           # Reset handler prototype
├── XN349E0/                # v1.02
│   ├── vectors.c           # Vector table (C struct, placed at 0x000000)
│   ├── isr.c               # ISR stubs
│   ├── startup.c           # Reset handler, hardware init
│   ├── main.c              # Main firmware (bulk — placeholder)
│   ├── lcd.c               # HD44780 LCD driver
│   ├── midi.c              # MIDI I/O (Program Change, Control Change, SysEx)
│   ├── dsp.c               # DSP EF1/EF2 control
│   └── strings.S           # UI strings, effect names (version-specific)
├── XN349F0/                # v1.10 (same layout)
└── XN349G0/                # v1.11 (same layout — canonical)
```

---

## Build System

### Requirements

- `m68k-linux-gnu-gcc` (or `m68k-elf-gcc`) — GCC cross-compiler for 68000
- GNU `make`
- `m68k-linux-gnu-objcopy` — binary output

```sh
sudo apt-get install gcc-m68k-linux-gnu binutils-m68k-linux-gnu
```

### Compiler flags (MCC68K-compatible subset via GCC)

The firmware was compiled with **Microtec Research MCC68K v4.x**. The equivalent
GCC flags for a faithful reconstruction are:

```
-m68000 -Os -mshort -funsigned-char -ffreestanding -fno-builtin -fno-common -nostdlib
```

Key flags:
- `-mshort` — `int` is 16-bit (critical — MCC68K default)
- `-funsigned-char` — `char` is unsigned
- `-Os` — optimize for size (matches MCC68K's typical output density)

### Compile script

```bash
bash ghidra/scripts/compile.sh \
    ~/dmp9_export/XN349G0 \
    roms/dumps/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin
```

### Linker constraints (current policy)

Only the following are pinned in the linker script:

| Section | Address | Reason |
|---------|---------|--------|
| `.vectors` | `0x000000` | 68000 hardware requirement |
| DSP coefficient tables | `0x059B00` | `.incbin` of extracted binary — ROM-position-dependent |

Hardware I/O addresses are expressed as `#define` macros in `dmp9_board_regs.h` —
they are baked in as immediate constants by the compiler, not as linker symbols.
String tables and all code sections float freely within the 512 KB ROM space.

---

## Ghidra Analysis Workflow

For each ROM:

1. Install `ghidra/lang/tmp68301.cspec` and `ghidra/lang/68000.ldefs`
2. Import ROM as `68000:BE:32:TMP68301`, base `0x00000000`
3. Run `TMP68301_Setup.java` — labels TMP68301 internal registers with comments
4. Run `DMP9_Board_Setup.java` — creates memory blocks, labels LCD/DSP/RAM, decodes HD44780 commands
5. Run `DMP9_FuncFixup.java` — 3-pass calling convention fixer:
   - Pass 1: Tags vector table targets (`yamaha_reg` / `__interrupt`)
   - Pass 2: Heuristic prologue scan (`__stdcall` vs `yamaha_reg`)
   - Pass 3: Protects ISR entries from Pass 2 overwrite
6. Run `DMP9_LibMatch.java` — byte-pattern matching for known library functions
7. Run `DMP9_MidiAnalysis.java` — labels MIDI anchors, annotates CC table and SysEx dispatch, scans for undocumented Note On / Pitch Bend handlers
8. Enable auto-analysis with **Decompiler Parameter ID**
9. Manual review; refine conventions as needed
10. Export decompiler output per module via `DMP9_ExportC.java`

### Cross-ROM version propagation

After step 9 on v1.11 (canonical):

11. Run `--version-track` mode to propagate names/types/comments to v1.10 and v1.02
12. Review `analysis/version_track/match_report.md` for false positives
13. Repeat steps 9–12 iteratively as more functions are named

For headless (CI) operation: see `ghidra/HEADLESS.md` and `ghidra/scripts/run_analysis.sh`.

---

## Verification Strategy

A reconstructed ROM is considered **verified** when:

1. `make VERSION=XN349G0` builds without error
2. Binary size matches original (512 KB, with FF padding)
3. All hardware I/O addresses appear at expected locations in the binary
4. `build/verify.sh` passes checksum of data/coefficient table section
5. Vector table entries point to valid code within the ROM image
6. Version string at `0x05059A` matches the expected version banner

Bit-identical output is **not** required. Function addresses may differ from the original
as long as vector table and hardware references remain correct.

---

## Release Document Generation

The diff markdown files in `analysis/diffs/` serve as the primary release notes:

| File | Content |
|------|---------| 
| `analysis/diffs/v102-v110.md` | v1.02 → v1.10 change summary |
| `analysis/diffs/v110-v111.md` | v1.10 → v1.11 change summary |
| `analysis/diffs/v102-v111.md` | v1.02 → v1.11 cumulative diff |

For source-level release notes (once source trees are established):

```sh
diff -rq src/XN349F0 src/XN349G0   # Which files changed
diff -ru src/XN349F0 src/XN349G0   # Full source diff
```

---

## Open Questions / Future Work

- [ ] Complete function naming in v1.11 — use `DMP9_MidiAnalysis.java` output as starting point for MIDI dispatcher
- [ ] Confirm or refute presence of Note On (`0x9n`) / Pitch Bend (`0xEn`) handlers in DMP9 (undoc DMP7 legacy)
- [ ] Map all SysEx sub_status values — documented: `0x75` `0x7D` `0x7E` `0x7C` `0x7F` `0x7B`; scan for others
- [ ] Compare MIDI dispatcher across v1.02/v1.10/v1.11 — region `0x00DF81–0x00E311` is a known large patch
- [ ] Identify Yamaha in-house C compiler/linker (version, flags) — explains calling convention mix
- [ ] Map DSP coefficient table format (YM3804/YM3807 register dumps?)
- [ ] Determine whether `yamaha_reg` functions are hand-written assembly or custom compiler output
- [ ] Confirm DRAM layout: precise stack vs DSP delay buffer boundary at runtime
- [ ] Map effect parameter names from string table to DSP EF1/EF2 register write sequences
- [ ] Extend firmware: add new SysEx sub_status or Note On handler (long-term goal, after reconstruction)

---

*Generated by Perplexity Computer — DMP9-16 ROM Reconstruction Project*
