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
    "Flow callback resolves its originating trace": "Object flow = chain.getThisObject();" in source and "Long flowTraceId = flowObservationId(flow);" in source and '" flowTraceId="' in source,
    "new diagnostic sessions clear stale Flow identities": "FLOW_TRACE_IDS.clear();" in source and source.index("FLOW_TRACE_IDS.clear();") < source.index("SESSION_STARTED_AT_MS = System.currentTimeMillis();"),
    "correlation is metadata-only": "flowIdentity=" in source and "observedObjectShape(flow)" not in source,
}
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " " + name)
print(f"RESULT {sum(checks.values())}/{len(checks)}")
if not all(checks.values()): raise SystemExit(1)
PY
