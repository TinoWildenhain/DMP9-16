# MIDI Reference — Yamaha DMP9 / DMP9-16

This document catalogues all MIDI message types, LCD strings, and protocol details
extracted from the DMP9 Owner's Guide and used to identify MIDI-handling code in the
firmware disassembly.

---

## Transport

- **Interface:** TMP68301 internal UART (SC0), SC0BUF register at `0xFFFC00` region
- **Baud rate:** 31250 baud, 8N1
- **Physical:** Standard MIDI DIN-5 IN / OUT / THRU
- **RX path:** SC0 receive interrupt → ISR → MIDI message parser
- **TX path:** `midi_tx_byte` at `0x000122E0` (v1.11)

---

## Message Types

The DMP9 uses three MIDI message types:

| Type | Status byte | Notes |
|------|-------------|-------|
| Program Change | `0xCn` (n = channel) | Scene memory recall/store |
| Control Change | `0xBn` | Parameter control (671 params, 16 banks × 96 CCs) |
| System Exclusive | `0xF0 0x43 …` | Bulk dump / request |

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

### Bulk Dump types

| Tag | Content |
|-----|---------|
| `ALL`   | All data types simultaneously |
| `MEM`   | Scene memory data (range 1–50 selectable for Tx; Rx always all) |
| `SETUP` | Setup data |
| `EDIT`  | Edit buffer data |
| `PGM`   | Scene → Program Change assignment table |
| `CTRL`  | Control Change → parameter assignment table |

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
| `0x0XFFFC00` | `SC0BUF` (TMP68301) | UART data register |

### Recognition patterns

**Program Change handler** — look for:
- Read of a byte in range `0xC0–0xCF` from SC0BUF
- AND with `0x0F` to extract channel
- Table lookup into scene-to-PGM assignment table
- Call to `scene_recall` at `0x00013454`

**Control Change handler** — look for:
- Read of `0xBn` status, then CC number (may be 0x62 for NRPN LSB = bank select)
- Table dispatch based on bank × 96 + CC
- Write to parameter in edit buffer

**Bulk Dump Tx** — look for:
- `0xF0` `0x43` header bytes sent via `midi_tx_byte`
- Loop over scene memory / setup data structure

**Bulk Dump Rx** — look for:
- `0xF0` `0x43` receive detection in `midi_process_rx`
- Data buffering and checksum validation before writing to memory

---

*Derived from DMP9 Owner's Guide (DMP9E_2_ocr.pdf, pp. 74–82) and*
*yamaha_dmp9-8-dmp9-16_mixer_user_manual.pdf, pp. 47–52*
