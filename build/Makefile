# Makefile - Yamaha DMP9/16 firmware reconstruction
#
# Toolchain: m68k-linux-gnu-gcc (or m68k-elf-gcc)
# Target:    Motorola 68000, big-endian, no hosted libc

CROSS   ?= m68k-linux-gnu
CC       = $(CROSS)-gcc
LD       = $(CROSS)-ld
OBJCOPY  = $(CROSS)-objcopy
SIZE     = $(CROSS)-size

CFLAGS  = -m68000 -O1 -fomit-frame-pointer -fno-builtin \
           -ffreestanding -Wall -Wextra \
           -I../src/hw -I../src/firmware

LDFLAGS = -T dmp9.ld -nostdlib

SRC     = $(wildcard ../src/firmware/*.c)
OBJ     = $(patsubst ../src/firmware/%.c, obj/%.o, $(SRC))

all: obj dmp9.elf dmp9.bin

obj:
	mkdir -p obj

obj/%.o: ../src/firmware/%.c
	$(CC) $(CFLAGS) -c $< -o $@

dmp9.elf: $(OBJ)
	$(LD) $(LDFLAGS) -o $@ $^
	$(SIZE) $@

dmp9.bin: dmp9.elf
	$(OBJCOPY) -O binary $< $@

clean:
	rm -rf obj dmp9.elf dmp9.bin

verify: dmp9.bin
	@echo "Comparing against canonical ROM..."
	@sha256sum dmp9.bin
	@sha256sum ../roms/TMS27C240-10-DMP9-16-XN349G0-v1.11-10.03.1994.bin

.PHONY: all clean verify
