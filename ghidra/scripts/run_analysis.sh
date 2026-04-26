#!/usr/bin/env bash
# =============================================================================
# run_analysis.sh — DMP9/16 Ghidra headless analysis CI script
#
# Runs full analyzeHeadless pipeline for all three DMP9/16 ROM versions:
#   XN349E0 (v1.02), XN349F0 (v1.10), XN349G0 (v1.11)
#
# Usage:
#   bash run_analysis.sh [ROMS_DIR] [PROJECTS_DIR]
#
# Environment:
#   GHIDRAHOME  — path to Ghidra installation directory
#                 (must contain support/analyzeHeadless)
#                 e.g. /home/tino/opt/ghidra_12.0.4_PUBLIC
#
# Arguments (both optional, have defaults):
#   ROMS_DIR     — directory containing the .bin ROM files (default: ../../roms)
#   PROJECTS_DIR — Ghidra project directory (default: ~/ghidra_projects)
#
# The script:
#   1. Verifies Ghidra installation
#   2. Installs custom language files if not already present
#   3. Runs analyzeHeadless on all three ROMs in a shared project
#   4. Runs all annotation scripts (TMP68301_Setup, DMP9_Board_Setup,
#      DMP9_FuncFixup, DMP9_LibMatch)
#   5. Prints per-ROM timing and pass/fail summary
#
# Requires Ghidra 11.x or 12.x (non-snap install).
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ROMS_DIR="${1:-$REPO_ROOT/roms}"
PROJECTS_DIR="${2:-$HOME/ghidra_projects}"

PROJECT_NAME="DMP9-16"
LANG_DIR="$REPO_ROOT/ghidra/lang"
LOG_DIR="$REPO_ROOT/ghidra/logs"

# ---------------------------------------------------------------------------
# Ghidra location
# ---------------------------------------------------------------------------

if [ -z "${GHIDRAHOME:-}" ]; then
    echo "ERROR: GHIDRAHOME environment variable is not set."
    echo "  Set it to your Ghidra installation directory, e.g.:"
    echo "  export GHIDRAHOME=/home/tino/opt/ghidra_12.0.4_PUBLIC"
    exit 1
fi

ANALYZE_HEADLESS="$GHIDRAHOME/support/analyzeHeadless"

if [ ! -x "$ANALYZE_HEADLESS" ]; then
    echo "ERROR: analyzeHeadless not found or not executable at:"
    echo "  $ANALYZE_HEADLESS"
    echo "  Check that GHIDRAHOME is set correctly and is not a snap install."
    exit 1
fi

GHIDRA_VERSION=$("$ANALYZE_HEADLESS" --version 2>/dev/null | head -1 || echo "unknown")
echo "Ghidra: $GHIDRAHOME"
echo "Version: $GHIDRA_VERSION"

# ---------------------------------------------------------------------------
# Install language files
# ---------------------------------------------------------------------------

GHIDRA_LANG_DIR="$GHIDRAHOME/Ghidra/Processors/68000/data/languages"

install_lang_files() {
    local changed=0
    for f in tmp68301.cspec 68000.ldefs; do
        src="$LANG_DIR/$f"
        dst="$GHIDRA_LANG_DIR/$f"
        if [ ! -f "$src" ]; then
            echo "WARNING: Language file not found: $src (skipping)"
            continue
        fi
        if [ ! -f "$dst" ] || ! cmp -s "$src" "$dst"; then
            cp "$src" "$dst"
            echo "Installed: $dst"
            changed=1
        else
            echo "Up to date: $dst"
        fi
    done
    if [ "$changed" -eq 1 ]; then
        echo "Language files updated — Ghidra will reload them on next run."
    fi
}

echo ""
echo "=== Installing language files ==="
install_lang_files

# ---------------------------------------------------------------------------
# Setup directories
# ---------------------------------------------------------------------------

mkdir -p "$PROJECTS_DIR"
mkdir -p "$LOG_DIR"

# ---------------------------------------------------------------------------
# ROM definitions
# ---------------------------------------------------------------------------

# Array of: "LABEL:VERSION:DATE:FILENAME_GLOB"
ROMS=(
    "XN349E0:v1.02:1993-11-10:*XN349E0*.bin"
    "XN349F0:v1.10:1994-01-20:*XN349F0*.bin"
    "XN349G0:v1.11:1994-03-10:*XN349G0*.bin"
)

# ---------------------------------------------------------------------------
# Analysis options
# ---------------------------------------------------------------------------

# Analyser timeout per file (seconds)
ANALYSIS_TIMEOUT=900

# Extra analysis options — passed to the pre-script via environment
DECOMPILER_PARAM_ID=true

# ---------------------------------------------------------------------------
# Pre-script: set analysis options (written inline)
# ---------------------------------------------------------------------------

PRESCRIPT_PATH="$SCRIPT_DIR/DMP9_AnalysisOptions.java"
if [ ! -f "$PRESCRIPT_PATH" ]; then
cat > "$PRESCRIPT_PATH" << 'JAVA_EOF'
// DMP9_AnalysisOptions.java — Ghidra pre-script
// Sets analysis options for optimal 68000 ROM analysis.
// Run as -preScript before auto-analysis.
//@category DMP9
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Program;

public class DMP9_AnalysisOptions extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("DMP9_AnalysisOptions: configuring analysis options");
        setAnalysisOption(currentProgram, "Disassemble Entry Points", "true");
        setAnalysisOption(currentProgram, "Create Address Tables", "true");
        setAnalysisOption(currentProgram, "Stack", "true");
        setAnalysisOption(currentProgram, "Decompiler Parameter ID", "true");
        setAnalysisOption(currentProgram, "Reference", "true");
        // Disable aggressive ARM/THUMB finder (irrelevant for 68k)
        setAnalysisOption(currentProgram, "ARM Aggressive Instruction Finder", "false");
        println("DMP9_AnalysisOptions: done");
    }
}
JAVA_EOF
    echo "Created pre-script: $PRESCRIPT_PATH"
fi

# ---------------------------------------------------------------------------
# Helper: find ROM file
# ---------------------------------------------------------------------------

find_rom() {
    local glob="$1"
    local found
    found=$(ls "$ROMS_DIR"/$glob 2>/dev/null | head -1)
    echo "$found"
}

# ---------------------------------------------------------------------------
# Helper: run analyzeHeadless for one ROM
# ---------------------------------------------------------------------------

run_headless() {
    local label="$1"
    local version="$2"
    local date="$3"
    local glob="$4"
    local logfile="$LOG_DIR/headless_${label}.log"

    echo ""
    echo "=== $label ($version, $date) ==="

    local rom_path
    rom_path=$(find_rom "$glob")

    if [ -z "$rom_path" ]; then
        echo "  SKIP: No ROM file matching '$glob' in $ROMS_DIR"
        echo "  Place the ROM as: $ROMS_DIR/TMS27C240-10-DMP9-16-${label}-${version}-*.bin"
        return 0
    fi

    echo "  ROM: $rom_path"
    echo "  Log: $logfile"

    local t_start
    t_start=$(date +%s)

    # First pass: import + analyze + annotate
    "$ANALYZE_HEADLESS" \
        "$PROJECTS_DIR" "$PROJECT_NAME" \
        -import "$rom_path" \
        -processor 68000 \
        -cspec tmp68301 \
        -loader BinaryLoader \
        -loader-baseAddr 0x0 \
        -loader-blockName ROM \
        -loader-length 0x80000 \
        -analysisTimeoutPerFile "$ANALYSIS_TIMEOUT" \
        -overwrite \
        -preScript DMP9_AnalysisOptions.java \
        -postScript TMP68301_Setup.java \
        -postScript DMP9_Board_Setup.java \
        -postScript DMP9_FuncFixup.java \
        -postScript DMP9_LibMatch.java \
        -scriptPath "$SCRIPT_DIR" \
        -log "$logfile" \
        2>&1 | tee -a "$logfile"

    local exit_code=$?
    local t_end
    t_end=$(date +%s)
    local elapsed=$((t_end - t_start))

    if [ $exit_code -eq 0 ]; then
        echo "  OK: $label completed in ${elapsed}s"
        return 0
    else
        echo "  FAIL: $label exited with code $exit_code after ${elapsed}s"
        echo "  See log: $logfile"
        return $exit_code
    fi
}

# ---------------------------------------------------------------------------
# Helper: re-run scripts on already-imported ROM (no re-import)
# ---------------------------------------------------------------------------

rerun_scripts() {
    local label="$1"
    local version="$2"
    local date="$3"
    local glob="$4"
    local logfile="$LOG_DIR/rerun_${label}.log"

    echo ""
    echo "=== Re-run scripts: $label ($version) ==="

    # Derive the program name from the ROM filename
    local rom_path
    rom_path=$(find_rom "$glob")
    if [ -z "$rom_path" ]; then
        echo "  SKIP: ROM not found"
        return 0
    fi

    local prog_name
    prog_name=$(basename "$rom_path")

    "$ANALYZE_HEADLESS" \
        "$PROJECTS_DIR" "$PROJECT_NAME" \
        -process "$prog_name" \
        -noanalysis \
        -postScript TMP68301_Setup.java \
        -postScript DMP9_Board_Setup.java \
        -postScript DMP9_FuncFixup.java \
        -postScript DMP9_LibMatch.java \
        -scriptPath "$SCRIPT_DIR" \
        -log "$logfile" \
        2>&1 | tee -a "$logfile"

    echo "  Done. Log: $logfile"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

echo ""
echo "=== DMP9/16 Ghidra Headless Analysis ==="
echo "Project: $PROJECTS_DIR/$PROJECT_NAME"
echo "ROMs:    $ROMS_DIR"
echo "Scripts: $SCRIPT_DIR"
echo ""

# Parse command-line flags
MODE="${DMP9_MODE:-full}"   # full | rerun
FAIL_COUNT=0

for entry in "${ROMS[@]}"; do
    IFS=':' read -r label version date glob <<< "$entry"
    if [ "$MODE" = "rerun" ]; then
        rerun_scripts "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
    else
        run_headless "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
done

echo ""
echo "=== Summary ==="
echo "ROMs processed: ${#ROMS[@]}"
if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "Result: ALL PASSED"
    echo ""
    echo "Next steps:"
    echo "  1. Open Ghidra GUI → project at $PROJECTS_DIR/$PROJECT_NAME"
    echo "  2. Start with XN349G0 (v1.11) — canonical analysis target"
    echo "  3. Check Script Console for annotation results"
    echo "  4. Navigate to 0x0003BF4E (reset_handler) to begin review"
    echo "  5. Use Version Tracking to propagate markup to v1.10 and v1.02"
    exit 0
else
    echo "Result: $FAIL_COUNT ROM(s) FAILED"
    echo "  Check logs in $LOG_DIR/"
    exit 1
fi
