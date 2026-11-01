#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/target-process-guard-test"
RUNTIME_DIR="$ROOT/module-kotlin-runtime"
rm -rf "$OUT"
mkdir -p "$OUT"

GRADLE_BIN="$(bash "$ROOT/scripts/gradle-bootstrap.sh")"
"$GRADLE_BIN" --no-daemon --stacktrace -p "$RUNTIME_DIR" jar stageRuntimeClasspath

RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
KOTLIN_STDLIB_JAR="$(find "$RUNTIME_DIR/build/runtime-classpath" -maxdepth 1 \
    -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$RUNTIME_JAR"
test -s "$KOTLIN_STDLIB_JAR"

javac --release 8 -nowarn -cp "$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" -d "$OUT" \
    "$ROOT/module/tests/process/TargetProcessGuardRegressionTest.java"
java -cp "$OUT:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
    ds.dsh.DeepSeek.TargetProcessGuardRegressionTest
