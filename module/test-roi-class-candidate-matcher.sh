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

# Runtime wiring must retain only ClassLoader-backed candidates and correlate accepted
# host class-load events; the correlation result is diagnostic and never activation.
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
grep -q 'RoiClassCandidateMatcher.registerCandidate' "$DISCOVERY"
grep -q 'RoiClassCandidateMatcher.matchObservedClass' "$MAIN"
grep -q '\[ROI_CLASS_MATCH\]' "$MAIN"
