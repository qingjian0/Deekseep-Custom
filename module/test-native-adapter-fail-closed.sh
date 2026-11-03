#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
STUB="$ROOT/module/src/ds/dsh/DeepSeek/z14.java"
FACADE="$ROOT/module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java"
PROBE="$ROOT/module/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java"
Z18="$ROOT/module/src/ds/dsh/DeepSeek/z18.java"
HOST_V271="$ROOT/module/src/ds/dsh/DeepSeek/HostV271.java"
CHAT="$ROOT/module/src/ds/dsh/DeepSeek/HookChatPipeline.java"

python - "$MAIN" "$STUB" "$FACADE" "$PROBE" "$Z18" "$HOST_V271" "$CHAT" <<'PY'
import pathlib
import re
import sys

main, stub, facade, probe, z18, host_v271, chat = [
    pathlib.Path(path).read_text(encoding="utf-8") for path in sys.argv[1:]
]

def require(source, pattern, label):
    if not re.search(pattern, source, re.S):
        raise SystemExit("Missing native adapter fail-closed contract: " + label)

require(
    stub,
    r"static boolean available\(\)\s*\{\s*return false;\s*\}",
    "Open payload availability remains false",
)
require(
    stub,
    r"static Class<\?> payloadClass\(Context context, String name\)\s*\{\s*return null;\s*\}",
    "Open payload resolver remains unavailable",
)
require(
    main,
    r"@Override\s+public boolean isReady\(\)\s*\{\s*return BuildInfo\.LOCAL_API_INCLUDED\s*&&",
    "Backend readiness requires the native adapter build flag",
)
require(
    main,
    r"@Override\s+public boolean isReady\(\)\s*\{\s*return BuildInfo\.LOCAL_API_INCLUDED\s*&&\s*hostClassLoader != null && liveR92 != null && liveQ71 != null",
    "Backend readiness does not accept missing legacy transport or PoW objects",
)
require(
    main,
    r'if \(!HostCompat\.isV271\(\)\) installBestEffortHook\("network\.payload-capture"',
    "legacy transport capture is not installed for the 2.7.x family",
)
require(
    main,
    r'if \(!HostCompat\.isV271\(\)\) installBestEffortHook\("chat\.pow-manager-capture"',
    "legacy PoW capture is not installed for the 2.7.x family",
)
require(
    host_v271,
    r"static void installFeatureHooks\(final ClassLoader cl\)",
    "2.7.x limited whitelist remains an explicit separate hook path",
)
if "installPowManagerCapture" in host_v271 or "hookTransport(" in host_v271:
    raise SystemExit("2.7.x whitelist must not silently reuse legacy q71/transport capture")
require(
    chat,
    r"void installPowManagerCapture\(ClassLoader cl\)",
    "legacy q71 capture entry point remains identifiable for a future version-specific adapter",
)
require(
    main,
    r'if \(!BuildInfo\.LOCAL_API_INCLUDED && "Open"\.equals\(BuildInfo\.BUILD_EDITION\)\)\s*\{\s*throw new z2\.GatewayException\(503, "native_adapter_unavailable"',
    "Open completion requests fail with HTTP 503",
)
require(
    facade,
    r"Class<\?> cls = z14\.payloadClass\([^;]+;\s*java\.lang\.reflect\.Method m = cls == null \? null",
    "execution facade fails closed when payload class is absent",
)
require(
    probe,
    r"Read-only runtime inventory",
    "probe remains explicitly read-only",
)
require(
    probe,
    r"static void inspectHost\(ClassLoader loader\)",
    "Open host inventory entry point remains present",
)
require(
    probe,
    r'line\(out, "report\.schema", "2"\)',
    "probe report schema is versioned",
)
require(
    probe,
    r'line\(out, "dex\.eligibleClassNameCount"',
    "probe reports eligible class-name coverage",
)
require(
    probe,
    r'line\(out, "dex\.classLoadFailureCount"',
    "probe reports class-load failures",
)
require(
    probe,
    r'line\(out, "probe\.elapsedMs"',
    "probe reports scan duration",
)
require(
    facade,
    r'z14\.payloadClass\(Main\.hostApplicationContext, "ds\.dsh\.DeepSeek\.z18"\)',
    "execution facade payload class target remains explicit",
)
if re.search(r"executeLocalApiCompletion\s*\(", z18):
    raise SystemExit("Repository z18 helper must not be mistaken for the Completion payload")

require(
    main,
    r'if \(!new File\(HookAccountLoginSecurity\.LOCAL_API_ENABLED_FILE\)\.exists\(\)\) return;\s*// 2\.7\.x deliberately skips the older q71/r92 capture hooks\. The read-only inventory is\s*// diagnostic only',
    "native host inventory is gated by explicit Local API enablement",
)
require(
    main,
    r'if \(openEdition\) \{\s*Thread probeThread = new Thread\(\s*\(\) -> OpenNativeCompletionProbe\.inspectHost\(hostClassLoader\),\s*"dsh-native-completion-probe"\);\s*probeThread\.setDaemon\(true\);\s*probeThread\.start\(\);\s*\}',
    "native host inventory runs once on a daemon worker rather than the startup/UI caller",
)
if re.search(r'if \(openEdition\) OpenNativeCompletionProbe\.inspectHost\(hostClassLoader\)', main):
    raise SystemExit("native host inventory must not execute synchronously on the startup/UI caller")

inspect = probe.split("static void inspectHost(ClassLoader loader)", 1)[1].split(
    "/** Legacy object-capture entry point", 1
)[0]
if re.search(r"method\.invoke\(|constructor\.newInstance\(|field\.get\(", inspect):
    raise SystemExit("Probe must not invoke candidate host methods or read live field values")
print("Native adapter fail-closed and read-only probe source checks passed")
PY
