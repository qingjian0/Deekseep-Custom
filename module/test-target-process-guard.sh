#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/target-process-guard-test"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
    "$ROOT/module/src/com/dsmod/probe/TargetProcessGuard.java" \
    "$ROOT/module/tests/process/TargetProcessGuardRegressionTest.java"
java -cp "$OUT" com.dsmod.probe.TargetProcessGuardRegressionTest
