#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-dex-code-reader-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexCodeReader.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionDecoder.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionScanner.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexExceptionControlFlow.java" \
 "$ROOT/module/tests/roidexcodereader/ds/dsh/DeepSeek/RoiDexCodeReaderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexCodeReaderTest
