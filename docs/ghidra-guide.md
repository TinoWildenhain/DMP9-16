# Ghidra Analysis Guide — DMP9/16

## Prerequisites

- Ghidra 11.x or **12.x** (12.0.4 recommended; must be a non-snap install)
- Custom language files installed (see `ghidra/lang/INSTALL.md`)
- Scripts directory: `ghidra/scripts/`

## Installing custom language files

```bash
LANG=$GHIDRAHOME/Ghidra/Processors/68000/data/languages
cp ghidra/lang/tmp68301.cspec  $LANG/
cp ghidra/lang/68000.ldefs     $LANG/
```

Restart Ghidra after copying. To verify the language is loaded:
- File → New Project → Import File
- Click **...** next to Language
- Search for "TMP68301" — it should appear in the list

## Import

1. Ghidra → New Project
2. File → Import File → select your `.bin`
3. Language: `68000:BE:32:TMP68301` (Motorola 68000, Big Endian, TMP68301 cspec)
   - If TMP68301 is not available, use `68000:BE:32:default` (then fix cspec manually)
4. Click **Options** → set Base Address to `0x00000000`
5. Repeat for all three ROM images in the same project

## Script execution order

Run scripts in this order for each ROM:

### 1. TMP68301_Setup.java

Labels all TMP68301 internal peripheral registers at `0xFFFC00–0xFFFC3F` with
descriptive names and comments from the datasheet. Creates memory block for SFR region.

### 2. DMP9_Board_Setup.java

- Creates memory blocks: DRAM (0x400000, 64K), DSP_EF1 (0x460000, 4K),
  DSP_EF2 (0x470000, 4K), LCD (0x490000, 2B)
- Labels all board-level I/O addresses
- Decodes HD44780 command bytes in existing references
- Labels version string, fault strings, DSP coefficient table region

### 3. DMP9_FuncFixup.java

Three-pass calling convention fixer:

**Pass 1:** Tags all vector table entries (ISRs as `__interrupt`, reset handler as `yamaha_reg noreturn`).
Stores ISR addresses in a protected set.

**Pass 2:** Scans all functions for prologue patterns:
- `LINK A6,#N` within first 6 instructions → `__stdcall`
- `MOVEM.L ...,-(SP)` within first 6 instructions → `__stdcall`
- Otherwise → `yamaha_reg`

**Pass 3:** Re-applies `__interrupt` to all ISR entries to prevent Pass 2 from overwriting them.

### 4. DMP9_LibMatch.java

Byte-pattern matcher for known library functions:
- `memcpy_b`, `memcpy_w`, `memcpy_l`
- `memset_b`, `memset_w`
- Other common 68000 library patterns

### 5. Auto-analysis

1. Analysis → Auto Analyze
2. Enable: **Decompiler Parameter ID** (critical for correct parameter recovery)
3. Keep defaults for all other analysers
4. Run analysis (takes several minutes per ROM)

## Verify entry point

- Press `G`, go to `0x0003BF4E` (v1.11 reset handler)
- Confirm `MOVE.W #0x2700,SR` as first instruction — disables all interrupts at startup
- This is the expected 68000 reset handler pattern

## Key confirmed addresses (v1.11 / XN349G0)

| Address | Name | Notes |
|---------|------|-------|
| `0x0003BF4E` | `reset_handler` | Entry point — via JMP thunk at 0x000400 |
| `0x0000238C` | `lcd_write_cmd` | HD44780 instruction write |
| `0x000023DC` | `lcd_write_data` | HD44780 data byte |
| `0x0000274E` | `dsp_ef1_write` | Write EF1 DSP register |
| `0x00002904` | `dsp_ef2_write` | Write EF2 DSP register |
| `0x00002AA6` | `uart_write` | Raw UART transmit |
| `0x0000A5A4` | `report_bus_error` | Prints "Bus Error" via UART, then halts |
| `0x0000A5B8` | `report_address_error` | |
| `0x0000A6A8` | `print_rjust16` | 16-char right-justified UART output |
| `0x000122E0` | `midi_tx_byte` | MIDI transmit via SC0 |
| `0x000128EC` | `midi_process_rx` | MIDI message parser |
| `0x00013186` | `scene_store` | Store scene to memory |
| `0x00013454` | `scene_recall` | Recall scene from memory |

## Locating MIDI code

- Cross-reference `SC0BUF` (TMP68301 UART data register, ~`0xFFFC0F`)
- All reads/writes are MIDI bytes
- Follow references to locate MIDI RX ISR and TX routines
- Search string `"--MIDI Setting--"` in Memory Search to find MIDI LCD handler

## Locating LCD code

- Cross-reference `LCD_CMD` (`0x490000`) and `LCD_DATA` (`0x490001`)
- `lcd_write_cmd` and `lcd_write_data` are the primary callers
- Search for string `"--MIDI Setting--"` or `"Bus Error"` to find string table start

## ISR pattern

All 68000 exception stubs at `0x000400–0x0007FF` follow a panic-halt pattern:

```asm
MOVEM.L {D0-D7/A0-A6},-(SP)
JSR     report_<error>
MOVEM.L (SP)+,{D0-D7/A0-A6}
STOP    #0x2700
RTE
```

These are intentional: all hardware exceptions are considered fatal and halt execution.

## Reading decompiler output

### __stdcall functions (C-compiled)

```c
void report_bus_error(void) {
    /* sets up local string "Bus Error" in stack frame via LINK A6 */
    print_rjust16("Bus Error");
}
```

### yamaha_reg functions (register-call)

Arguments arrive in D0, D1, D2, D3, A0, A1 without any LINK frame.
The decompiler may not infer parameters automatically — use cross-references
and context to determine call sites and argument types.

### __interrupt functions

No arguments, must end with RTE. Ghidra should generate:

```c
void __interrupt vec_bus_error_handler(void) {
    MOVEM save;
    report_bus_error();
    MOVEM restore;
    /* STOP — decompiler may show as inline asm */
}
```

## Version Tracking (propagating v1.11 markup to older ROMs)

1. Tools → Version Tracking
2. Source program: v1.11 (`XN349G0`)
3. Destination: v1.10 (`XN349F0`), then v1.02 (`XN349E0`)
4. Run correlators: **Exact Function Instructions**, **Bulk Basic Block**
5. Review matches — accept only high-confidence ones manually
6. Apply accepted matches to transfer names and comments

Note: v1.02 → v1.10 is a full recompile (52.8% changed); Version Tracking will have
many false positives. Review carefully.

## Headless (CI) operation

See `ghidra/HEADLESS.md` and `ghidra/scripts/run_analysis.sh`.

## API notes (Ghidra 11.x / 12.x)

| Issue | Correct usage |
|-------|--------------|
| `setComment` argument order | `listing.setComment(addr, CommentType, str)` — Address FIRST |
| `getComment` argument order | `listing.getComment(CommentType, addr)` — CommentType FIRST |
| CommentType enum | `CommentType.PLATE` not `CodeUnit.PLATE_COMMENT` |
| cspec prototype | `<input/>` not `<void/>` for functions with no args |
| FlatProgramAPI conflict | Rename `writePlateComment` → custom name to avoid collision |

## Useful shortcuts

| Key | Action |
|-----|--------|
| `G` | Go to address |
| `X` | Cross-references |
| `F` | Search for text |
| `L` | Rename label |
| `;` | Add comment |
| `D` | Disassemble at cursor |
| `C` | Clear code |
| `P` | Create function at cursor |
