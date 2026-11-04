#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-candidate-identity-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/tests/roiidentity/ds/dsh/DeepSeek/RoiCandidateIdentityTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiCandidateIdentityTest
