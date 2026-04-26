// DMP9_LibMatch.java — Ghidra 11.x / 12.x
//
// Byte-pattern based library function matcher for Yamaha DMP9/16 firmware.
//
// Matches common C runtime / compiler-support functions (memcpy, memset,
// memcmp, strlen, strcpy, etc.) using masked byte patterns derived from
// observed 68k implementations. Patterns are architecture-specific but
// compiler-agnostic where possible — wildcards (MASK=0x00) cover register
// choices and minor compiler variations.
//
// HOW TO ADD YOUR OWN PATTERNS
// ────────────────────────────
// 1. In Ghidra, navigate to a known function (e.g. memcpy you identified manually).
// 2. Copy the first ~8–12 bytes of the function body from the Listing window.
// 3. Add an entry to PATTERNS below. Use 0xFF mask for bytes you want to
//    match exactly; 0x00 for "don't care" (register numbers, displacements).
//    For 68k: the low 3 bits of the first word often encode the register,
//    so masking with 0xFFF8 or 0xFF00 is common.
// 4. Run the script — it will report match addresses and rename them.
//
// PATTERN FORMAT
// ──────────────
// Each pattern entry is:
//   { name, comment, category, isNoReturn, bytes[], masks[] }
// bytes[] and masks[] must be the same length.
// A byte matches if (romByte & mask) == (patternByte & mask).
// mask=0xFF → exact match. mask=0x00 → wildcard (always matches).
//
// @author  DMP9-16 project
// @category DMP9
// @keybinding
// @menupath
// @toolbar

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.listing.Function.FunctionUpdateType;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.lang.*;
import ghidra.util.exception.InvalidInputException;

import java.util.*;

public class DMP9_LibMatch extends GhidraScript {

    // =========================================================================
    // Pattern table
    // Each entry: { name, plateComment, callingConvention, noReturn, bytes[], masks[] }
    //
    // These patterns target Fujitsu/Toshiba 68k embedded C runtime (which is
    // what TMP68301 firmware typically uses). They cover:
    //   - Both "register-passing" variants (args in D0/A0) and
    //   - "stack-passing" variants (args pushed before JSR)
    // so the same function may match multiple entries — the script picks the
    // first match and reports ambiguity if more than one matches the same addr.
    //
    // *** IMPORTANT: run this script AFTER DMP9_FuncFixup.java so calling
    //     conventions are already assigned. This script will NOT overwrite
    //     a calling convention that was already set to yamaha_reg or __stdcall.
    // =========================================================================

    static class Pattern {
        final String   name;
        final String   comment;
        final String   callingConvention; // null = don't change
        final boolean  noReturn;
        final byte[]   bytes;
        final byte[]   masks;

        Pattern(String name, String comment, String cc, boolean noReturn,
                int[] bytes, int[] masks) {
            this.name   = name;
            this.comment = comment;
            this.callingConvention = cc;
            this.noReturn = noReturn;
            this.bytes  = new byte[bytes.length];
            this.masks  = new byte[masks.length];
            for (int i = 0; i < bytes.length; i++) {
                this.bytes[i] = (byte)(bytes[i] & 0xFF);
                this.masks[i] = (byte)(masks[i] & 0xFF);
            }
        }
    }

    // ── Calling convention name constants (must match your cspec) ──────────
    private static final String CC_REG   = "yamaha_reg";
    private static final String CC_STD   = "__stdcall";
    private static final String CC_UNK   = "unknown";

    // ── Pattern table ─────────────────────────────────────────────────────
    //
    // 68k byte layout reminders:
    //   MOVE.L  An,Dm   = 0x2008 | (m<<9) | n   → mask hi byte for reg variants
    //   MOVE.W  Dn,Dm   = 0x3000-ish
    //   MOVEM.L regs,-(SP) = 0x48E7 xxxx
    //   LINK    A6,#N   = 0x4E56 NNNN
    //   SUBQ.L  #n,SP   = 0x5F8F (n=1..8)
    //   MOVEQ   #0,Dn   = 0x7000 | (n<<9)  → mask for reg
    //   CLR.B   (An)+   = 0x4218 | n
    //   CMPA.L  An,Ak   = 0xB1CA-ish
    //   BEQ     rel8    = 0x6700  (mask rel byte 0x00)
    //   BNE     rel8    = 0x6600
    //   RTS             = 0x4E75
    //   MOVE.B  (An)+,(Am)+  = 0x10C0 | (m<<9) | n+8 (post-inc both)

    private static final Pattern[] PATTERNS = {

        // ── memset (register variant: A0=ptr, D0=val, D1=count) ──────────
        // Classic 68k: loop with CLR or MOVE.B (A0)+
        // MOVEQ #0,D0 / TST.L D1 / BLE.S end / CLR.B (A0)+ / SUBQ.L #1,D1 / BNE ...
        new Pattern("memset",
            "void *memset(void *s /*A0*/, int c /*D0.b*/, size_t n /*D1*/)\n" +
            "Register-calling variant: A0=dest, D0.b=fill, D1=count",
            CC_REG, false,
            new int[]{ 0x4A81,       // TST.L D1
                       0x6F00,       // BLE.S end  (mask offset)
                       0x1140, 0x00, // MOVE.B D0,(A0)+  — rough
                       0x5381,       // SUBQ.L #1,D1
                       0x66FA },     // BNE.S loop
            new int[]{ 0xFFFF,
                       0xFF00,
                       0xFF00, 0x00,
                       0xFFFF,
                       0xFFFF }),

        // ── memset (stack variant: 4(SP)=ptr, 8(SP)=val, 12(SP)=count) ──
        // LINK A6,#0 / MOVEA.L 8(A6),A0 / MOVE.B 12(A6),D0 / ...
        new Pattern("memset",
            "void *memset(void *s, int c, size_t n) — stack-calling variant",
            CC_STD, false,
            new int[]{ 0x4E56, 0x0000, // LINK A6,#0
                       0x206E, 0x0008, // MOVEA.L 8(A6),A0
                       0x102E, 0x000C, // MOVE.B 12(A6),D0
                       0x222E, 0x0010  // MOVE.L 16(A6),D1
                     },
            new int[]{ 0xFFFF, 0xFFFF,
                       0xFFFF, 0xFFFF,
                       0xFFFF, 0xFFFF,
                       0xFFFF, 0xFFFF }),

        // ── memcpy (register variant: A0=dst, A1=src, D0=count) ──────────
        // MOVE.B (A1)+,(A0)+ loop
        new Pattern("memcpy",
            "void *memcpy(void *dst /*A0*/, const void *src /*A1*/, size_t n /*D0*/)\n" +
            "Register-calling variant",
            CC_REG, false,
            new int[]{ 0x4A80,       // TST.L D0
                       0x6700,       // BEQ.S end (wildcard offset)
                       0x10D9,       // MOVE.B (A1)+,(A0)+
                       0x5380,       // SUBQ.L #1,D0
                       0x66FA },     // BNE.S loop
            new int[]{ 0xFFFF,
                       0xFF00,
                       0xFFFF,
                       0xFFFF,
                       0xFFFF }),

        // ── memcpy (register variant 2: A0=dst, A1=src, D1=count) ────────
        new Pattern("memcpy",
            "void *memcpy(void *dst /*A0*/, const void *src /*A1*/, size_t n /*D1*/)\n" +
            "Register-calling variant (count in D1)",
            CC_REG, false,
            new int[]{ 0x4A81,       // TST.L D1
                       0x6700,       // BEQ.S end
                       0x10D9,       // MOVE.B (A1)+,(A0)+
                       0x5381,       // SUBQ.L #1,D1
                       0x66FA },     // BNE.S
            new int[]{ 0xFFFF, 0xFF00, 0xFFFF, 0xFFFF, 0xFFFF }),

        // ── memcpy (stack variant) ────────────────────────────────────────
        new Pattern("memcpy",
            "void *memcpy(void *dst, const void *src, size_t n) — stack variant",
            CC_STD, false,
            new int[]{ 0x4E56, 0x0000, // LINK A6,#0
                       0x206E, 0x0008, // MOVEA.L 8(A6),A0 = dst
                       0x226E, 0x000C, // MOVEA.L 12(A6),A1 = src
                       0x202E, 0x0010  // MOVE.L 16(A6),D0 = n
                     },
            new int[]{ 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF }),

        // ── memcpy_w (word-aligned copy, register variant) ────────────────
        // Some compilers emit a word-copy loop for even sizes:
        // TST.L D0 / BEQ end / MOVE.W (A1)+,(A0)+ / SUBQ.L #1,D0 / BNE
        new Pattern("memcpy_w",
            "Word-granularity copy (aligned buffers, count in words)\n" +
            "void *memcpy_w(void *dst /*A0*/, const void *src /*A1*/, size_t words /*D0*/)",
            CC_REG, false,
            new int[]{ 0x4A80,       // TST.L D0
                       0x6700,       // BEQ
                       0x30D9,       // MOVE.W (A1)+,(A0)+
                       0x5380,       // SUBQ.L #1,D0
                       0x66FA },
            new int[]{ 0xFFFF, 0xFF00, 0xFFFF, 0xFFFF, 0xFFFF }),

        // ── memcpy_l (longword-aligned copy) ─────────────────────────────
        new Pattern("memcpy_l",
            "Longword-granularity copy\n" +
            "void *memcpy_l(void *dst /*A0*/, const void *src /*A1*/, size_t longs /*D0*/)",
            CC_REG, false,
            new int[]{ 0x4A80,
                       0x6700,
                       0x20D9,       // MOVE.L (A1)+,(A0)+
                       0x5380,
                       0x66FA },
            new int[]{ 0xFFFF, 0xFF00, 0xFFFF, 0xFFFF, 0xFFFF }),

        // ── memcmp ────────────────────────────────────────────────────────
        // MOVE.B (A0)+,D0 / CMPM.B (A0)+,(A1)+ / BNE / SUBQ.L #1,D2 / BNE
        new Pattern("memcmp",
            "int memcmp(const void *s1 /*A0*/, const void *s2 /*A1*/, size_t n /*D0*/)\n" +
            "Returns 0 if equal",
            CC_REG, false,
            new int[]{ 0x4A80,       // TST.L D0 (or D1/D2)
                       0x6700,       // BEQ  (equal path)
                       0xB108,       // CMPM.B (A0)+,(A1)+   -- 0xB1 | (1<<9) | 0x08
                       0x6600,       // BNE
                       0x5380,       // SUBQ.L #1,D0
                       0x66F8 },     // BNE loop
            new int[]{ 0xFFFF, 0xFF00, 0xFFFF, 0xFF00, 0xFFFF, 0xFFFF }),

        // ── strlen ────────────────────────────────────────────────────────
        // MOVEA.L A0,A1 / TST.B (A0)+ / BNE / SUBA.L A1,A0 / SUBQ.L #1,D0
        new Pattern("strlen",
            "size_t strlen(const char *s /*A0*/)\nReturns length in D0",
            CC_REG, false,
            new int[]{ 0x2248,       // MOVEA.L A0,A1  (save start ptr)
                       0x4A18,       // TST.B (A0)+
                       0x66FC,       // BNE.S loop (short back-edge)
                       0x9089 },     // SUBA.L A1,A0  or SUBA.W
            new int[]{ 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF }),

        // ── strcpy ────────────────────────────────────────────────────────
        // MOVE.B (A1)+,(A0)+ / BNE (checks zero-terminator via CCR)
        new Pattern("strcpy",
            "char *strcpy(char *dst /*A0*/, const char *src /*A1*/)",
            CC_REG, false,
            new int[]{ 0x10D9,       // MOVE.B (A1)+,(A0)+
                       0x66FC },     // BNE.S loop
            new int[]{ 0xFFFF, 0xFFFF }),

        // ── strcmp ────────────────────────────────────────────────────────
        new Pattern("strcmp",
            "int strcmp(const char *s1 /*A0*/, const char *s2 /*A1*/)",
            CC_REG, false,
            new int[]{ 0x1018,       // MOVE.B (A0)+,D0
                       0xB219,       // CMP.B (A1)+,D0  (or similar)
                       0x6600,       // BNE
                       0x4A00,       // TST.B D0
                       0x66F6 },     // BNE loop
            new int[]{ 0xFFFF, 0xFFFF, 0xFF00, 0xFFFF, 0xFFFF }),

        // ── __mulsi3 / lmul (32×32→32 software multiply) ─────────────────
        // On 68000 (no 32×32 MUL instruction in base ISA), compilers emit
        // a soft-mul routine. Typical pattern: MULU + shifts + adds.
        new Pattern("__mulsi3",
            "int32 __mulsi3(int32 a /*D0*/, int32 b /*D1*/)\n" +
            "Software 32-bit multiply (no MULU.L on 68000 base)",
            CC_REG, false,
            new int[]{ 0x2001,       // MOVE.L D1,D0 — or similar setup
                       0xC0FC,       // MULU.W #n,D0 — rough
                       0x0000, 0x00  // wildcard immediate
                     },
            new int[]{ 0xFFFF, 0xFF00, 0x0000, 0x00 }),

        // ── __divsi3 / ldiv (32÷32 software divide) ──────────────────────
        // Typically starts with sign-handling then DIVU loop
        new Pattern("__divsi3",
            "int32 __divsi3(int32 num /*D0*/, int32 den /*D1*/)\n" +
            "Software 32-bit signed divide",
            CC_REG, false,
            new int[]{ 0x4A80,       // TST.L D0
                       0x6C00,       // BGE (non-negative path)
                       0x4480 },     // NEG.L D0
            new int[]{ 0xFFFF, 0xFF00, 0xFFFF }),

        // ── __modsi3 (32-bit remainder) ───────────────────────────────────
        // Usually shares code with __divsi3; look for similar head + different tail
        new Pattern("__modsi3",
            "int32 __modsi3(int32 num /*D0*/, int32 den /*D1*/)\n" +
            "Software 32-bit signed modulo",
            CC_REG, false,
            new int[]{ 0x2200,       // MOVE.L D0,D1 — swap for remainder path
                       0x4A80,
                       0x6C00,
                       0x4481 },     // NEG.L D1
            new int[]{ 0xFFFF, 0xFFFF, 0xFF00, 0xFFFF }),

    };

    // =========================================================================
    // Known function signatures
    // Applied to matched functions in addition to (or instead of) the pattern
    // name. Keys are the function name strings from PATTERNS[].name.
    // If a name appears here its full prototype is applied to the function,
    // making the decompiler show the correct arguments.
    //
    // HOW TO ADD YOUR OWN
    // ───────────────────
    // Add a Signature entry with:
    //   name        — must match the Pattern name exactly
    //   returnType  — Ghidra DataType string: "void","int","pointer",etc.
    //   params      — array of { paramName, typeString } pairs
    //   callingConv — CC name (CC_STD or CC_REG)
    //
    // Common type strings:
    //   "void"     → VoidDataType
    //   "int"      → IntegerDataType (32-bit)
    //   "uint"     → UnsignedIntegerDataType
    //   "short"    → ShortDataType
    //   "char"     → CharDataType
    //   "pointer"  → PointerDataType (32-bit ptr to undefined)
    //   "charptr"  → PointerDataType to CharDataType
    // =========================================================================
    static class Sig {
        final String   name;
        final String   returnType;
        final String[] paramNames;
        final String[] paramTypes;
        final String   cc;

        Sig(String name, String ret, String cc, String... namesAndTypes) {
            this.name = name;
            this.returnType = ret;
            this.cc = cc;
            // namesAndTypes: name0, type0, name1, type1, ...
            int count = namesAndTypes.length / 2;
            this.paramNames = new String[count];
            this.paramTypes = new String[count];
            for (int i = 0; i < count; i++) {
                paramNames[i] = namesAndTypes[i * 2];
                paramTypes[i] = namesAndTypes[i * 2 + 1];
            }
        }
    }

