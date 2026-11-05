#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-dex-data-reader-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexDataReader.java" \
 "$ROOT/module/tests/roidexdatareader/ds/dsh/DeepSeek/RoiDexDataReaderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexDataReaderTest
