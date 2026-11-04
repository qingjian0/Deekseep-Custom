#!/usr/bin/env python3
"""Verify DexKit's Gradle-packaged launcher APK.

Compressed JNI libraries are accepted only when Gradle's legacy packaging mode is
explicitly enabled (installer extraction). Stored JNI libraries must be 16 KiB aligned.
Every embedded DexKit ELF is also checked for 16 KiB PT_LOAD alignment.
"""
from __future__ import annotations

import importlib.util
import pathlib
import struct
import sys
import zipfile

ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
DEXKIT_CLASS = b"Lorg/luckypray/dexkit/DexKitBridge;"


def load_elf_verifier(path: pathlib.Path):
    spec = importlib.util.spec_from_file_location("dexkit_elf_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load ELF alignment verifier")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.inspect


def zip_data_offset(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    if archive.fp is None:
        raise ValueError("ZIP archive is not open")
    archive.fp.seek(info.header_offset)
    header = archive.fp.read(30)
    if len(header) != 30:
        raise ValueError("truncated local ZIP header for " + info.filename)
    values = struct.unpack("<IHHHHHIIIHH", header)
    if values[0] != 0x04034B50:
        raise ValueError("invalid local ZIP header for " + info.filename)
    name_length, extra_length = values[-2:]
    return info.header_offset + 30 + name_length + extra_length


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "usage: verify-dexkit-gradle-apk.py <apk> <app-build-gradle.kts> <elf-verifier>",
            file=sys.stderr,
        )
        return 2

    apk = pathlib.Path(sys.argv[1])
    gradle_file = pathlib.Path(sys.argv[2])
    elf_verifier = load_elf_verifier(pathlib.Path(sys.argv[3]))
    gradle_text = gradle_file.read_text(encoding="utf-8")
    legacy_packaging = "jniLibs.useLegacyPackaging = true" in gradle_text

    try:
        with zipfile.ZipFile(apk) as archive:
            names = set(archive.namelist())
            dex_entries = sorted(
                name for name in names
                if name.startswith("classes") and name.endswith(".dex")
            )
            if not dex_entries:
                raise ValueError("APK contains no DEX files")
            if not any(DEXKIT_CLASS in archive.read(name) for name in dex_entries):
                raise ValueError("DexKitBridge class is absent from APK DEX files")

            expected = {f"lib/{abi}/libdexkit.so" for abi in ABIS}
            actual = {name for name in names if name.endswith("/libdexkit.so")}
            if actual != expected:
                raise ValueError(
                    "DexKit ABI set mismatch: expected=%s actual=%s"
                    % (sorted(expected), sorted(actual))
                )

            for abi in ABIS:
                name = f"lib/{abi}/libdexkit.so"
                info = archive.getinfo(name)
                payload = archive.read(name)
                if info.compress_type == zipfile.ZIP_STORED:
                    offset = zip_data_offset(archive, info)
                    if offset % 16384:
                        raise ValueError(
                            f"{name} is stored but data offset {offset} is not 16 KiB aligned"
                        )
                    packaging = f"stored, offset={offset}, aligned=16KiB"
                elif info.compress_type == zipfile.ZIP_DEFLATED:
                    if not legacy_packaging:
                        raise ValueError(
                            f"{name} is compressed but Gradle legacy extraction is not enabled"
                        )
                    packaging = "deflated, installer-extracted"
                else:
                    raise ValueError(
                        f"{name} uses unsupported ZIP compression method {info.compress_type}"
                    )

                import tempfile
                with tempfile.TemporaryDirectory(prefix="dexkit-elf-") as temp_dir:
                    extracted = pathlib.Path(temp_dir) / "libdexkit.so"
                    extracted.write_bytes(payload)
                    alignments = elf_verifier(extracted)
                bad = [
                    value for value in alignments
                    if value < 16384 or value & (value - 1)
                ]
                if bad:
                    raise ValueError(
                        f"{name} has incompatible PT_LOAD alignments: {bad}"
                    )
                print(
                    f"OK {name}: {packaging}; PT_LOAD="
                    + ",".join(f"0x{x:x}" for x in alignments)
                )

            print(
                f"DexKit Gradle APK verified: DEX={len(dex_entries)}, "
                f"ABI={len(ABIS)}, legacyPackaging={legacy_packaging}"
            )
    except (OSError, ValueError, zipfile.BadZipFile, struct.error) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
