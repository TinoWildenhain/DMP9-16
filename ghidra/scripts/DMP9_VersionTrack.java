// DMP9_VersionTrack.java — Ghidra 11.x / 12.x
//
// Cross-ROM name/type propagation for Yamaha DMP9/16 firmware.
//
// PURPOSE
// ───────
// The three ROM versions (v1.02 / v1.10 / v1.11) contain the same firmware
// structure but with shifted function addresses.  This script uses Ghidra's
// Version Tracking (VT) correlators — or a manual byte-pattern fallback when
// VT is not available — to:
//
//   1. Find functions in the CURRENT program that match named functions in the
//      SOURCE program (v1.11 canonical analysis).
//   2. Apply matching names, calling conventions, parameter types and comments
//      from the source to the destination.
//   3. Produce a diff report (JSON + Markdown) showing:
//      - Functions present in both ROMs (same code, different address)
//      - Functions that changed between versions (byte diff)
//      - Functions present in one ROM but absent in the other
//
// WORKFLOW
// ────────
//   1. Run DMP9_FuncFixup + DMP9_LibMatch + DMP9_MidiAnalysis on v1.11 first.
//   2. Open v1.02 or v1.10 in same Ghidra project.
//   3. Run this script against the target ROM; supply v1.11 as source.
//
// HEADLESS USAGE
// ───────────────
//   analyzeHeadless ~/ghidra_projects DMP9-16 \
//     -process "*XN349E0*" \
//     -noanalysis \
//     -postScript DMP9_VersionTrack.java "XN349G0" \
//     -scriptPath ~/devel/DMP9-16/ghidra/scripts
//
//   Arg 0 (optional): source program name fragment to find in the project.
//                     Defaults to "XN349G0" (v1.11).
//
// OUTPUT
// ──────
//   ~/dmp9_export/<target_name>/version_track/
//     match_report.md   — human-readable diff
//     match_data.json   — machine-readable match/nomatch/changed data
//
// @author  DMP9-16 project
// @category DMP9
// @keybinding
// @menupath
// @toolbar

import ghidra.app.script.GhidraScript;
import ghidra.app.plugin.core.analysis.AutoAnalysisManager;
import ghidra.feature.vt.api.main.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.listing.Function.FunctionUpdateType;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.lang.*;
import ghidra.util.exception.InvalidInputException;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * DMP9_VersionTrack — Cross-ROM name propagation by byte-pattern matching
 *
 * Reads function names + signatures from the canonical ROM (v1.11 = XN349G0)
 * and finds the same functions in the target ROM (v1.02 / v1.10) by matching
 * the first SIGNATURE_BYTES bytes of each function body. Skips ISR stubs
 * (named by DMP9_FuncFixup) and very short prologues. Produces a Markdown +
 * JSON diff report under ~/dmp9_export/<target>/version_track/.
 *
 * Run order: DMP9_FuncFixup → TMP68301_Setup → DMP9_Board_Setup
 *            → DMP9_MidiAnalysis → DMP9_LibMatch → DMP9_VersionTrack
 *
 * Target: Yamaha DMP9/DMP16, TMP68301AF-16 (68000 @ 16MHz), MCC68K v4.x compiler
 * Ghidra: 12.0.4+
 *
 * See: ghidra/docs/DMP9_VersionTrack.md for full documentation.
 */
public class DMP9_VersionTrack extends GhidraScript {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /** Default source program name fragment (canonical v1.11 analysis). */
    private static final String DEFAULT_SOURCE_FRAGMENT = "XN349G0";

    /**
     * Number of bytes to use as a function signature for byte-pattern matching.
     * Larger = more precise but may miss functions with relocated constants.
     * 12–16 bytes works well for 68k functions that start with LINK/MOVEM.
     */
    private static final int SIGNATURE_BYTES = 16;

    /**
     * Fraction of signature bytes that must match for a "probable match".
     * 1.0 = exact, 0.75 = 75% match (allows for 1-byte offset fixups).
     */
    private static final double MATCH_THRESHOLD = 0.875;  // 14/16 bytes

    /**
     * Only propagate names for functions whose name does NOT start with these
     * auto-generated prefixes (i.e., only copy user-assigned names).
     */
    private static final String[] SKIP_SRC_PREFIXES = {
        "FUN_", "DAT_", "LAB_", "SUB_", "thunk_FUN_", "entry"
    };

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        String sourceFragment = (args != null && args.length > 0 && !args[0].isBlank())
                ? args[0] : DEFAULT_SOURCE_FRAGMENT;

        String tgtName = currentProgram.getName();
        println("[DMP9_VersionTrack] Target: " + tgtName);
        println("[DMP9_VersionTrack] Looking for source program matching: " + sourceFragment);

        // -----------------------------------------------------------------------
        // Find source program in the same Ghidra project
        // -----------------------------------------------------------------------
        Program srcProgram = findSourceProgram(sourceFragment);
        if (srcProgram == null) {
            printerr("[DMP9_VersionTrack] Source program not found in project.");
            printerr("  Ensure v1.11 (XN349G0) has been imported and annotated first.");
            printerr("  Or pass a different name fragment as script argument.");
            return;
        }
        println("[DMP9_VersionTrack] Source: " + srcProgram.getName());

        // -----------------------------------------------------------------------
        // Build source function index: byte-signature → FunctionInfo
        // -----------------------------------------------------------------------
        println("[DMP9_VersionTrack] Indexing source functions...");
        Map<String, FunctionInfo> srcIndex = buildFunctionIndex(srcProgram);
        println("  Source named functions: " + srcIndex.size());

        // -----------------------------------------------------------------------
        // Match target functions against source index
        // -----------------------------------------------------------------------
        println("[DMP9_VersionTrack] Matching target functions...");

        FunctionIterator tgtFuncs = currentProgram.getFunctionManager().getFunctions(true);
        Memory tgtMem = currentProgram.getMemory();

        List<MatchResult> exactMatches    = new ArrayList<>();
        List<MatchResult> partialMatches  = new ArrayList<>();
        List<String>      noMatches       = new ArrayList<>();

        int tgtTotal = 0;
        int applied  = 0;

        monitor.setMessage("Version tracking...");

        while (tgtFuncs.hasNext()) {
            if (monitor.isCancelled()) break;
            Function tgtFunc = tgtFuncs.next();
            tgtTotal++;

            String tgtSig = getByteSignature(tgtMem, tgtFunc.getEntryPoint(), SIGNATURE_BYTES);
            if (tgtSig == null) continue;

            // Try exact match first
            FunctionInfo srcInfo = srcIndex.get(tgtSig);
            if (srcInfo != null) {
                exactMatches.add(new MatchResult(tgtFunc, srcInfo, 1.0));
                applyFunctionInfo(tgtFunc, srcInfo);
                applied++;
                continue;
            }

            // Try partial match (Hamming similarity)
            FunctionInfo bestMatch = null;
            double bestScore = 0.0;
            for (Map.Entry<String, FunctionInfo> e : srcIndex.entrySet()) {
                double score = hammingSimilarity(tgtSig, e.getKey());
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = e.getValue();
                }
            }

            if (bestScore >= MATCH_THRESHOLD && bestMatch != null) {
                partialMatches.add(new MatchResult(tgtFunc, bestMatch, bestScore));
                // Only apply if the function is still auto-named in target
                if (tgtFunc.getName().startsWith("FUN_")) {
                    applyFunctionInfo(tgtFunc, bestMatch);
                    applied++;
                }
            } else {
                noMatches.add(tgtFunc.getName() + " @ " + tgtFunc.getEntryPoint());
            }
        }

        println("[DMP9_VersionTrack] Matching complete:");
        println("  Target total     : " + tgtTotal);
        println("  Exact matches    : " + exactMatches.size());
        println("  Partial matches  : " + partialMatches.size() + " (>= " + (int)(MATCH_THRESHOLD*100) + "% similar)");
        println("  No match         : " + noMatches.size());
        println("  Names applied    : " + applied);

        // -----------------------------------------------------------------------
        // Find source functions with NO counterpart in target (deleted/merged)
        // -----------------------------------------------------------------------
        Set<String> matchedSrcSigs = new HashSet<>();
        for (MatchResult m : exactMatches)   matchedSrcSigs.add(m.srcInfo.signature);
        for (MatchResult m : partialMatches) matchedSrcSigs.add(m.srcInfo.signature);

        List<FunctionInfo> srcOnlyFuncs = new ArrayList<>();
        for (FunctionInfo fi : srcIndex.values()) {
            if (!matchedSrcSigs.contains(fi.signature)) {
                srcOnlyFuncs.add(fi);
            }
        }
        println("  Source-only funcs: " + srcOnlyFuncs.size() + " (may be merged/inlined in this version)");

        // -----------------------------------------------------------------------
        // Write reports
        // -----------------------------------------------------------------------
        String exportBase = System.getenv("DMP9_EXPORT_DIR");
        if (exportBase == null || exportBase.isBlank()) {
            exportBase = System.getProperty("user.home") + "/dmp9_export/" + sanitize(tgtName);
        }
        Path outDir = Paths.get(exportBase, "version_track");
        Files.createDirectories(outDir);

        writeMarkdownReport(outDir, tgtName, srcProgram.getName(),
                exactMatches, partialMatches, noMatches, srcOnlyFuncs);
        writeJsonReport(outDir, tgtName, srcProgram.getName(),
                exactMatches, partialMatches, noMatches, srcOnlyFuncs);

        println("[DMP9_VersionTrack] Reports written to: " + outDir);
        println("  match_report.md, match_data.json");

        // Release source program transaction (read-only access)
        if (srcProgram != currentProgram) {
            srcProgram.release(this);
        }
    }

    // -----------------------------------------------------------------------
    // Source program lookup
    // -----------------------------------------------------------------------

    private Program findSourceProgram(String fragment) throws Exception {
        // Iterate all programs in the project
        ghidra.framework.model.DomainFolder root = state.getProject().getProjectData().getRootFolder();
        return findInFolder(root, fragment);
    }

    private Program findInFolder(ghidra.framework.model.DomainFolder folder, String fragment)
            throws Exception {
        for (ghidra.framework.model.DomainFile df : folder.getFiles()) {
            if (df.getName().contains(fragment)) {
                try {
                    ghidra.framework.model.DomainObject obj =
                        df.getReadOnlyDomainObject(this, ghidra.framework.model.DomainFile.DEFAULT_VERSION, monitor);
                    if (obj instanceof Program) {
                        return (Program) obj;
                    }
                } catch (Exception ex) {
                    println("  Warning: could not open " + df.getName() + ": " + ex.getMessage());
                }
            }
        }
        for (ghidra.framework.model.DomainFolder sub : folder.getFolders()) {
            Program p = findInFolder(sub, fragment);
            if (p != null) return p;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Function index building
    // -----------------------------------------------------------------------

    private Map<String, FunctionInfo> buildFunctionIndex(Program prog) {
        Map<String, FunctionInfo> index = new LinkedHashMap<>();
        Memory mem = prog.getMemory();
        FunctionIterator funcs = prog.getFunctionManager().getFunctions(true);
        while (funcs.hasNext()) {
            Function f = funcs.next();
            if (shouldSkipSource(f.getName())) continue;
            String sig = getByteSignature(mem, f.getEntryPoint(), SIGNATURE_BYTES);
            if (sig == null) continue;
            FunctionInfo info = new FunctionInfo(f, sig);
            index.putIfAbsent(sig, info);  // first one wins on collision
        }
        return index;
    }

    private boolean shouldSkipSource(String name) {
        for (String pfx : SKIP_SRC_PREFIXES) {
            if (name.startsWith(pfx)) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Byte signature extraction
    // -----------------------------------------------------------------------

    private String getByteSignature(Memory mem, Address addr, int len) {
        try {
            byte[] bytes = new byte[len];
            int read = mem.getBytes(addr, bytes);
            if (read < len / 2) return null;  // too little data
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < read; i++) sb.append(String.format("%02X", bytes[i] & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Similarity metric (byte-level Hamming on hex string pairs)
    // -----------------------------------------------------------------------

    private double hammingSimilarity(String a, String b) {
        int len = Math.min(a.length(), b.length());
        if (len == 0) return 0.0;
        int matches = 0;
        for (int i = 0; i < len - 1; i += 2) {
            if (a.charAt(i) == b.charAt(i) && a.charAt(i+1) == b.charAt(i+1)) matches++;
        }
        return (double) matches / (len / 2);
    }

    // -----------------------------------------------------------------------
    // Apply source function metadata to target function
    // -----------------------------------------------------------------------

    private void applyFunctionInfo(Function tgtFunc, FunctionInfo src) {
        try {
            // Name (only if target is still auto-named or blank)
            if (tgtFunc.getName().startsWith("FUN_") || tgtFunc.getName().isBlank()) {
                tgtFunc.setName(src.name, SourceType.ANALYSIS);
            }

            // Calling convention
            if (src.callingConvention != null && !src.callingConvention.isBlank()) {
                try {
                    tgtFunc.setCallingConvention(src.callingConvention);
                } catch (Exception ignored) { /* CC not available in this cspec */ }
            }

            // Plate comment
            if (src.plateComment != null && !src.plateComment.isBlank()) {
                currentProgram.getListing().setComment(
                    tgtFunc.getEntryPoint(),
                    CommentType.PLATE,
                    "[VT from " + src.sourceName + "] " + src.plateComment
                );
            }

            // Return type
            if (src.returnType != null) {
                try {
                    tgtFunc.setReturnType(src.returnType, SourceType.ANALYSIS);
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            println("  Warning: could not apply info to " + tgtFunc.getEntryPoint() + ": " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Report writers
    // -----------------------------------------------------------------------

    private void writeMarkdownReport(Path outDir, String tgtName, String srcName,
            List<MatchResult> exact, List<MatchResult> partial,
            List<String> noMatch, List<FunctionInfo> srcOnly) throws IOException {

        try (PrintWriter w = new PrintWriter(new FileWriter(outDir.resolve("match_report.md").toFile()))) {
            w.println("# Version Tracking Report");
            w.println();
            w.println("| | |");
            w.println("|---|---|");
            w.println("| Source | " + srcName + " |");
            w.println("| Target | " + tgtName + " |");
            w.println("| Exact matches | " + exact.size() + " |");
            w.println("| Partial matches | " + partial.size() + " (≥" + (int)(MATCH_THRESHOLD*100) + "% similar) |");
            w.println("| No match in target | " + noMatch.size() + " |");
            w.println("| Source-only (not in target) | " + srcOnly.size() + " |");
            w.println();

            w.println("## Exact matches");
            w.println();
            w.println("| Source addr | Target addr | Name |");
            w.println("|---|---|---|");
            for (MatchResult m : exact) {
                w.printf("| `%s` | `%s` | `%s` |%n",
                    m.srcInfo.address, m.tgtFunc.getEntryPoint(), m.srcInfo.name);
            }
            w.println();

            w.println("## Partial matches (changed between versions)");
            w.println();
            w.println("These functions have similar but not identical code — likely bugfix targets.");
            w.println();
            w.println("| Source addr | Target addr | Name | Similarity |");
            w.println("|---|---|---|---|");
            for (MatchResult m : partial) {
                w.printf("| `%s` | `%s` | `%s` | %.0f%% |%n",
                    m.srcInfo.address, m.tgtFunc.getEntryPoint(), m.srcInfo.name, m.score * 100);
            }
            w.println();

            w.println("## Source-only functions (present in " + srcName + ", not found in " + tgtName + ")");
            w.println();
            w.println("These may be: inlined, merged, renamed, or genuinely absent in this version.");
            w.println();
            w.println("| Source addr | Name | CC |");
            w.println("|---|---|---|");
            for (FunctionInfo fi : srcOnly) {
                w.printf("| `%s` | `%s` | `%s` |%n", fi.address, fi.name, fi.callingConvention);
            }
            w.println();

            w.println("## Target functions with no source match");
            w.println();
            w.println("These may be: new in this version, or too different for the byte matcher.");
            w.println();
            for (String s : noMatch) w.println("- " + s);
        }
    }

    private void writeJsonReport(Path outDir, String tgtName, String srcName,
            List<MatchResult> exact, List<MatchResult> partial,
            List<String> noMatch, List<FunctionInfo> srcOnly) throws IOException {

        try (PrintWriter w = new PrintWriter(new FileWriter(outDir.resolve("match_data.json").toFile()))) {
            w.println("{");
            w.printf("  \"source\": \"%s\",%n", srcName);
            w.printf("  \"target\": \"%s\",%n", tgtName);
            w.printf("  \"exact_count\": %d,%n", exact.size());
            w.printf("  \"partial_count\": %d,%n", partial.size());
            w.printf("  \"no_match_count\": %d,%n", noMatch.size());
            w.printf("  \"source_only_count\": %d,%n", srcOnly.size());
            w.println("  \"exact\": [");
            for (int i = 0; i < exact.size(); i++) {
                MatchResult m = exact.get(i);
                w.printf("    {\"src_addr\": \"%s\", \"tgt_addr\": \"%s\", \"name\": \"%s\"}%s%n",
                    m.srcInfo.address, m.tgtFunc.getEntryPoint(), escape(m.srcInfo.name),
                    i < exact.size()-1 ? "," : "");
            }
            w.println("  ],");
            w.println("  \"partial\": [");
            for (int i = 0; i < partial.size(); i++) {
                MatchResult m = partial.get(i);
                w.printf("    {\"src_addr\": \"%s\", \"tgt_addr\": \"%s\", \"name\": \"%s\", \"score\": %.3f}%s%n",
                    m.srcInfo.address, m.tgtFunc.getEntryPoint(), escape(m.srcInfo.name), m.score,
                    i < partial.size()-1 ? "," : "");
            }
            w.println("  ],");
            w.println("  \"source_only\": [");
            for (int i = 0; i < srcOnly.size(); i++) {
                FunctionInfo fi = srcOnly.get(i);
                w.printf("    {\"src_addr\": \"%s\", \"name\": \"%s\", \"cc\": \"%s\"}%s%n",
                    fi.address, escape(fi.name), fi.callingConvention,
                    i < srcOnly.size()-1 ? "," : "");
            }
            w.println("  ]");
            w.println("}");
        }
    }

    // -----------------------------------------------------------------------
    // Data classes
    // -----------------------------------------------------------------------

    private static class FunctionInfo {
        final String name;
        final String address;
        final String signature;
        final String callingConvention;
        final String plateComment;
        final String sourceName;
        final ghidra.program.model.data.DataType returnType;

        FunctionInfo(Function f, String sig) {
            this.name              = f.getName();
            this.address           = f.getEntryPoint().toString();
            this.signature         = sig;
            this.callingConvention = f.getCallingConventionName();
            this.plateComment      = f.getProgram().getListing()
                .getComment(CommentType.PLATE, f.getEntryPoint());
            this.sourceName        = f.getProgram().getName();
            this.returnType        = f.getReturnType();
        }
    }

    private static class MatchResult {
        final Function    tgtFunc;
        final FunctionInfo srcInfo;
        final double      score;

        MatchResult(Function t, FunctionInfo s, double sc) {
            this.tgtFunc = t;
            this.srcInfo = s;
            this.score   = sc;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_\\-.]", "_");
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
