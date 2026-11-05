#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-unknown-host-hook-policy-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java" \
  "$ROOT/module/tests/roiunknownhostpolicy/ds/dsh/DeepSeek/RoiUnknownHostHookPolicyTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUnknownHostHookPolicyTest

MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
HOOK_POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java"
grep -q 'deekseep_roi_unknown_host_hook_enabled' "$MAIN"
grep -q 'unknownVersionAttemptsEnabled' "$MAIN"
grep -q 'diagnosticProbesEnabled' "$MAIN"
grep -q 'featureHooksAuthorized' "$MAIN"
grep -q 'unknownHostActivationEnabled = unknownVersionAttemptsEnabled' "$MAIN"
grep -q 'boolean metadataObservationEnabled = maintainedHost' "$MAIN"
grep -q 'unknownHostOptIn && RoiUnknownHostHookPolicy.isExplicitlyEnabled' "$MAIN"
grep -q 'RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled' "$MAIN"
grep -q 'RoiUnknownHostFeatureHookPolicy.isExplicitlyEnabled' "$MAIN"
grep -q 'authorizesFunctionalHook' "$HOOK_POLICY"
grep -q 'return false;' "$HOOK_POLICY"

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
