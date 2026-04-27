/*
 * dmp9_types.h - Ghidra C Header Parser input
 *
 * Import via: File -> Parse C Source...
 * Add this file, then click Parse to Current Program.
 *
 * Defines structs and enums for the Yamaha DMP9/16 firmware
 * that Ghidra cannot infer automatically.
 *
 * RE_NOTE: All layouts verified against ROM disassembly where marked [V].
 * Unmarked items are inferred from access patterns -- treat with caution.
 */

#ifndef DMP9_TYPES_H
#define DMP9_TYPES_H

/* ----------------------------------------------------------------
 * TMP68301 SFR (Supervisor Frame Register block)
 * Base address: 0x00FFFC00
 * All registers are byte-wide at odd offsets.
 * ---------------------------------------------------------------- */
typedef struct {
    unsigned char pad00;
    unsigned char GPDR;    /* GPIO data                        [V] */
    unsigned char pad02;
    unsigned char DDR;     /* GPIO direction                   [V] */
    unsigned char pad04;
    unsigned char IERA;    /* IRQ enable A                     [V] */
    unsigned char pad06;
    unsigned char IERB;    /* IRQ enable B                         */
    unsigned char pad08;
    unsigned char IPRA;    /* IRQ pending A                        */
    unsigned char pad0A;
    unsigned char IPRB;    /* IRQ pending B                        */
    unsigned char pad0C;
    unsigned char ISRA;    /* IRQ in-service A                     */
    unsigned char pad0E;
    unsigned char ISRB;    /* IRQ in-service B                     */
    unsigned char pad10;
    unsigned char IMRA;    /* IRQ mask A                           */
    unsigned char pad12;
    unsigned char IMRB;    /* IRQ mask B                           */
    unsigned char pad14;
    unsigned char VR;      /* MFP vector base register             */
    unsigned char pad16;
    unsigned char GPIP;    /* GPIO input                           */
    unsigned char pad18;
    unsigned char TACR;    /* Timer A control                      */
    unsigned char pad1A;
    unsigned char TBCR;    /* Timer B control                      */
    unsigned char pad1C;
    unsigned char TCDCR;   /* Timer C+D control                    */
    unsigned char pad1E;
    unsigned char TADR;    /* Timer A data                         */
    unsigned char pad20;
    unsigned char TBDR;    /* Timer B data                         */
    unsigned char pad22;
    unsigned char TCDR;    /* Timer C data                         */
    unsigned char pad24;
    unsigned char TDDR;    /* Timer D data                         */
    unsigned char pad26;
    unsigned char SCR;     /* Sync char register                   */
    unsigned char pad28;
    unsigned char UCR;     /* UART control (MIDI 31250 baud)    [V] */
    unsigned char pad2A;
    unsigned char RSR;     /* RX status                        [V] */
    unsigned char pad2C;
    unsigned char TSR;     /* TX status                        [V] */
    unsigned char pad2E;
    unsigned char UDR;     /* UART data register (MIDI byte)   [V] */
} TMP68301_SFR;

/* ----------------------------------------------------------------
 * M68K / TMP68301 exception vector table
 * Base address: 0x00000000, size = 96 × 4 = 0x180 bytes
 *
 * Slot 0 (initial_ssp) is a 32-bit raw stack address — NOT a code pointer.
 * Slots 1..95 are 32-bit function pointers.  Typing them as a function
 * pointer (void (*)(void)) makes Ghidra resolve each slot to the symbol
 * of the function it points to instead of showing a raw hex value.
 *
 * Entries 64–79 are TMP68301 MFP vectors (base = IVNR, typically 0x40).
 * ---------------------------------------------------------------- */
/* Use local names to avoid clashing with <stdint.h> via shared headers. */
typedef unsigned int  vector_word_t;   /* 32-bit raw stack address (initial SSP)        */
typedef void (*isr_func_t)(void);      /* 32-bit code pointer — Ghidra resolves to symbol */

