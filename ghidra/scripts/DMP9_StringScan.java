//Scans ROM for adjacent null-terminated ASCII strings and creates structs.
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
 * See: ghidra/docs/DMP9_StringScan.md for documentation.
 */
public class DMP9_StringScan extends GhidraScript {

    // Minimum printable ASCII run to qualify as a string
    private static final int MIN_STRING_LEN = 3;
    // Maximum gap between adjacent strings to merge into same struct (bytes)
    private static final int MAX_CLUSTER_GAP = 4;
    // Minimum strings in a cluster to create a struct (avoid false positives)
    private static final int MIN_CLUSTER_STRINGS = 3;

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

        // Step 1: find all null-terminated ASCII strings in ROM
        List<long[]> strings = new ArrayList<>(); // [start, length-incl-null]
        long off = start.getOffset();
        while (off < end.getOffset() - MIN_STRING_LEN) {
            int len = getPrintableRunLen(mem, off, end.getOffset());
            if (len >= MIN_STRING_LEN) {
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

        // Step 2: cluster adjacent strings
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
        for (int ci = 0; ci < clusters.size(); ci++) {
            List<long[]> cluster = clusters.get(ci);
            long clusterStart = cluster.get(0)[0];
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
                           pad, "pad_" + Long.toHexString(pos), "alignment");
                }
                String content = readString(mem, s[0], (int)s[1] - 1);
                int fieldLen = (int)s[1];
                st.add(new ArrayDataType(CharDataType.dataType, fieldLen, 1),
                       fieldLen, "str_" + Long.toHexString(s[0] - clusterStart),
                       "\"" + content.replace("\"", "\\\"") + "\"");
                pos = s[0] + s[1];
            }
            dtm.addDataType(st, DataTypeConflictHandler.KEEP_HANDLER);

            // Apply struct at the cluster start address
            try {
                Address addr = toAddr(clusterStart);
                clearListing(addr, addr.add(st.getLength() - 1));
                createData(addr, st);
                createLabel(addr, structName, SourceType.ANALYSIS);
                structsCreated++;
            } catch (Exception e) {
                println("[DMP9_StringScan] Warning: could not apply " + structName + ": " + e.getMessage());
            }
        }

        println("[DMP9_StringScan] Created " + structsCreated + " string table structs");
        println("[DMP9_StringScan] TIP: Add your own patterns from confirmed string tables and re-run.");
        println("[DMP9_StringScan] TIP: Adjust MIN_STRING_LEN / MAX_CLUSTER_GAP / MIN_CLUSTER_STRINGS if results are noisy.");
    }

    private int getPrintableRunLen(Memory mem, long start, long limit) {
        int len = 0;
        try {
            while (start + len < limit) {
                byte b = mem.getByte(toAddr(start + len));
                // printable ASCII: 0x20-0x7E, plus common control: 0x0A, 0x0D
                if ((b >= 0x20 && b <= 0x7E) || b == 0x0A || b == 0x0D) {
                    len++;
                } else {
                    break;
                }
            }
        } catch (Exception e) { /* end of block */ }
        return len;
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
}
