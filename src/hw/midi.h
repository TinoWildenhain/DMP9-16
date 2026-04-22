#ifndef MIDI_H
#define MIDI_H

/*
 * midi.h - MIDI interface via TMP68301 UART
 *
 * Baud rate: 31250
 * TX: MFP_UDR write
 * RX: interrupt-driven, MFP_UDR read in ISR
 */

#include <stdint.h>

/* MIDI status bytes */
#define MIDI_NOTE_OFF        0x80
#define MIDI_NOTE_ON         0x90
#define MIDI_CONTROL_CHANGE  0xB0
#define MIDI_PROGRAM_CHANGE  0xC0
#define MIDI_SYSEX_START     0xF0
#define MIDI_SYSEX_END       0xF7
#define MIDI_CLOCK           0xF8
#define MIDI_ACTIVE_SENSE    0xFE
#define MIDI_RESET           0xFF

/* Yamaha SysEx manufacturer ID */
#define YAMAHA_SYSEX_ID      0x43

void midi_init(void);
void midi_tx_byte(uint8_t byte);
void midi_process_rx(uint8_t byte);
uint8_t midi_rx_available(void);
uint8_t midi_rx_read(void);

#endif /* MIDI_H */
