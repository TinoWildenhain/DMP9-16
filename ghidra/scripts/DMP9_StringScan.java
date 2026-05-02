//Scans ROM for adjacent null-terminated strings (incl. HD44780 custom chars) and creates structs.
//@author DMP9 RE Project
//@category DMP9
//@keybinding
//@menupath
//@toolbar

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import java.util.*;

/**
 * DMP9_StringScan — auto-detects ROM string tables and creates structs.
 *
 * Run order: after DMP9_FuncFixup (headers parsed, memory blocks exist).
 * Run on any ROM version.
 *
 * String detection rules:
 *   - Valid string chars: printable ASCII (0x20-0x7E), HD44780 CGRAM custom
 *     chars (0x01-0x07) and other extended/control bytes (0x01-0x1F, 0x80-0xFF).
 *     0x00 is the terminator.
 *   - Length: total body >= 3, OR (total body >= 2 AND >= 2 printable ASCII chars).
 *
 * Cluster packing:
 *   - Strings are packed with no alignment between them. MAX_CLUSTER_GAP allows
 *     a single stray byte (e.g. length prefix); any actual gap bytes are emitted
 *     as pad_XXXX fields so the struct mirrors memory exactly.
 *
 * Label promotion:
 *   - Structs are always created and applied.
 *   - Labels are only created for clusters with at least one XREF from a known
 *     LCD string function (or any function-resident XREF in ROM).
 *
 * See: ghidra/docs/DMP9_StringScan.md for documentation.
 */
public class DMP9_StringScan extends GhidraScript {

    // Need at least 2 printable ASCII chars OR total body >= 3 chars
    private static final int MIN_PRINTABLE_CHARS = 2;
    private static final int MIN_TOTAL_LEN = 2;
    // Max gap between adjacent strings to keep them in the same cluster.
    // Strings are normally packed (gap=0); allow 1 stray byte for length prefix.
    private static final int MAX_CLUSTER_GAP = 1;
    // Minimum strings in a cluster to create a struct (avoid false positives)
    private static final int MIN_CLUSTER_STRINGS = 3;

    // Known LCD string function entry points (v1.11 — add others as confirmed)
    private static final long[] LCD_STRING_FUNCS = {
        0x78D4L,  // lcd_write_str
        0x793AL,  // lcd_write_str_row
        0x7B4EL,  // lcd_format_num
        0x7A06L,  // lcd_str_padded_l
        0x238CL,  // lcd_write_cmd
        0x23DCL,  // lcd_write_data
    };

    @Override
    public void run() throws Exception {
        println("[DMP9_StringScan] Scanning ROM for string table clusters...");

        Memory mem = currentProgram.getMemory();
        MemoryBlock romBlock = mem.getBlock("ROM");
        if (romBlock == null) {
            println("[DMP9_StringScan] ERROR: ROM block not found");
            return;
        }

        Address start = romBlock.getStart();
        Address end   = romBlock.getEnd();

        // Step 1: find all null-terminated strings in ROM
        List<long[]> strings = new ArrayList<>(); // [start, length-incl-null]
        long off = start.getOffset();
        while (off < end.getOffset() - MIN_TOTAL_LEN) {
            int[] run = getStringRun(mem, off, end.getOffset());
            int len = run[0];
            int printable = run[1];
            if (qualifies(len, printable)) {
                try {
                    byte term = mem.getByte(toAddr(off + len));
                    if (term == 0) {
                        strings.add(new long[]{off, len + 1}); // include null
                        off += len + 1;
                        continue;
                    }
                } catch (Exception e) { /* skip */ }
            }
            off++;
        }
        println("[DMP9_StringScan] Found " + strings.size() + " candidate strings");

        // Step 2: cluster adjacent strings (gap <= MAX_CLUSTER_GAP)
        List<List<long[]>> clusters = new ArrayList<>();
        List<long[]> current = new ArrayList<>();
        for (long[] s : strings) {
            if (current.isEmpty()) {
                current.add(s);
            } else {
                long[] prev = current.get(current.size() - 1);
                long prevEnd = prev[0] + prev[1];
                long gap = s[0] - prevEnd;
                if (gap <= MAX_CLUSTER_GAP) {
                    current.add(s);
                } else {
                    if (current.size() >= MIN_CLUSTER_STRINGS) clusters.add(current);
                    current = new ArrayList<>();
                    current.add(s);
                }
            }
        }
        if (current.size() >= MIN_CLUSTER_STRINGS) clusters.add(current);
        println("[DMP9_StringScan] Found " + clusters.size() + " string table clusters");

        // Step 3: for each cluster, create a struct in the data type manager
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        CategoryPath cat = new CategoryPath("/dmp9_strings");

        int structsCreated = 0;
        int labelsCreated = 0;
        for (int ci = 0; ci < clusters.size(); ci++) {
            List<long[]> cluster = clusters.get(ci);
            long clusterStart = cluster.get(0)[0];
            long[] last = cluster.get(cluster.size() - 1);
            long clusterEnd = last[0] + last[1] - 1;
            String structName = "str_table_" + Long.toHexString(clusterStart);

            // Check if struct already exists
            if (dtm.getDataType(cat, structName) != null) continue;

            StructureDataType st = new StructureDataType(cat, structName, 0);
            long pos = clusterStart;
            for (int si = 0; si < cluster.size(); si++) {
                long[] s = cluster.get(si);
                if (s[0] > pos) {
                    int pad = (int)(s[0] - pos);
                    st.add(new ArrayDataType(ByteDataType.dataType, pad, 1),
                           pad, "pad_" + Long.toHexString(pos), "stray bytes between strings");
                }
                String content = readString(mem, s[0], (int)s[1] - 1);
                int fieldLen = (int)s[1];
                st.add(new ArrayDataType(CharDataType.dataType, fieldLen, 1),
                       fieldLen, "str_" + Long.toHexString(s[0] - clusterStart),
                       "\"" + content.replace("\"", "\\\"") + "\"");
                pos = s[0] + s[1];
            }
            dtm.addDataType(st, DataTypeConflictHandler.KEEP_HANDLER);

            // Apply struct at the cluster start address (always)
            try {
                Address addr = toAddr(clusterStart);
                clearListing(addr, addr.add(st.getLength() - 1));
                createData(addr, st);
                structsCreated++;

                // Only create a label if the cluster has function XREFs
                if (hasLcdXref(clusterStart, clusterEnd)) {
                    createLabel(addr, structName, SourceType.ANALYSIS);
                    labelsCreated++;
                }
            } catch (Exception e) {
                println("[DMP9_StringScan] Warning: could not apply " + structName + ": " + e.getMessage());
            }
        }

        println("[DMP9_StringScan] Created " + structsCreated + " string table structs ("
                + labelsCreated + " labeled with function XREFs)");
        println("[DMP9_StringScan] TIP: Add your own patterns from confirmed string tables and re-run.");
        println("[DMP9_StringScan] TIP: Adjust MIN_PRINTABLE_CHARS / MIN_TOTAL_LEN / MAX_CLUSTER_GAP / MIN_CLUSTER_STRINGS if results are noisy.");
    }

    /**
     * Returns whether a byte counts as a string character.
     * Null = terminator. Printable ASCII, HD44780 custom chars (0x01-0x07),
     * other control/extended bytes (0x01-0x1F, 0x80-0xFF) all qualify.
     */
    private boolean isStringChar(byte b) {
        int u = b & 0xFF;
        if (u == 0x00) return false;
        if (u >= 0x20 && u <= 0x7E) return true;
        if (u >= 0x01 && u <= 0x1F) return true;
        if (u >= 0x80) return true;
        return false;
    }

    private boolean isPrintableAscii(byte b) {
        int u = b & 0xFF;
        return u >= 0x20 && u <= 0x7E;
    }

    /**
     * Scans forward and returns [total_len, printable_count] for the run of
     * string chars starting at `start` (exclusive of any terminating null).
     */
    private int[] getStringRun(Memory mem, long start, long limit) {
        int len = 0;
        int printable = 0;
        try {
            while (start + len < limit) {
                byte b = mem.getByte(toAddr(start + len));
                if (!isStringChar(b)) break;
                if (isPrintableAscii(b)) printable++;
                len++;
            }
        } catch (Exception e) { /* end of block */ }
        return new int[]{len, printable};
    }

    /**
     * A run qualifies if the body is >= 3 chars OR (body >= 2 AND printable >= 2).
     */
    private boolean qualifies(int len, int printable) {
        if (len >= 3) return true;
        if (len >= MIN_TOTAL_LEN && printable >= MIN_PRINTABLE_CHARS) return true;
        return false;
    }

    private String readString(Memory mem, long start, int maxLen) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            try {
                byte b = mem.getByte(toAddr(start + i));
                if (b == 0) break;
                if (b >= 0x20 && b <= 0x7E) sb.append((char)b);
                else sb.append('?');
            } catch (Exception e) { break; }
        }
        return sb.toString();
    }

    /**
     * Returns true if any address in [clusterStart, clusterEnd] is referenced
     * from inside a function — preferring known LCD string functions but
     * accepting any function-resident xref as a "live data" signal.
     */
    private boolean hasLcdXref(long clusterStart, long clusterEnd) {
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager fnMgr = currentProgram.getFunctionManager();
        for (long addr = clusterStart; addr <= clusterEnd; addr++) {
            try {
                ReferenceIterator refs = refMgr.getReferencesTo(toAddr(addr));
                while (refs.hasNext()) {
                    Reference ref = refs.next();
                    Address fromAddr = ref.getFromAddress();
                    for (long lcdFn : LCD_STRING_FUNCS) {
                        long delta = Math.abs(fromAddr.getOffset() - lcdFn);
                        if (delta < 512) return true;
                    }
                    Function fn = fnMgr.getFunctionContaining(fromAddr);
                    if (fn != null) return true;
                }
            } catch (Exception e) { /* skip */ }
        }
        return false;
    }
}
