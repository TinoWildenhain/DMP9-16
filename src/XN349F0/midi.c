/**
 * midi.c — MIDI I/O via TMP68301 SC0/SC1 serial channels
 * XN349F0 (v1.10, 1994-01-20)
 *
 * TMP68301 SC0 is typically used for MIDI IN/OUT (31.25 kBaud).
 * SC1 may be used for panel/diagnostic UART or a second MIDI port.
 *
 * TODO: Confirm channel assignment from Ghidra analysis.
 */

#include <stdint.h>
#include "../hw/tmp68301.h"
#include "../hw/midi.h"

void midi_init(void)
{
    /*
     * TODO: Configure SC0CR/SC1CR for 31.25 kBaud, 8N1, MIDI framing.
     * Reference: TMP68301 datasheet, serial channel control register.
     */
}

void midi_send(uint8_t byte)
{
    /* TODO: Write to SC0BUF when TxRDY set in SC0SR */
    volatile tmp68301_sc_t *sc0 = (volatile tmp68301_sc_t *)TMP68301_SC0_BASE;
    (void)sc0;
}

int midi_recv(uint8_t *byte)
{
    /* TODO: Read SC0BUF when RxRDY set in SC0SR */
    return 0;
}
