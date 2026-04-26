#!/usr/bin/env bash
# =============================================================================
# compile.sh — Attempt to compile Ghidra-decompiled C back to 68000 binary
#
# This is the "compile" leg of the DMP9/16 round-trip workflow:
#
#   Ghidra export (DMP9_ExportC.java)
#       → compile.sh (this script)
#           → m68k-linux-gnu-gcc compiles all_functions.c
#           → objcopy strips to raw binary
#           → compare_roms.py diffs against original ROM
#
# IMPORTANT — WHAT TO EXPECT
# ───────────────────────────
# Ghidra's decompiled C is NOT directly compilable out of the box:
#
#  1. Missing headers / types (undefined__int, BADSPACEBASE, etc.)
#  2. Implicit globals that Ghidra renders as pointer dereferences
#  3. Inline asm / hardware I/O patterns (MOVEP, bitfields)
#  4. No linker script — the ROM has a fixed flat layout
#
# The goal of this script is NOT to produce a bit-identical ROM.
# The goal is to measure PROGRESS:
#
#   ✓ Does it compile at all?
#   ✓ How many functions compile cleanly vs. need fixups?
#   ✓ For functions that compile, how close is the output binary?
#
# Over time, as you clean up Ghidra output and add typedefs, the
# diff shrinks. When a function's compiled bytes match the ROM exactly,
# that function is "round-trip verified".
#
# USAGE
# ─────
#   bash compile.sh <export_dir> [<original_rom.bin>]
#
#   export_dir      — output of DMP9_ExportC.java (contains all_functions.c)
#   original_rom    — .bin to compare against (optional but recommended)
#
# TOOLCHAIN
# ─────────
#   Install: sudo apt install gcc-m68k-linux-gnu binutils-m68k-linux-gnu
#   Verify:  m68k-linux-gnu-gcc --version
#
# Alternatively with GCC cross-compile:
#   sudo apt install gcc-m68k-linux-gnu
#
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Args
# ---------------------------------------------------------------------------

if [ $# -lt 1 ]; then
    echo "Usage: bash compile.sh <export_dir> [<original_rom.bin>]"
    exit 1
fi

EXPORT_DIR="$(realpath "$1")"
ORIGINAL_ROM="${2:-}"

if [ ! -d "$EXPORT_DIR" ]; then
    echo "ERROR: Export directory not found: $EXPORT_DIR"
    exit 1
fi

if [ ! -f "$EXPORT_DIR/all_functions.c" ]; then
    echo "ERROR: all_functions.c not found in $EXPORT_DIR"
    echo "  Run: bash run_analysis.sh --export-only"
    exit 1
fi

BUILD_DIR="$EXPORT_DIR/build"
mkdir -p "$BUILD_DIR"

echo "=== DMP9/16 Round-Trip Compile ==="
echo "Export dir : $EXPORT_DIR"
echo "Build dir  : $BUILD_DIR"
echo "ROM        : ${ORIGINAL_ROM:-'(not provided)'}"
echo ""

# ---------------------------------------------------------------------------
# Check toolchain
# ---------------------------------------------------------------------------

CROSS="m68k-linux-gnu"
CC="${CC:-${CROSS}-gcc}"
OBJCOPY="${OBJCOPY:-${CROSS}-objcopy}"
OBJDUMP="${OBJDUMP:-${CROSS}-objdump}"
NM="${NM:-${CROSS}-nm}"

check_tool() {
    if ! command -v "$1" &>/dev/null; then
        echo "ERROR: $1 not found."
        echo "  Install: sudo apt install gcc-m68k-linux-gnu binutils-m68k-linux-gnu"
        exit 1
    fi
    echo "Found: $1 ($(command -v "$1"))"
}

echo "=== Checking toolchain ==="
check_tool "$CC"
check_tool "$OBJCOPY"
check_tool "$OBJDUMP"
echo ""

# ---------------------------------------------------------------------------
# Linker script — ROM layout matching DMP9/16 memory map
# ---------------------------------------------------------------------------
# ROM: 0x000000–0x07FFFF (512 KB flat, loaded by BinaryLoader at base 0)
# Stack: 0x400000 (DRAM, not in ROM image)
# The linker script places .text at 0x000000 so addresses match the original.

LDSCRIPT="$BUILD_DIR/dmp9.ld"
cat > "$LDSCRIPT" << 'LD_EOF'
/* DMP9/16 linker script — flat 68000 ROM, 512KB at 0x000000 */
OUTPUT_FORMAT("binary")
OUTPUT_ARCH(m68k)

ENTRY(_start)

SECTIONS {
    . = 0x00000000;

    .text : {
        /* Vector table placeholder — 96 × 4 bytes */
        . = 0x00000180;   /* 0x180 = end of vector table */
        *(.text*)
    }

    .rodata : { *(.rodata*) }
    .data   : { *(.data*)   }
    .bss    : { *(.bss*)    }

    /DISCARD/ : { *(.comment) *(.eh_frame) *(.note*) }
}
LD_EOF

# ---------------------------------------------------------------------------
# Compatibility shim header
# ---------------------------------------------------------------------------
# Ghidra emits types like undefined1/2/4, BADSPACEBASE pointers, etc.
# This header maps them to standard C types so the file at least parses.

COMPAT_H="$BUILD_DIR/ghidra_compat.h"
cat > "$COMPAT_H" << 'COMPAT_EOF'
/*
 * ghidra_compat.h — Map Ghidra decompiler pseudo-types to stdint.
 * Include before all_functions.h when compiling Ghidra output.
 */
#pragma once
#include <stdint.h>
#include <stddef.h>

/* Ghidra undefined types */
typedef uint8_t   undefined;
typedef uint8_t   undefined1;
typedef uint16_t  undefined2;
typedef uint32_t  undefined4;
typedef uint64_t  undefined8;

/* Ghidra code/data pointer type */
typedef void *    code;
typedef uint32_t  BADSPACEBASE;   /* used when Ghidra loses track of base */

/* Ghidra "bool" */
typedef uint8_t   bool;
#define true  1
#define false 0

/* 68k register-width integer */
typedef int32_t   int_m68k;
typedef uint32_t  uint_m68k;

/* Hardware I/O helpers */
#define MMIO8(addr)  (*(volatile uint8_t  *)(addr))
#define MMIO16(addr) (*(volatile uint16_t *)(addr))
#define MMIO32(addr) (*(volatile uint32_t *)(addr))
COMPAT_EOF

# ---------------------------------------------------------------------------
# GCC flags for 68000 bare-metal
# ---------------------------------------------------------------------------
#
# -m68000            Target the original MC68000 (no 68010/20/30 extensions)
# -Os                Optimise for size — closest to what Yamaha's compiler did
# -ffreestanding     No libc assumed
# -fno-builtin       Don't substitute memcpy etc. with builtins
# -fomit-frame-pointer  Yamaha code doesn't always use A6 as frame pointer
# -mshort            int = 16-bit (common for 68k embedded compilers of era)
# -nostdlib          No startup code or default libraries
# -Wall -Wno-...     Expect many warnings from Ghidra output

CFLAGS=(
    -m68000
    -Os
    -ffreestanding
    -fno-builtin
    -fomit-frame-pointer
    -mshort
    -nostdlib
    -Wall
    -Wno-implicit-function-declaration
    -Wno-int-conversion
    -Wno-unused-variable
    -Wno-unused-but-set-variable
    -Wno-pointer-to-int-cast
    -Wno-int-to-pointer-cast
    -Wno-incompatible-pointer-types
    -I"$EXPORT_DIR"
    -I"$BUILD_DIR"
    -include "$COMPAT_H"
)

echo "=== Compiling all_functions.c ==="
echo "  Flags: ${CFLAGS[*]}"
echo ""

COMPILE_LOG="$BUILD_DIR/compile.log"
COMPILE_OK=0

# Try to compile — capture errors but don't abort on failure
set +e
"$CC" "${CFLAGS[@]}" \
    -T "$LDSCRIPT" \
    -o "$BUILD_DIR/dmp9_decompiled.elf" \
    "$EXPORT_DIR/all_functions.c" \
    2>&1 | tee "$COMPILE_LOG"
COMPILE_RC=${PIPESTATUS[0]}
set -e

if [ $COMPILE_RC -eq 0 ]; then
    echo ""
    echo "  ✓ Compile SUCCEEDED (rc=0)"
    COMPILE_OK=1
else
    echo ""
    echo "  ✗ Compile FAILED (rc=$COMPILE_RC)"
    # Count errors/warnings for progress tracking
    ERRORS=$(grep -c "^.*: error:" "$COMPILE_LOG" 2>/dev/null || echo 0)
    WARNS=$(grep -c "^.*: warning:" "$COMPILE_LOG" 2>/dev/null || echo 0)
    echo "    Errors   : $ERRORS"
    echo "    Warnings : $WARNS"
    echo "    Log      : $COMPILE_LOG"
fi

# ---------------------------------------------------------------------------
# Per-function compilation (even if combined fails, try individuals)
# ---------------------------------------------------------------------------
echo ""
echo "=== Per-function compilation ==="

FUNC_DIR="$EXPORT_DIR/functions"
FUNC_BUILD="$BUILD_DIR/functions"
mkdir -p "$FUNC_BUILD"

FUNC_OK=0
FUNC_FAIL=0
FUNC_TOTAL=0

if [ -d "$FUNC_DIR" ]; then
    for src in "$FUNC_DIR"/*.c; do
        [ -f "$src" ] || continue
        fname=$(basename "$src" .c)
        FUNC_TOTAL=$((FUNC_TOTAL + 1))

        set +e
        "$CC" "${CFLAGS[@]}" \
            -c \
            -o "$FUNC_BUILD/${fname}.o" \
            "$src" \
            2>"$FUNC_BUILD/${fname}.err"
        rc=$?
        set -e

        if [ $rc -eq 0 ]; then
            FUNC_OK=$((FUNC_OK + 1))
        else
            FUNC_FAIL=$((FUNC_FAIL + 1))
            # Append to a summary of failures
            echo "${fname}: $(head -1 "$FUNC_BUILD/${fname}.err")" >> "$BUILD_DIR/func_errors.txt"
        fi
    done
    echo "  Functions compiled OK : $FUNC_OK / $FUNC_TOTAL"
    echo "  Functions failed      : $FUNC_FAIL / $FUNC_TOTAL"
    if [ $FUNC_FAIL -gt 0 ]; then
        echo "  Failure log : $BUILD_DIR/func_errors.txt"
    fi
else
    echo "  No per-function .c files found in $FUNC_DIR (run --export first)"
fi

# ---------------------------------------------------------------------------
# Binary extraction and ROM diff (only if linked binary was produced)
# ---------------------------------------------------------------------------

if [ $COMPILE_OK -eq 1 ]; then
    echo ""
    echo "=== Extracting raw binary ==="

    OUT_BIN="$BUILD_DIR/dmp9_decompiled.bin"
    "$OBJCOPY" -O binary "$BUILD_DIR/dmp9_decompiled.elf" "$OUT_BIN"
    BIN_SIZE=$(wc -c < "$OUT_BIN")
    echo "  Output: $OUT_BIN ($BIN_SIZE bytes)"

    if [ -n "$ORIGINAL_ROM" ] && [ -f "$ORIGINAL_ROM" ]; then
        echo ""
        echo "=== ROM diff ==="
        ROM_SIZE=$(wc -c < "$ORIGINAL_ROM")
        echo "  Original ROM : $ORIGINAL_ROM ($ROM_SIZE bytes)"
        echo "  Compiled bin : $OUT_BIN ($BIN_SIZE bytes)"

        # Count identical bytes using python (xxd+diff is fragile with binary)
        python3 - "$ORIGINAL_ROM" "$OUT_BIN" << 'PY'
import sys, os

rom_path, bin_path = sys.argv[1], sys.argv[2]
rom  = open(rom_path, 'rb').read()
binf = open(bin_path, 'rb').read()

compare_len = min(len(rom), len(binf))
matches = sum(r == b for r, b in zip(rom[:compare_len], binf[:compare_len]))
pct = 100.0 * matches / compare_len if compare_len else 0

print(f"  Compare length : {compare_len:,} bytes")
print(f"  Matching bytes : {matches:,} ({pct:.1f}%)")
print(f"  Differing bytes: {compare_len - matches:,} ({100-pct:.1f}%)")

if pct > 99.0:
    print("  *** EXCELLENT — >99% match. Near round-trip complete! ***")
elif pct > 80.0:
    print("  *** GOOD — majority of code reconstructed. ***")
elif pct > 20.0:
    print("  *** PARTIAL — significant sections match. Keep naming functions. ***")
else:
    print("  *** LOW — expected for early stage. Name more functions in Ghidra. ***")
PY
    else
        echo "  (No original ROM provided for diff — pass it as arg 2)"
    fi

    # Dump symbol table for cross-reference
    echo ""
    echo "=== Symbol table (named functions) ==="
    "$NM" --demangle --numeric-sort "$BUILD_DIR/dmp9_decompiled.elf" \
        2>/dev/null | grep " T " | head -30 || true
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
echo "=== Compile Summary ==="
echo "  Combined compile  : $([ $COMPILE_OK -eq 1 ] && echo 'OK' || echo 'FAILED')"
echo "  Per-function OK   : $FUNC_OK / $FUNC_TOTAL"
if [ $COMPILE_OK -eq 0 ]; then
    echo ""
    echo "Common fixes needed in Ghidra output:"
    echo "  • Add typedefs for any remaining undefined* types to $COMPAT_H"
    echo "  • Replace BADSPACEBASE dereferences with correct MMIO macros"
    echo "  • Add extern declarations for globals Ghidra rendered as magic numbers"
    echo "  • Hardware register accesses may need volatile qualifiers"
    echo ""
    echo "Then re-run:  bash compile.sh $EXPORT_DIR ${ORIGINAL_ROM:-<rom.bin>}"
fi
