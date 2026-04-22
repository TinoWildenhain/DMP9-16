#!/usr/bin/env python3
"""
inject_example.py - Example script showing how to inject
panel events and MIDI bytes into the DMP9 stub emulator.

Run the emulator in a thread and inject events from this script.
"""

import threading
import time
import sys
sys.path.insert(0, '..')
from dmp9_emu import DMP9Emulator

ROM_PATH = '../../roms/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin'

def run_emu(emu):
    emu.run(max_insn=0)

if __name__ == '__main__':
    emu = DMP9Emulator(ROM_PATH)

    t = threading.Thread(target=run_emu, args=(emu,), daemon=True)
    t.start()

    time.sleep(0.5)

    # Inject a MIDI Note On (channel 1, note 60, velocity 100)
    print('[INJECT] MIDI Note On C4')
    emu.inject_midi(0x90)  # Note On ch1
    emu.inject_midi(0x3C)  # note 60 = C4
    emu.inject_midi(0x64)  # velocity 100

    time.sleep(0.5)

    # Inject a MIDI Program Change
    print('[INJECT] MIDI Program Change 5')
    emu.inject_midi(0xC0)
    emu.inject_midi(0x05)

    t.join(timeout=5)
