# TMP68301_Setup.java

**Purpose:** Maps TMP68301 Special Function Registers into Ghidra's address space.

## What it does

### Memory block creation
Creates a volatile, uninitialized memory block named `TMP68301_SFR` at `0xFFFC00–0xFFFFFF`
(512 bytes) if it doesn't already exist. This region is the TMP68301's on-chip peripheral
register space — it is NOT in the ROM binary. Marking it volatile suppresses Ghidra's
"no data" warnings and correctly models memory-mapped I/O behaviour.

### Register labelling and typing
For each TMP68301 SFR:
1. Creates a named label (e.g. `TMP68301_IMR`) at the register's address
2. Applies the correct data type: `ByteDataType` for 8-bit registers, `WordDataType` for
   16-bit registers

### Register groups covered
- **SIO channels 0/1/2:** SCBR, SMR0/1/2, SCR0/1/2, TDR/RDR byte registers, SSR
- **Timers 0/1/2:** TCR, timer counter registers
- **Parallel I/O:** PADR/PBDR/PCDR, PADDR/PBDDR/PCDDR direction registers
- **Bus controller:** AMELRx, AACR0/2, ATCR
- **Interrupt controller:** IMR, IPR, IISR, IVNR
- **System:** SCMR

### Notes on address range
The TMP68301 places its SFR at the top of the 68000 address space. Address decoding
on the DMP9/DMP16 hardware is done externally via logic circuits — the TMP's SFR
region is therefore always at `0xFFFC00` regardless of ROM placement.
