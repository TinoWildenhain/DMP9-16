/**
 * strings.S — Version-specific UI strings and parameter tables
 * XN349E0 (v1.02, 1993-11-10)
 *
 * ROM region: 0x050000–0x059A54
 *
 * These strings are NOT identical across versions (unlike the coefficient
 * tables at 0x059B00+).  They must be extracted per-version from the ROM.
 *
 * TODO: Extract from ROM using:
 *   dd if=TMS27C240-10-DMP9-16-XN349E0-v1.02-10.11.1993.bin bs=1 skip=$((0x050000)) \
 *      count=$((0x059A54 - 0x050000)) of=strings_raw.bin
 *   Then use .incbin or transcribe identified strings.
 */

    .section .rodata.strings, "a", @progbits

    /* Version banner — placed at fixed address 0x05059A */
    .global firmware_version_banner
firmware_version_banner:
    .asciz  "Version 1.02"
    .balign 2

    /* TODO: Add UI strings, effect names, MIDI param strings */
    /* Use .incbin "strings_raw.bin" once extracted           */
