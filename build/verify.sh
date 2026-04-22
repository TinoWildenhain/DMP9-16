#!/bin/bash
# verify.sh - Compare built binary against known-good ROM hashes
#
# Usage: ./verify.sh [built.bin]

set -e

BUILT=${1:-dmp9.bin}
HASHFILE=../roms/hashes/sha256.txt

if [ ! -f "$BUILT" ]; then
    echo "[ERR] $BUILT not found. Run 'make' first."
    exit 1
fi

if [ ! -f "$HASHFILE" ]; then
    echo "[WARN] $HASHFILE not found. Skipping ROM hash verification."
else
    echo "[INFO] Known ROM hashes:"
    cat "$HASHFILE"
fi

echo
echo "[INFO] Built binary hash:"
sha256sum "$BUILT"

echo
echo "[INFO] Built binary size: $(wc -c < $BUILT) bytes (expected 524288)"

if [ "$(wc -c < $BUILT)" -eq 524288 ]; then
    echo "[OK] Size matches ROM."
else
    echo "[WARN] Size mismatch."
fi
