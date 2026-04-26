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
 * Each entry is a 32-bit absolute function pointer.
 * Entries 64–79 are TMP68301 MFP vectors (base = IVNR, typically 0x40).
 * ---------------------------------------------------------------- */
typedef unsigned int isr_ptr;   /* 32-bit function pointer for Ghidra parser */

typedef struct {
    isr_ptr initial_ssp;          /* [0]  Initial Supervisor Stack Pointer */
    isr_ptr initial_pc;           /* [1]  Reset entry point                */
    isr_ptr bus_error;            /* [2]  */
    isr_ptr address_error;        /* [3]  */
    isr_ptr illegal_insn;         /* [4]  */
    isr_ptr zero_divide;          /* [5]  */
    isr_ptr chk_insn;             /* [6]  */
    isr_ptr trapv_insn;           /* [7]  */
    isr_ptr privilege_violation;  /* [8]  */
    isr_ptr trace;                /* [9]  */
    isr_ptr line_a_emulator;      /* [10] Line-A emulator */
    isr_ptr line_f_emulator;      /* [11] Line-F emulator */
    isr_ptr reserved_12_23[12];   /* [12-23] reserved */
    isr_ptr spurious_irq;         /* [24] */
    isr_ptr autovector_l1;        /* [25] Level 1 IRQ autovector */
    isr_ptr autovector_l2;        /* [26] Level 2 IRQ autovector */
    isr_ptr autovector_l3;        /* [27] Level 3 IRQ autovector */
    isr_ptr autovector_l4;        /* [28] Level 4 IRQ autovector */
    isr_ptr autovector_l5;        /* [29] Level 5 IRQ autovector */
    isr_ptr autovector_l6;        /* [30] Level 6 IRQ autovector */
    isr_ptr autovector_l7;        /* [31] Level 7 NMI */
    isr_ptr trap_handlers[16];    /* [32-47] TRAP #0–15 */
    isr_ptr fp_reserved[8];       /* [48-55] FP exceptions (unused) */
    isr_ptr mmu_reserved[4];      /* [56-59] MMU exceptions (unused) */
    isr_ptr reserved_60_63[4];    /* [60-63] reserved */
    /* [64-79] TMP68301 MFP vectors (IVNR base = 0x40 → vec 64 = index 64) */
    isr_ptr mfp_vec64;            /* [64] Timer D */
    isr_ptr mfp_vec65;            /* [65] Timer C */
    isr_ptr mfp_vec66;            /* [66] GPIO 0 */
    isr_ptr mfp_vec67;            /* [67] GPIO 1 */
    isr_ptr mfp_vec68;            /* [68] SIO0 error */
    isr_ptr mfp_vec69_timer_isr;  /* [69] Timer housekeeping ISR (0x6F0) */
    isr_ptr mfp_vec70;            /* [70] SIO0 special */
    isr_ptr mfp_vec71;            /* [71] SIO0 Tx */
    isr_ptr mfp_vec72_sio0_stat;  /* [72] SIO0 status / cause decode (0x7AE) */
    isr_ptr mfp_vec73_sio0_rx;    /* [73] SIO0 MIDI Rx ring buffer (0x7EC) */
    isr_ptr mfp_vec74_sio0_tx;    /* [74] SIO0 Tx feeder (0x836) */
    isr_ptr mfp_vec75_sio0_spc;   /* [75] SIO0 special/error (0x88A) */
    isr_ptr mfp_vec76_sio1_stat;  /* [76] SIO1 status / cause decode (0x8B4) */
    isr_ptr mfp_vec77_sio1_rx;    /* [77] SIO1 Rx ring buffer (0x8F2) */
    isr_ptr mfp_vec78_sio1_tx;    /* [78] SIO1 Tx feeder (0x93C) */
    isr_ptr mfp_vec79_sio1_spc;   /* [79] SIO1 special/error (0x990) */
    isr_ptr user_vecs[16];        /* [80-95] remaining user / TMP68301 vectors */
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
