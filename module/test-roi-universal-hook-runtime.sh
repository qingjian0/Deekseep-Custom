#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-universal-hook-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUniversalHookRuntime.java" \
  "$ROOT/module/tests/roiuniversalhook/ds/dsh/DeepSeek/RoiUniversalHookRuntimeTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUniversalHookRuntimeTest
