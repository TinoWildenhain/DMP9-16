/**
 * midi.h — MIDI I/O definitions for Yamaha DMP9/16
 *
 * MIDI is handled through the TMP68301 on-chip serial channels SC0/SC1.
 * Standard MIDI: 31.25 kBaud, 8 data bits, no parity, 1 stop bit.
 *
 * SC0 is assumed to be MIDI IN/OUT (to be confirmed from Ghidra analysis).
 * SC1 may be a second MIDI port or diagnostic UART.
 *
 * Register access is via tmp68301.h (SC0BUF, SC0CR, SC0SR etc. at 0xFFFC00+).
 */

#ifndef DMP9_MIDI_H
#define DMP9_MIDI_H

#include <stdint.h>

/* -------------------------------------------------------------------------
 * MIDI status bytes (standard)
 * ------------------------------------------------------------------------- */
#define MIDI_NOTE_OFF         0x80
#define MIDI_NOTE_ON          0x90
#define MIDI_KEY_PRESSURE     0xA0
#define MIDI_CONTROL_CHANGE   0xB0
#define MIDI_PROGRAM_CHANGE   0xC0
#define MIDI_CHANNEL_PRESSURE 0xD0
#define MIDI_PITCH_BEND       0xE0
#define MIDI_SYSEX_START      0xF0
#define MIDI_SYSEX_END        0xF7
#define MIDI_CLOCK            0xF8
#define MIDI_ACTIVE_SENSE     0xFE
#define MIDI_RESET            0xFF

/* -------------------------------------------------------------------------
 * Yamaha SysEx manufacturer ID
 * ------------------------------------------------------------------------- */
#define MIDI_YAMAHA_MFR_ID    0x43

/* -------------------------------------------------------------------------
 * MIDI buffer sizes (must match DRAM layout — verify from Ghidra)
 * ------------------------------------------------------------------------- */
#define MIDI_RX_BUF_SIZE      256
#define MIDI_TX_BUF_SIZE      256

/* -------------------------------------------------------------------------
 * Driver API
 * ------------------------------------------------------------------------- */
void     midi_init(void);
void     midi_send(uint8_t byte);
int      midi_recv(uint8_t *byte);   /**< Returns 1 if byte available, 0 otherwise */
void     midi_send_sysex(const uint8_t *data, uint16_t len);

#endif /* DMP9_MIDI_H */
