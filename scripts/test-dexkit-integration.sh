#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$ROOT" <<'PY'
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
legacy = (root / "module-legacy/build.sh").read_text()
universal = (root / "module-universal/build.sh").read_text()
gradle = (root / "module-kotlin-runtime/build.gradle.kts").read_text()
main = (root / "module/src/ds/dsh/DeepSeek/Main.java").read_text()
inventory = (root / "module/src/ds/dsh/DeepSeek/RoiHostDiscovery.java").read_text()
dexkit = (root / "module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java").read_text()

for name, source in (("legacy", legacy), ("universal", universal)):
    for required in (
        "stageDexKitArtifacts",
        "dexkit-2.3.0.jar",
        "flatbuffers-java-23.5.26.jar",
        "package-dexkit-native.sh",
        "-P 16 4",
        "verify-dexkit-apk.py",
    ):
        if required not in source:
            raise SystemExit(f"{name} APK build is missing DexKit integration: {required}")

for required in (
    '"arm64-v8a"', '"armeabi-v7a"', '"x86"', '"x86_64"',
    "abiNames == expectedAbis",
):
    if required not in gradle:
        raise SystemExit(f"DexKit staging does not enforce complete ABI coverage: {required}")

if "param.appInfo.splitSourceDirs" not in main:
    raise SystemExit("runtime discovery must receive installed Split APK paths")
if "RoiDexKitDiscovery.inspect(root, apkPath, splitApkPaths)" not in inventory:
    raise SystemExit("DEX inventory must invoke the read-only DexKit inventory")
for required in (
    "DexKitBridge.create(loaders.get(i), true)",
    "DexKitBridge.create(apkPaths.get(i))",
    "bridge.close()",
    "hooksInstalled=false",
    "semanticCandidates=not_queried",
):
    if required not in dexkit:
        raise SystemExit(f"DexKit discovery contract missing: {required}")
for forbidden in ("XposedBridge.hook", "Main.MODULE.hook(", "Class.forName("):
    if forbidden in dexkit:
        raise SystemExit(f"DexKit inventory must remain read-only: {forbidden}")

print("DexKit dual-APK integration and read-only discovery regression checks passed")
PY
