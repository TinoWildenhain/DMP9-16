/**
 * @file tmp68301_regs.h
 * @brief Toshiba TMP68301 / TMP68303 Internal Peripheral Register Definitions
 *
 * Covers all memory-mapped internal peripheral registers at base address
 * 0xFFFC00 (relocatable via ARELR).  Derived from:
 *   - Toshiba TMP68301AK datasheet (TMP68301AKF-8 / TMP68301AKFR-8)
 *   - MAME src/devices/cpu/m68000/tmp68301.cpp (Olivier Galibert, BSD-3-Clause)
 *
 * Register space layout (offsets from peripheral base, default 0xFFFC00):
 *   0x000–0x00F  Address Decoder (AMAR, AAMR, AACR, ATOR, ARELR)
 *   0x080–0x09F  Interrupt Controller (ICR, IMR, IPR, IISR, IVNR, IEIR)
 *   0x100–0x10F  Parallel Interface (PDIR, PCR, PSR, PCMR, PMR, PDR, PPR1/2)
 *   0x180–0x1AF  Serial Interface Ch0–Ch2 (SMR, SCMR, SBRR, SSR, SDR, SPR, SCR)
 *   0x200–0x25F  16-bit Timer/Counter Ch0–Ch2 (TCR, TMCR, TCTR)
 *
 * NOTE: The TMP68301 uses a 68000-style byte-addressed, word-aligned bus.
 * All registers are 8-bit or 16-bit; odd addresses hold the low byte.
 * Absolute addresses = (ARELR << 8) + offset; default ARELR = 0xFFFC.
 */

#ifndef TMP68301_REGS_H
#define TMP68301_REGS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Peripheral base address (default; relocatable via ARELR)
 * ========================================================================= */
#define TMP68301_BASE           0xFFFC00UL

/* Absolute address helper */
#define TMP68301_REG(off)       (TMP68301_BASE + (off))


/* =========================================================================
 * SECTION 1 – ADDRESS DECODER
 * Registers are 8-bit, located at odd/even bytes within 16-bit word slots.
 * ========================================================================= */

/** Memory Area Address Register 0 – sets bits [23:16] of CS0 base address */
#define TMP68301_AMAR0          TMP68301_REG(0x000)
/** Address Area Mask Register 0   – bits [7:2] mask 256K blocks; bits [1:0] extend */
#define TMP68301_AAMR0          TMP68301_REG(0x001)
/** Address Area Control Register 0 – CS0 enable, DTACK, wait states */
#define TMP68301_AACR0          TMP68301_REG(0x003)
/** Memory Area Address Register 1 – sets bits [23:16] of CS1 base address */
#define TMP68301_AMAR1          TMP68301_REG(0x004)
/** Address Area Mask Register 1   – same encoding as AAMR0 */
#define TMP68301_AAMR1          TMP68301_REG(0x005)
/** Address Area Control Register 1 – CS1 enable, DTACK, wait states */
#define TMP68301_AACR1          TMP68301_REG(0x007)
/** Address Area Control Register 2 – IACK cycle DTACK and wait states */
#define TMP68301_AACR2          TMP68301_REG(0x009)
/** Address Timeout Register – BERR bus timeout selection */
#define TMP68301_ATOR           TMP68301_REG(0x00B)
/** Address Remap / relocation Register – bits[15:2] = new base >> 8 */
#define TMP68301_ARELR          TMP68301_REG(0x00C)   /* 16-bit */


/* --- AAMR bitfield (Address Area Mask Register) -------------------------------- */
/**
 * @defgroup AAMR_BITS AAMR bitfield values
 * Bits [7:2]: 256K-block mask granularity (1=compare, 0=don't care)
 * Bit  [1]:   additionally mask 512-byte sub-block range
 * Bit  [0]:   additionally mask 256-byte sub-block range
 */
#define AAMR_MASK_256K(n)       ((n) << 2)   /**< n = 0x3F for 256KB resolution */
#define AAMR_SUB512             (1 << 1)     /**< extend masking to 512-byte sub-range */
#define AAMR_SUB256             (1 << 0)     /**< extend masking to 256-byte sub-range */


/* --- AACR0/1 bitfield (Address Area Control Register for CSx) ------------------ */
/**
 * @defgroup AACR01_BITS AACR0/AACR1 bitfield values (8-bit)
 * Bit  [5]:   CS output ENABLE (1=enabled, 0=disabled)
 * Bits [4:3]: DTACK source  (00=reserved, 01=internal, 10=external, 11=both)
 * Bits [2:0]: Wait states inserted (0–7)
 */
#define AACR_CS_ENABLE          (1 << 5)     /**< Chip-select output enabled */
#define AACR_DTACK_INTERNAL     (0x1 << 3)   /**< DTACK generated internally */
#define AACR_DTACK_EXTERNAL     (0x2 << 3)   /**< DTACK from external pin */
#define AACR_DTACK_BOTH         (0x3 << 3)   /**< DTACK internal OR external */
#define AACR_WAIT_MASK          0x07         /**< Wait state count mask */
#define AACR_WAIT(n)            ((n) & 0x07) /**< n = 0..7 wait states */

/* Reset values */
#define AACR0_RESET             0x3D         /**< CS0 reset: enabled, int DTACK, 5 waits */
#define AACR1_RESET             0x18         /**< CS1 reset: disabled, int DTACK, 0 waits */


/* --- AACR2 bitfield (IACK cycle control) --------------------------------------- */
/**
 * @defgroup AACR2_BITS AACR2 bitfield values (8-bit, bits [4:0] only)
 * Bits [4:3]: DTACK source for interrupt-acknowledge cycles
 * Bits [2:0]: Wait states for IACK cycles
 */
#define AACR2_DTACK_INTERNAL    (0x1 << 3)
#define AACR2_DTACK_EXTERNAL    (0x2 << 3)
#define AACR2_DTACK_BOTH        (0x3 << 3)
#define AACR2_WAIT_MASK         0x07


/* --- ATOR bitfield (Address Timeout Register) ---------------------------------- */
/**
 * @defgroup ATOR_BITS ATOR bitfield values (8-bit, bits [3:0])
 * Only one bit should be set at a time; highest set bit wins.
 * 0x00 = timeout disabled.
 */
#define ATOR_TIMEOUT_DIS        0x00         /**< Bus monitor disabled */
#define ATOR_TIMEOUT_32         0x01         /**< BERR after 32 clocks */
#define ATOR_TIMEOUT_64         0x02         /**< BERR after 64 clocks */
#define ATOR_TIMEOUT_128        0x04         /**< BERR after 128 clocks */
#define ATOR_TIMEOUT_256        0x08         /**< BERR after 256 clocks */
#define ATOR_RESET              0x08         /**< Reset value = 256 clocks */


/* --- ARELR (Register Relocation Register) ------------------------------------- */
/**
 * @defgroup ARELR_BITS ARELR (16-bit)
 * Bits [15:2]: new base address >> 8 (bits [1:0] are always 0)
 * Write new value to remap the entire peripheral block.
 * Default = 0xFFFC → base 0xFFFC00
 */
#define ARELR_RESET             0xFFFC       /**< Default: registers at 0xFFFC00 */
#define ARELR_BASE(arelr)       ((uint32_t)(arelr) << 8)  /**< Expand to absolute base */


/* =========================================================================
 * SECTION 2 – INTERRUPT CONTROLLER
 * ========================================================================= */

/* ICR0–ICR9: one byte per channel at odd addresses within 16-bit words */
#define TMP68301_ICR0           TMP68301_REG(0x081)  /**< Ext INT0 control register */
#define TMP68301_ICR1           TMP68301_REG(0x083)  /**< Ext INT1 control register */
#define TMP68301_ICR2           TMP68301_REG(0x085)  /**< Ext INT2 control register */
#define TMP68301_ICR3           TMP68301_REG(0x087)  /**< Serial ch0 interrupt control */
#define TMP68301_ICR4           TMP68301_REG(0x089)  /**< Serial ch1 interrupt control */
#define TMP68301_ICR5           TMP68301_REG(0x08B)  /**< Serial ch2 interrupt control */
#define TMP68301_ICR6           TMP68301_REG(0x08D)  /**< Parallel INT6 control (reserved in AK) */
#define TMP68301_ICR7           TMP68301_REG(0x08F)  /**< Timer ch0 interrupt control */
#define TMP68301_ICR8           TMP68301_REG(0x091)  /**< Timer ch1 interrupt control */
#define TMP68301_ICR9           TMP68301_REG(0x093)  /**< Timer ch2 interrupt control */
#define TMP68301_IMR            TMP68301_REG(0x094)  /**< Interrupt Mask Register (16-bit) */
#define TMP68301_IPR            TMP68301_REG(0x096)  /**< Interrupt Pending Register (16-bit) */
#define TMP68301_IISR           TMP68301_REG(0x098)  /**< Interrupt In-Service Register (16-bit) */
#define TMP68301_IVNR           TMP68301_REG(0x09B)  /**< Interrupt Vector Number Register (8-bit) */
#define TMP68301_IEIR           TMP68301_REG(0x09D)  /**< Expansion Interrupt Enable Register (8-bit) */


/* --- ICR0–ICR2 bitfield (external interrupt channels) ------------------------- */
/**
 * @defgroup ICR_EXT_BITS ICR0/1/2 external interrupt control (8-bit)
 * Bit  [6]:   V  – vector generation: 1=automatic, 0=external
 * Bit  [5]:   R/F – rising/falling edge (1=rising, 0=falling)
 * Bit  [4]:   L/E – level or edge mode (1=level, 0=edge)
 * Bits [2:0]: Level – IPL level to assert (0=no interrupt, 1–7)
 * Note: bit [3] is an internal state bit (latched request), read-only context
 */
#define ICR_EXT_VECTOR_AUTO     (1 << 6)     /**< Automatic internal vector generation */
#define ICR_EXT_RISING          (1 << 5)     /**< Rising-edge trigger */
#define ICR_EXT_FALLING         0            /**< Falling-edge trigger (reset default) */
#define ICR_EXT_LEVEL_MODE      (1 << 4)     /**< Level-sensitive mode */
#define ICR_EXT_EDGE_MODE       0            /**< Edge-sensitive mode (reset default) */
#define ICR_EXT_LEVEL_MASK      0x07         /**< IPL level field mask */
#define ICR_EXT_LEVEL(n)        ((n) & 0x07) /**< n = 0..7; 0 disables */
#define ICR_EXT_RESET           0x07         /**< Reset: auto-vector, falling, edge, level 7 */

/** Encoding for R/F and L/E combined (2-bit field [5:4]) */
typedef enum {
    ICR_MODE_FALLING_EDGE = 0x00,  /**< R/F=0, L/E=0 */
    ICR_MODE_LEVEL_LOW    = 0x10,  /**< R/F=0, L/E=1 */
    ICR_MODE_RISING_EDGE  = 0x20,  /**< R/F=1, L/E=0 */
    ICR_MODE_LEVEL_HIGH   = 0x30,  /**< R/F=1, L/E=1 */
} tmp68301_icr_mode_t;


/* --- ICR3–ICR9 bitfield (internal peripheral interrupt channels) --------------- */
/**
 * @defgroup ICR_INT_BITS ICR3–9 internal interrupt control (8-bit)
 * Bits [2:0]: Level – IPL level (0=disabled, 1–7)
 * Reset: 0x07 (level 7)
 */
#define ICR_INT_LEVEL_MASK      0x07
#define ICR_INT_LEVEL(n)        ((n) & 0x07)
#define ICR_INT_RESET           0x07


/* --- IMR / IPR / IISR bitfield (16-bit, shared layout) ------------------------ */
/**
 * @defgroup IMR_BITS IMR/IPR/IISR interrupt channel bitmask (16-bit)
 * Bit [12]: T2 – Timer channel 2
 * Bit [11]: T1 – Timer channel 1
 * Bit [10]: T0 – Timer channel 0
 * Bit  [9]: P  – Parallel interface (INT6)
 * Bit  [8]: S2 – Serial channel 2
 * Bit  [7]: S1 – Serial channel 1
 * Bit  [6]: S0 – Serial channel 0 (NB: datasheet bit numbering starts from 0=E0)
 * Bit  [2]: E2 – External INT2
 * Bit  [1]: E1 – External INT1
 * Bit  [0]: E0 – External INT0
 * IMR: 1=masked, 0=active;  IPR/IISR: 1=pending/in-service, 0=clear
 */
#define IMR_T2                  (1 << 12)
#define IMR_T1                  (1 << 11)
#define IMR_T0                  (1 << 10)
#define IMR_P                   (1 << 9)
#define IMR_S2                  (1 << 8)
#define IMR_S1                  (1 << 7)
#define IMR_S0                  (1 << 6)
#define IMR_E2                  (1 << 2)
#define IMR_E1                  (1 << 1)
#define IMR_E0                  (1 << 0)
#define IMR_ALL_INTERNAL        (IMR_T2|IMR_T1|IMR_T0|IMR_P|IMR_S2|IMR_S1|IMR_S0)
#define IMR_ALL_EXTERNAL        (IMR_E2|IMR_E1|IMR_E0)
#define IMR_RESET               0x07F7       /**< Reset: all channels masked */


/* --- IVNR (Interrupt Vector Number Register) ---------------------------------- */
/**
 * @defgroup IVNR_BITS IVNR (8-bit)
 * Bits [7:3]: Upper 5 bits of auto-generated vector; lower 5 bits are channel-defined.
 * Reset = 0x00. Must be set ≥ 0x40 to avoid overlap with 68000 exception vectors.
 */
#define IVNR_MASK               0xF8         /**< Only bits [7:3] are significant */
#define IVNR_RECOMMENDED_MIN    0x40         /**< Set this or above after reset */

/**
 * @brief Auto-generated vector numbers (lower 5 bits; OR with IVNR upper bits)
 */
typedef enum {
    IVEC_EXT0         = 0x00,  /**< External INT0 */
    IVEC_EXT1         = 0x01,  /**< External INT1 */
    IVEC_EXT2         = 0x02,  /**< External INT2 */
    IVEC_TIMER0       = 0x04,  /**< Timer channel 0 */
    IVEC_TIMER1       = 0x05,  /**< Timer channel 1 */
    IVEC_TIMER2       = 0x06,  /**< Timer channel 2 */
    IVEC_SERIAL0_ERR  = 0x08,  /**< Serial ch0: receive error / break */
    IVEC_SERIAL0_RX   = 0x09,  /**< Serial ch0: receive ready */
    IVEC_SERIAL0_TX   = 0x0A,  /**< Serial ch0: transmit ready */
    IVEC_SERIAL0_UNK  = 0x0B,  /**< Serial ch0: source cleared while pending */
    IVEC_SERIAL1_ERR  = 0x0C,
    IVEC_SERIAL1_RX   = 0x0D,
    IVEC_SERIAL1_TX   = 0x0E,
    IVEC_SERIAL1_UNK  = 0x0F,
    IVEC_SERIAL2_ERR  = 0x10,
    IVEC_SERIAL2_RX   = 0x11,
    IVEC_SERIAL2_TX   = 0x12,
    IVEC_SERIAL2_UNK  = 0x13,
} tmp68301_ivec_t;


/* --- IEIR (Expansion Interrupt Enable Register) ------------------------------- */
/**
 * @defgroup IEIR_BITS IEIR (8-bit)
 * Same bit layout as IMR bits [8:6] and [12:10] (T2,T1,T0,S2,S1,S0).
 * 1 = use peripheral interrupt channel as external pin input.
 * Expansion interrupt pins: RxD0→INT3, RxD1→INT4, RxD2→INT5,
 *                           IO12→INT6, TIN→INT7, IO14/DSR0→INT8.
 */
#define IEIR_T2                 (1 << 6)
#define IEIR_T1                 (1 << 5)
#define IEIR_T0                 (1 << 4)
#define IEIR_S2                 (1 << 3)  /* note: bit positions here follow datasheet table */
#define IEIR_S1                 (1 << 2)
#define IEIR_S0                 (1 << 1)
#define IEIR_RESET              0x00     /**< All channels internal */


/* =========================================================================
 * SECTION 3 – PARALLEL INTERFACE
 * ========================================================================= */

#define TMP68301_PDIR           TMP68301_REG(0x100)  /**< Parallel Direction Register (16-bit) */
#define TMP68301_PCR            TMP68301_REG(0x103)  /**< Parallel Control Register (8-bit) */
#define TMP68301_PSR            TMP68301_REG(0x105)  /**< Parallel Status Register (8-bit) */
#define TMP68301_PCMR           TMP68301_REG(0x107)  /**< Parallel Command Mask Register (8-bit) */
#define TMP68301_PMR            TMP68301_REG(0x108)  /**< Parallel Mode Register (8-bit, lower) */
                                                     /* 0x109 = upper byte (unused) */
#define TMP68301_PDR            TMP68301_REG(0x10A)  /**< Parallel Data Register (16-bit) */
#define TMP68301_PPR1           TMP68301_REG(0x10D)  /**< Parallel Pulse Register 1 (8-bit) */
#define TMP68301_PPR2           TMP68301_REG(0x10F)  /**< Parallel Pulse Register 2 (8-bit) */


/* --- PDIR (Parallel Direction Register, 16-bit) ------------------------------- */
/**
 * Bits [15:0]: DR15–DR0; 1=output, 0=input (per-bit direction control).
 * Reset = 0x0000 (all inputs).
 */
#define PDIR_ALL_INPUT          0x0000
#define PDIR_ALL_OUTPUT         0xFFFF


/* --- PCR (Parallel Control Register, 8-bit, bits [3:0]) ----------------------- */
/**
 * @defgroup PCR_BITS PCR mode control
 * Bits [3:0]: selects parallel interface operating mode (0–15).
 *   0 = GPIO mode (pure I/O port)
 *   Other values configure Centronics/handshake/serial-assist modes.
 * Reset = 0x00 (GPIO).
 */
#define PCR_MODE_MASK           0x0F
#define PCR_MODE_GPIO           0x00         /**< Mode 0: pure 16-bit GPIO */
#define PCR_RESET               0x00


/* --- PSR (Parallel Status Register, 8-bit) ------------------------------------ */
/**
 * Bit [7]: IF – interrupt flag (set when parallel interrupt occurs)
 * Bit [6]: Busy/status bit (read-only, set at reset)
 * Other bits undefined.
 * Reset = 0x40.
 */
#define PSR_IF                  (1 << 7)     /**< Interrupt flag */
#define PSR_RESET               0x40


/* --- PCMR (Parallel Command Mask Register, 8-bit, bits [4:0]) ----------------- */
/**
 * Bit [4]: CMD_COMPLETE mask (1=masked)
 * Bit [3]: CHANGE mask
 * Bit [2]: TXREADY mask
 * Bit [1]: FAULT logic output value
 * Bit [0]: PRIME logic output value
 * Reset = 0x00.
 */
#define PCMR_COMPLETE_MASK      (1 << 4)
#define PCMR_CHANGE_MASK        (1 << 3)
#define PCMR_TXREADY_MASK       (1 << 2)
#define PCMR_FAULT              (1 << 1)     /**< FAULT pin output level */
#define PCMR_PRIME              (1 << 0)     /**< PRIME pin output level */


/* --- PMR (Parallel Mode Register, 8-bit, bits [3:0]) -------------------------- */
/**
 * Bit [3]: DSTB polarity (1=falling, 0=rising)
 * Bit [2]: ACK mode     (1=auto, 0=manual)
 * Bit [1]: BUSY/ACK sel (1=ACK, 0=none)
 * Bit [0]: Transfer mode (1=external, 0=internal)
 * Reset = 0x00.
 */
#define PMR_DSTB_FALLING        (1 << 3)
#define PMR_ACK_AUTO            (1 << 2)
#define PMR_ACK_SEL             (1 << 1)
#define PMR_TRANSFER_EXT        (1 << 0)


/* =========================================================================
 * SECTION 4 – SERIAL INTERFACE
 * Three identical channels (ch0, ch1, ch2).  Base offsets:
 *   Ch0: 0x180   Ch1: 0x190   Ch2: 0x1A0
 * All registers are 8-bit, located at odd addresses within word slots.
 * ========================================================================= */

/*
 * Serial channel base offsets (relative to TMP68301_BASE).
 * Ch0 block: 0x180–0x18F  (SMR at odd addr +1, SCR/SPR at end of ch0 block)
 * Ch1 block: 0x190–0x19F
 * Ch2 block: 0x1A0–0x1AF
 * Verified from MAME internal_map:
 *   Ch0: 0xfffd81,83,85,87,89; SPR=0xfffd8d; SCR=0xfffd8f
 *   Ch1: 0xfffd91,93,95,97,99
 *   Ch2: 0xfffda1,a3,a5,a7,a9
 */
#define TMP68301_SER_CH0_BASE   0xFFFFD80UL  /**< Serial ch0 block base (absolute) */
#define TMP68301_SER_CH1_BASE   0xFFFFD90UL  /**< Serial ch1 block base (absolute) */
#define TMP68301_SER_CH2_BASE   0xFFFFDA0UL  /**< Serial ch2 block base (absolute) */

/* Offsets within a serial channel block (0x10 bytes per channel) */
#define TMP68301_SMR_OFF        0x01   /**< Serial Mode Register (byte, odd addr) */
#define TMP68301_SCMR_OFF       0x03   /**< Serial Command Register */
#define TMP68301_SBRR_OFF       0x05   /**< Serial Baud Rate Register */
#define TMP68301_SSR_OFF        0x07   /**< Serial Status Register (read-only) */
#define TMP68301_SDR_OFF        0x09   /**< Serial Data Register (TX/RX buffer) */
/* Ch0 block only – global serial registers: */
#define TMP68301_SPR_OFF        0x0D   /**< Serial Prescaler Register (ch0 block, 0xfffd8d) */
#define TMP68301_SCR_OFF        0x0F   /**< Serial Control Register   (ch0 block, 0xfffd8f) */

/* Absolute address macros (base offset version) */
#define TMP68301_SMR(ch)        TMP68301_REG(0x181 + (ch)*0x10)
#define TMP68301_SCMR(ch)       TMP68301_REG(0x183 + (ch)*0x10)
#define TMP68301_SBRR(ch)       TMP68301_REG(0x185 + (ch)*0x10)
#define TMP68301_SSR(ch)        TMP68301_REG(0x187 + (ch)*0x10)
#define TMP68301_SDR(ch)        TMP68301_REG(0x189 + (ch)*0x10)
#define TMP68301_SPR            TMP68301_REG(0x18D)   /**< Prescaler (global, in ch0 block) */
#define TMP68301_SCR            TMP68301_REG(0x18F)   /**< Serial control (global, in ch0 block) */

/* Convenience aliases */
#define TMP68301_SMR0           TMP68301_SMR(0)
#define TMP68301_SCMR0          TMP68301_SCMR(0)
#define TMP68301_SBRR0          TMP68301_SBRR(0)
#define TMP68301_SSR0           TMP68301_SSR(0)
#define TMP68301_SDR0           TMP68301_SDR(0)
#define TMP68301_SMR1           TMP68301_SMR(1)
#define TMP68301_SCMR1          TMP68301_SCMR(1)
#define TMP68301_SBRR1          TMP68301_SBRR(1)
#define TMP68301_SSR1           TMP68301_SSR(1)
#define TMP68301_SDR1           TMP68301_SDR(1)
#define TMP68301_SMR2           TMP68301_SMR(2)
#define TMP68301_SCMR2          TMP68301_SCMR(2)
#define TMP68301_SBRR2          TMP68301_SBRR(2)
#define TMP68301_SSR2           TMP68301_SSR(2)
#define TMP68301_SDR2           TMP68301_SDR(2)


/* --- SCR (Serial Control Register, 8-bit, global) ----------------------------- */
/**
 * @defgroup SCR_BITS SCR bitfield
 * Bit [7]: CKSE – clock source (1=internal, 0=external)
 * Bit [5]: RES  – serial reset (1=in-reset, 0=operational)
 * Bit [0]: INTM – global interrupt mask (1=all serial IRQs disabled)
 * Reset = 0xA1 (CKSE|RES|INTM).
 */
#define SCR_CKSE                (1 << 7)     /**< Internal clock source */
#define SCR_RES                 (1 << 5)     /**< Hold serial block in reset */
#define SCR_INTM                (1 << 0)     /**< Mask all serial interrupts */
#define SCR_RESET               (SCR_CKSE | SCR_RES | SCR_INTM)


/* --- SMR (Serial Mode Register, 8-bit, per channel) --------------------------- */
/**
 * @defgroup SMR_BITS SMR bitfield
 * Bit  [7]: RXINTM – receive interrupt mask     (1=masked)
 * Bit  [6]: ERINTM – error interrupt mask       (1=masked)
 * Bit  [5]: PEO    – parity even/odd            (1=odd parity)
 * Bit  [4]: PEN    – parity enable              (1=parity enabled)
 * Bits [3:2]: CL   – character length           (00=5bit, 01=6bit, 10=7bit, 11=8bit)
 * Bit  [1]: TXINTM – transmit interrupt mask    (1=masked)
 * Bit  [0]: ST     – stop bits                  (1=2 stop bits, 0=1 stop bit)
 * Reset = 0xC2 (RXINTM|ERINTM|TXINTM).
 */
#define SMR_RXINTM              (1 << 7)
#define SMR_ERINTM              (1 << 6)
#define SMR_PEO                 (1 << 5)     /**< Odd parity (when PEN=1) */
#define SMR_PEN                 (1 << 4)     /**< Parity enable */
#define SMR_CL_SHIFT            2            /**< Character length field shift */
#define SMR_CL_MASK             (0x3 << 2)
#define SMR_CL_5BIT             (0x0 << 2)
#define SMR_CL_6BIT             (0x1 << 2)
#define SMR_CL_7BIT             (0x2 << 2)
#define SMR_CL_8BIT             (0x3 << 2)
#define SMR_TXINTM              (1 << 1)
#define SMR_ST                  (1 << 0)     /**< 2 stop bits */
#define SMR_RESET               (SMR_RXINTM | SMR_ERINTM | SMR_TXINTM)

typedef enum {
    SMR_CHARLEN_5 = 0, SMR_CHARLEN_6 = 1,
    SMR_CHARLEN_7 = 2, SMR_CHARLEN_8 = 3,
} tmp68301_smr_charlen_t;


/* --- SCMR (Serial Command Register, 8-bit, per channel) ----------------------- */
/**
 * @defgroup SCMR_BITS SCMR bitfield
 * Bit [5]: RTS   – force RTS low                (ch0 only; 1=low)
 * Bit [4]: ERS   – error reset                  (write 1 to clear SSR error bits)
 * Bit [3]: SBRK  – send break                   (1=transmit continuous break)
 * Bit [2]: RXEN  – receiver enable              (1=enabled)
 * Bit [1]: DTR   – force DTR low                (ch0 only; 1=low)
 * Bit [0]: TXEN  – transmitter enable           (1=enabled)
 * Reset = 0x10 (ERS only).
 * Note: ch1/ch2 mask to 0x1D (no RTS/DTR bits).
 */
#define SCMR_RTS                (1 << 5)     /**< RTS output low (ch0 only) */
#define SCMR_ERS                (1 << 4)     /**< Error status reset */
#define SCMR_SBRK               (1 << 3)     /**< Send break */
#define SCMR_RXEN               (1 << 2)     /**< Receiver enable */
#define SCMR_DTR                (1 << 1)     /**< DTR output low (ch0 only) */
#define SCMR_TXEN               (1 << 0)     /**< Transmitter enable */
#define SCMR_RESET              SCMR_ERS


/* --- SSR (Serial Status Register, 8-bit read-only, per channel) --------------- */
/**
 * @defgroup SSR_BITS SSR bitfield
 * Bit [7]: DSR   – DSR input state             (ch0 only, read-only)
 * Bit [6]: RBRK  – receive break detected
 * Bit [5]: FE    – framing error
 * Bit [4]: OE    – overrun error
 * Bit [3]: PE    – parity error
 * Bit [2]: TXE   – transmitter empty           (shift register empty)
 * Bit [1]: RXRDY – receive data ready          (byte in SDR)
 * Bit [0]: TXRDY – transmit ready              (SDR can accept new byte)
 * Reset = 0x04 (TXE).
 */
#define SSR_DSR                 (1 << 7)
#define SSR_RBRK                (1 << 6)
#define SSR_FE                  (1 << 5)
#define SSR_OE                  (1 << 4)
#define SSR_PE                  (1 << 3)
#define SSR_TXE                 (1 << 2)
#define SSR_RXRDY               (1 << 1)
#define SSR_TXRDY               (1 << 0)
#define SSR_ERROR_BITS          (SSR_RBRK | SSR_FE | SSR_OE | SSR_PE)
#define SSR_RESET               SSR_TXE

/** Serial interrupt sub-types (encoded in lower vector bits when IVNR is set) */
typedef enum {
    SSR_INT_ERR = 0,   /**< Receive error / break */
    SSR_INT_RX  = 1,   /**< Receive data ready */
    SSR_INT_TX  = 2,   /**< Transmit data ready */
    SSR_INT_UNK = 3,   /**< Source cleared while pending */
} tmp68301_ssr_int_t;


/* --- SBRR (Serial Baud Rate Register, 8-bit) ---------------------------------- */
/**
 * Baud rate divisor: actual bit clock = (SPR_prescaler_clock) / div
 * Values 0x00–0x3F; 0x01 is reset default.
 * Actual divisor table (from MAME): see sbrr_to_div() in tmp68301.cpp.
 */
#define SBRR_RESET              0x01


/* =========================================================================
 * SECTION 5 – 16-BIT TIMER/COUNTER (3 channels)
 * Base offsets:  Ch0: 0x200  Ch1: 0x220  Ch2: 0x240
 * All registers are 16-bit (word access).
 * ========================================================================= */

/* Channel base offsets (relative to TMP68301_BASE) */
#define TMP68301_TMR_CH0_OFF    0x200
#define TMP68301_TMR_CH1_OFF    0x220
#define TMP68301_TMR_CH2_OFF    0x240

/* Register offsets within a timer channel */
#define TMP68301_TCR_OFF        0x00   /**< Timer Control Register (16-bit) */
#define TMP68301_TMCR1_OFF      0x04   /**< Timer Match Control Register 1 (16-bit) */
#define TMP68301_TMCR2_OFF      0x08   /**< Timer Match Control Register 2 (16-bit, ch1/ch2 only) */
#define TMP68301_TCTR_OFF       0x0C   /**< Timer Counter Register (16-bit) */

/* Absolute address macros */
#define TMP68301_TCR(ch)        TMP68301_REG(TMP68301_TMR_CH0_OFF + (ch)*0x20 + TMP68301_TCR_OFF)
#define TMP68301_TMCR1(ch)      TMP68301_REG(TMP68301_TMR_CH0_OFF + (ch)*0x20 + TMP68301_TMCR1_OFF)
#define TMP68301_TMCR2(ch)      TMP68301_REG(TMP68301_TMR_CH0_OFF + (ch)*0x20 + TMP68301_TMCR2_OFF)
#define TMP68301_TCTR(ch)       TMP68301_REG(TMP68301_TMR_CH0_OFF + (ch)*0x20 + TMP68301_TCTR_OFF)

/* Convenience aliases */
#define TMP68301_TCR0           TMP68301_TCR(0)        /* 0xFFFE00 */
#define TMP68301_TMCR01         TMP68301_TMCR1(0)      /* 0xFFFE04 */
#define TMP68301_TCTR0          TMP68301_TCTR(0)       /* 0xFFFE0C */
#define TMP68301_TCR1           TMP68301_TCR(1)        /* 0xFFFE20 */
#define TMP68301_TMCR11         TMP68301_TMCR1(1)      /* 0xFFFE24 */
#define TMP68301_TMCR12         TMP68301_TMCR2(1)      /* 0xFFFE28 */
#define TMP68301_TCTR1          TMP68301_TCTR(1)       /* 0xFFFE2C */
#define TMP68301_TCR2           TMP68301_TCR(2)        /* 0xFFFE40 */
#define TMP68301_TMCR21         TMP68301_TMCR1(2)      /* 0xFFFE44 */
#define TMP68301_TMCR22         TMP68301_TMCR2(2)      /* 0xFFFE48 */
#define TMP68301_TCTR2          TMP68301_TCTR(2)       /* 0xFFFE4C */


/* --- TCR (Timer Control Register, 16-bit) -------------------------------------- */
/**
 * @defgroup TCR_BITS TCR bitfield
 * Bits [15:14]: CK  – clock source selection
 * Bits [13:10]: P   – prescaler (clock divider) selection (4-bit)
 * Bits  [9: 8]: T   – count mode
 * Bit      [7]: N1  – pulse/output mode (1=enabled)
 * Bit      [6]: RP  – output polarity   (1=invert, 0=pulse)
 * Bits  [5: 4]: MR  – match/max mode selection (2-bit)
 * Bit      [2]: INT – interrupt enable on compare match
 * Bit      [1]: CS  – counter stop      (1=stopped, 0=running)
 * Bit      [0]: TS  – timer set/clear   (1=enabled, write 0 to clear counter)
 *
 * Reset: ch0 = 0x0052, ch1/ch2 = 0x0012.
 */
#define TCR_CK_SHIFT            14
#define TCR_CK_MASK             (0x3 << 14)
#define TCR_CK_SYSCLK           (0x0 << 14)  /**< Internal system clock */
#define TCR_CK_TIN              (0x1 << 14)  /**< External TIN pin */
#define TCR_CK_CH0OUT           (0x2 << 14)  /**< Timer ch0 output (cascade ch1) */
#define TCR_CK_CH1OUT           (0x3 << 14)  /**< Timer ch1 output (cascade ch2) */

#define TCR_P_SHIFT             10
#define TCR_P_MASK              (0xF << 10)   /**< Prescaler: divide by 2^(P+1) */

#define TCR_T_SHIFT             8
#define TCR_T_MASK              (0x3 << 8)
#define TCR_T_FREERUN           (0x0 << 8)   /**< Free-running count mode */
#define TCR_T_MATCH             (0x1 << 8)   /**< Count to match register then reload */
#define TCR_T_CAPTURE           (0x2 << 8)   /**< Capture on TIN edge */
#define TCR_T_PWM               (0x3 << 8)   /**< PWM output mode */

#define TCR_N1                  (1 << 7)     /**< Output waveform enable */
#define TCR_RP                  (1 << 6)     /**< Output polarity invert */

#define TCR_MR_SHIFT            4
#define TCR_MR_MASK             (0x3 << 4)
#define TCR_MR_0                (0x0 << 4)   /**< Count to 0xFFFF */
#define TCR_MR_TMCR1            (0x1 << 4)   /**< Count to TMCR1 */
#define TCR_MR_TMCR2            (0x2 << 4)   /**< Count to TMCR2 (ch1/ch2 only) */
#define TCR_MR_DUAL             (0x3 << 4)   /**< Dual-match mode */

#define TCR_INT                 (1 << 2)     /**< Interrupt on compare match */
#define TCR_CS                  (1 << 1)     /**< Counter stop (1=halted) */
#define TCR_TS                  (1 << 0)     /**< Timer start/clear control */

#define TCR0_RESET              0x0052       /**< ch0 reset: prescale÷4, stopped, TS */
#define TCR1_RESET              0x0012       /**< ch1/ch2 reset: prescale÷2, stopped, TS */


/* =========================================================================
 * SECTION 6 – COMPLETE REGISTER MAP TABLE (for reference / iteration)
 * ========================================================================= */

/**
 * @brief Flat list of all TMP68301 register absolute addresses (default base)
 */
typedef enum {
    /* Address Decoder */
    REG_AMAR0   = 0xFFFC00,
    REG_AAMR0   = 0xFFFC01,
    REG_AACR0   = 0xFFFC03,
    REG_AMAR1   = 0xFFFC04,
    REG_AAMR1   = 0xFFFC05,
    REG_AACR1   = 0xFFFC07,
    REG_AACR2   = 0xFFFC09,
    REG_ATOR    = 0xFFFC0B,
    REG_ARELR   = 0xFFFC0C,  /* 16-bit */

    /* Interrupt Controller */
    REG_ICR0    = 0xFFFC81,
    REG_ICR1    = 0xFFFC83,
    REG_ICR2    = 0xFFFC85,
    REG_ICR3    = 0xFFFC87,
    REG_ICR4    = 0xFFFC89,
    REG_ICR5    = 0xFFFC8B,
    REG_ICR6    = 0xFFFC8D,
    REG_ICR7    = 0xFFFC8F,
    REG_ICR8    = 0xFFFC91,
    REG_ICR9    = 0xFFFC93,
    REG_IMR     = 0xFFFC94,  /* 16-bit */
    REG_IPR     = 0xFFFC96,  /* 16-bit */
    REG_IISR    = 0xFFFC98,  /* 16-bit */
    REG_IVNR    = 0xFFFC9B,
    REG_IEIR    = 0xFFFC9D,

    /* Parallel Interface */
    REG_PDIR    = 0xFFFD00,  /* 16-bit */
    REG_PCR     = 0xFFFD03,
    REG_PSR     = 0xFFFD05,
    REG_PCMR    = 0xFFFD07,
    REG_PMR     = 0xFFFD08,
    REG_PDR     = 0xFFFD0A,  /* 16-bit */
    REG_PPR1    = 0xFFFD0D,
    REG_PPR2    = 0xFFFD0F,

    /* Serial Interface Ch0 (base 0xFFFFD80, registers at odd addresses) */
    REG_SMR0    = 0xFFFFD81,
    REG_SCMR0   = 0xFFFFD83,
    REG_SBRR0   = 0xFFFFD85,
    REG_SSR0    = 0xFFFFD87,
    REG_SDR0    = 0xFFFFD89,
    REG_SPR     = 0xFFFFD8D,  /**< Global serial prescaler */
    REG_SCR     = 0xFFFFD8F,  /**< Global serial control */

    /* Serial Interface Ch1 (base 0xFFFFD90) */
    REG_SMR1    = 0xFFFFD91,
    REG_SCMR1   = 0xFFFFD93,
    REG_SBRR1   = 0xFFFFD95,
    REG_SSR1    = 0xFFFFD97,
    REG_SDR1    = 0xFFFFD99,

    /* Serial Interface Ch2 (base 0xFFFFDA0) */
    REG_SMR2    = 0xFFFFDA1,
    REG_SCMR2   = 0xFFFFDA3,
    REG_SBRR2   = 0xFFFFDA5,
    REG_SSR2    = 0xFFFFDA7,
    REG_SDR2    = 0xFFFFDA9,

    /* Timer Ch0 */
    REG_TCR0    = 0xFFFE00,  /* 16-bit */
    REG_TMCR01  = 0xFFFE04,  /* 16-bit */
    REG_TCTR0   = 0xFFFE0C,  /* 16-bit */

    /* Timer Ch1 */
    REG_TCR1    = 0xFFFE20,  /* 16-bit */
    REG_TMCR11  = 0xFFFE24,  /* 16-bit */
    REG_TMCR12  = 0xFFFE28,  /* 16-bit */
    REG_TCTR1   = 0xFFFE2C,  /* 16-bit */

    /* Timer Ch2 */
    REG_TCR2    = 0xFFFE40,  /* 16-bit */
    REG_TMCR21  = 0xFFFE44,  /* 16-bit */
    REG_TMCR22  = 0xFFFE48,  /* 16-bit */
    REG_TCTR2   = 0xFFFE4C,  /* 16-bit */
} tmp68301_reg_t;


/* =========================================================================
 * SECTION 7 – STRUCTURED REGISTER BITFIELD TYPES (C99 bitfields)
 *
 * WARNING: Bitfield layout is compiler-dependent and assumes little-endian
 * bit ordering.  These structs are provided for documentation / Ghidra data
 * type import purposes.  Do NOT rely on them for direct memory-mapped I/O
 * in production firmware without verifying compiler layout.
 * ========================================================================= */

/** AACR0/AACR1 – chip-select area control (8-bit) */
typedef union {
    uint8_t raw;
    struct {
        uint8_t wait    : 3; /**< [2:0] wait state count */
        uint8_t dtack   : 2; /**< [4:3] DTACK source (01=int,10=ext,11=both) */
        uint8_t cs_en   : 1; /**< [5]   chip-select output enable */
        uint8_t _res    : 2; /**< [7:6] reserved */
    } bits;
} tmp68301_aacr_t;

/** ICR0–ICR2 – external interrupt control (8-bit) */
typedef union {
    uint8_t raw;
    struct {
        uint8_t level   : 3; /**< [2:0] IPL level to assert */
        uint8_t le      : 1; /**< [3]   level-enable (1=level mode) */
        uint8_t rf      : 1; /**< [4]   rising/falling (1=rising) */
        uint8_t v       : 1; /**< [5]   vector: 1=auto, 0=external */
        uint8_t _res    : 2; /**< [7:6] reserved */
    } bits;
} tmp68301_icr_ext_t;

/** ICR3–ICR9 – internal peripheral interrupt control (8-bit) */
typedef union {
    uint8_t raw;
    struct {
        uint8_t level   : 3; /**< [2:0] IPL level */
        uint8_t _res    : 5; /**< [7:3] reserved */
    } bits;
} tmp68301_icr_int_t;

/** IMR / IPR / IISR – interrupt status (16-bit) */
typedef union {
    uint16_t raw;
    struct {
        uint16_t e0     : 1; /**<  [0] External INT0 */
        uint16_t e1     : 1; /**<  [1] External INT1 */
        uint16_t e2     : 1; /**<  [2] External INT2 */
        uint16_t _res1  : 3; /**<  [5:3] reserved */
        uint16_t s0     : 1; /**<  [6] Serial ch0 */
        uint16_t s1     : 1; /**<  [7] Serial ch1 */
        uint16_t s2     : 1; /**<  [8] Serial ch2 */
        uint16_t p      : 1; /**<  [9] Parallel (INT6) */
        uint16_t t0     : 1; /**< [10] Timer ch0 */
        uint16_t t1     : 1; /**< [11] Timer ch1 */
        uint16_t t2     : 1; /**< [12] Timer ch2 */
        uint16_t _res2  : 3; /**< [15:13] reserved */
    } bits;
} tmp68301_irq_reg_t;

/** SMR – serial mode register (8-bit, per channel) */
typedef union {
    uint8_t raw;
    struct {
        uint8_t st      : 1; /**< [0]   stop bits: 1=2 stop bits */
        uint8_t txintm  : 1; /**< [1]   TX interrupt mask */
        uint8_t cl      : 2; /**< [3:2] character length (0=5…3=8) */
        uint8_t pen     : 1; /**< [4]   parity enable */
        uint8_t peo     : 1; /**< [5]   parity even(0)/odd(1) */
        uint8_t erintm  : 1; /**< [6]   error interrupt mask */
        uint8_t rxintm  : 1; /**< [7]   RX interrupt mask */
    } bits;
} tmp68301_smr_t;

/** SCMR – serial command register (8-bit, per channel) */
typedef union {
    uint8_t raw;
    struct {
        uint8_t txen    : 1; /**< [0] transmitter enable */
        uint8_t dtr     : 1; /**< [1] DTR output low (ch0 only) */
        uint8_t rxen    : 1; /**< [2] receiver enable */
        uint8_t sbrk    : 1; /**< [3] send break */
        uint8_t ers     : 1; /**< [4] error status reset */
        uint8_t rts     : 1; /**< [5] RTS output low (ch0 only) */
        uint8_t _res    : 2; /**< [7:6] reserved */
    } bits;
} tmp68301_scmr_t;

/** SSR – serial status register (8-bit read-only, per channel) */
typedef union {
    uint8_t raw;
    struct {
        uint8_t txrdy   : 1; /**< [0] TX data register ready */
        uint8_t rxrdy   : 1; /**< [1] RX data register ready */
        uint8_t txe     : 1; /**< [2] TX shift register empty */
        uint8_t pe      : 1; /**< [3] parity error */
        uint8_t oe      : 1; /**< [4] overrun error */
        uint8_t fe      : 1; /**< [5] framing error */
        uint8_t rbrk    : 1; /**< [6] break received */
        uint8_t dsr     : 1; /**< [7] DSR input state (ch0 only) */
    } bits;
} tmp68301_ssr_t;

/** TCR – timer control register (16-bit) */
typedef union {
    uint16_t raw;
    struct {
        uint16_t ts     : 1; /**<  [0] timer start/clear */
        uint16_t cs     : 1; /**<  [1] counter stop */
        uint16_t _res1  : 1; /**<  [2] (was INT in earlier notation) */
        uint16_t intena : 1; /**<  [3] interrupt enable – NOTE: actual bit [2] in MAME enum TCR_INT=0x0004 */
        uint16_t mr     : 2; /**<  [5:4] max/match mode */
        uint16_t rp     : 1; /**<  [6] output polarity */
        uint16_t n1     : 1; /**<  [7] output enable */
        uint16_t t      : 2; /**<  [9:8] count mode */
        uint16_t p      : 4; /**< [13:10] prescaler */
        uint16_t ck     : 2; /**< [15:14] clock source */
    } bits;
} tmp68301_tcr_t;


/* =========================================================================
 * SECTION 8 – NOTES ON LCD / DSP PERIPHERALS
 *
 * The base TMP68301 (68301A and 68301AK) does NOT contain an integrated
 * LCD controller or DSP.  These peripherals exist in application-specific
 * derivatives or companion chips on target boards (e.g., Kurzweil KRZ2000,
 * Yamaha VL70-m).  Board-specific registers are mapped via the chip-select
 * outputs (CS0/CS1) of the TMP68301 to external peripheral address ranges.
 *
 * If reverse-engineering a specific board, map CS0/CS1 base+mask via
 * AMAR0/AAMR0 and AMAR1/AAMR1 to determine the external peripheral windows.
 *
 * Common companion chips found on TMP68301-based music hardware:
 *   - Yamaha YM262/OPL3, YMF278 (FM/PCM synthesis)
 *   - Yamaha DSP cores (DSPV, YSS225, etc.)
 *   - LCD controller modules (HD44780-compatible via parallel port I/O12-I/O15)
 *
 * For TMP68303 (Toshiba variant with different timer IRQ base):
 *   - Identical register map to TMP68301
 *   - base_timer_irq() returns 3 instead of 4 (timer IRQ vectors shifted)
 * ========================================================================= */

/** TMP68303 distinction: timer IRQ base vector offset */
#define TMP68303_TIMER_IRQ_BASE     3
#define TMP68301_TIMER_IRQ_BASE     4


#ifdef __cplusplus
}
#endif

#endif /* TMP68301_REGS_H */