    private static final Sig[] SIGNATURES = {

        // ── C string / memory functions ───────────────────────────────────
        new Sig("memset",  "pointer", CC_REG,
                "dst", "pointer",  "val", "int",     "n", "uint"),
        new Sig("memcpy",  "pointer", CC_REG,
                "dst", "pointer",  "src", "pointer", "n", "uint"),
        new Sig("memcpy_w","pointer", CC_REG,
                "dst", "pointer",  "src", "pointer", "words", "uint"),
        new Sig("memcpy_l","pointer", CC_REG,
                "dst", "pointer",  "src", "pointer", "longs", "uint"),
        new Sig("memcmp",  "int",     CC_REG,
                "s1",  "pointer",  "s2",  "pointer", "n", "uint"),
        new Sig("strlen",  "uint",    CC_REG,
                "s",   "charptr"),
        new Sig("strcpy",  "charptr", CC_REG,
                "dst",  "charptr", "src", "charptr"),
        new Sig("strcmp",  "int",     CC_REG,
                "s1",   "charptr", "s2",  "charptr"),

        // ── Compiler support ──────────────────────────────────────────────
        new Sig("__mulsi3", "int", CC_REG, "a", "int", "b", "int"),
        new Sig("__divsi3", "int", CC_REG, "num", "int", "den", "int"),
        new Sig("__modsi3", "int", CC_REG, "num", "int", "den", "int"),

        // ── Board-specific confirmed functions ────────────────────────────
        // These are manually confirmed from the DMP9/16 ROM disassembly.
        // All use __stdcall (LINK/UNLK frame, arg at 8(A6)).

        // 0x0000a5a4 family: thin wrappers that push a literal string and
        // call print_rjust16. One per exception type.
        new Sig("report_bus_error",     "void", CC_STD),
        new Sig("report_address_error", "void", CC_STD),
        new Sig("report_illegal_insn",  "void", CC_STD),
        new Sig("report_zero_divide",   "void", CC_STD),

        // 0x0000a6a8: right-justified 16-char print.
        // Measures string length, pads with spaces, then calls FUN_00002aa6
        // twice (once for padding, once for the string itself).
        // FUN_00002aa6 is the actual character output routine (uart/LCD write).
        new Sig("print_rjust16", "void", CC_STD, "s", "charptr"),

        // 0x00002aa6: low-level string output (called with 2 stack args:
        //   char *str, int len — prints exactly `len` chars from `str`).
        // Signature tentative until confirmed from its own disassembly.
        new Sig("uart_write", "void", CC_STD, "str", "charptr", "len", "int"),

        // 0x0003f4b4: called with (0x10 - strlen(s), 2) — returns an int
        // used as a column/cursor position. Likely a display positioning
        // or padding calculation helper.
        new Sig("calc_pad", "uint", CC_STD, "width", "uint", "align", "uint"),
    };

    // =========================================================================
    // Minimum confidence threshold: a pattern must match this many
    // non-wildcard bytes to be accepted (prevents tiny patterns from
    // matching everywhere). Adjust down if you have very short functions.
    // =========================================================================
    private static final int MIN_EXACT_BYTES = 4;

    @Override
    public void run() throws Exception {
        Memory        mem    = currentProgram.getMemory();
        Listing       listing = currentProgram.getListing();
        FunctionManager fmgr = currentProgram.getFunctionManager();
        SymbolTable   symtab = currentProgram.getSymbolTable();
        CompilerSpec  cspec  = currentProgram.getCompilerSpec();
        AddressSpace  space  = currentProgram.getAddressFactory().getDefaultAddressSpace();

        PrototypeModel regCC = cspec.getCallingConvention(CC_REG);
        if (regCC == null) {
            println("[DMP9_LibMatch] NOTE: '" + CC_REG + "' not in cspec — " +
                    "register-based library functions will be tagged 'unknown'.");
        }

        int txId = currentProgram.startTransaction("DMP9 library function matching");
        boolean ok = false;
        int renamed = 0;
        int conflicts = 0;
        int skipped = 0;

        // Map: address → list of pattern names that matched (to detect conflicts)
        Map<Address, List<String>> matchMap = new LinkedHashMap<>();

        try {
            // ----------------------------------------------------------------
            // PASS 1: Scan all known functions for pattern matches.
            //         Only scan within ROM (0x000000 .. 0x3FFFFF).
            // ----------------------------------------------------------------
            Address romStart = space.getAddress(0x000000L);
            Address romEnd   = space.getAddress(0x3FFFFFL);

            FunctionIterator funcs = fmgr.getFunctions(true);
            while (funcs.hasNext() && !monitor.isCancelled()) {
                Function func = funcs.next();
                Address  entry = func.getEntryPoint();

                // Only look in ROM range
                if (entry.compareTo(romStart) < 0 || entry.compareTo(romEnd) > 0)
                    continue;
                // Skip already-named functions (not FUN_xxxxxxxx)
                if (!func.getName().startsWith("FUN_"))
                    continue;

                List<String> matches = new ArrayList<>();
                for (Pattern p : PATTERNS) {
                    if (patternMatches(mem, entry, p)) {
                        matches.add(p.name);
                    }
                }

                if (!matches.isEmpty()) {
                    matchMap.put(entry, matches);
                }
            }

            // ----------------------------------------------------------------
            // PASS 2: Apply names and calling conventions.
            //         Conflict = more than one distinct pattern name matched.
            // ----------------------------------------------------------------
            for (Map.Entry<Address, List<String>> e : matchMap.entrySet()) {
                Address  addr    = e.getKey();
                List<String> hits = e.getValue();

                // Deduplicate names while preserving order
                List<String> distinctNames = new ArrayList<>();
                for (String n : hits) {
                    if (!distinctNames.contains(n)) distinctNames.add(n);
                }

                Function func = fmgr.getFunctionAt(addr);
                if (func == null) { skipped++; continue; }

                if (distinctNames.size() == 1) {
                    // Unambiguous match
                    String name = distinctNames.get(0);
                    applyMatch(func, name, cspec, regCC);
                    println("[DMP9_LibMatch] " + String.format("0x%08X", addr.getOffset()) +
                            "  " + name + "  (was " + func.getName() + ")");
                    renamed++;
                } else {
                    // Multiple different names — report and skip rename
                    println("[DMP9_LibMatch] CONFLICT @ 0x" +
                            Long.toHexString(addr.getOffset()) +
                            " — matched: " + String.join(", ", distinctNames) +
                            " — NOT renamed, needs manual review");
                    // Add a bookmark so you can find it
                    createBookmark(addr, "DMP9_LibMatch",
                            "Pattern conflict: " + String.join(" / ", distinctNames));
                    conflicts++;
                }
            }

            // ----------------------------------------------------------------
            // PASS 3: Apply confirmed address→signature mappings.
            //         These are functions identified manually from the listing.
            //         Unlike PASS 1/2, these work even if the function is
            //         already named (not FUN_xxxx) so re-running is safe.
            // ----------------------------------------------------------------
            println("[DMP9_LibMatch] Pass 3: applying confirmed address signatures...");
            int confirmed = applyConfirmedAddresses(fmgr, space, cspec, regCC);
            println("[DMP9_LibMatch]   Applied: " + confirmed);

            ok = true;
        } finally {
            currentProgram.endTransaction(txId, ok);
        }

        println("[DMP9_LibMatch] Done.");
        println("  Functions renamed   : " + renamed);
        println("  Conflicts (bookmarked): " + conflicts);
        println("  Skipped             : " + skipped);
        println("");
        println("TIP: Add your own patterns from confirmed functions and re-run.");
        println("     Increase MIN_EXACT_BYTES if you see false positives.");
    }

    // =========================================================================
    // Apply name + comment + calling convention + full signature to a function
    // =========================================================================
    private void applyMatch(Function func, String name,
                            CompilerSpec cspec, PrototypeModel regCC)
            throws Exception {

        // Find the matching Pattern entry
        Pattern matched = null;
        for (Pattern p : PATTERNS) {
            if (p.name.equals(name) && patternMatches(
                    currentProgram.getMemory(), func.getEntryPoint(), p)) {
                matched = p;
                break;
            }
        }
        if (matched == null) return;

        // Rename (only if still FUN_xxxx)
        if (func.getName().startsWith("FUN_")) {
            func.setName(name, SourceType.ANALYSIS);
        }

        // Plate comment
        Listing listing = currentProgram.getListing();
        String existingPlate = listing.getComment(CommentType.PLATE, func.getEntryPoint());
        if (existingPlate == null || existingPlate.isEmpty()) {
            listing.setComment(func.getEntryPoint(), CommentType.PLATE,
                    matched.comment + "\n[auto-identified by DMP9_LibMatch]");
        }

        // noReturn
        if (matched.noReturn) {
            func.setNoReturn(true);
        }

        // Look up a full signature for this function name
        Sig sig = null;
        for (Sig s : SIGNATURES) {
            if (s.name.equals(name)) { sig = s; break; }
        }

        if (sig != null) {
            // Apply the full typed prototype
            applySignature(func, sig, cspec, regCC);
        } else {
            // No full signature — at least set the calling convention
            if (matched.callingConvention != null) {
                String currentCC = func.getCallingConventionName();
                if (currentCC == null || currentCC.isEmpty() ||
                        currentCC.equals("unknown") || currentCC.equals("default")) {
                    try {
                        if (matched.callingConvention.equals(CC_REG) && regCC == null) {
                            func.setCallingConvention("unknown");
                        } else {
                            func.setCallingConvention(matched.callingConvention);
                        }
                    } catch (InvalidInputException ex) {
                        // Convention not available — leave as-is
                    }
                }
            }
        }
    }

    // =========================================================================
    // Apply a full typed signature (return type + parameters) to a function.
    // Uses FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS so Ghidra assigns
    // storage according to the calling convention (registers or stack).
    // =========================================================================
    private void applySignature(Function func, Sig sig,
                                CompilerSpec cspec, PrototypeModel regCC)
            throws Exception {

        DataTypeManager dtm = currentProgram.getDataTypeManager();

        // Resolve return type
        DataType returnDT = resolveType(sig.returnType, dtm);

        // Resolve parameter types
        List<ParameterImpl> params = new ArrayList<>();
        for (int i = 0; i < sig.paramNames.length; i++) {
            DataType pdt = resolveType(sig.paramTypes[i], dtm);
            params.add(new ParameterImpl(sig.paramNames[i], pdt,
                    currentProgram));
        }

        // Determine calling convention to use
        String ccName = sig.cc;
        if (ccName.equals(CC_REG) && regCC == null) ccName = "unknown";

        // Apply — DYNAMIC_STORAGE_ALL_PARAMS lets Ghidra assign register/stack
        // slots according to the calling convention automatically
        func.updateFunction(
                ccName,
                new ReturnParameterImpl(returnDT, currentProgram),
                params,
                FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                true,            // force — overwrite existing params
                SourceType.ANALYSIS
        );
    }

    // =========================================================================
    // Resolve a type name string to a Ghidra DataType.
    // =========================================================================
    private DataType resolveType(String typeName, DataTypeManager dtm) {
        switch (typeName) {
            case "void":    return VoidDataType.dataType;
            case "int":     return IntegerDataType.dataType;
            case "uint":    return UnsignedIntegerDataType.dataType;
            case "short":   return ShortDataType.dataType;
            case "ushort":  return UnsignedShortDataType.dataType;
            case "char":    return CharDataType.dataType;
            case "uchar":   return UnsignedCharDataType.dataType;
            case "byte":    return ByteDataType.dataType;
            case "long":    return LongDataType.dataType;
            case "ulong":   return UnsignedLongDataType.dataType;
            case "pointer": return new PointerDataType(Undefined4DataType.dataType);
            case "charptr": return new PointerDataType(CharDataType.dataType);
            case "voidptr": return new PointerDataType(VoidDataType.dataType);
            default:
                // Try to look up in the program's data type manager
                DataType found = dtm.getDataType(new CategoryPath("/"), typeName);
                if (found != null) return found;
                println("[DMP9_LibMatch] WARNING: unknown type '" + typeName +
                        "' — using undefined4");
                return Undefined4DataType.dataType;
        }
    }

    // =========================================================================
    // Apply a full signature to any function by name (call this from your own
    // one-off fixups without needing a byte pattern match).
    // Usage: applySignatureByName("FUN_0000a6a8", new Sig(
    //            "uart_puts", "void", CC_STD, "s", "charptr"));
    // =========================================================================
    @SuppressWarnings("unused")
    private void applySignatureByName(String funcName, Sig sig) throws Exception {
        FunctionManager fmgr = currentProgram.getFunctionManager();
        CompilerSpec cspec   = currentProgram.getCompilerSpec();
        PrototypeModel regCC = cspec.getCallingConvention(CC_REG);

        FunctionIterator it = fmgr.getFunctions(true);
        while (it.hasNext()) {
            Function f = it.next();
            if (f.getName().equals(funcName) || f.getName().equals(sig.name)) {
                f.setName(sig.name, SourceType.USER_DEFINED);
                applySignature(f, sig, cspec, regCC);
                println("[DMP9_LibMatch] Applied signature to " + sig.name +
                        " @ 0x" + Long.toHexString(f.getEntryPoint().getOffset()));
            }
        }
    }

    // =========================================================================
    // Core pattern matcher
    // Returns true if the pattern matches at the given address.
    // Also checks that at least MIN_EXACT_BYTES bytes have mask=0xFF.
    // =========================================================================
    private boolean patternMatches(Memory mem, Address start, Pattern p) {
        int exactCount = 0;
        for (int i = 0; i < p.bytes.length; i++) {
            Address a = start.add(i);
            byte romByte;
            try {
                romByte = mem.getByte(a);
            } catch (Exception e) {
                return false; // Address not in memory map
            }
            byte mask = p.masks[i];
            if ((romByte & mask) != (p.bytes[i] & mask)) {
                return false;
            }
            if ((mask & 0xFF) == 0xFF) exactCount++;
        }
        return exactCount >= MIN_EXACT_BYTES;
    }

    // =========================================================================
    // Confirmed address → signature table
    // Format: { address, name, returnType, callingConv, param pairs... }
    // Add a row for every function you've positively identified.
    // The address is used to find the function; the name is applied if the
    // function is still FUN_xxxx. Safe to re-run — won't overwrite USER_DEFINED.
    // =========================================================================

    // Inner class to hold one confirmed entry
    static class ConfirmedFunc {
        final long   addr;
        final String name;
        final Sig    sig;
        ConfirmedFunc(long addr, String name, String ret, String cc,
                      String... namesAndTypes) {
            this.addr = addr;
            this.name = name;
            this.sig  = new Sig(name, ret, cc, namesAndTypes);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ADD YOUR CONFIRMED FUNCTIONS HERE
    // Addresses are ROM-relative (the actual address in the Ghidra listing).
    // ────────────────────────────────────────────────────────────────────────────
    private static final ConfirmedFunc[] CONFIRMED = {

        // ── Exception report wrappers (push literal string, call print_rjust16) ──
        new ConfirmedFunc(0x0000a5a4L, "report_bus_error",     "void", CC_STD),
        new ConfirmedFunc(0x0000a5b8L, "report_address_error", "void", CC_STD),
        new ConfirmedFunc(0x0000a5ccL, "report_illegal_insn",  "void", CC_STD),
        new ConfirmedFunc(0x0000a5e0L, "report_zero_divide",   "void", CC_STD),
        new ConfirmedFunc(0x0000a5f4L, "report_chk_error",     "void", CC_STD),
        new ConfirmedFunc(0x0000a608L, "report_trapv",         "void", CC_STD),
        new ConfirmedFunc(0x0000a61cL, "report_priv_violation","void", CC_STD),
        new ConfirmedFunc(0x0000a630L, "report_trace",         "void", CC_STD),
        new ConfirmedFunc(0x0000a644L, "report_line_a",        "void", CC_STD),
        new ConfirmedFunc(0x0000a658L, "report_line_f",        "void", CC_STD),
        new ConfirmedFunc(0x0000a66cL, "report_spurious_irq",  "void", CC_STD),
        new ConfirmedFunc(0x0000a680L, "report_uninitialized", "void", CC_STD),
        new ConfirmedFunc(0x0000a694L, "report_unknown",       "void", CC_STD),

        // ── Output functions ──────────────────────────────────────────────
        // Right-justify string in 16-char field, then output
        new ConfirmedFunc(0x0000a6a8L, "print_rjust16", "void", CC_STD,
                          "s", "charptr"),

        // Low-level write: outputs exactly `len` chars from `str`
        // (tentative — update name/sig once FUN_00002aa6 is confirmed)
        new ConfirmedFunc(0x00002aa6L, "uart_write", "void", CC_STD,
                          "str", "charptr", "len", "int"),

        // Padding calculation helper: returns column offset
        // called as calc_pad(0x10 - strlen(s), 2)
        new ConfirmedFunc(0x0003f4b4L, "calc_pad", "uint", CC_STD,
                          "width", "uint", "align", "uint"),

        // ── Add more confirmed functions below as you identify them ─────────
    };

    private int applyConfirmedAddresses(FunctionManager fmgr, AddressSpace space,
                                        CompilerSpec cspec, PrototypeModel regCC)
            throws Exception {
        int count = 0;
        for (ConfirmedFunc cf : CONFIRMED) {
            Address addr;
            try {
                addr = space.getAddress(cf.addr);
            } catch (Exception e) {
                println("[DMP9_LibMatch] Bad address in CONFIRMED: 0x" +
                        Long.toHexString(cf.addr));
                continue;
            }

            Function func = fmgr.getFunctionAt(addr);
            if (func == null) {
                // Try to create it
                func = createFunction(addr, cf.name);
                if (func == null) {
                    println("[DMP9_LibMatch] Could not find/create function at 0x" +
                            Long.toHexString(cf.addr) + " (" + cf.name + ")");
                    continue;
                }
            }

            // Rename only if auto-named or still FUN_xxxx;
            // preserve USER_DEFINED names
            if (func.getSymbol().getSource() != SourceType.USER_DEFINED &&
                    func.getName().startsWith("FUN_")) {
                func.setName(cf.name, SourceType.ANALYSIS);
            }

            // Always (re-)apply the signature so types stay current
            applySignature(func, cf.sig, cspec, regCC);

            println("[DMP9_LibMatch]   0x" + String.format("%08X", cf.addr) +
                    "  " + func.getName());
            count++;
        }
        return count;
    }
}
