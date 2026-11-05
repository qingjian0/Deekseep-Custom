#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-functional-role-classifier-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiSemanticRoleHints.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiFunctionalRoleClassifier.java" \
  "$ROOT/module/tests/roifunctionalroles/ds/dsh/DeepSeek/RoiFunctionalRoleClassifierTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiFunctionalRoleClassifierTest
