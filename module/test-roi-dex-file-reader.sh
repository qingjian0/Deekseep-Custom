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
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitExportReader.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInstructionScanner.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexExceptionControlFlow.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexRegisterFlowAnalyzer.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexReachingDefinitions.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexMethodEntryLayout.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexUseDefChains.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexMethodAnalysis.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateGraph.java" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexCandidateEvidence.java" \
 "$ROOT/module/tests/roidexfilereader/ds/dsh/DeepSeek/RoiDexFileReaderTest.java" \
 "$ROOT/module/tests/roidexexportreader/ds/dsh/DeepSeek/RoiDexKitExportReaderTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexFileReaderTest
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexKitExportReaderTest
