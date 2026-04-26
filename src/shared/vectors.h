/**
 * vectors.h — 68000 exception vector table as a C struct
 *
 * The 68000/TMP68301 reset vector table starts at address 0x000000.
 * It contains 256 entries of 4 bytes each = 1024 bytes total.
 * Only the first 96 are used on the DMP9/16 (TMP68301 has 96 vectors).
 *
 * Using a C struct instead of raw assembly gives:
 *   - Named access to every slot (no magic offsets)
 *   - Compiler-checked types (function pointers)
 *   - Single source of truth — one struct initialiser per ROM version
 *   - Works with GCC -m68000 with __attribute__((section(".vectors")))
 *
 * PLACEMENT: The linker script places .vectors at 0x000000 (ROM base).
 * The struct must be exactly 384 bytes (96 × 4) — no padding, no reorder.
 * Enforced with __attribute__((packed)) and a static_assert.
 *
 * CALLING CONVENTION: ISR pointers use void(*)(void).
 * The function attributes (__interrupt, yamaha_reg) are on the definitions,
 * not on the pointer types — GCC does not propagate CC through pointers.
 */

#ifndef DMP9_VECTORS_H
#define DMP9_VECTORS_H

#include <stdint.h>

/* -------------------------------------------------------------------------
 * Function pointer types
 * ------------------------------------------------------------------------- */
typedef void (*isr_fn_t)(void);      /**< ISR / exception handler           */
typedef void (*reset_fn_t)(void);    /**< Reset handler (yamaha_reg, noret) */

/* -------------------------------------------------------------------------
 * 68000/TMP68301 exception vector table
 *
 * Entries follow the MC68000 reference manual order exactly.
 * Slots not used by the DMP9/16 firmware are filled with _isr_reserved.
 * ------------------------------------------------------------------------- */
typedef struct __attribute__((packed)) {

    /* ----- Group 0: Initial state --------------------------------------- */
    uint32_t    initial_ssp;        /**< Vec[0]  Initial Supervisor Stack Ptr */
    reset_fn_t  reset_pc;           /**< Vec[1]  Reset: Initial Program Ctr  */

    /* ----- Group 1: Synchronous exceptions ------------------------------ */
    isr_fn_t    bus_error;          /**< Vec[2]  Bus error                   */
    isr_fn_t    address_error;      /**< Vec[3]  Address error               */
    isr_fn_t    illegal_insn;       /**< Vec[4]  Illegal instruction         */
    isr_fn_t    zero_divide;        /**< Vec[5]  Zero divide                 */
    isr_fn_t    chk;                /**< Vec[6]  CHK instruction             */
    isr_fn_t    trapv;              /**< Vec[7]  TRAPV instruction           */
    isr_fn_t    privilege_viol;     /**< Vec[8]  Privilege violation         */
    isr_fn_t    trace;              /**< Vec[9]  Trace                       */
    isr_fn_t    line_a;             /**< Vec[10] Line-A emulator             */
    isr_fn_t    line_f;             /**< Vec[11] Line-F emulator             */

    /* ----- Reserved (hardware/coprocessor) ------------------------------ */
    isr_fn_t    reserved_12;        /**< Vec[12] (Unassigned/reserved)       */
    isr_fn_t    reserved_13;        /**< Vec[13] (Unassigned/reserved)       */
    isr_fn_t    reserved_14;        /**< Vec[14] (Unassigned/reserved)       */
    isr_fn_t    uninit_interrupt;   /**< Vec[15] Uninitialised interrupt      */
    isr_fn_t    reserved_16;        /**< Vec[16] (Unassigned/reserved)       */
    isr_fn_t    reserved_17;        /**< Vec[17] (Unassigned/reserved)       */
    isr_fn_t    reserved_18;        /**< Vec[18] (Unassigned/reserved)       */
    isr_fn_t    reserved_19;        /**< Vec[19] (Unassigned/reserved)       */
    isr_fn_t    reserved_20;        /**< Vec[20] (Unassigned/reserved)       */
    isr_fn_t    reserved_21;        /**< Vec[21] (Unassigned/reserved)       */
    isr_fn_t    reserved_22;        /**< Vec[22] (Unassigned/reserved)       */
    isr_fn_t    reserved_23;        /**< Vec[23] (Unassigned/reserved)       */

    /* ----- Group 2: Hardware interrupts --------------------------------- */
    isr_fn_t    spurious;           /**< Vec[24] Spurious interrupt          */
    isr_fn_t    autovec_1;          /**< Vec[25] Level 1 autovector — TMP68301 INT0? */
    isr_fn_t    autovec_2;          /**< Vec[26] Level 2 autovector — TMP68301 INT1? */
    isr_fn_t    autovec_3;          /**< Vec[27] Level 3 autovector — TMP68301 INT2? */
    isr_fn_t    autovec_4;          /**< Vec[28] Level 4 autovector          */
    isr_fn_t    autovec_5;          /**< Vec[29] Level 5 autovector          */
    isr_fn_t    autovec_6;          /**< Vec[30] Level 6 autovector          */
    isr_fn_t    autovec_7;          /**< Vec[31] Level 7 autovector (NMI)   */

    /* ----- TRAP #0–#15 -------------------------------------------------- */
    isr_fn_t    trap[16];           /**< Vec[32–47] TRAP instructions        */

    /* ----- Reserved 48–63 ----------------------------------------------- */
    isr_fn_t    reserved_48_63[16]; /**< Vec[48–63] (Unassigned/reserved)   */

    /* ----- User interrupt vectors 64–95 --------------------------------- */
    /*
     * TMP68301 routes INT0/INT1/INT2 through the interrupt acknowledge
     * cycle.  The IACK0/IACK1/IACK2 pins drive the vector number.
     * Exact routing depends on firmware init of TMP68301 interrupt ctrl.
     * TODO: Map from Ghidra analysis of interrupt setup in startup.c.
     */
    isr_fn_t    user[32];           /**< Vec[64–95] User/peripheral vectors  */

} m68k_vector_table_t;

/* Sanity check: table must be exactly 96 * 4 = 384 bytes */
_Static_assert(sizeof(m68k_vector_table_t) == 384,
               "m68k_vector_table_t size mismatch — check alignment/padding");

/* -------------------------------------------------------------------------
 * External declaration — each per-ROM vectors.c defines one instance
 * placed in section .vectors by the linker script.
 * ------------------------------------------------------------------------- */
extern const m68k_vector_table_t _vector_table;

#endif /* DMP9_VECTORS_H */
