#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-class-loader-profile-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiClassLoaderProfile.java" \
  "$ROOT/module/tests/roiloader/ds/dsh/DeepSeek/RoiClassLoaderProfileTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiClassLoaderProfileTest
