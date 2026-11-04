#!/usr/bin/env python3
"""Verify DexKit JNI entries in a manually assembled APK.

Checks exact ABI coverage, ZIP_STORED mode, 16 KiB data offsets, and byte identity
against the staged AAR payload. Uses only the Python standard library.
"""
from __future__ import annotations

import hashlib
import pathlib
import struct
import sys
import zipfile

ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
ALIGNMENT = 16 * 1024


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def data_offset(archive, info: zipfile.ZipInfo) -> int:
    archive.fp.seek(info.header_offset)
    header = archive.fp.read(30)
    if len(header) != 30:
        raise ValueError(f"truncated local ZIP header: {info.filename}")
    signature, = struct.unpack_from("<I", header, 0)
    if signature != 0x04034B50:
        raise ValueError(f"invalid local ZIP header: {info.filename}")
    name_length, extra_length = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + name_length + extra_length


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: verify-dexkit-apk.py <apk> <dexkit-stage>", file=sys.stderr)
        return 2
    apk = pathlib.Path(sys.argv[1])
    stage = pathlib.Path(sys.argv[2])
    expected = {f"lib/{abi}/libdexkit.so" for abi in ABIS}

    try:
        with zipfile.ZipFile(apk) as archive:
            entries = {name for name in archive.namelist() if name.endswith("/libdexkit.so")}
            if entries != expected:
                raise ValueError(
                    f"libdexkit.so ABI set mismatch: expected={sorted(expected)}, "
                    f"actual={sorted(entries)}"
                )
            for abi in ABIS:
                name = f"lib/{abi}/libdexkit.so"
                info = archive.getinfo(name)
                if info.compress_type != zipfile.ZIP_STORED:
                    raise ValueError(f"{name} must be ZIP_STORED")
                offset = data_offset(archive, info)
                if offset % ALIGNMENT:
                    raise ValueError(
                        f"{name} data offset {offset} is not {ALIGNMENT}-byte aligned"
                    )
                apk_bytes = archive.read(info)
                staged_bytes = (stage / "jni" / abi / "libdexkit.so").read_bytes()
                if sha256(apk_bytes) != sha256(staged_bytes):
                    raise ValueError(f"{name} differs from staged DexKit AAR payload")
                print(f"OK {name}: stored, {ALIGNMENT}-byte aligned, SHA-256 matched")
    except (OSError, zipfile.BadZipFile, KeyError, ValueError, struct.error) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
