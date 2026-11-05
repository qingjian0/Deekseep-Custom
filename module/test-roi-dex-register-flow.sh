#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-dex-register-flow-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionScanner.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexRegisterFlowAnalyzer.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexReachingDefinitions.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexUseDefChains.java" \
 "$ROOT/module/tests/roidexregisterflow/ds/dsh/DeepSeek/RoiDexRegisterFlowAnalyzerTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexRegisterFlowAnalyzerTest
