#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
HOST_DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiHostDiscovery.java"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"

python3 - "$DISCOVERY" "$HOST_DISCOVERY" "$MAIN" <<'PY'
import pathlib
import re
import sys

discovery, host_discovery, main = [pathlib.Path(p).read_text(encoding="utf-8") for p in sys.argv[1:]]

def require(source, pattern, label):
    if not re.search(pattern, source, re.S):
        raise SystemExit("Missing read-only DexKit discovery contract: " + label)

require(discovery, r'DexKitBridge\.create\(loaders\.get\(i\), true\)', "ART memory DEX is preferred")
require(discovery, r'DexKitBridge\.create\(apkPaths\.get\(i\)\)', "APK and split APK fallback exists")
require(discovery, r'collectApkPaths\(apkPath, splitApkPaths\)', "base and split APK paths are collected")
require(discovery, r'for \(int i = 0; i < apkPaths\.size\(\); i\+\+\)', "APK paths are scanned independently")
require(discovery, r'SEMANTIC_ANCHORS', "semantic query anchors are defined")
require(discovery, r'MethodMatcher\.create\(\)\.usingStrings', "DexKit semantic method matching is used")
require(discovery, r'MAX_CANDIDATES_PER_ANCHOR\s*=\s*8', "candidate output is bounded")
require(discovery, r'MAX_DISCOVERY_PASSES[ ]*=[ ]*3', "runtime rescans are bounded")
require(discovery, r'Thread[.]sleep[(]pass == 1 [?] 5000L : 10000L[)]', "late DEX rescan cadence is bounded")
require(discovery, r'inspect[(]root, apkPath, splitApkPaths, pass == 0[)]', "APK scans run only on the initial pass")
require(discovery, r'apk[.]scanSkipped=runtime_rescan', "runtime rescans avoid redundant APK scans")
require(discovery, r'loader[.]rescan=bounded_thread_context_snapshot', "runtime loader rescan scope is explicit")
require(discovery, r'bridge\.close\(\)', "native bridge resources are closed")
require(discovery, r'System\.loadLibrary\("dexkit"\)', "JNI loading is explicit")
require(discovery, r'REGISTERED_LOADERS', "dynamic loader registration API exists")
require(discovery, r'activation=forbidden_without_external_validation', "candidate activation remains gated")
require(discovery, r'hooksInstalled=false', "discovery does not install hooks")
require(main, r'RoiDexKitDiscovery\.schedule\(cl,.*?splitSourceDirs\)', "unknown-host path schedules DexKit discovery")
require(host_discovery, r"static void schedule", "DEX inventory remains a separate first layer")

for forbidden in ("getMethodInstance(", "getConstructorInstance(", "Main.MODULE.hook(", "XposedBridge.hookMethod("):
    if forbidden in discovery:
        raise SystemExit("DexKit discovery must not resolve live hook targets: " + forbidden)

print("ROI DexKit read-only discovery regression checks passed")
PY
