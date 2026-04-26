#!/usr/bin/env bash
# =============================================================================
# run_analysis.sh — DMP9/16 Ghidra headless analysis + round-trip export
#
# MODES
# ─────
#   --full         (default) Import ROMs + full auto-analysis + all scripts.
#                  Creates/overwrites the Ghidra project. Use on first run or
#                  after a cspec/ldefs change.
#
#   --incremental  Re-run annotation scripts only on already-imported ROMs.
#                  Skips auto-analysis (~3-5× faster). Use this after editing
#                  DMP9_FuncFixup, DMP9_LibMatch, DMP9_Board_Setup, etc.
#
#   --export       Re-run scripts (--incremental) then export decompiled C/H
#                  via DMP9_ExportC.java. Output goes to $DMP9_EXPORT_DIR or
#                  ~/dmp9_export/<romname>/
#
#   --export-only  Export decompiled C without re-running annotation scripts.
#                  Fastest option when you only changed function names in GUI.
#
# USAGE
# ─────
#   bash run_analysis.sh [--full|--incremental|--export|--export-only] [ROMS_DIR] [PROJECTS_DIR]
#
#   # First-time full import:
#   bash run_analysis.sh --full
#
#   # Iterate on a script fix (fast):
#   bash run_analysis.sh --incremental
#
#   # Dump decompiled C after renaming functions in GUI:
#   bash run_analysis.sh --export-only
#
# ENVIRONMENT
# ───────────
#   GHIDRAHOME       — Ghidra installation directory (required)
#   DMP9_EXPORT_DIR  — base output dir for --export (default: ~/dmp9_export)
#   DMP9_ROM_FILTER  — if set, only process ROMs matching this label
#                      e.g. DMP9_ROM_FILTER=XN349G0 bash run_analysis.sh --incremental
#
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Parse mode flag
# ---------------------------------------------------------------------------

MODE="full"
POSITIONAL=()

for arg in "$@"; do
    case "$arg" in
        --full)         MODE="full" ;;
        --incremental)  MODE="incremental" ;;
        --export)       MODE="export" ;;
        --export-only)  MODE="export-only" ;;
        --*)            echo "ERROR: Unknown flag: $arg"; echo "  Valid: --full --incremental --export --export-only"; exit 1 ;;
        *)              POSITIONAL+=("$arg") ;;
    esac
done

set -- "${POSITIONAL[@]+"${POSITIONAL[@]}"}"

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
EXPORT_DIR="${DMP9_EXPORT_DIR:-$HOME/dmp9_export}"
ROM_FILTER="${DMP9_ROM_FILTER:-}"

# ---------------------------------------------------------------------------
# Ghidra location
# ---------------------------------------------------------------------------

if [ -z "${GHIDRAHOME:-}" ]; then
    echo "ERROR: GHIDRAHOME environment variable is not set."
    echo "  export GHIDRAHOME=/home/tino/opt/ghidra_12.0.4_PUBLIC"
    exit 1
fi

ANALYZE_HEADLESS="$GHIDRAHOME/support/analyzeHeadless"

if [ ! -x "$ANALYZE_HEADLESS" ]; then
    echo "ERROR: analyzeHeadless not found or not executable at:"
    echo "  $ANALYZE_HEADLESS"
    echo "  Ensure GHIDRAHOME is set to a non-snap Ghidra install."
    exit 1
fi

GHIDRA_VERSION=$(grep -m1 'application.version=' \
    "$GHIDRAHOME/Ghidra/application.properties" 2>/dev/null \
    | cut -d= -f2 || echo "unknown")

echo "Ghidra: $GHIDRAHOME"
echo "Version: $GHIDRA_VERSION"
echo "Mode:    $MODE"

# ---------------------------------------------------------------------------
# Install language files (always done — cheap, idempotent)
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
}

echo ""
echo "=== Installing language files ==="
install_lang_files

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

mkdir -p "$PROJECTS_DIR" "$LOG_DIR"

# ---------------------------------------------------------------------------
# ROM table: "LABEL:VERSION:DATE:GLOB"
# ---------------------------------------------------------------------------

ROMS=(
    "XN349E0:v1.02:1993-11-10:*XN349E0*.bin"
    "XN349F0:v1.10:1994-01-20:*XN349F0*.bin"
    "XN349G0:v1.11:1994-03-10:*XN349G0*.bin"
)

ANALYSIS_TIMEOUT=900

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

find_rom() {
    local glob="$1"
    ls "$ROMS_DIR/"$glob 2>/dev/null | head -1 || true
}

# Run full import + analysis + all post-scripts
run_full() {
    local label="$1" version="$2" date="$3" glob="$4"
    local logfile="$LOG_DIR/headless_${label}.log"
    local rom_path
    rom_path=$(find_rom "$glob")

    echo ""
    echo "=== $label ($version, $date) — FULL ==="
    if [ -z "$rom_path" ]; then
        echo "  SKIP: No ROM matching '$glob' in $ROMS_DIR"
        return 0
    fi
    echo "  ROM: $rom_path"
    echo "  Log: $logfile"

    local t_start; t_start=$(date +%s)

    "$ANALYZE_HEADLESS" \
        "$PROJECTS_DIR" "$PROJECT_NAME" \
        -import "$rom_path" \
        -processor "68000:BE:32:default" \
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

    local rc=$?; local t_end; t_end=$(date +%s)
    local elapsed=$(( t_end - t_start ))
    if [ $rc -eq 0 ]; then echo "  OK: $label in ${elapsed}s"; else echo "  FAIL: $label (rc=$rc, ${elapsed}s)"; fi
    return $rc
}

# Re-run annotation scripts on existing project entry, no auto-analysis
run_incremental() {
    local label="$1" version="$2" date="$3" glob="$4"
    local logfile="$LOG_DIR/incremental_${label}.log"
    local rom_path; rom_path=$(find_rom "$glob")

    echo ""
    echo "=== $label ($version) — INCREMENTAL ==="
    if [ -z "$rom_path" ]; then echo "  SKIP: ROM not found"; return 0; fi

    local prog_name; prog_name=$(basename "$rom_path")
    echo "  Program: $prog_name"
    echo "  Log: $logfile"

    local t_start; t_start=$(date +%s)

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

    local rc=$?; local t_end; t_end=$(date +%s)
    local elapsed=$(( t_end - t_start ))
    if [ $rc -eq 0 ]; then echo "  OK: $label in ${elapsed}s"; else echo "  FAIL: $label (rc=$rc, ${elapsed}s)"; fi
    return $rc
}

# Run DMP9_ExportC.java on an existing project entry
run_export() {
    local label="$1" version="$2" date="$3" glob="$4"
    local logfile="$LOG_DIR/export_${label}.log"
    local rom_path; rom_path=$(find_rom "$glob")

    echo ""
    echo "=== $label ($version) — EXPORT ==="
    if [ -z "$rom_path" ]; then echo "  SKIP: ROM not found"; return 0; fi

    local prog_name; prog_name=$(basename "$rom_path")
    local label_export_dir="$EXPORT_DIR/$label"
    echo "  Program : $prog_name"
    echo "  Out dir : $label_export_dir"
    echo "  Log     : $logfile"
    mkdir -p "$label_export_dir"

    local t_start; t_start=$(date +%s)

    # Pass the output directory as a script argument
    DMP9_EXPORT_DIR="$label_export_dir" \
    "$ANALYZE_HEADLESS" \
        "$PROJECTS_DIR" "$PROJECT_NAME" \
        -process "$prog_name" \
        -noanalysis \
        -postScript "DMP9_ExportC.java" \
        -scriptPath "$SCRIPT_DIR" \
        -log "$logfile" \
        2>&1 | tee -a "$logfile"

    local rc=$?; local t_end; t_end=$(date +%s)
    local elapsed=$(( t_end - t_start ))
    if [ $rc -eq 0 ]; then
        echo "  OK: $label exported in ${elapsed}s → $label_export_dir"
        echo "  Files:"
        ls "$label_export_dir/" 2>/dev/null | sed 's/^/    /'
    else
        echo "  FAIL: $label (rc=$rc, ${elapsed}s)"
    fi
    return $rc
}

# ---------------------------------------------------------------------------
# Pre-script (written inline if missing)
# ---------------------------------------------------------------------------

PRESCRIPT_PATH="$SCRIPT_DIR/DMP9_AnalysisOptions.java"
if [ ! -f "$PRESCRIPT_PATH" ]; then
cat > "$PRESCRIPT_PATH" << 'JAVA_EOF'
// DMP9_AnalysisOptions.java — Ghidra pre-script
//@category DMP9
import ghidra.app.script.GhidraScript;
public class DMP9_AnalysisOptions extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("DMP9_AnalysisOptions: configuring analysis options");
        setAnalysisOption(currentProgram, "Disassemble Entry Points", "true");
        setAnalysisOption(currentProgram, "Create Address Tables", "true");
        setAnalysisOption(currentProgram, "Stack", "true");
        setAnalysisOption(currentProgram, "Decompiler Parameter ID", "true");
        setAnalysisOption(currentProgram, "Reference", "true");
        setAnalysisOption(currentProgram, "ARM Aggressive Instruction Finder", "false");
        println("DMP9_AnalysisOptions: done");
    }
}
JAVA_EOF
    echo "Created pre-script: $PRESCRIPT_PATH"
fi

# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------

echo ""
echo "=== DMP9/16 Ghidra Headless Analysis ==="
echo "Project  : $PROJECTS_DIR/$PROJECT_NAME"
echo "ROMs dir : $ROMS_DIR"
echo "Scripts  : $SCRIPT_DIR"
[ -n "$ROM_FILTER" ] && echo "Filter   : $ROM_FILTER (only processing this label)"
echo ""

FAIL_COUNT=0
PROCESSED=0

for entry in "${ROMS[@]}"; do
    IFS=':' read -r label version date glob <<< "$entry"

    # Apply label filter if set
    if [ -n "$ROM_FILTER" ] && [ "$label" != "$ROM_FILTER" ]; then
        continue
    fi
    PROCESSED=$((PROCESSED + 1))

    case "$MODE" in
        full)
            run_full "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
            ;;
        incremental)
            run_incremental "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
            ;;
        export)
            run_incremental "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
            run_export      "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
            ;;
        export-only)
            run_export "$label" "$version" "$date" "$glob" || FAIL_COUNT=$((FAIL_COUNT + 1))
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
echo "=== Summary ==="
echo "Mode     : $MODE"
echo "ROMs     : $PROCESSED processed"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "Result   : ALL PASSED"
    echo ""
    echo "Tips:"
    case "$MODE" in
        full)
            echo "  → Open Ghidra GUI: project at $PROJECTS_DIR/$PROJECT_NAME"
            echo "  → Start with XN349G0 (v1.11) — canonical analysis target"
            echo "  → Navigate to reset_handler @ 0x3BF4E to begin"
            echo "  → After renaming functions, run:  bash run_analysis.sh --export-only"
            ;;
        incremental)
            echo "  → Scripts re-applied to existing analysis."
            echo "  → To export decompiled C: bash run_analysis.sh --export-only"
            ;;
        export|export-only)
            echo "  → Decompiled C in: $EXPORT_DIR/"
            echo "  → To attempt compilation: bash $REPO_ROOT/ghidra/scripts/compile.sh $EXPORT_DIR/XN349G0"
            ;;
    esac
    exit 0
else
    echo "Result   : $FAIL_COUNT step(s) FAILED"
    echo "  Check logs in $LOG_DIR/"
    exit 1
fi
