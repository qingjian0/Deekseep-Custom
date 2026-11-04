#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-runtime-recorder-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java" \
  "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiRuntimeInvocationRecorderTest

# Static integration guard: the observer must remain opt-in and use existing callbacks only.
OBSERVER="$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationObserver.java"
CHAT="$ROOT/module/src/ds/dsh/DeepSeek/HookChatPipeline.java"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
grep -q 'deekseep_roi_runtime_observation_enabled' "$OBSERVER"
grep -q 'new IdentityHashMap<Object, String>()' "$OBSERVER"
grep -q 'recordBuilt(chain.proceed()' "$CHAT"
grep -q 'recordBuilt(chain.proceed(originalArgs)' "$CHAT"
grep -q 'recordBuilt(chain.proceed(args)' "$CHAT"
grep -q 'recordTransport(request, m)' "$CHAT"
grep -q 'recordTransportResult(reqObj, r, m)' "$CHAT"
grep -q 'wrapFlowCollector(' "$ROOT/module/src/ds/dsh/DeepSeek/HookAttachmentPipeline.java"
grep -q 'self, collector, q03' "$ROOT/module/src/ds/dsh/DeepSeek/HookAttachmentPipeline.java"
grep -q 'collectorType.isInterface()' "$OBSERVER"
grep -q '"emit".equals(method.getName())' "$OBSERVER"
grep -q 'recordResponseReceived(key, method)' "$OBSERVER"
grep -q 'rethrowFatal(ignored)' "$OBSERVER"
grep -q 'RoiRuntimeInvocationObserver.initialize()' "$MAIN"

# A suspending Kotlin emit is not confirmed merely because the reflective call returned.
grep -q 'hasContinuationParameter(method)' "$OBSERVER"
grep -q 'kotlin.coroutines.Continuation' "$OBSERVER"
grep -q 'hasSuspendingEmit(collectorType)' "$OBSERVER"
grep -q 'Do not proxy its collector' "$OBSERVER"
# SSE event construction and session-message replacement are not request-correlated parser evidence.
if grep -q 'Phase.RESPONSE_PARSED' "$CHAT"; then
  echo "Uncorrelated RESPONSE_PARSED event must not be emitted from HookChatPipeline" >&2
  exit 1
fi

# Ordered evidence tiers must be implemented and covered by recorder tests.
grep -q 'orderedRequestTransportResponse' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'confirmsParsedResponse()' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'testOutOfOrderPhasesDoNotConfirm' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testParsedPhaseRequiresOrderedReceipt' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
