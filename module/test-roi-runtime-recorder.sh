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
grep -q 'findSuspendingEmit(collectorType)' "$OBSERVER"
grep -q 'UNSUPPORTED_SUSPEND_COLLECTOR' "$OBSERVER"
grep -q 'recordCoverage(' "$OBSERVER"
grep -q 'snapshotCoverage()' "$OBSERVER"
grep -q 'Preserve collector identity' "$OBSERVER"
# SSE event construction and session-message replacement are not request-correlated parser evidence.
if grep -q 'Phase.RESPONSE_PARSED' "$CHAT"; then
  echo "Uncorrelated RESPONSE_PARSED event must not be emitted from HookChatPipeline" >&2
  exit 1
fi

# Sanitized read-only snapshots must remain gated, bounded, TTL-pruned and payload-free.
grep -q 'snapshotEvidence(long nowMillis)' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'snapshotEvidence()' "$OBSERVER"
grep -q 'activeEnabled(active)' "$OBSERVER"
grep -q 'currentAdapterIdentity()' "$OBSERVER"
grep -q 'candidateDescriptor.equals(item.candidateDescriptor)' "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
grep -q 'adapterIdentity.equals(item.adapterIdentity)' "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
grep -q 'runtimeEvidence.resolverContribution=none' "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
grep -q 'testSanitizedEvidenceSnapshot' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testDisabledAndExpiredEvidenceSnapshotsAreEmpty' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testCoverageDiagnosticsAreBoundedSanitizedAndExpire' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testDisabledCoverageIsNotReported' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"

# Runtime evidence must be bound to the exact process-local class-loader scope.
grep -q 'RoiCandidateIdentity.forClassLoader(constructor.getDeclaringClass().getClassLoader())' "$OBSERVER"
grep -q 'RoiCandidateIdentity.forClassLoader(method.getDeclaringClass().getClassLoader())' "$OBSERVER"
grep -q 'RoiCandidateIdentity.forClassLoader(emitMethod.getDeclaringClass().getClassLoader())' "$OBSERVER"
grep -q 'candidateSourceScope.equals(item.sourceScope)' "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
grep -q 'builtScope.equals(event.sourceScope)' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'transportScope.equals(event.sourceScope)' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'sourceScope.length() > 0' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'empty source scope accepted' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testSameDescriptorDifferentLoaderScopesRemainDistinct' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testCrossLoaderPhasesDoNotConfirm' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"

# Ordered evidence tiers must be implemented and covered by recorder tests.
grep -q 'orderedRequestTransportResponse' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'confirmsParsedResponse()' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'testOutOfOrderPhasesDoNotConfirm' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testParsedPhaseRequiresOrderedReceipt' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"

# Equal timestamps are disambiguated by recorder insertion order; reverse-order ties must fail.
grep -q 'testSameMillisecondOutOfOrderPhasesDoNotConfirm' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'testTimestampRegressionDoesNotConfirm' "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
grep -q 'eventIndex > builtIndex' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"
grep -q 'eventIndex > transportIndex' "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java"


# Flow identity correlation is reusable for repeated collection but expires and stays bounded.
grep -q 'FLOW_REQUEST_TIMESTAMPS' "$OBSERVER"
grep -q 'FLOW_REQUEST_KEYS.get(flow)' "$OBSERVER"
grep -q 'pruneFlowBindings(System.currentTimeMillis())' "$OBSERVER"
grep -q 'nowMillis - created > 120000L' "$OBSERVER"
if grep -q 'FLOW_REQUEST_KEYS.remove(flow)' "$OBSERVER"; then
  echo "Flow request binding must survive repeated collection until TTL" >&2
  exit 1
fi

# Cross-loader observations are diagnostics only; exact scope remains required for matches.
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
grep -q 'otherClassLoaderScopeEvidenceEntries' "$DISCOVERY"
grep -q 'otherClassLoaderScopeCoverageEntries' "$DISCOVERY"
grep -q 'exactScopeCoverage.unsupportedSuspendCollector' "$DISCOVERY"
grep -q 'candidateSourceScope.startsWith("loader:")' "$DISCOVERY"
