#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-dex-instruction-scanner-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionScanner.java" \
 "$ROOT/module/tests/roidexscanner/ds/dsh/DeepSeek/RoiDexInstructionScannerTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexInstructionScannerTest
