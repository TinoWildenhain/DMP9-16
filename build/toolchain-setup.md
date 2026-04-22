# Toolchain setup

## Required tools

| Tool | Purpose |
|---|---|
| `m68k-linux-gnu-gcc` | Cross-compiler for 68000 target |
| `m68k-linux-gnu-binutils` | Linker, objcopy, size |
| `radare2` | Binary diffing and analysis |
| `ghidra` | Disassembly and decompilation |
| `python3` + `unicorn` | Emulator harness |

## Debian/Ubuntu install

```bash
sudo apt install gcc-m68k-linux-gnu binutils-m68k-linux-gnu
sudo apt install radare2
pip3 install unicorn
```

## macOS install (Homebrew)

```bash
brew install m68k-elf-gcc   # or via cross-compilation tap
brew install radare2
pip3 install unicorn
```

## Ghidra

Download from https://ghidra-sre.org/
Required version: 10.3 or later (for improved 68000 support).

## Build

```bash
cd build/
make
```

To compare output against canonical ROM:

```bash
make verify
```

## Compiler notes

- Use `-O1 -fomit-frame-pointer` to match ROM code generation patterns
- Do NOT use `-O2` or higher without verifying output matches disassembly
- `-fno-builtin -ffreestanding` required — no hosted libc available on target
- If using `m68k-elf-gcc` instead of `m68k-linux-gnu-gcc`, set `CROSS=m68k-elf` in Makefile
