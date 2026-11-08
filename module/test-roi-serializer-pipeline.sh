#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-serializer-pipeline-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn \
  -sourcepath "$ROOT/module/src:$ROOT/module/tests" \
  -d "$OUT" \
  "$ROOT/module/tests/roidexserializerpipeline/ds/dsh/DeepSeek/RoiDexSerializerPipelineTest.java" \
  "$ROOT/module/tests/roidexserializermapper/ds/dsh/DeepSeek/RoiDexSerializerMapperTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexSerializerPipelineTest
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexSerializerMapperTest
