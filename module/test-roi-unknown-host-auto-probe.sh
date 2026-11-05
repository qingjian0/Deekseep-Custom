#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-unknown-host-auto-probe-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiLoadedClassInspector.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbePolicy.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostAutoProbeCoordinator.java" \
  "$ROOT/module/tests/roiunknownhostautoprobe/ds/dsh/DeepSeek/RoiUnknownHostAutoProbeCoordinatorTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUnknownHostAutoProbeCoordinatorTest

echo "ROI unknown-host auto probe wiring passed"
