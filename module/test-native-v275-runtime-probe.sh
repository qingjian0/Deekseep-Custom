#!/usr/bin/env bash
set -euo pipefail
MODULE="$(cd "$(dirname "$0")" && pwd)"
python3 - "$MODULE/src/ds/dsh/DeepSeek/NativeV275RuntimeProbe.java" \
  "$MODULE/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java" <<'PY'
import pathlib
import sys

probe = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
entry = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")

def require(source, token, label):
    if token not in source:
        raise SystemExit("Missing code295 read-only probe contract: " + label)

for token, label in [
    ("HostCompat.isV275StaticOnly()", "exact static-only profile gate"),
    ("Main.isOpenNativeCompletionProbeEnabled()", "explicit opt-in gate"),
    ('"nw3", "e", "b70"', "request Flow boundary"),
    ('"r3", "c", "java.lang.Object"', "Delta event dispatch boundary"),
    ('"pv1", "a", "java.lang.Object"', "SSE event adapter boundary"),
    ('"pv1", "e", "java.lang.Object"', "DeltaCommand application boundary"),
    ('"iv1", "c", "void"', "SSE lifecycle boundary"),
    ("Main.MODULE.hook(target)", "transparent Hook registration"),
    ("final Object result;", "original result storage"),
    ("result = chain.proceed();", "single original invocation"),
    ("return result;", "original result returned unchanged"),
    ("throw failure;", "original exception rethrown"),
    ("OpenNativeCompletionProbe.apiTrace", "opt-in diagnostic report integration"),
]:
    require(probe, token, label)

for forbidden in (
    "getDeclaredField(", "getField(", "field.get(", "getArg(0).toString()",
    "request.toString()", "Authorization", "requestBody", "prompt", "cookie",
    "target.invoke(", "constructor.newInstance("
):
    if forbidden in probe:
        raise SystemExit("Read-only probe must not inspect or invoke payload data: " + forbidden)

require(entry, "if (HostCompat.isV275StaticOnly())", "2.7.5 branch")
require(entry, "NativeV275RuntimeProbe.install(loader);", "2.7.5 probe integration")
require(entry, "if (HostCompat.hostCode271() != 289) return;", "2.7.2/code289 path remains separately gated")
print("2.7.5 read-only runtime probe source contract passed")
PY
