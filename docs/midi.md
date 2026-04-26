# MIDI Reference — Yamaha DMP9 / DMP9-16

This document catalogues all MIDI message types, LCD strings, and protocol details
extracted from the DMP9 Owner's Guide and used to identify MIDI-handling code in the
firmware disassembly.  A secondary focus is the investigation of **undocumented** MIDI
commands that may have survived from the earlier DMP7/DMP11 platform.

---

## Transport

- **Interface:** TMP68301 internal UART (SC0), SC0BUF register at `0xFFFC00` region
- **Baud rate:** 31250 baud, 8N1
- **Physical:** Standard MIDI DIN-5 IN / OUT / THRU
- **RX path:** SC0 receive interrupt → ISR → MIDI message parser
- **TX path:** `midi_tx_byte` at `0x000122E0` (v1.11)

---

## Message Types

The DMP9 documents three MIDI message types:

| Type | Status byte | Notes |
|------|-------------|-------|
| Program Change | `0xCn` (n = channel) | Scene memory recall/store |
| Control Change | `0xBn` | Parameter control (671 params, 16 banks × 96 CCs) |
| System Exclusive | `0xF0 0x43 …` | Bulk dump / request |

Two additional types are **not documented but suspected** based on the DMP7/DMP11 heritage:

| Type | Status byte | Suspected use |
|------|-------------|---------------|
| Note On | `0x9n` | Legacy DMP7-style parameter control (206 params via note number/velocity) |
| Pitch Bend | `0xEn` | Legacy DMP7 pitch shift control — may map to EF1/EF2 pitch parameter |

