#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-dex-instruction-decoder-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionDecoder.java" \
 "$ROOT/module/tests/roidexdecoder/ds/dsh/DeepSeek/RoiDexInstructionDecoderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexInstructionDecoderTest
