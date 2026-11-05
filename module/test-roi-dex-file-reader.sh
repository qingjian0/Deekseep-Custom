#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-dex-file-reader-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexDataReader.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionDecoder.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexCodeReader.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexFileReader.java" \
 "$ROOT/module/tests/roidexfilereader/ds/dsh/DeepSeek/RoiDexFileReaderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexFileReaderTest
