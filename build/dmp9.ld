/*
 * dmp9.ld - Linker script for Yamaha DMP9/16 firmware
 *
 * ROM:  0x00000000 - 0x0007FFFF (512KB, TMS27C240)
 * SRAM: 0x00400000 - 0x0040FFFF (64KB)
 *
 * RE_NOTE: Section boundaries approximate until full
 * memory map is confirmed from ROM disassembly.
 */

OUTPUT_FORMAT("binary")
OUTPUT_ARCH(m68k)
ENTRY(reset_handler)

MEMORY
{
    ROM  (rx)  : ORIGIN = 0x00000000, LENGTH = 512K
    SRAM (rwx) : ORIGIN = 0x00400000, LENGTH = 64K
}

SECTIONS
{
    .vectors 0x00000000 :
    {
        KEEP(*(.vectors))
    } > ROM

    .text :
    {
        *(.text .text.*)
        *(.rodata .rodata.*)
    } > ROM

    .data :
    {
        *(.data .data.*)
    } > SRAM AT > ROM

    .bss :
    {
        *(.bss .bss.*)
        *(COMMON)
    } > SRAM

    /DISCARD/ :
    {
        *(.comment)
        *(.note*)
    }
}
