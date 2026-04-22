#!/usr/bin/env python3
"""
dmp9_emu.py - Yamaha DMP9/16 behavioral stub emulator

Uses Unicorn Engine for 68000 CPU emulation with memory-mapped I/O
callbacks for LCD, MIDI, DSP, and panel registers.

Usage:
    python3 dmp9_emu.py --rom path/to/rom.bin [--trace] [--script script.py]
"""

import argparse
import sys
from unicorn import *
from unicorn.m68k_const import *

# Memory map
ROM_BASE   = 0x00000000
ROM_SIZE   = 0x00080000  # 512KB
SRAM_BASE  = 0x00400000
SRAM_SIZE  = 0x00010000  # 64KB
EF1_BASE   = 0x00460000
EF1_SIZE   = 0x00001000
EF2_BASE   = 0x00470000
EF2_SIZE   = 0x00001000
LCD_BASE   = 0x00490000
LCD_SIZE   = 0x00000010
SFR_BASE   = 0x00FFFC00
SFR_SIZE   = 0x00000040

# TMP68301 UART offsets (byte-addressed)
MFP_TSR_OFF = 0x2D
MFP_UDR_OFF = 0x2F
MFP_RSR_OFF = 0x2B

class DMP9Emulator:
    def __init__(self, rom_path, trace=False):
        self.trace = trace
        self.lcd_row = 0
        self.lcd_col = 0
        self.lcd_buf = [['.' ] * 40 for _ in range(2)]
        self.midi_rx_queue = []

        with open(rom_path, 'rb') as f:
            self.rom = f.read()
        if len(self.rom) != ROM_SIZE:
            print(f"[WARN] ROM size {len(self.rom)} != expected {ROM_SIZE}")

        self.uc = Uc(UC_ARCH_M68K, UC_MODE_BIG_ENDIAN)
        self._map_memory()
        self._install_hooks()

        # Read reset vector from ROM (offset 4, 4 bytes, big-endian)
        entry = int.from_bytes(self.rom[4:8], 'big')
        sp    = int.from_bytes(self.rom[0:4], 'big')
        print(f"[EMU] ROM loaded: {rom_path}")
        print(f"[EMU] Reset vector: PC=0x{entry:08X}  SP=0x{sp:08X}")
        self.uc.reg_write(UC_M68K_REG_PC, entry)
        self.uc.reg_write(UC_M68K_REG_A7, sp)

    def _map_memory(self):
        self.uc.mem_map(ROM_BASE,  ROM_SIZE,  UC_PROT_READ | UC_PROT_EXEC)
        self.uc.mem_map(SRAM_BASE, SRAM_SIZE, UC_PROT_ALL)
        self.uc.mem_map(EF1_BASE,  EF1_SIZE,  UC_PROT_ALL)
        self.uc.mem_map(EF2_BASE,  EF2_SIZE,  UC_PROT_ALL)
        self.uc.mem_map(LCD_BASE,  LCD_SIZE,  UC_PROT_ALL)
        self.uc.mem_map(SFR_BASE,  SFR_SIZE,  UC_PROT_ALL)
        self.uc.mem_write(ROM_BASE, self.rom)
        # TMP68301 TSR: always report TX buffer empty (0x80)
        self.uc.mem_write(SFR_BASE + MFP_TSR_OFF, bytes([0x80]))

    def _install_hooks(self):
        self.uc.hook_add(UC_HOOK_MEM_WRITE, self._on_mem_write)
        self.uc.hook_add(UC_HOOK_MEM_READ_AFTER, self._on_mem_read)
        if self.trace:
            self.uc.hook_add(UC_HOOK_CODE, self._on_insn)
        self.uc.hook_add(UC_HOOK_MEM_INVALID, self._on_invalid)

    def _on_insn(self, uc, address, size, user_data):
        print(f"  TRACE 0x{address:08X}")

    def _on_mem_write(self, uc, access, address, size, value, user_data):
        if LCD_BASE <= address < LCD_BASE + LCD_SIZE:
            self._handle_lcd_write(address - LCD_BASE, value & 0xFF)
        elif SFR_BASE <= address < SFR_BASE + SFR_SIZE:
            self._handle_sfr_write(address - SFR_BASE, value & 0xFF)
        elif EF1_BASE <= address < EF1_BASE + EF1_SIZE:
            print(f"[EF1] write @+0x{address-EF1_BASE:04X} = 0x{value:04X}")
        elif EF2_BASE <= address < EF2_BASE + EF2_SIZE:
            print(f"[EF2] write @+0x{address-EF2_BASE:04X} = 0x{value:04X}")

    def _on_mem_read(self, uc, access, address, size, value, user_data):
        if SFR_BASE <= address < SFR_BASE + SFR_SIZE:
            off = address - SFR_BASE
            if off == MFP_RSR_OFF and self.midi_rx_queue:
                uc.mem_write(address, bytes([0x80]))  # buffer full
            elif off == MFP_UDR_OFF and self.midi_rx_queue:
                byte = self.midi_rx_queue.pop(0)
                uc.mem_write(address, bytes([byte]))
                print(f"[MIDI RX] 0x{byte:02X}")

    def _handle_lcd_write(self, offset, value):
        if offset == 0:  # command
            if value == 0x01:
                print("[LCD] CLEAR")
                self.lcd_buf = [['.' ] * 40 for _ in range(2)]
            elif value & 0xC0 == 0x80:  # DDRAM address set
                addr = value & 0x3F
                self.lcd_row = 1 if addr >= 0x40 else 0
                self.lcd_col = addr - (0x40 if self.lcd_row else 0)
        else:  # data
            ch = chr(value) if 0x20 <= value <= 0x7E else '.'
            if self.lcd_row < 2 and self.lcd_col < 40:
                self.lcd_buf[self.lcd_row][self.lcd_col] = ch
                self.lcd_col += 1
            print(f"[LCD] row={self.lcd_row} col={self.lcd_col-1} <- '{ch}'")
            print(f"[LCD] display: {''.join(self.lcd_buf[0])}")
            print(f"[LCD]          {''.join(self.lcd_buf[1])}")

    def _handle_sfr_write(self, offset, value):
        if offset == MFP_UDR_OFF:
            print(f"[MIDI TX] 0x{value:02X}")
        else:
            print(f"[SFR] write +0x{offset:02X} = 0x{value:02X}")

    def _on_invalid(self, uc, access, address, size, value, user_data):
        pc = uc.reg_read(UC_M68K_REG_PC)
        print(f"[ERR] Invalid memory access at 0x{address:08X} (PC=0x{pc:08X})")
        return False

    def inject_midi(self, byte):
        """Inject a MIDI byte into the RX queue."""
        self.midi_rx_queue.append(byte)

    def run(self, max_insn=0):
        try:
            print("[EMU] Starting execution...")
            pc = self.uc.reg_read(UC_M68K_REG_PC)
            self.uc.emu_start(pc, ROM_BASE + ROM_SIZE,
                              count=max_insn if max_insn else 0)
        except UcError as e:
            pc = self.uc.reg_read(UC_M68K_REG_PC)
            print(f"[EMU] Stopped at PC=0x{pc:08X}: {e}")


def main():
    parser = argparse.ArgumentParser(description='DMP9/16 stub emulator')
    parser.add_argument('--rom',    required=True, help='ROM binary path')
    parser.add_argument('--trace',  action='store_true', help='Trace every instruction')
    parser.add_argument('--insn',   type=int, default=100000, help='Max instructions (0=unlimited)')
    args = parser.parse_args()

    emu = DMP9Emulator(args.rom, trace=args.trace)
    emu.run(max_insn=args.insn)


if __name__ == '__main__':
    main()
