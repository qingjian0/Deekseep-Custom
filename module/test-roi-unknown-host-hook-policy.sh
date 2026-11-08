#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-unknown-host-hook-policy-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java" \
  "$ROOT/module/tests/roiunknownhostpolicy/ds/dsh/DeepSeek/RoiUnknownHostHookPolicyTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUnknownHostHookPolicyTest

javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostActivationGates.java" \
  "$ROOT/module/tests/roiunknownhostactivationgates/ds/dsh/DeepSeek/RoiUnknownHostActivationGatesTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUnknownHostActivationGatesTest

MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
HOOK_POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java"
FEATURE_POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostFeatureHookPolicy.java"
GATE_POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostActivationGates.java"
UI="$ROOT/module/src/ds/dsh/DeepSeek/DeekseepUi.java"
grep -q 'deekseep_roi_unknown_host_hook_enabled' "$MAIN"
grep -q 'unknownVersionAttemptsEnabled' "$MAIN"
grep -q 'diagnosticProbesEnabled' "$MAIN"
grep -q 'featureHooksAuthorized' "$MAIN"
# Unknown hosts still report actual process activation without enabling feature Hooks.
grep -q 'installUnknownHostActivationReporter();' "$MAIN"
grep -q 'unknown-host activation observer registered (read-only)' "$MAIN"
grep -q 'reportActivationHeartbeat(activity);' "$MAIN"
grep -q 'UNKNOWN_HOST_ACTIVATION_REPORTER_INSTALLED' "$MAIN"
grep -q 'unknownHostGates.requiresClassLoadDispatcher()' "$MAIN"
grep -q 'liveGates.allowsMetadataObservation()' "$MAIN"
grep -q 'liveGates.allowsDiagnosticProbes()' "$MAIN"
grep -q 'liveGates.allowsFeatureHookAttempts()' "$MAIN"
grep -q 'boolean metadataObservationEnabled = maintainedHost' "$MAIN"
grep -q 'unknownHostOptIn && RoiUnknownHostHookPolicy.isExplicitlyEnabled' "$MAIN"
grep -q 'RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled' "$MAIN"
grep -q 'RoiUnknownHostFeatureHookPolicy.isExplicitlyEnabled' "$MAIN"
grep -q 'authorizesFunctionalHook' "$HOOK_POLICY"
grep -q 'return false;' "$HOOK_POLICY"
grep -q 'unknownVersionAttemptSwitchFile' "$FEATURE_POLICY"
grep -q 'RoiUnknownHostHookPolicy.isExplicitlyEnabled(unknownVersionAttemptSwitchFile)' "$FEATURE_POLICY"
grep -q 'unknownVersionAttemptsEnabled && featureHooksAuthorized' "$GATE_POLICY"

# Settings UI must expose three distinct controls and refresh the visible gate explanation.
grep -q '允许未知版本尝试 Hook' "$UI"
grep -q '允许诊断探针' "$UI"
grep -q '授权功能 Hook' "$UI"
grep -q 'Main.isUnknownVersionHookAttemptsEnabled()' "$UI"
grep -q 'Main.isUnknownHostDiagnosticProbesEnabled()' "$UI"
grep -q 'Main.isUnknownHostFeatureHooksAuthorized()' "$UI"
grep -q 'roiGateStatus.setText(Main.unknownHostHookGateStatus())' "$UI"
grep -q 'static boolean setUnknownVersionHookAttemptsEnabled(boolean enabled)' "$MAIN"
grep -q 'static boolean setUnknownHostDiagnosticProbesEnabled(boolean enabled)' "$MAIN"
grep -q 'static boolean setUnknownHostFeatureHooksAuthorized(boolean enabled)' "$MAIN"
grep -q 'static String unknownHostHookGateStatus()' "$MAIN"

# The shared class-load dispatcher may be installed for any one lane, but metadata diagnostics
# must be gated only by the unknown-version-attempt switch, never by probe/feature switches.
if grep -q 'unknownSwitchEnabled || invocationProbeEnabled' "$MAIN"; then
  echo "cross-gate OR expression is forbidden" >&2
  exit 1
fi
if grep -q '|| RoiUnknownHostFeatureHookPolicy.isExplicitlyEnabled' "$MAIN"; then
  echo "feature-hook switch must not enable metadata diagnostics" >&2
  exit 1
fi
