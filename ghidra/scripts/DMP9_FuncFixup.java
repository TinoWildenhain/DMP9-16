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

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.lang.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.*;
import ghidra.util.exception.InvalidInputException;

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
