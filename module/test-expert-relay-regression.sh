#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

ROOT_DIR="$(cd .. && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
GRADLE_BIN="$(bash "$ROOT_DIR/scripts/gradle-bootstrap.sh")"
KOTLIN_RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
RUNTIME_CLASSPATH="$RUNTIME_DIR/build/runtime-classpath"

if [ ! -s "$KOTLIN_RUNTIME_JAR" ] || [ ! -d "$RUNTIME_CLASSPATH" ]; then
    "$GRADLE_BIN" --no-daemon --stacktrace -p "$RUNTIME_DIR" jar stageRuntimeClasspath
fi
KOTLIN_STDLIB_JAR="$(find "$RUNTIME_CLASSPATH" -maxdepth 1 \
    -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$KOTLIN_RUNTIME_JAR"
test -s "$KOTLIN_STDLIB_JAR"

OUT="build/expert-relay-test"
rm -rf "$OUT"
mkdir -p "$OUT/classes"

javac -source 8 -target 8 \
    -cp "$KOTLIN_RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
    -d "$OUT/classes" \
    src/ds/dsh/DeepSeek/relay/ExpertRelayGate.java \
    tests/ds/dsh/DeepSeek/relay/ExpertRelayGateRegressionTest.java

java -cp "$OUT/classes:$KOTLIN_RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
    ds.dsh.DeepSeek.relay.ExpertRelayGateRegressionTest
