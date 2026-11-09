#!/usr/bin/env bash
set -euo pipefail

SOURCE="module/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java"
test -s "$SOURCE"

python3 - "$SOURCE" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text(encoding="utf-8")

def section(start, end):
    a = source.index(start)
    b = source.index(end, a + len(start))
    return source[a:b]

checks = {
    "code295 path is isolated behind the static-only host profile":
        'if (HostCompat.isV275StaticOnly())' in source,
    "request marker is loaded without initialization and must be an interface":
        'Class.forName("yu1", false, loader)' in source and 'if (!requestMarker.isInterface()) return;' in source,
    "request route observer targets nw3.e":
        'installObservedMethod(loader, "nw3", "e", requestMarker,' in source,
    "code295 request route requires the exact b70 Flow return type":
        'Class.forName("b70", false, loader)' in source
        and 'installObservedMethod(loader, "nw3", "e", requestMarker, flowType,' in source
        and "(expectedReturnType == null" in source,
    "DeltaCommand observer targets r3.c(fv1, dc3)":
        'Class.forName("r3", false, loader)' in source
        and 'Class.forName("fv1", false, loader)' in source
        and 'Class.forName("dc3", false, loader)' in source
        and 'owner.getDeclaredMethod("c", eventType, continuationType)' in source,
    "DeltaCommand observer rejects unexpected return types":
        'if (target.getReturnType() != Object.class)' in section(
            "private static void installCode295DeltaDispatchObserver",
            "private static void installCode295PowObserver"),
    "PoW observer targets of2.k(ts9, ec3)":
        'Class.forName("of2", false, loader)' in source
        and 'Class.forName("ts9", false, loader)' in source
        and 'Class.forName("ec3", false, loader)' in source
        and 'owner.getDeclaredMethod("k", challengeType, continuationType)' in source,
    "PoW observer rejects unexpected return types":
        'if (target.getReturnType() != Object.class)' in section(
            "private static void installCode295PowObserver",
            "private static void installCode295SseDispatchObserver"),
    "all three observers are installed only in the code295 branch":
        section('if (HostCompat.isV275StaticOnly())', 'if (HostCompat.hostCode271() != 289)')
        .count("install") >= 3,
    "request and Flow observer preserve the original host return object":
        "installContinuationObserver(result);" in source and "return result;" in source,
    "code295 runtime profile starts pending rather than falsely confirmed or denied":
        "staticEvidenceOnly=true runtimeConfirmed=pending" in source,
    "actual transport invocation emits a runtime-confirmed event":
        'if ("transport-dispatch".equals(role))' in source
        and '"[OPEN_NATIVE_CALL_OBSERVER] runtime-confirmed"' in source
        and '" requestType="' in source,
    "runtime request metadata includes constructors and methods without field reads":
        "observedRequestShape(requestArg)" in source
        and "private static String observedRequestShape(Object value)" in source
        and "getDeclaredConstructors()" in source
        and "getDeclaredMethods()" in source
        and "getDeclaredField(" not in section(
            "private static String observedRequestShape", "private static String observedObjectShape"),

    "Flow identity uses weak references rather than retaining host Flow objects":
        "ReferenceQueue<Object> FLOW_TRACE_QUEUE" in source
        and "new IdentityWeakReference(flow, FLOW_TRACE_QUEUE)" in source,
    "DeltaCommand observer is pass-through and does not serialize event payload":
        "Object result = chain.proceed();" in section(
            "private static void installCode295DeltaDispatchObserver",
            "private static void installCode295PowObserver")
        and "toString()" not in section(
            "private static void installCode295DeltaDispatchObserver",
            "private static void installCode295PowObserver"),
    "PoW observer is pass-through and does not inspect challenge values":
        "Object result = chain.proceed();" in section(
            "private static void installCode295PowObserver",
            "private static void installCode295SseDispatchObserver"),
    "legacy pv1 SSE observer is not installed for code295":
        "installCode295SseDispatchObserver(loader);" not in section(
            'if (HostCompat.isV275StaticOnly())', 'if (HostCompat.hostCode271() != 289)'),
    "continuation failure captures sanitized stack frames":
        "observedThrowableStack(hostError)" in source
        and "private static String observedThrowableStack(Throwable error)" in source
        and ".append(element.getClassName()).append('#').append(element.getMethodName())" in source,
    "continuation diagnostics do not serialize exception messages":
        "hostError.getMessage()" not in section(
            "private static void installCode295DeltaDispatchObserver",
            "private static void installCode295PowObserver")
        and "hostError.getMessage()" not in section(
            "private static void installContinuationObserver",
            "private static void installCallbackObserver"),
}
for name, passed in checks.items():
    print(("PASS " if passed else "FAIL ") + name)
print(f"RESULT {sum(checks.values())}/{len(checks)}")
if not all(checks.values()):
    raise SystemExit(1)
PY
