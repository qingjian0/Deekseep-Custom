#!/usr/bin/env python3
"""Validate a DeepSeek APK intake and extract DEX files for a later read-only scan.

This is an intake stage, not the Android-runtime DexKit ROI scanner. It never
loads classes, invokes app code, installs hooks, or grants hook authorization.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import zipfile


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--expected-package", default="com.deepseek.chat")
    parser.add_argument("--expected-version", default="2.7.5")
    args = parser.parse_args()

    apk = args.apk
    if not apk.is_file() or apk.stat().st_size == 0:
        raise SystemExit(f"APK missing or empty: {apk}")

    try:
        with zipfile.ZipFile(apk) as archive:
            bad_member = archive.testzip()
            if bad_member:
                raise ValueError(f"ZIP CRC failure: {bad_member}")
            names = archive.namelist()
            dex_names = sorted(
                (name for name in names if re.fullmatch(r"classes(?:[2-9][0-9]*)?\\.dex|classes\\.dex", name)),
                key=lambda name: 1 if name == "classes.dex" else int(re.search(r"(\\d+)", name).group(1)),
            )
            if not dex_names or "AndroidManifest.xml" not in names:
                raise ValueError("APK must contain AndroidManifest.xml and at least one root classes*.dex")
            args.out.mkdir(parents=True, exist_ok=True)
            dex_dir = args.out / "dex"
            dex_dir.mkdir(exist_ok=True)
            dex_records = []
            for name in dex_names:
                payload = archive.read(name)
                if len(payload) < 112 or payload[:4] != b"dex\\n":
                    raise ValueError(f"invalid DEX header: {name}")
                target = dex_dir / name
                target.write_bytes(payload)
                dex_records.append({
                    "entry": name,
                    "size_bytes": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "extracted_path": str(target.relative_to(args.out)),
                })
    except (OSError, zipfile.BadZipFile, ValueError) as error:
        raise SystemExit(f"APK validation failed: {error}") from error

    result = subprocess.run(
        [args.aapt, "dump", "badging", str(apk)],
        check=False, capture_output=True, text=True,
    )
    if result.returncode:
        raise SystemExit("aapt dump badging failed: " + result.stderr[-2000:])
    match = re.search(r"^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", result.stdout, re.M)
    if not match:
        raise SystemExit("aapt output did not contain a parseable package record")
    package_name, version_code, version_name = match.groups()
    if package_name != args.expected_package:
        raise SystemExit(f"package mismatch: expected {args.expected_package}, got {package_name}")
    if args.expected_version and version_name != args.expected_version:
        raise SystemExit(f"version mismatch: expected {args.expected_version}, got {version_name}")

    report = {
        "schema": 1,
        "stage": "apk-intake-and-dex-extraction",
        "analysis_mode": "static-input-validation-only",
        "package": package_name,
        "version_code": version_code,
        "version_name": version_name,
        "apk": {
            "filename": apk.name,
            "size_bytes": apk.stat().st_size,
            "sha256": sha256_file(apk),
        },
        "split_apks_supplied": False,
        "dex_count": len(dex_records),
        "dex": dex_records,
        "hook_activation": "not_performed",
        "hook_authorization": False,
        "limitations": [
            "This workflow validates one APK file; split APKs must be supplied separately if the installed app uses splits.",
            "This stage extracts DEX only; it does not run the Android-runtime DexKit discovery engine.",
            "Static evidence cannot establish runtime call paths or authorize hooks.",
        ],
    }
    (args.out / "intake-report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
