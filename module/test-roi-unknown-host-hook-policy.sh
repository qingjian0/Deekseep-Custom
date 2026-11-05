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
grep -q 'deekseep_roi_unknown_host_hook_enabled' "$MAIN"
grep -q 'RoiUnknownHostHookPolicy.allowsMetadataObservation' "$MAIN"
grep -q 'authorizesFunctionalHook' "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java"
grep -q 'return false;' "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java"
grep -q 'unknown-host ROI switch enabled: metadata-only' "$MAIN"
grep -q 'boolean observationEnabled = maintainedHost' "$MAIN"
grep -q 'if (!observationEnabled) return loaded;' "$MAIN"
grep -q 'unknownHostOptIn && RoiUnknownHostHookPolicy.isExplicitlyEnabled' "$MAIN"
