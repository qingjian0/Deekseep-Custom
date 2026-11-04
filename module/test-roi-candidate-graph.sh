#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-candidate-graph-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateGraph.java" \
  "$ROOT/module/tests/roigraph/ds/dsh/DeepSeek/RoiCandidateGraphTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiCandidateGraphTest
