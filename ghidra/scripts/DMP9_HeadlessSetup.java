/**
 * DMP9_HeadlessSetup.java — Ghidra headless post-analysis script
 *
 * Runs all DMP9/16 annotation scripts in order after auto-analysis.
 * Intended for use with analyzeHeadless (Ghidra batch/CI mode).
 *
 * Usage:
 *   analyzeHeadless <project_dir> DMP9 \
 *     -import <rom.bin> \
 *     -processor 68000 \
 *     -cspec tmp68301 \
 *     -loader BinaryLoader \
 *     -loader-baseAddr 0x0 \
 *     -postScript DMP9_HeadlessSetup.java
 *
 * Full command example (v1.11):
 *
 *   GHIDRA=$HOME/opt/ghidra_12.0.4_PUBLIC
 *   $GHIDRA/support/analyzeHeadless \
 *     $HOME/ghidra_projects DMP9 \
 *     -import TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin \
 *     -processor 68000 \
 *     -cspec tmp68301 \
 *     -loader BinaryLoader \
 *     -loader-baseAddr 0x0 \
 *     -loader-blockName ROM \
 *     -analysisTimeoutPerFile 600 \
 *     -postScript DMP9_HeadlessSetup.java \
 *     -scriptPath $HOME/DMP9-16/ghidra/scripts
 *
 * The script runs:
 *   1. TMP68301_Setup      — TMP68301 internal register labels
 *   2. DMP9_Board_Setup    — LCD/DSP/RAM region labels, HD44780 decoder
 *   3. DMP9_FuncFixup      — calling convention 3-pass fixer
 *   4. DMP9_LibMatch       — library function byte-pattern matcher
 *
 * @category DMP9-16.Automation
 */

import ghidra.app.script.GhidraScript;
import ghidra.app.util.headless.HeadlessScript;

public class DMP9_HeadlessSetup extends HeadlessScript {

    @Override
    public void run() throws Exception {
        println("=== DMP9_HeadlessSetup: starting post-analysis annotation ===");

        runScript("TMP68301_Setup.java");
        println("  [1/4] TMP68301_Setup done");

        runScript("DMP9_Board_Setup.java");
        println("  [2/4] DMP9_Board_Setup done");

        runScript("DMP9_FuncFixup.java");
        println("  [3/4] DMP9_FuncFixup done");

        runScript("DMP9_LibMatch.java");
        println("  [4/4] DMP9_LibMatch done");

        println("=== DMP9_HeadlessSetup: all scripts complete ===");
    }
}
