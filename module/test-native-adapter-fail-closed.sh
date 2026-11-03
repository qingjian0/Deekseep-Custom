#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
STUB="$ROOT/module/src/ds/dsh/DeepSeek/z14.java"
FACADE="$ROOT/module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java"
PROBE="$ROOT/module/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java"

python - "$MAIN" "$STUB" "$FACADE" "$PROBE" <<'PY'
import pathlib
import re
import sys

main, stub, facade, probe = [
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
inspect = probe.split("static void inspectHost(ClassLoader loader)", 1)[1].split(
    "/** Legacy object-capture entry point", 1
)[0]
if re.search(r"method\.invoke\(|constructor\.newInstance\(|field\.get\(", inspect):
    raise SystemExit("Probe must not invoke candidate host methods or read live field values")
print("Native adapter fail-closed and read-only probe source checks passed")
PY
