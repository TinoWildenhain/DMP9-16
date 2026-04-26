// DMP9_FuncFixup.java — Ghidra 11.x / 12.x
// Applies correct calling conventions to Yamaha DMP9/16 firmware functions.
//
// Strategy:
//   1. All vector table entry points (reset, IRQ handlers) get "unknown"
//      calling convention + noreturn where appropriate.
//   2. Functions whose prologue does NOT set up a stack frame (no LINK A6,#xx
//      and no MOVEM [...],-(SP) within first 6 instructions) are tagged with
//      the custom "yamaha_reg" calling convention.
//   3. Functions that DO have a standard stack frame prologue are left as
//      "__stdcall" (Ghidra default for 68k).
//
// SNAP INSTALL NOTE — you cannot edit /snap/ghidra/.../68000.cspec.
// Instead, place a user-level override:
//
//   mkdir -p ~/.ghidra/.ghidra_12.0_PUBLIC/languages
//   cp /snap/ghidra/current/Ghidra/Processors/68000/data/languages/68000.cspec \
//      ~/.ghidra/.ghidra_12.0_PUBLIC/languages/68000.cspec
//   # then edit the copy to add the <prototype name="yamaha_reg"> block
//
// Ghidra loads user-overridden language files from that path at startup.
// The directory name must match your exact Ghidra version string.
// If that doesn't work (version-locked snap): the script falls back to
// tagging functions as "unknown" instead of "yamaha_reg" — the decompiler
// still benefits from the prologue analysis even without the named CC.
//
// Prerequisites:
//   - Run AFTER DMP9_Board_Setup.java and TMP68301_Setup.java.
//   - yamaha_reg in cspec is optional but recommended (see above).
//
// @author  DMP9-16 project
// @category DMP9
// @keybinding
// @menupath
// @toolbar

import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.script.GhidraScript;
import ghidra.app.util.cparser.C.CParserUtils;
import ghidra.program.model.address.*;
import ghidra.program.model.data.*;
import ghidra.program.model.lang.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.*;
import ghidra.util.exception.InvalidInputException;

import java.io.File;
import java.util.*;

public class DMP9_FuncFixup extends GhidraScript {

    // -----------------------------------------------------------------------
    // Calling convention names — must match your 68000.cspec prototype names
    // -----------------------------------------------------------------------
    private static final String CC_DEFAULT  = "__stdcall";    // stack-based C default
    private static final String CC_REG      = "yamaha_reg";   // register-based (D0/D1/A0/A1)
    private static final String CC_ISR      = "__interrupt";  // hardware exception entry (new in tmp68301.cspec)
    private static final String CC_UNKNOWN  = "unknown";      // fallback if cspec not loaded

    // Vector table base — 68k standard
    private static final long VECTOR_BASE   = 0x000000L;
    // Number of exception vectors (0..63 = 64 vectors × 4 bytes = 256 bytes)
    // TMP68301 adds user vectors up to ~0x100+
    private static final int  VECTOR_COUNT  = 96;  // covers TMP68301 user vectors

    // Vectors that are truly "entry from hardware" (no caller, no return expected
    // in a conventional sense). Everything else is a regular ISR that does RTE.
    private static final Set<Integer> NORETURN_VECTORS = new HashSet<>(Arrays.asList(
        0,  // initial SSP (not a function)
        1   // reset PC — this IS a function but never returns
    ));

    // -----------------------------------------------------------------------
    // Heuristic: detect standard stack-frame prologue
    //   A function is "stack-frame-based" if within its first N instructions
    //   it contains LINK A6 or MOVEM reg,-(SP).
    // -----------------------------------------------------------------------
    private static final int PROLOGUE_SCAN_DEPTH = 6;

    @Override
    public void run() throws Exception {

        Listing        listing = currentProgram.getListing();
        SymbolTable    symtab  = currentProgram.getSymbolTable();
        FunctionManager fmgr   = currentProgram.getFunctionManager();
        AddressFactory afac    = currentProgram.getAddressFactory();
        Memory         mem     = currentProgram.getMemory();
        Language       lang    = currentProgram.getLanguage();
        CompilerSpec   cspec   = currentProgram.getCompilerSpec();

        // Check that the custom calling conventions exist
        PrototypeModel regCC = cspec.getCallingConvention(CC_REG);
        PrototypeModel isrCC = cspec.getCallingConvention(CC_ISR);
        if (regCC == null) {
            println("[DMP9_FuncFixup] WARNING: '" + CC_REG + "' not found in cspec — " +
                    "register-based functions will be tagged 'unknown'.");
        }
        if (isrCC == null) {
            println("[DMP9_FuncFixup] WARNING: '" + CC_ISR + "' not found in cspec — " +
                    "ISR handlers will be tagged 'unknown'.");
        }

        // ------------------------------------------------------------------
        // STEP 0: Parse C header files into the program's data type manager
        // ------------------------------------------------------------------
        // Headers live in ghidra/include/ relative to the script directory.
        // We resolve the path from the script source file location.
        File includeDir;
        try {
            generic.jar.ResourceFile rf = getSourceFile();
            File scriptFile = (rf != null) ? rf.getFile(false) : null;
            includeDir = (scriptFile != null)
                ? new File(scriptFile.getParentFile().getParentFile(), "include")
                : new File(System.getProperty("user.home"), "devel/DMP9-16/ghidra/include");
        } catch (Exception e) {
            includeDir = new File(System.getProperty("user.home"), "devel/DMP9-16/ghidra/include");
        }

        String[] headerFiles = {
            new File(includeDir, "tmp68301_regs.h").getAbsolutePath(),
            new File(includeDir, "dmp9_board_regs.h").getAbsolutePath(),
            new File(includeDir, "dmp9_types.h").getAbsolutePath(),
        };

        // Verify at least one header exists before attempting to parse
        boolean anyHeaderFound = false;
        for (String hf : headerFiles) {
            if (new File(hf).exists()) { anyHeaderFound = true; break; }
        }

        if (anyHeaderFound) {
            println("[DMP9_FuncFixup] Step 0: parsing C headers from " + includeDir);
            try {
                DataTypeManager dtm = currentProgram.getDataTypeManager();
                String[] includePaths = { includeDir.getAbsolutePath() };
                String[] parseArgs   = { "-D__GHIDRA__", "-D__68000__" };
                CParserUtils.parseHeaderFiles(
                    new DataTypeManager[]{ dtm },
                    headerFiles, includePaths, parseArgs,
                    dtm, monitor);
                println("[DMP9_FuncFixup]   Headers parsed OK.");
            } catch (Exception e) {
                println("[DMP9_FuncFixup]   Header parse WARNING: " + e.getMessage()
                        + " — continuing without header types.");
            }
        } else {
            println("[DMP9_FuncFixup] Step 0: header dir not found at "
                    + includeDir + " — skipping C header import.");
        }

        // ------------------------------------------------------------------
        // STEP 0b: Force-disassemble the ISR stub block (0x400–0x9FF)
        // ------------------------------------------------------------------
        // The ISR stubs in this range are NOT reachable from the vector table
        // in the first auto-analysis pass because the vector table entries
        // point to individual ISR addresses — but the padding bytes between
        // entries confuse the linear-sweep.  Force it here before Pass 1
        // creates functions from vector targets.
        {
            println("[DMP9_FuncFixup] Step 0b: force-disassembling ISR stub block 0x400–0x9FF");
            Address stubStart = currentProgram.getAddressFactory()
                .getDefaultAddressSpace().getAddress(0x000400L);
            Address stubEnd   = currentProgram.getAddressFactory()
                .getDefaultAddressSpace().getAddress(0x000A00L);
            AddressSet stubRange = new AddressSet(stubStart, stubEnd);
            DisassembleCommand disCmd = new DisassembleCommand(stubRange, null, true);
            disCmd.applyTo(currentProgram, monitor);
            println("[DMP9_FuncFixup]   ISR stub block disassembled.");
        }

        int txId = currentProgram.startTransaction("DMP9 function calling convention fixup");
        boolean ok = false;
        int taggedEntry  = 0;
        int taggedReg    = 0;
        int taggedStack  = 0;
        int skipped      = 0;

        // Track addresses tagged in Pass 1 so Pass 2 does not overwrite them
        Set<Address> vectorFunctions = new HashSet<>();

        try {
            // ------------------------------------------------------------------
            // PASS 1: Vector table — create/tag all entry-point functions
            // ------------------------------------------------------------------
            println("[DMP9_FuncFixup] Pass 1: scanning vector table at 0x" +
                    Long.toHexString(VECTOR_BASE) + " (" + VECTOR_COUNT + " vectors)");

            Address vBase = afac.getDefaultAddressSpace().getAddress(VECTOR_BASE);

            for (int i = 0; i < VECTOR_COUNT; i++) {
                Address vAddr = vBase.add(i * 4L);

                if (!mem.contains(vAddr)) continue;

                // Read the 32-bit vector value
                long target;
                try {
                    target = mem.getInt(vAddr) & 0xFFFFFFFFL;
                } catch (Exception e) {
                    continue;
                }

                if (i == 0) continue; // Vector 0 is initial SSP, not a function

                if (target == 0x00000000L || target == 0xFFFFFFFFL) continue; // uninitialized

                Address funcAddr = afac.getDefaultAddressSpace().getAddress(target);
                if (!mem.contains(funcAddr)) continue;

                // Ensure a function exists at the target
                Function func = fmgr.getFunctionAt(funcAddr);
                if (func == null) {
                    func = createFunction(funcAddr, null);
                    if (func == null) {
                        println("[DMP9_FuncFixup]   Could not create function at vector " +
                                i + " -> 0x" + Long.toHexString(target));
                        continue;
                    }
                    println("[DMP9_FuncFixup]   Created function at vector " + i +
                            " -> " + func.getName() + " @ 0x" + Long.toHexString(target));
                }

                // If the function is a thunk (e.g. reset vector contains JMP real_entry),
                // follow the thunk chain to find the real function and tag that too.
                Function realFunc = func;
                if (func.isThunk()) {
                    Function thunkedFunc = func.getThunkedFunction(true); // true = follow chain
                    if (thunkedFunc != null) {
                        realFunc = thunkedFunc;
                        println("[DMP9_FuncFixup]   Vector " + i + " is a thunk -> real function: " +
                                realFunc.getName() + " @ 0x" + Long.toHexString(
                                realFunc.getEntryPoint().getOffset()));
                    }
                }

                // Vector 1 = reset PC: the real function never returns.
                // Use yamaha_reg (it's an app entry point, not a true ISR).
                // All other vectors: use __interrupt.
                if (i == 1) {
                    realFunc.setNoReturn(true);
                    func.setNoReturn(true); // mark thunk wrapper too
                    setCC(realFunc, regCC != null ? CC_REG : CC_UNKNOWN);
                    setCC(func,     regCC != null ? CC_REG : CC_UNKNOWN);
                    println("[DMP9_FuncFixup]   reset_handler " +
                            realFunc.getName() + " -> noreturn + " +
                            (regCC != null ? CC_REG : CC_UNKNOWN));
                } else {
                    String cc = isrCC != null ? CC_ISR : CC_UNKNOWN;
                    setCC(realFunc, cc);
                    setCC(func, cc); // no-op if func == realFunc
                }

                vectorFunctions.add(func.getEntryPoint());
                vectorFunctions.add(realFunc.getEntryPoint());
                taggedEntry++;
            }

            // ------------------------------------------------------------------
            // PASS 1b: Overlay vector table as Pointer32 array
            // ------------------------------------------------------------------
            // Apply a typed Pointer32[VECTOR_COUNT] array at 0x000000 so the
            // Listing view shows each slot as a named function pointer rather
            // than raw ?? bytes.  Also apply the M68K_VectorTable struct from
            // the parsed dmp9_types.h if it is available in the DTM.
            {
                DataTypeManager dtm = currentProgram.getDataTypeManager();
                Address vtBase = afac.getDefaultAddressSpace().getAddress(VECTOR_BASE);
                try {
                    listing.clearCodeUnits(vtBase, vtBase.add(VECTOR_COUNT * 4L - 1), false);

                    // Prefer the named struct from headers; fall back to Pointer32 array.
                    // CParserUtils places parsed types under the header file's category
                    // (e.g. "/dmp9_types.h/M68K_VectorTable"), so a root-path lookup
                    // misses them — search by name across all categories instead.
                    java.util.List<DataType> dtResults = new java.util.ArrayList<>();
                    dtm.findDataTypes("M68K_VectorTable", dtResults);
                    DataType vtType = dtResults.isEmpty() ? null : dtResults.get(0);

                    DataType vtStruct = vtType;
                    if (vtStruct == null) {
                        println("[DMP9_FuncFixup] Pass 1b: M68K_VectorTable struct not found "
                                + "in DTM — falling back to Pointer32 array.");
                        vtStruct = new ArrayDataType(new Pointer32DataType(), VECTOR_COUNT, 4);
                    }

                    listing.createData(vtBase, vtStruct);
                    println("[DMP9_FuncFixup] Pass 1b: vector table overlay applied ("
                            + vtStruct.getName() + ") at 0x000000.");
                } catch (Exception e) {
                    println("[DMP9_FuncFixup] Pass 1b WARNING: could not apply vector overlay: "
                            + e.getMessage());
                }
            }

            // ------------------------------------------------------------------
            // PASS 1c: Name ISR stub functions (and direct ISRs) from vector slots
            // ------------------------------------------------------------------
            // Each vector slot at offset (vecNum * 4) holds a 32-bit pointer.
            // If the pointer lands in 0x400-0x9FF, it's a small panic/dispatch
            // stub — name it "stub_<slot_name>".  Otherwise the pointer goes
            // straight to a real ISR body — name it "<slot_name>".
            // Names are taken from the canonical 68000 + TMP68301 MFP table.
            try {
                nameStubFunctions();
            } catch (Exception e) {
                println("[DMP9_FuncFixup] Pass 1c WARNING: " + e.getMessage());
            }

            // ------------------------------------------------------------------
            // PASS 2: All other functions — heuristic prologue scan.
            // Skip anything already tagged in Pass 1 (vector entries / ISRs).
            // ------------------------------------------------------------------
            println("[DMP9_FuncFixup] Pass 2: scanning all functions for calling convention...");

            FunctionIterator funcs = fmgr.getFunctions(true);
            while (funcs.hasNext() && !monitor.isCancelled()) {
                Function func = funcs.next();
                Address entry = func.getEntryPoint();

                // Skip thunks, externals, and anything tagged in Pass 1
                if (func.isThunk() || func.isExternal()) {
                    skipped++;
                    continue;
                }
                if (vectorFunctions.contains(entry)) {
                    // Already handled — don't overwrite
                    continue;
                }

                boolean hasStackFrame = hasPrologue(listing, entry);

                if (hasStackFrame) {
                    // Standard stack-based — leave as __stdcall (Ghidra default)
                    taggedStack++;
                } else {
                    // No stack frame = register-based
                    try {
                        func.setCallingConvention(regCC != null ? CC_REG : CC_UNKNOWN);
                        taggedReg++;
                    } catch (InvalidInputException e) {
                        skipped++;
                    }
                }
            }

            ok = true;

        } finally {
            currentProgram.endTransaction(txId, ok);
        }

        println("[DMP9_FuncFixup] Done.");
        println("  Vector entry points tagged : " + taggedEntry);
        println("  Register-call functions    : " + taggedReg);
        println("  Stack-frame functions      : " + taggedStack);
        println("  Skipped (thunks/external)  : " + skipped);
        println("");
        println("Next: Review functions tagged 'unknown' (vector entries) manually.");
        println("      If yamaha_reg / __interrupt missing from cspec, add them and re-run.");
    }

    // -----------------------------------------------------------------------
    // Helper: set calling convention, silently ignoring invalid names
    // -----------------------------------------------------------------------
    private void setCC(Function func, String cc) {
        try {
            func.setCallingConvention(cc);
        } catch (InvalidInputException e) {
            // Convention not in cspec — leave as-is
        }
    }

    // -----------------------------------------------------------------------
    // Vector slot name table (68000 standard + TMP68301 MFP user vectors).
    // Built lazily because Ghidra scripts run as instances and static field
    // initialisation has had quirks across versions.
    // -----------------------------------------------------------------------
    private String[] buildVectorNames() {
        String[] n = new String[256];
        n[2]  = "bus_error";
        n[3]  = "address_error";
        n[4]  = "illegal_insn";
        n[5]  = "zero_divide";
        n[6]  = "chk_insn";
        n[7]  = "trapv_insn";
        n[8]  = "privilege_violation";
        n[9]  = "trace";
        n[10] = "line_a_emulator";
        n[11] = "line_f_emulator";
        n[24] = "spurious_irq";
        n[25] = "autovector_l1";
        n[26] = "autovector_l2";
        n[27] = "autovector_l3";
        n[28] = "autovector_l4";
        n[29] = "autovector_l5";
        n[30] = "autovector_l6";
        n[31] = "autovector_l7";
        for (int i = 32; i <= 47; i++) n[i] = "trap_" + (i - 32);
        n[64] = "mfp_vec64";
        n[65] = "mfp_vec65";
        n[66] = "mfp_vec66";
        n[67] = "mfp_vec67";
        n[68] = "mfp_vec68";
        n[69] = "timer_housekeeping_isr";
        n[70] = "mfp_vec70";
        n[71] = "mfp_vec71";
        n[72] = "serial0_status_isr";
        n[73] = "serial0_rx_isr";
        n[74] = "serial0_tx_isr";
        n[75] = "serial0_special_isr";
        n[76] = "serial1_status_isr";
        n[77] = "serial1_rx_isr";
        n[78] = "serial1_tx_isr";
        n[79] = "serial1_special_isr";
        return n;
    }

    // -----------------------------------------------------------------------
    // Pass 1c: name ISR stub functions (or direct ISRs) from each vector slot.
    //
    //   pointer in [0x400, 0x9FF]  -> stub_<slot_name>
    //   pointer elsewhere          -> <slot_name> (real ISR body)
    //
    // Skips slots without a canonical name (NULL entries in VEC_NAME) and
    // never overwrites a function that already has a non-auto name.
    // -----------------------------------------------------------------------
    private void nameStubFunctions() throws Exception {
        Memory          mem = currentProgram.getMemory();
        FunctionManager fm  = currentProgram.getFunctionManager();
        AddressFactory  af  = currentProgram.getAddressFactory();

        String[] vecName = buildVectorNames();

        println("[DMP9_FuncFixup] Pass 1c: naming stub / ISR functions from vector table...");

        int named = 0;

        for (int vecNum = 2; vecNum < 256; vecNum++) {
            String slotName = vecName[vecNum];
            if (slotName == null) continue;

            long slotOffset = vecNum * 4L;
            Address slotAddr = af.getDefaultAddressSpace().getAddress(slotOffset);
            if (!mem.contains(slotAddr)) continue;

            long targetOffset;
            try {
                targetOffset = mem.getInt(slotAddr) & 0xFFFFFFFFL;
            } catch (Exception e) {
                continue;
            }
            if (targetOffset == 0L || targetOffset == 0xFFFFFFFFL) continue;

            Address targetAddr = af.getDefaultAddressSpace().getAddress(targetOffset);
            if (!mem.contains(targetAddr)) continue;

            boolean isStub = (targetOffset >= 0x400L && targetOffset <= 0x9FFL);
            String funcName = isStub ? ("stub_" + slotName) : slotName;

            Function existing = fm.getFunctionAt(targetAddr);
            if (existing == null) {
                // Make sure the bytes are disassembled before creating a function.
                try {
                    disassemble(targetAddr);
                } catch (Exception ignore) { }
                try {
                    existing = createFunction(targetAddr, funcName);
                } catch (Exception e) {
                    println("  vec" + vecNum + ": could not create function at 0x"
                            + Long.toHexString(targetOffset) + " (" + e.getMessage() + ")");
                    continue;
                }
                if (existing == null) continue;
            } else {
                String curName = existing.getName();
                // Only rename auto-generated names — preserve any human/script-set name.
                if (curName != null
                        && (curName.startsWith("FUN_")
                            || curName.startsWith("stub_")
                            || curName.startsWith("LAB_")
                            || curName.startsWith("SUB_"))) {
                    try {
                        existing.setName(funcName, SourceType.ANALYSIS);
                    } catch (InvalidInputException e) {
                        // name collision — try adding a suffix
                        try {
                            existing.setName(funcName + "_v" + vecNum, SourceType.ANALYSIS);
                        } catch (InvalidInputException e2) {
                            continue;
                        }
                    }
                } else {
                    // Already named explicitly — leave it alone.
                    continue;
                }
            }

            named++;
            println("  vec" + vecNum + " -> " + existing.getName()
                    + " @ 0x" + Long.toHexString(targetOffset)
                    + (isStub ? "  (stub)" : "  (direct ISR)"));
        }

        println("[DMP9_FuncFixup]   named " + named + " stub / ISR functions.");
    }

    // -----------------------------------------------------------------------
    // Heuristic prologue detector
    //
    // Returns true if the function looks like it has a standard stack frame:
    //   - LINK A6,#N   (sets up frame pointer)
    //   - MOVEM.L Dn/An,-(SP)  (saves callee-preserved regs)
    //
    // A function with neither is treated as register-calling.
    // -----------------------------------------------------------------------
    private boolean hasPrologue(Listing listing, Address entry) {
        Address cursor = entry;
        for (int i = 0; i < PROLOGUE_SCAN_DEPTH; i++) {
            Instruction instr = listing.getInstructionAt(cursor);
            if (instr == null) break;

            String mnem = instr.getMnemonicString().toUpperCase();

            // LINK A6,#N — classic m68k stack frame setup
            if (mnem.equals("LINK")) return true;

            // MOVEM into -(SP) — callee-saved register spill
            if (mnem.startsWith("MOVEM")) {
                String repr = instr.toString().toUpperCase();
                if (repr.contains("-(SP)") || repr.contains("-(A7)")) return true;
            }

            // RTS / RTE immediately at entry = tiny leaf function with no frame
            // but could still be stack-based (just no locals) — leave as default
            if (mnem.equals("RTS")) return true;   // conservative: treat as stack-based

            // If we see a JSR before a LINK, still no frame
            // If we see a MOVE.L (SP),An — compiler-generated frame pointer copy
            if (mnem.equals("MOVE")) {
                String repr = instr.toString().toUpperCase();
                if (repr.contains("SP") && repr.contains("A6")) return true;
            }

            try {
                cursor = instr.getMaxAddress().add(1);
            } catch (Exception e) {
                break;
            }
        }
        return false;
    }
}
