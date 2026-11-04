#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-discovery-budget-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiDiscoveryBudget.java" \
  "$ROOT/module/tests/roibudget/ds/dsh/DeepSeek/RoiDiscoveryBudgetTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDiscoveryBudgetTest
