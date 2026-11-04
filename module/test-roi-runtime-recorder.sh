#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-runtime-recorder-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorder.java" \
  "$ROOT/module/tests/roiruntime/ds/dsh/DeepSeek/RoiRuntimeInvocationRecorderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiRuntimeInvocationRecorderTest
