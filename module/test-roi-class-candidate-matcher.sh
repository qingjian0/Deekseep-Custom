#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-class-candidate-matcher-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiLoadedClassInspector.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiClassCandidateMatcher.java" \
  "$ROOT/module/tests/roiclasscandidatematcher/ds/dsh/DeepSeek/RoiClassCandidateMatcherTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiClassCandidateMatcherTest
