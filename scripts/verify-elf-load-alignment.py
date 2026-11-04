#!/usr/bin/env python3
"""Validate ELF PT_LOAD alignment for Android 16 KiB page-size compatibility.

Usage: python3 scripts/verify-elf-load-alignment.py <shared-library> [...]
The script uses only the Python standard library and fails closed on malformed ELF.
"""
import pathlib
import struct
import sys

PT_LOAD = 1
MIN_ALIGNMENT = 1 << 14


def inspect(path: pathlib.Path) -> list[int]:
    data = path.read_bytes()
    if len(data) < 16 or data[:4] != b"\\x7fELF":
        raise ValueError("not an ELF file")
    elf_class, encoding = data[4], data[5]
    if elf_class not in (1, 2) or encoding not in (1, 2):
        raise ValueError("unsupported ELF class or byte order")
    endian = "<" if encoding == 1 else ">"
    if elf_class == 2:
        if len(data) < 64:
            raise ValueError("truncated ELF64 header")
        phoff = struct.unpack_from(endian + "Q", data, 32)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 54)
        expected_size = 56
        fmt = endian + "IIQQQQQQ"
    else:
        if len(data) < 52:
            raise ValueError("truncated ELF32 header")
        phoff = struct.unpack_from(endian + "I", data, 28)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 42)
        expected_size = 32
        fmt = endian + "IIIIIIII"
    if phentsize < expected_size or phnum == 0:
        raise ValueError("missing or invalid program header table")
    if phoff + phentsize * phnum > len(data):
        raise ValueError("program header table exceeds file size")

    alignments = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        fields = struct.unpack_from(fmt, data, offset)
        p_type = fields[0]
        p_align = fields[7] if elf_class == 2 else fields[7]
        if p_type == PT_LOAD:
            alignments.append(p_align)
    if not alignments:
        raise ValueError("ELF contains no PT_LOAD segments")
    return alignments


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: verify-elf-load-alignment.py <shared-library> [...]", file=sys.stderr)
        return 2
    failed = False
    for argument in sys.argv[1:]:
        path = pathlib.Path(argument)
        try:
            alignments = inspect(path)
            rendered = ", ".join("0x%x" % value for value in alignments)
            print("%s: PT_LOAD p_align=[%s]" % (path, rendered))
            bad = [value for value in alignments if value < MIN_ALIGNMENT or value & (value - 1)]
            if bad:
                print("ERROR: every PT_LOAD segment must have power-of-two p_align >= 0x4000",
                      file=sys.stderr)
                failed = True
        except (OSError, ValueError, struct.error) as error:
            print("%s: ERROR: %s" % (path, error), file=sys.stderr)
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
