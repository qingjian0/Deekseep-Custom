#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/native-flow-lifecycle-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/NativeFlowLifecycle.java" \
  "$ROOT/module/tests/nativeflow/ds/dsh/DeepSeek/NativeFlowLifecycleTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.NativeFlowLifecycleTest