typedef struct {
    vector_word_t initial_ssp;       /* [0]  Initial Supervisor Stack Pointer (raw value) */
    isr_func_t initial_pc;           /* [1]  Reset entry point                */
    isr_func_t bus_error;            /* [2]  */
    isr_func_t address_error;        /* [3]  */
    isr_func_t illegal_insn;         /* [4]  */
    isr_func_t zero_divide;          /* [5]  */
    isr_func_t chk_insn;             /* [6]  */
    isr_func_t trapv_insn;           /* [7]  */
    isr_func_t privilege_violation;  /* [8]  */
    isr_func_t trace;                /* [9]  */
    isr_func_t line_a_emulator;      /* [10] Line-A emulator */
    isr_func_t line_f_emulator;      /* [11] Line-F emulator */
    isr_func_t reserved_12_23[12];   /* [12-23] reserved */
    isr_func_t spurious_irq;         /* [24] */
    isr_func_t autovector_l1;        /* [25] Level 1 IRQ autovector */
    isr_func_t autovector_l2;        /* [26] Level 2 IRQ autovector */
    isr_func_t autovector_l3;        /* [27] Level 3 IRQ autovector */
    isr_func_t autovector_l4;        /* [28] Level 4 IRQ autovector */
    isr_func_t autovector_l5;        /* [29] Level 5 IRQ autovector */
    isr_func_t autovector_l6;        /* [30] Level 6 IRQ autovector */
    isr_func_t autovector_l7;        /* [31] Level 7 NMI */
    isr_func_t trap_handlers[16];    /* [32-47] TRAP #0–15 */
    isr_func_t fp_reserved[8];       /* [48-55] FP exceptions (unused) */
    isr_func_t mmu_reserved[4];      /* [56-59] MMU exceptions (unused) */
    isr_func_t reserved_60_63[4];    /* [60-63] reserved */
    /* [64-79] TMP68301 MFP vectors (IVNR base = 0x40 → vec 64 = index 64) */
    isr_func_t mfp_vec64;            /* [64] Timer D */
    isr_func_t mfp_vec65;            /* [65] Timer C */
    isr_func_t mfp_vec66;            /* [66] GPIO 0 */
    isr_func_t mfp_vec67;            /* [67] GPIO 1 */
    isr_func_t mfp_vec68;            /* [68] SIO0 error */
    isr_func_t timer_housekeeping_isr; /* [69] Timer housekeeping ISR (0x6F0) */
    isr_func_t mfp_vec70;            /* [70] SIO0 special */
    isr_func_t mfp_vec71;            /* [71] SIO0 Tx */
    isr_func_t serial0_status_isr;   /* [72] SIO0 status / cause decode (0x7AE) */
    isr_func_t serial0_rx_isr;       /* [73] SIO0 MIDI Rx ring buffer (0x7EC) */
    isr_func_t serial0_tx_isr;       /* [74] SIO0 Tx feeder (0x836) */
    isr_func_t serial0_special_isr;  /* [75] SIO0 special/error (0x88A) */
    isr_func_t serial1_status_isr;   /* [76] SIO1 status / cause decode (0x8B4) */
    isr_func_t serial1_rx_isr;       /* [77] SIO1 Rx ring buffer (0x8F2) */
    isr_func_t serial1_tx_isr;       /* [78] SIO1 Tx feeder (0x93C) */
    isr_func_t serial1_special_isr;  /* [79] SIO1 special/error (0x990) */
    isr_func_t mfp_vec80_95[16];     /* [80-95] remaining user / TMP68301 vectors */
} M68K_VectorTable;

/* ----------------------------------------------------------------
 * Scene parameter block
 * RE_NOTE: layout inferred from scene_recall / scene_store access.
 * Verify field order and sizes against ROM disassembly.
 * ---------------------------------------------------------------- */
typedef struct {
    unsigned short  ef1_type;       /* Effect type (dsp_effect_type_t) */
    unsigned short  ef2_type;
    unsigned char   ef1_params[32]; /* EF1 parameter bytes */
    unsigned char   ef2_params[16]; /* EF2 parameter bytes */
    unsigned char   name[8];        /* Scene name (ASCII, space-padded) */
    unsigned short  checksum;       /* RE_NOTE: may or may not exist */
    unsigned char   reserved[6];
} SceneParams;   /* RE_NOTE: total size approx 72 bytes -- verify */

/* ----------------------------------------------------------------
 * DSP effect type enum
 * RE_NOTE: values inferred from effect init sequences in ROM.
 * ---------------------------------------------------------------- */
typedef enum {
    EFX_REVERB_HALL    = 0x00,
    EFX_REVERB_ROOM    = 0x01,
    EFX_REVERB_PLATE   = 0x02,
    EFX_REVERB_STAGE   = 0x03,
    EFX_DELAY_MONO     = 0x10,
    EFX_DELAY_STEREO   = 0x11,
    EFX_DELAY_PINGPONG = 0x12,
    EFX_CHORUS         = 0x20,
    EFX_FLANGER        = 0x21,
    EFX_PHASER         = 0x22,
    EFX_PITCH_SHIFT    = 0x30,
    EFX_EQ_PARAMETRIC  = 0x40,
} dsp_effect_type_t;

#endif /* DMP9_TYPES_H */
