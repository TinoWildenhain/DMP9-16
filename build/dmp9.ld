/**
 * dmp9.ld — GNU LD linker script for DMP9/16 firmware reconstruction
 *
 * Memory map (confirmed from ROM analysis):
 *
 *   ROM  0x000000–0x07FFFF   512 KB (TMS27C240-10)
 *   DRAM 0x400000–0x40FFFF   64 KB  (CPU stack + DSP delay buffer)
 *
 * Placement constraints:
 *   - Vector table must start at ROM base (0x000000)
 *   - Hardware I/O addresses are NOT in ROM/RAM — they are volatile pointers
 *     in source code, no linker placement needed
 *   - Data/coefficient tables (0x059B00+) are ROM — .rodata.dsp_tables
 *   - Version string must land at 0x05059A (per all three ROM versions)
 *   - BSS/data sections go in DRAM (0x400000+)
 *   - Stack runs downward from 0x0040FED0 (set in vector table vec[0])
 *
 * Reconstruction notes:
 *   - Code section addresses may differ from original ROM (recompile)
 *   - Hardware register access addresses are preserved via volatile pointers
 *   - Vector table entries may differ (recompile) except hardware I/O refs
 */

OUTPUT_FORMAT("elf32-m68k")
OUTPUT_ARCH(m68k:68000)
ENTRY(_reset_handler)

/* =========================================================================
 * Memory regions
 * ========================================================================= */
MEMORY
{
    /* ROM: 512 KB at base 0                                               */
    ROM   (rx)  : ORIGIN = 0x000000, LENGTH = 512K

    /* DRAM: 64 KB for CPU stack and runtime data                          */
    DRAM  (rwx) : ORIGIN = 0x400000, LENGTH = 64K
}

/* =========================================================================
 * Sections
 * ========================================================================= */
SECTIONS
{
    /* --------------------------------------------------------------------- */
    /* Exception vector table — MUST be at ROM base                          */
    /* --------------------------------------------------------------------- */
    .vectors 0x000000 :
    {
        KEEP(*(.vectors))
    } > ROM

    /* --------------------------------------------------------------------- */
    /* ISR stubs — immediately after vector table                            */
    /* --------------------------------------------------------------------- */
    .isr_stubs :
    {
        *(.isr_stubs)
    } > ROM

    /* --------------------------------------------------------------------- */
    /* Init / startup code                                                   */
    /* --------------------------------------------------------------------- */
    .startup :
    {
        *(.startup)
        *(.startup.*)
    } > ROM

    /* --------------------------------------------------------------------- */
    /* Main text (code)                                                      */
    /* --------------------------------------------------------------------- */
    .text :
    {
        *(.text)
        *(.text.*)
        *(.gnu.linkonce.t.*)
    } > ROM

    /* --------------------------------------------------------------------- */
    /* Read-only data                                                         */
    /* --------------------------------------------------------------------- */
    .rodata :
    {
        *(.rodata)
        *(.rodata.*)
        *(.gnu.linkonce.r.*)
    } > ROM

    /* --------------------------------------------------------------------- */
    /* Version string — fixed address 0x05059A                               */
    /* All three firmware versions place version banner here.                */
    /* --------------------------------------------------------------------- */
    .version_string 0x05059A :
    {
        KEEP(*(.version_string))
        . = ALIGN(2);
    } > ROM

    /* --------------------------------------------------------------------- */
    /* String / parameter tables — ROM 0x050000–0x059A54                     */
    /* Placed after version string; let linker fill .rodata.strings          */
    /* --------------------------------------------------------------------- */
    .strings :
    {
        *(.rodata.strings)
    } > ROM

    /* --------------------------------------------------------------------- */
    /* DSP coefficient tables — fixed at 0x059B00                            */
    /* IDENTICAL across all three ROM versions — extract once and share.     */
    /* --------------------------------------------------------------------- */
    .dsp_tables 0x059B00 :
    {
        KEEP(*(.rodata.dsp_tables))
        . = ALIGN(2);
    } > ROM

    /* --------------------------------------------------------------------- */
    /* Fault strings — near end of ROM (0x07FF00+)                           */
    /* --------------------------------------------------------------------- */
    .fault_strings 0x07FF00 :
    {
        KEEP(*(.rodata.fault_strings))
    } > ROM

    /* --------------------------------------------------------------------- */
    /* Data initialisation image (ROM side)                                  */
    /* --------------------------------------------------------------------- */
    _data_load = LOADADDR(.data);

    .data :
    {
        _data_start = .;
        *(.data)
        *(.data.*)
        _data_end = .;
    } > DRAM AT > ROM

    /* --------------------------------------------------------------------- */
    /* BSS (zero-initialised RAM)                                            */
    /* Must be cleared by startup code before main_loop().                  */
    /* --------------------------------------------------------------------- */
    .bss (NOLOAD) :
    {
        _bss_start = .;
        *(.bss)
        *(.bss.*)
        *(COMMON)
        _bss_end = .;
    } > DRAM

    /* --------------------------------------------------------------------- */
    /* Stack — grows downward from 0x0040FED0                                */
    /* Defined symbolically; actual SP set in vector table vec[0].           */
    /* --------------------------------------------------------------------- */
    _stack_top = 0x0040FED0;

    /* --------------------------------------------------------------------- */
    /* Discard                                                                */
    /* --------------------------------------------------------------------- */
    /DISCARD/ :
    {
        *(.comment)
        *(.note.*)
        *(.eh_frame)
        *(.debug_*)
    }

    /* --------------------------------------------------------------------- */
    /* Diagnostic symbols                                                    */
    /* --------------------------------------------------------------------- */
    _rom_size   = SIZEOF(.vectors) + SIZEOF(.isr_stubs) + SIZEOF(.startup)
                + SIZEOF(.text) + SIZEOF(.rodata);
    _dram_use   = SIZEOF(.data) + SIZEOF(.bss);
}
