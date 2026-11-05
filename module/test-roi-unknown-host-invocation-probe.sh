#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-unknown-host-invocation-probe-test"
mkdir -p "$OUT"
echo "ROI invocation probe: compiling isolated test sources"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiLoadedClassInspector.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbePolicy.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbe.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbeInstaller.java" \
  "$ROOT/module/tests/roiunknownhostinvocationprobe/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbeTest.java"
echo "ROI invocation probe: running executable tests"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUnknownHostInvocationProbeTest
echo "ROI invocation probe: executable tests completed"
