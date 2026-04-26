# =============================================================================
# DMP9/16 ROM Reconstruction — Top-level build Makefile
# =============================================================================
#
# Usage:
#   make VERSION=XN349G0          Build v1.11
#   make VERSION=XN349F0          Build v1.10
#   make VERSION=XN349E0          Build v1.02
#   make all                      Build all three versions
#   make verify VERSION=XN349G0   Size + coefficient-table checksum check
#   make clean VERSION=XN349G0    Remove build artefacts for one version
#   make clean-all                Remove all build artefacts
#
# Prerequisites:
#   m68k-linux-gnu-gcc  (Debian/Ubuntu: gcc-m68k-linux-gnu)
#   m68k-linux-gnu-as
#   m68k-linux-gnu-ld
#   m68k-linux-gnu-objcopy
#   m68k-linux-gnu-objdump
#   m68k-linux-gnu-size
#   md5sum / sha256sum
#
# See build/toolchain-setup.md for installation instructions.
# =============================================================================

# --- Toolchain ----------------------------------------------------------------
CROSS   ?= m68k-linux-gnu-
CC       = $(CROSS)gcc
AS       = $(CROSS)as
LD       = $(CROSS)ld
OBJCOPY  = $(CROSS)objcopy
OBJDUMP  = $(CROSS)objdump
SIZE     = $(CROSS)size

# --- Version selection --------------------------------------------------------
VERSION ?= XN349G0

ifeq ($(VERSION),XN349G0)
  VER_STR  := v1.11
  VER_DATE := 1994-03-10
else ifeq ($(VERSION),XN349F0)
  VER_STR  := v1.10
  VER_DATE := 1994-01-20
else ifeq ($(VERSION),XN349E0)
  VER_STR  := v1.02
  VER_DATE := 1993-11-10
else
  $(error Unknown VERSION "$(VERSION)". Use XN349G0, XN349F0, or XN349E0)
endif

# --- Directories --------------------------------------------------------------
REPO_ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
SRC_DIR   := $(REPO_ROOT)/src/$(VERSION)
SHARED_DIR:= $(REPO_ROOT)/src/shared
BUILD_DIR := $(REPO_ROOT)/build/obj/$(VERSION)
OUT_DIR   := $(REPO_ROOT)/build/out/$(VERSION)

# --- Source files -------------------------------------------------------------
C_SRCS := \
    $(SRC_DIR)/startup.c \
    $(SRC_DIR)/isr.c \
    $(SRC_DIR)/main.c \
    $(SRC_DIR)/lcd.c \
    $(SRC_DIR)/midi.c \
    $(SRC_DIR)/dsp.c

ASM_SRCS := \
    $(SRC_DIR)/vectors.S \
    $(SRC_DIR)/strings.S \
    $(SHARED_DIR)/dsp_tables.S

OBJS := \
    $(patsubst $(SRC_DIR)/%.c,   $(BUILD_DIR)/%.o, $(filter $(SRC_DIR)/%, $(C_SRCS))) \
    $(patsubst $(SRC_DIR)/%.S,   $(BUILD_DIR)/%.o, $(filter $(SRC_DIR)/%, $(ASM_SRCS))) \
    $(patsubst $(SHARED_DIR)/%.S,$(BUILD_DIR)/shared_%.o, $(filter $(SHARED_DIR)/%, $(ASM_SRCS)))

# --- Include paths ------------------------------------------------------------
INCLUDES := \
    -I$(REPO_ROOT)/src \
    -I$(REPO_ROOT)/src/hw

# --- Compiler flags -----------------------------------------------------------
#
# -m68000       Pure 68000 (NOT ColdFire — TMP68301 has 68000 core)
# -Os           Optimise for size (matches expected ROM density)
# -fno-builtin  No implicit stdlib assumptions
# -fomit-frame-pointer  Allow register CC (yamaha_reg style)
# -ffreestanding        No hosted runtime assumptions
# -nostdlib     No CRT startup
# -Wall -Wextra Pedantic warnings

CFLAGS := \
    -m68000 \
    -Os \
    -fno-builtin \
    -fomit-frame-pointer \
    -ffreestanding \
    -nostdlib \
    -Wall -Wextra \
    -DVERSION_LABEL=\"$(VERSION)\" \
    -DVERSION_STRING=\"$(VER_STR)\" \
    $(INCLUDES)

ASFLAGS := \
    -m68000 \
    $(INCLUDES)

# --- Linker -------------------------------------------------------------------
LDSCRIPT := $(REPO_ROOT)/build/dmp9.ld

LDFLAGS := \
    -T$(LDSCRIPT) \
    -nostdlib \
    -Map=$(OUT_DIR)/$(VERSION).map

# --- Output files -------------------------------------------------------------
ELF  := $(OUT_DIR)/$(VERSION).elf
BIN  := $(OUT_DIR)/$(VERSION).bin
LST  := $(OUT_DIR)/$(VERSION).lst

# =============================================================================
# Rules
# =============================================================================

.PHONY: all clean clean-all verify disasm help

all:
	$(MAKE) VERSION=XN349E0
	$(MAKE) VERSION=XN349F0
	$(MAKE) VERSION=XN349G0

$(BUILD_DIR) $(OUT_DIR):
	mkdir -p $@

# Compile C sources
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c -o $@ $<

# Assemble .S sources from version directory
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.S | $(BUILD_DIR)
	$(CC) $(ASFLAGS) -c -o $@ $<

# Assemble .S sources from shared directory
$(BUILD_DIR)/shared_%.o: $(SHARED_DIR)/%.S | $(BUILD_DIR)
	$(CC) $(ASFLAGS) -c -o $@ $<

# Link
$(ELF): $(OBJS) $(LDSCRIPT) | $(OUT_DIR)
	$(LD) $(LDFLAGS) -o $@ $(OBJS)
	$(SIZE) $@

# Flat binary (512 KB, FF-padded)
$(BIN): $(ELF)
	$(OBJCOPY) -O binary $< $@
	# Pad to 512 KB (524288 bytes) with 0xFF
	@ACTUAL=$$(wc -c < $@); \
	if [ $$ACTUAL -lt 524288 ]; then \
	    dd if=/dev/zero bs=1 count=$$((524288 - $$ACTUAL)) 2>/dev/null | \
	        tr '\0' '\377' >> $@; \
	fi
	@echo "  ROM image: $(BIN) ($$(wc -c < $@) bytes)"

# Disassembly listing
$(LST): $(ELF)
	$(OBJDUMP) -d -S $< > $@

# Default target for a single version
$(VERSION): $(BIN) $(LST)

# Verification
verify: $(BIN)
	@echo "=== Verify $(VERSION) ==="
	@# Check total size
	@ACTUAL=$$(wc -c < $(BIN)); \
	if [ $$ACTUAL -eq 524288 ]; then \
	    echo "  [OK] Size: 524288 bytes (512 KB)"; \
	else \
	    echo "  [FAIL] Size: $$ACTUAL bytes (expected 524288)"; exit 1; \
	fi
	@# Check data/coefficient table region (0x059B00–0x06283F) matches reference
	@# Reference SHA256 (from XN349G0 original, identical all versions):
	@REF_SHA="$(shell dd if=$(REPO_ROOT)/roms/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin \
	    bs=1 skip=$$((0x059B00)) count=$$((0x062840-0x059B00)) 2>/dev/null | sha256sum | cut -d' ' -f1)"; \
	GOT_SHA=$$(dd if=$(BIN) bs=1 skip=$$((0x059B00)) count=$$((0x062840-0x059B00)) 2>/dev/null | sha256sum | cut -d' ' -f1); \
	if [ "$$GOT_SHA" = "$$REF_SHA" ]; then \
	    echo "  [OK] Coefficient table SHA256 matches"; \
	else \
	    echo "  [WARN] Coefficient table SHA256 differs (expected $$REF_SHA, got $$GOT_SHA)"; \
	fi
	@echo "  SHA256($(VERSION).bin): $$(sha256sum $(BIN) | cut -d' ' -f1)"

disasm: $(LST)
	@echo "Listing written to $(LST)"

clean:
	rm -rf $(BUILD_DIR) $(OUT_DIR)

clean-all:
	rm -rf $(REPO_ROOT)/build/obj $(REPO_ROOT)/build/out

help:
	@echo "DMP9/16 ROM Reconstruction Build System"
	@echo ""
	@echo "Targets:"
	@echo "  make VERSION=XN349G0          Build v1.11 (default)"
	@echo "  make VERSION=XN349F0          Build v1.10"
	@echo "  make VERSION=XN349E0          Build v1.02"
	@echo "  make all                      Build all three versions"
	@echo "  make verify VERSION=XN349G0   Run verification checks"
	@echo "  make disasm VERSION=XN349G0   Generate disassembly listing"
	@echo "  make clean VERSION=XN349G0    Remove build output for one version"
	@echo "  make clean-all                Remove all build output"
	@echo ""
	@echo "See build/toolchain-setup.md for toolchain installation."
