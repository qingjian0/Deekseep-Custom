#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-loaded-class-inspector-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiLoadedClassInspector.java" \
  "$ROOT/module/tests/roiclass/ds/dsh/DeepSeek/RoiLoadedClassInspectorTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiLoadedClassInspectorTest
