#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-semantic-profile-matcher-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiSemanticProfileMatcher.java" \
  "$ROOT/module/tests/roiprofilematcher/ds/dsh/DeepSeek/RoiSemanticProfileMatcherTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiSemanticProfileMatcherTest
