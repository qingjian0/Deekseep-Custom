#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-hook-resolver-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiHookResolver.java" \
  "$ROOT/module/tests/roiresolver/ds/dsh/DeepSeek/RoiHookResolverTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiHookResolverTest
