#!/usr/bin/env python3
"""Audit the code289 chat-session creation fallback from an all-smali.zip artifact.

Usage:
  python3 tools/audit_code289_session_fallback.py /path/to/all-smali.zip

This is a static evidence check only. It does not execute Android code or prove
runtime authentication, host lifecycle, or device behavior.
"""
from __future__ import annotations

import argparse
import sys
import zipfile


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("smali_zip", help="all-smali.zip from code289-cn static evidence")
    args = parser.parse_args()

    try:
        with zipfile.ZipFile(args.smali_zip) as archive:
            names = set(archive.namelist())

            def source(path: str) -> str:
                if path not in names:
                    raise KeyError(f"missing Smali file: {path}")
                return archive.read(path).decode("utf-8", "replace")

            files = {
                "fx1": source("smali/fx1.smali"),
                "mb0": source("smali/mb0.smali"),
                "ch2": source("smali/ch2.smali"),
                "ft9": source("smali/ft9.smali"),
                "dt9": source("smali/dt9.smali"),
                "us9": source("smali/us9.smali"),
                "rz0": source("smali/rz0.smali"),
                "zs9": source("smali/zs9.smali"),
                "j41": source("smali/j41.smali"),
            }
    except (OSError, zipfile.BadZipFile, KeyError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    checks = {
        "fx1.m creates mb0(pi) and enters ru7.N": (
            "Lmb0;-><init>(Lpi;Lyb3;)V" in files["fx1"]
            and "Lru7;->N(Ltc3;Lvy4;Lyb3;)Ljava/lang/Object;" in files["fx1"]
        ),
        "mb0 obtains zn3 from pi.c": (
            "iget-object v1, v1, Lpi;->c:Ljava/lang/Object;" in files["mb0"]
            and "check-cast v1, Lzn3;" in files["mb0"]
        ),
        "mb0 invokes ch2.b": "Lch2;->b(Lyb3;)Ljava/lang/Object;" in files["mb0"],
        "ch2 configures the biz wrapper serializer": (
            "Lft9;" in files["ch2"] and "Lws9;" in files["ch2"]
            and "ServerResponseBizDataWrapper" in files["ft9"]
        ),
        "rz0 branch contains /api/v0/chat_session/create": (
            '"/api/v0/chat_session/create"' in files["rz0"]
        ),
        "rz0 applies the POST verb marker": "Lz2c;->V(Lng5;Lxa3;)V" in files["rz0"],
        "rz0 sets a 5000ms timeout": "const-wide/16 v8, 0x1388" in files["rz0"],
        "biz wrapper descriptor names biz_code/msg/data": all(
            f'"{name}"' in files["dt9"] for name in ("biz_code", "biz_msg", "biz_data")
        ),
        "ServerDefaultBizCode serializer is present": (
            "ServerDefaultBizCode" in files["us9"]
        ),
        "zs9 checks the business code zero success branch": (
            "iget v1, v5, Ljs9;->a:I" in files["zs9"]
            and "if-nez v1, :cond_2" in files["zs9"]
        ),
        "j41 decodes ok2 from ft9.c": (
            "iget-object v1, v9, Lft9;->c:Lc26;" in files["j41"]
            and "Lok2;->Companion:Lnk2;" in files["j41"]
        ),
        "j41 extracts ss9 and constructs ys": (
            "iget-object v1, v1, Lok2;->a:Lss9;" in files["j41"]
            and "Lys;-><init>(Lss9;Lqs;)V" in files["j41"]
        ),
        "j41 wraps the created session in pt7/tn2": (
            "Ltn2;-><init>(Lys;Z)V" in files["j41"]
            and "Lpt7;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V" in files["j41"]
        ),
        "mb0 has an explicit business-error result path": (
            "Ltt7;-><init>(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V"
            in files["mb0"]
        ),
    }

    for label, passed in checks.items():
        print(f'{"PASS" if passed else "FAIL"} {label}')
    passed_count = sum(checks.values())
    print(f"RESULT {passed_count}/{len(checks)}")
    return 0 if passed_count == len(checks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
