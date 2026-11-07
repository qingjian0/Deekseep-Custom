#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=build/adapter-test
rm -rf "$OUT"
mkdir -p "$OUT"
ROOT_DIR="$(cd .. && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
GRADLE_BIN="$(bash "$ROOT_DIR/scripts/gradle-bootstrap.sh")"
"$GRADLE_BIN" --no-daemon -p "$RUNTIME_DIR" jar stageRuntimeClasspath
RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
STDLIB_JAR="$(find "$RUNTIME_DIR/build/runtime-classpath" -maxdepth 1 \
    -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$RUNTIME_JAR"
test -s "$STDLIB_JAR"
RUNTIME_CP="$RUNTIME_JAR:$STDLIB_JAR"
javac -source 8 -target 8 -cp "$RUNTIME_CP" -d "$OUT" \
    src/de/robv/android/xposed/XC_MethodHook.java \
    src/de/robv/android/xposed/XposedBridge.java \
    tests/support/ds/dsh/DeepSeek/LegacyInstrumentationStubs.java \
    compat/ds/dsh/DeepSeek/LegacyXposedModule.java \
    ../module/src/ds/dsh/DeepSeek/RoiUniversalHookRuntime.java \
    ../module/src/ds/dsh/DeepSeek/RoiManagedHookInstaller.java \
    tests/ds/dsh/DeepSeek/LegacyXposedModuleRegressionTest.java
matrix_output="$(java -cp "$OUT:$RUNTIME_CP" ds.dsh.DeepSeek.LegacyXposedModuleRegressionTest)"
printf '%s\n' "$matrix_output"
printf '%s\n' "$matrix_output" | grep -q \
    'Legacy Xposed API 82-102 matrix OK (21 versions)'
