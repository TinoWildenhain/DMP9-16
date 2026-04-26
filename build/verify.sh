#!/usr/bin/env bash
# =============================================================================
# verify.sh — DMP9/16 ROM Reconstruction verification script
# =============================================================================
#
# Usage:
#   ./build/verify.sh XN349G0           Verify built ROM against original
#   ./build/verify.sh --all             Verify all three versions
#
# What it checks:
#   1. Built .bin exists and is 524288 bytes (512 KB)
#   2. Vector table vec[0] (SSP) = 0x0040FED0
#   3. Data/coefficient table block (0x059B00–0x06283F) is identical to
#      original ROM (these bytes are invariant across all three versions)
#   4. Version string at 0x05059A contains expected text
#
# Exit code: 0 = all checks passed, 1 = one or more checks failed
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
ROMS_DIR="$REPO_ROOT/roms"
OUT_DIR="$REPO_ROOT/build/out"

COEFF_OFFSET=$((0x059B00))
COEFF_SIZE=$(( 0x062840 - 0x059B00 ))  # 35648 bytes
VER_OFFSET=$((0x05059A))
SSP_OFFSET=0   # vec[0] = bytes 0–3

declare -A ROM_FILES=(
    [XN349E0]="TMS27C240-10-DMP9-16-XN349E0-v1.02-10.11.1993.bin"
    [XN349F0]="TMS27C240-10-DMP9-16-XN349F0-v1.10-20.01.1994.bin"
    [XN349G0]="TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin"
)

declare -A VER_STRINGS=(
    [XN349E0]="Version 1.02"
    [XN349F0]="Version 1.10"
    [XN349G0]="Version 1.11"
)

PASS=0
FAIL=0

check() {
    local label="$1"
    local result="$2"   # "ok" or "fail: <msg>"
    if [ "$result" = "ok" ]; then
        echo "  [OK]   $label"
        PASS=$((PASS+1))
    else
        echo "  [FAIL] $label — ${result#fail: }"
        FAIL=$((FAIL+1))
    fi
}

verify_version() {
    local VER="$1"
    local BIN="$OUT_DIR/$VER/$VER.bin"
    local ORIG="$ROMS_DIR/${ROM_FILES[$VER]}"

    echo ""
    echo "=== $VER ==="

    # 1. Existence
    if [ ! -f "$BIN" ]; then
        echo "  [FAIL] $BIN not found — run: make VERSION=$VER"
        FAIL=$((FAIL+1))
        return
    fi

    # 2. Size
    actual=$(wc -c < "$BIN")
    if [ "$actual" -eq 524288 ]; then
        check "Size (524288 bytes)" "ok"
    else
        check "Size (524288 bytes)" "fail: got $actual bytes"
    fi

    # 3. SSP vector (vec[0]) = 0x0040FED0 (big-endian 32-bit)
    ssp=$(dd if="$BIN" bs=1 skip=$SSP_OFFSET count=4 2>/dev/null | xxd -p)
    if [ "$ssp" = "0040fed0" ]; then
        check "SSP vec[0] = 0x0040FED0" "ok"
    else
        check "SSP vec[0] = 0x0040FED0" "fail: got 0x$ssp"
    fi

    # 4. Coefficient table matches original (if original is available)
    if [ -f "$ORIG" ]; then
        orig_sha=$(dd if="$ORIG" bs=1 skip=$COEFF_OFFSET count=$COEFF_SIZE 2>/dev/null | sha256sum | cut -d' ' -f1)
        built_sha=$(dd if="$BIN" bs=1 skip=$COEFF_OFFSET count=$COEFF_SIZE 2>/dev/null | sha256sum | cut -d' ' -f1)
        if [ "$orig_sha" = "$built_sha" ]; then
            check "Coefficient table SHA256 matches original" "ok"
        else
            check "Coefficient table SHA256" "fail: expected $orig_sha, got $built_sha"
        fi
    else
        echo "  [SKIP] Coefficient table check — original ROM not found at $ORIG"
    fi

    # 5. Version string
    expected="${VER_STRINGS[$VER]}"
    actual_str=$(dd if="$BIN" bs=1 skip=$VER_OFFSET count=${#expected} 2>/dev/null | strings | head -1)
    if [[ "$actual_str" == *"$expected"* ]]; then
        check "Version string at 0x05059A contains \"$expected\"" "ok"
    else
        check "Version string at 0x05059A" "fail: got \"$actual_str\", expected \"$expected\""
    fi

    echo "  SHA256: $(sha256sum "$BIN" | cut -d' ' -f1)  $VER.bin"
}

main() {
    if [ "${1:-}" = "--all" ]; then
        VERSIONS=("XN349E0" "XN349F0" "XN349G0")
    elif [ -n "${1:-}" ]; then
        VERSIONS=("$1")
    else
        echo "Usage: $0 <VERSION> | --all"
        echo "  Versions: XN349E0 XN349F0 XN349G0"
        exit 1
    fi

    for v in "${VERSIONS[@]}"; do
        if [ -z "${ROM_FILES[$v]+_}" ]; then
            echo "Unknown version: $v"
            exit 1
        fi
        verify_version "$v"
    done

    echo ""
    echo "=== Summary: $PASS passed, $FAIL failed ==="
    [ "$FAIL" -eq 0 ]
}

main "$@"
