#!/usr/bin/env bash
set -euo pipefail
SOURCE="module/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java"
test -s "$SOURCE"
python3 - "$SOURCE" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding="utf-8")
checks = {
    "schema is version 7": 'line(out, "report.schema", "7");' in source,
    "identity weak references use a reference queue": "ReferenceQueue<Object> FLOW_TRACE_QUEUE" in source and "WeakReference<Object>" in source and "drainFlowTraceQueueLocked()" in source,
    "weak identity equality compares referents by identity": "left != null && left == right" in source and "System.identityHashCode(value)" in source,
    "transport observation associates each returned Flow": "associateFlowWithObservation(result);" in source and source.index("associateFlowWithObservation(result);") < source.index("OBSERVATION_Y60.compareAndSet(false, true)"),
    "Flow callback resolves its originating trace": "Object flow = chain.getThisObject();" in source and "Long flowTraceId = observationIdForObject(flow);" in source and '" flowTraceId="' in source,
    "Continuation is identity-linked but not method-hooked": "associateObjectWithObservation(second, flow);" in source and 'installCallbackObserver(second, "callback-1")' not in source,
    "collector callback receives the Flow trace identity": "associateObjectWithObservation(first, flow);" in source and "traceIdValue = observationIdForObject(receiver);" in source and 'flowTraceId=" + (traceIdValue == null ? "unlinked" : traceIdValue)' in source,
    "collector callback enter exit and throw logs retain trace IDs": source.count('flowTraceId=" + (callbackTraceId == null ? "unlinked" : callbackTraceId)') >= 2 and "flowTraceId=" in source[source.index('callback-throw method='):],
    "continuation identity is weakly associated with the Flow trace": "associateObjectWithObservation(second, flow);" in source and "new IdentityWeakReference(continuation, FLOW_TRACE_QUEUE)" in source,
    "DeltaCommand reducer resolves the exact continuation identity": "Object continuation = args != null && args.size() > 1 ? args.get(1) : null;" in source and "deltaTraceId = observationIdForObject(continuation);" in source,
    "DeltaCommand events expose linked or explicitly unlinked trace IDs": source.count('" flowTraceId=" + (deltaTraceId == null ? "unlinked" : deltaTraceId)') >= 3 and "continuationIdentity=" in source,
    "new diagnostic sessions clear stale Flow identities": "FLOW_TRACE_IDS.clear();" in source and source.index("FLOW_TRACE_IDS.clear();") < source.index("SESSION_STARTED_AT_MS = System.currentTimeMillis();"),
    "in-flight prior-session requests cannot repopulate current links": "frame.sessionId != CURRENT_OBSERVATION_SESSION_ID" in source and "link.sessionId != CURRENT_OBSERVATION_SESSION_ID" in source,
    "Flow association rechecks session generation under the map lock": (
        "private static void associateFlowWithObservation" in source
        and "private static void associateContinuationWithObservation" in source
        and "if (frame.sessionId != CURRENT_OBSERVATION_SESSION_ID) return;" in
            source[source.index("private static void associateFlowWithObservation"):
                   source.index("private static void associateContinuationWithObservation")]
    ),
    "observation ids remain monotonic across sessions": "OBSERVATION_IDS.set(0L)" not in source and "OBSERVATION_SESSION_IDS.incrementAndGet()" in source,
    "correlation is metadata-only": "flowIdentity=" in source and "observedObjectShape(flow)" not in source,
}
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " " + name)
print(f"RESULT {sum(checks.values())}/{len(checks)}")
if not all(checks.values()): raise SystemExit(1)
PY
