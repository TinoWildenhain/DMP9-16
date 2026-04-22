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
 * M68K 256-entry vector table
 * Base address: 0x00000000
 * ---------------------------------------------------------------- */
typedef struct {
    void *initial_ssp;          /* [0]  Initial Supervisor Stack Pointer */
    void *initial_pc;           /* [1]  Reset entry point                */
    void *bus_error;            /* [2]  */
    void *address_error;        /* [3]  */
    void *illegal_insn;         /* [4]  */
    void *zero_divide;          /* [5]  */
    void *chk;                  /* [6]  */
    void *trapv;                /* [7]  */
    void *privilege;            /* [8]  */
    void *trace;                /* [9]  */
    void *line_a;               /* [10] Line-A emulator */
    void *line_f;               /* [11] Line-F emulator */
    void *reserved_12_23[12];   /* [12-23] reserved */
    void *spurious_irq;         /* [24] */
    void *autovector_l1;        /* [25] Level 1 */
    void *autovector_l2;        /* [26] Level 2 */
    void *autovector_l3;        /* [27] Level 3 */
    void *autovector_l4;        /* [28] Level 4 */
    void *autovector_l5;        /* [29] Level 5 */
    void *autovector_l6;        /* [30] Level 6 */
    void *autovector_l7;        /* [31] NMI */
    void *trap[16];             /* [32-47] TRAP #0-15 */
    void *fp_reserved[8];       /* [48-55] FP exceptions */
    void *mmu_reserved[4];      /* [56-59] MMU exceptions */
    void *reserved_60_63[4];    /* [60-63] reserved */
    /* [64-79] TMP68301 MFP vectors (base = VR register, typically 0x40) */
    void *mfp_timer_d;          /* [64] */
    void *mfp_timer_c;          /* [65] */
    void *mfp_gpio_0;           /* [66] */
    void *mfp_gpio_1;           /* [67] */
    void *mfp_acia_rx_err;      /* [68] */
    void *mfp_acia_rx;          /* [69] MIDI receive ISR */
    void *mfp_acia_tx_err;      /* [70] */
    void *mfp_acia_tx;          /* [71] MIDI transmit ISR */
    void *mfp_timer_b;          /* [72] */
    void *mfp_gpio_2_5[4];      /* [73-76] */
    void *mfp_timer_a;          /* [77] */
    void *mfp_mono_detect;      /* [78] */
    void *user_vecs[177];       /* [79-255] remaining user vectors */
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