See [DMP7 / DMP11 Comparison](#dmp7--dmp11-comparison) and
[Undocumented Command Investigation](#undocumented-command-investigation) below.

---

## Program Change

Scene memories 1–50 default to Program Change 1–50 (user-assignable).

**Tx:** Output when scene is stored or recalled via [STORE] / [RECALL].
**Rx:** Recalls the assigned scene memory.

### LCD screens for Program Change

```
----PGM Change----     (title: --PGM Change--)
 Tx <=>  OMNI <=>
 Rx <=>  ECHO  <=>
```

```
-- PGM Assign --       (title: -- PGM Assign --)
 PGM  1 :  MEM  1
 PGM  2 :  MEM  2
 PGM  3 :  MEM  3
```

---

## Control Change

671 controllable parameters, split across 16 banks × 96 CCs.

### Modes

**Channel mode:** Each bank maps to a separate MIDI channel.
- Bank 0 → MIDI Channel n+0
- Bank 1 → MIDI Channel n+1
- … Bank 11 → MIDI Channel n+11 (wraps at 16)

**Register mode:** CC98 (NRPN LSB) selects the bank number; all CCs use the channel
set in MIDI Setup.

### CC parameter table location

The CC parameter dispatch table is at `0x054FEE` (v1.11):
- 671 parameters × 16 banks × 96 CCs
- Each entry maps (bank, CC) → parameter index → parameter handler

`DMP9_MidiAnalysis.java` annotates this table with Ghidra comments, one row per bank.

### LCD screens for Control Change

```
- CTRL Change -        (title: - CTRL Change -)
 Tx <=>  OMNI <=>
 Rx <=>  ECHO  <=>
 Memory RECALL<=>
```

```
- CTRL Assign -        (title: - CTRL Assign -)
 Prm No.:  0
  Inp 1 Level
 Bank:0  CTRL:0
```

```
-CTRL Out PRM.-        (title: -CTRL Out PRM.-)
 ON/OFF<=>  Pan<=>
 Level<=>  SEND<=>
 EQ<=>  OTHERS<=>
```

---

## System Exclusive (Bulk Dump / Request)

Yamaha manufacturer ID: `0x43`

### Frame format

```
F0 43 <dev_num> <sub_status> <size_hi> <size_lo> [data bytes] <checksum> F7
```

- `dev_num`: device number (0x10 + MIDI channel for device-specific, 0x7F for broadcast)
- `sub_status`: selects dump type (see table below)
- `checksum`: two's complement of all data bytes (ignoring status bytes)

### Documented sub_status values

| Tag | sub_status | Content |
|-----|-----------|---------|
| `CTRL_ASSIGN` | `0x7B` | CC → parameter assignment table |
| `EDIT` | `0x7C` | Edit buffer data |
| `MEM` | `0x7D` | Scene memory data (range 1–50 selectable for Tx; Rx always all) |
| `SETUP` | `0x7E` | Setup data |
| `PGM_ASSIGN` | `0x7F` | Scene → Program Change assignment table |
| `ALL` | `0x75` | All data types simultaneously |

### Undocumented sub_status candidates

The following sub_status values are **not mentioned in any DMP9 manual** but could
exist based on the pattern range and the DMP7 heritage. The investigation approach
is to scan the SysEx dispatch table in Ghidra (annotated by `DMP9_MidiAnalysis.java`)
for any handled sub_status values outside the documented set:

| Range | Notes |
|-------|-------|
| `0x00–0x74` | Completely undocumented — any handler here is a find |
| `0x76–0x7A` | Gap between `0x75` (ALL) and `0x7B` (CTRL_ASSIGN) |

### LCD screens for Bulk Dump

```
------ Bulk ------     (title: ------ Bulk ------)
 OMNI  <=>  ALL<=>
 MEM  1-  1  SETUP<=>
 EDIT  PGM  CTRL
```

Confirmation prompt: `"Sure?"` — appears before transmit or request.

---

## MIDI Local / Monitor

### Local LCD

```
---MIDI Local----      (title: ---MIDI Local----)
 Local:  ON
  MIDI IN:  EDIT
 Bulk ECHO:  OFF
```

Parameters:
- `Local` OFF/ON — controls whether front panel affects edit buffer
- `MIDI IN` EDIT/REMOTE — only effective when Local=OFF
- `Bulk ECHO` OFF/ON — echoes SysEx to MIDI OUT (channel must not match)

### Monitor LCD

```
--MIDI Monitor--       (title: --MIDI Monitor--)
```

F8/FE filter: suppresses MIDI Clock (F8) and Active Sensing (FE) from display.

---

## MIDI Setup LCD

```
--MIDI Setting--       (title: --MIDI Setting--)
 Tx Channel :  1
 Rx Channel :  1
 Mode :  Channel
```

Mode options: `Channel` / `Register`

---

## LCD String Catalog (MIDI-related)

These strings appear verbatim in the ROM string table (region `0x050000–0x059A54`)
and in the LCD driver code. Use them as search anchors in the disassembly.

| String (exact 16-char field) | Context |
|------------------------------|---------|
| `--MIDI Setting--` | MIDI Setup header |
| `Tx Channel : 1`   | MIDI Setup, channel display |
| `Rx Channel : 1`   | MIDI Setup, channel display |
| `Mode : Channel`   | MIDI Setup, mode display |
| `---PGM Change---` | PGM Change header |
| `-- PGM Assign --` | PGM Assign header |
| `- CTRL Change -`  | CTRL Change header |
| `- CTRL Assign -`  | CTRL Assign header |
| `-CTRL Out PRM.-`  | CTRL Out parameter header |
| `Inp 1 Level`      | Parameter name (CTRL Assign) |
| `Bank:0`           | Bank selector display |
| `CTRL:0`           | CC selector display |
| `Prm No.: 0`       | Parameter number display |
| `Memory RECALL`    | CTRL Change option |
| `---MIDI Local----`| Local MIDI header |
| `--MIDI Monitor--` | Monitor header |
| `------ Bulk ------` | Bulk dump header |
| `Sure?`            | Confirmation prompt |

---

## DMP7 / DMP11 Comparison

The DMP9 is the third generation of Yamaha's digital rack mixer line. The DMP7 and DMP11
used a radically different CPU and MIDI architecture; understanding them is key to finding
what may have carried over (documented or not) into the DMP9.

### Architecture comparison

| Parameter | DMP7 / DMP11 | DMP9 / DMP16 |
|-----------|-------------|-------------|
| CPU | Motorola **MC6809** (8-bit, ~1 MHz) | Toshiba **TMP68301** (68000 core, 16 MHz) |
| Architecture | 8-bit, 6809 ISA | 32-bit, 68000 ISA — completely different |
| Code/data compatibility | None — full rewrite for DMP9 | — |
| MIDI control method | **Note On `0x9n`** — 206-parameter real-time | **Control Change `0xBn`** — 671 params, 16 banks |
| Pitch control | **Pitch Bend `0xEn`** → pitch shift effect | CC-based effect parameter (standard) |
| Parameter count | 206 | 671 |
| Scene memories | Yes | 50 scenes |
| SysEx | Yamaha `0x43` | Yamaha `0x43` (same Mfr ID, different sub_status set) |

### DMP7 Note On parameter control

The DMP7 used Note On events in a non-standard way:
- **Note number** (0–127): selects the parameter index within the 206-parameter set
- **Velocity** (0–127): sets the parameter value
- This is documented in the DMP7 owner's guide but is entirely non-standard MIDI usage
- Sending Note On with velocity 0 (= Note Off semantics in standard MIDI) may have had
  a defined meaning (parameter set to minimum / off)

### DMP7 Pitch Bend

On the DMP7, the Pitch Bend wheel message (`0xEn`, 14-bit signed value) was routed
directly to the pitch shift effect parameter. This allowed real-time pitch control from
any MIDI controller with a pitch wheel.

### What the DMP9 replaced

When Yamaha redesigned on the TMP68301:
- The CC-based system (671 params, 16 banks) replaced the Note On control scheme entirely
  in the published documentation
- The pitch effect is now controlled via CC, same as all other parameters
- However, the firmware may contain backward-compatibility code paths for Note On / Pitch Bend
  — either intentionally (for users of DMP7-era control software) or as dead code that was
  never cleaned up

---

## Undocumented Command Investigation

### Goal

Find MIDI messages that the DMP9 firmware handles but that are **not described in any
published manual**. Candidates:

1. **Note On (`0x9n`) handler** — inherited from DMP7 parameter control scheme
2. **Pitch Bend (`0xEn`) handler** — inherited from DMP7 pitch shift control
3. **Undocumented SysEx sub_status** — Yamaha often uses additional sub_status values
   for factory/service functions, firmware updates, or extended data types

### Ghidra investigation method

`DMP9_MidiAnalysis.java` performs these steps automatically:

1. **Anchor labeling** — labels `midi_tx_byte`, `midi_process_rx`, and the CC/PGM/SysEx
   handler entry points from known addresses (v1.11 reference)
2. **CC table annotation** — adds plate comments to the CC parameter dispatch table at
   `0x054FEE`, one comment per bank row
3. **SysEx dispatch scan** — walks the SysEx handler's dispatch table and annotates each
   sub_status branch; flags any sub_status outside the documented set
4. **Note On / Pitch Bend scan** — searches the MIDI parser for `0x90`/`0x9F` or `0xE0`/`0xEF`
   comparisons; if found, creates a `midi_note_handler` or `midi_pitchbend_handler` label
   and adds a plate comment: `"UNDOCUMENTED: Note On used for parameter control (DMP7 legacy?)"`

After running the script, review:
- Plate comments on `midi_process_rx` — lists all identified dispatch branches
- Any `midi_note_handler` / `midi_pitchbend_handler` labels in the listing
- `analysis/midi/undoc_report.md` (generated by the script) — summary of all findings

### Cross-version comparison

Because the MIDI dispatcher region contains a **large known patch** between v1.10 and v1.11
(`0x00DF81–0x00E311`, 913 bytes), running `DMP9_MidiAnalysis.java` on all three ROMs and
comparing the undoc_report output is the most direct way to see whether undocumented handlers
were added, changed, or removed during the firmware's development.

```bash
# Run MIDI analysis on all 3 ROMs
bash ghidra/scripts/run_analysis.sh --incremental

# Compare undoc reports across versions
diff analysis/XN349E0/midi/undoc_report.md analysis/XN349G0/midi/undoc_report.md
diff analysis/XN349F0/midi/undoc_report.md analysis/XN349G0/midi/undoc_report.md
```

### Known MIDI patch region (v1.10 → v1.11)

| ROM region | Bytes changed | Likely content |
|-----------|--------------|----------------|
| `0x00DF81–0x00E311` | 913 B | MIDI handler or effect processing fix |
| `0x00E505–0x00E8C9` | 965 B | Additional MIDI or UI fix |

These are priority areas for manual review in Ghidra after `DMP9_MidiAnalysis.java` runs.

---

## Effect Type Strings (EF1 / EF2 TYPE display)

These names appear in the effect type selection screen and in the string table.
They are written to the LCD via `lcd_write_data`. EF1 supports all types;
EF2 supports the non-HQ variants (shown in parentheses in the manual).

```
- Effect1 TYPE -       (title, also - Effect2 TYPE -)
 HQ-REV 1 HALL
 Effect Recall
```

| Effect name (EF1) | EF2 name |
|-------------------|----------|
| `HQ-REV 1 HALL`   | `Rev1 Hall` |
| `HQ-REV 2 ROOM`   | `Rev2 Room` |
| `HQ-REV 3 STAGE`  | `Rev3 Stage` |
| `HQ-REV 4 PLATE`  | `Rev4 Plate` |
| `Flange`          | `Flange` |
| `Chorus`          | `Chorus` |
| `Phasing`         | `Phasing` |
| `Tremolo`         | `Tremolo` |
| `Symphonic`       | `Symphonic` |
| `Early Ref. 1`    | `Early Ref. 1` |
| `Early Ref. 2`    | `Early Ref. 2` |
| `Gate Reverb`     | `Gate Reverb` |
| `Reverse Gate`    | `Reverse Gate` |
| `Delay L-C-R`     | `Delay L-C-R` |
| `Stereo Echo`     | `Stereo Echo` |
| `Pitch Change`    | `Pitch Change` |

Effect parameter labels also appear in the LCD:
`Rev.Time=`, `High Ratio=`, `Diffusion=`, `Ini.Dly`, `Rev.Dly`,
`Mod.Freq`, `Mod.Depth`, `FB.Gain`, `Mod.Dly`, `Phase Ofst`,
`AM Depth`, `PM Depth`, `Liveness`, `Room Size`, `Dly(L)`, `Dly(R)`, `Dly(C)`,
`Level(C)`, `FB.Dly`, `Pitch`, `Fine 1`, `Fine 2`, `Out.Lvl(1)`, `Out.Lvl(2)`,
`Pan(1)`, `Pan(2)`, `FB.Gain 1`, `FB.Gain 2`

---

## Effect Assign LCD

```
-- Eff.Assign --       (header)
 Individual
```

Or in Serial mode:
```
-- Eff.Assign --
 Serial
 EF1+RET2:  50%
 EF2+RET2:  50%
```

---

## Other UI LCD Strings (cross-reference for disassembly)

| String | Context |
|--------|---------|
| `Memory RECALL`    | Scene recall action |
| `-Cascade Assign-` | Cascade unit assign header |
| `-Cascade PAD -`   | Cascade pad header |
| `--Master Delay--` | Master delay header |
| `-Master ON/OFF-`  | Master send ON/OFF header |
| `--Send3/4 Mode--` | Send 3/4 mode header |
| `-- PHASE ----`    | Phase reverse header |
| `--Bus Assign----` | Bus assign header |
| `-Level(RETURN )-` | Return level graphical monitor |
| `-Level Monitor-`  | Return level numeric monitor |
| `Ch.Title(RET 2 )` | Auxiliary return title editor |
| `[Return 2]`       | Default return name |
| `- Effect1 PRM. -` | Effect parameter edit header |
| `Effect Recall`    | Effect recall action text |

---

## ISR / Panic Strings (confirmed ROM addresses)

| String | ROM address (v1.11) | Notes |
|--------|---------------------|-------|
| `"Bus Error"`        | via ISR stub at `0x000400` → `report_bus_error` `0x0000A5A4` | |
| `"Address Error"`    | via ISR stub → `report_address_error` `0x0000A5B8` | |
| `"Illegal Insn"`     | via ISR stub | |
| `"Zero Divide"`      | via ISR stub | |
| `"Fault from interrupt"` | `0x07FF00` | end-of-ROM fault string |
| `"Version 1.11  Date Mar.10.1994"` | `0x05059A` | version banner |

---

## Identifying MIDI Code in Disassembly

### Key addresses (v1.11)

| Address | Name | Notes |
|---------|------|-------|
| `0x000122E0` | `midi_tx_byte` | Writes one byte to SC0BUF |
| `0x000128EC` | `midi_process_rx` | MIDI message parsing core |
| `0x054FEE` | CC parameter table | 671 params × 16 banks × 96 CCs |
| `0xFFFC00` | `SC0BUF` (TMP68301) | UART data register |

### Recognition patterns

**Program Change handler** — look for:
- Read of a byte in range `0xC0–0xCF` from SC0BUF
- AND with `0x0F` to extract channel
- Table lookup into scene-to-PGM assignment table
- Call to `scene_recall` at `0x00013454`

**Control Change handler** — look for:
- Read of `0xBn` status, then CC number (may be `0x62` for NRPN LSB = bank select)
- Table dispatch based on bank × 96 + CC
- Write to parameter in edit buffer

**Note On handler (undocumented)** — look for:
- Compare of received status byte against `0x90`–`0x9F`
- Note number used as parameter index (0–205, covering 206 DMP7-style params)
- Velocity used as parameter value
- If found: this is a significant discovery — DMP7 backward-compatible control mode

**Pitch Bend handler (undocumented)** — look for:
- Compare of received status byte against `0xE0`–`0xEF`
- 14-bit value assembled from two data bytes (LSB first)
- Write to pitch effect parameter in EF1 or EF2 register

**Bulk Dump Tx** — look for:
- `0xF0` `0x43` header bytes sent via `midi_tx_byte`
- Loop over scene memory / setup data structure

**Bulk Dump Rx** — look for:
- `0xF0` `0x43` receive detection in `midi_process_rx`
- sub_status byte compared against the documented set (`0x75`, `0x7B`–`0x7F`)
- Any additional compare values indicate undocumented sub_status handlers
- Data buffering and checksum validation before writing to memory

---

*Derived from DMP9 Owner's Guide (DMP9E_2_ocr.pdf, pp. 74–82),*
*yamaha_dmp9-8-dmp9-16_mixer_user_manual.pdf (pp. 47–52),*
*and DMP7 Owner's Guide (for predecessor MIDI architecture comparison)*
