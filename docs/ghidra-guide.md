# Ghidra analysis guide

## Import

1. Ghidra -> New Project
2. File -> Import File -> select your `.bin`
3. Language: `68000:BE:32:default` (Motorola 68000, Big Endian)
4. Click Options -> set Base Address to `0x00000000`
5. Repeat for all three ROM images in the same project

## Run DMP9_Setup.java

1. CodeBrowser -> Window -> Script Manager
2. Click the script folder button -> add the directory containing `DMP9_Setup.java`
3. Double-click `DMP9_Setup` -> watch the console output

The script creates:
- 5 memory blocks (SRAM, TMP68301 SFR, DSP EF1, DSP EF2, LCD)
- ~60 register labels with descriptive comments
- ~50 known RAM variable labels
- ~40 function labels at confirmed addresses

## Auto-analysis

1. Analysis -> Auto Analyze
2. Enable **Decompiler Parameter ID**
3. Run analysis

## Verify entry point

- Press `G`, go to `0x3B3F0`
- Confirm `MOVEW #0x2700,SR` as first instruction
- This disables interrupts at startup — the expected 68000 reset handler pattern

## Locate MIDI ISR

- Cross-reference `MFP_UDR` at `0xFFFC0F`
- This is the UART data register; all reads/writes are MIDI bytes
- Follow references to locate the MIDI receive interrupt service routine

## Version Tracking

Used to propagate markup from v1.11 to older versions:

1. Tools -> Version Tracking
2. Source program: v1.11
3. Destination program: v1.10 (repeat for v1.02)
4. Run correlators: Exact Function Instructions, Bulk Basic Block
5. Review matches — accept only high-confidence ones manually
6. Apply accepted matches to transfer names and comments

See also: https://www.lrqa.com/en/cyber-labs/version-tracking-in-ghidra/

## Useful shortcuts

| Key | Action |
|---|---|
| `G` | Go to address |
| `X` | Cross-references |
| `F` | Search for text |
| `L` | Rename label |
| `;` | Add comment |
| `D` | Disassemble at cursor |
| `C` | Clear code |

## Known function addresses (v1.11)

| Address | Name | Notes |
|---|---|---|
| `0x0003B3F0` | `reset_handler` | Entry point |
| `0x0000238C` | `lcd_write_cmd` | Write LCD command |
| `0x000023DC` | `lcd_write_data` | Write LCD data byte |
| `0x0000274E` | `dsp_ef1_write` | Write EF1 DSP register |
| `0x00002904` | `dsp_ef2_write` | Write EF2 DSP register |
| `0x000122E0` | `midi_tx_byte` | Transmit one MIDI byte |
| `0x000128EC` | `midi_process_rx` | MIDI message processing core |
| `0x00013454` | `scene_recall` | Recall scene from memory |
| `0x00013186` | `scene_store` | Store scene to memory |
