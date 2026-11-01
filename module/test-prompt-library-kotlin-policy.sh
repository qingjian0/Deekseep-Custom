#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
grep -Fq 'PromptLibraryPolicy.normalizeCategory(category, CATEGORIES)' "$ROOT_DIR/module/src/ds/dsh/DeepSeek/PromptLibrary.java"
grep -Fq 'PromptLibraryPolicy.newId()' "$ROOT_DIR/module/src/ds/dsh/DeepSeek/PromptLibrary.java"
GRADLE_BIN="$(bash "$ROOT_DIR/scripts/gradle-bootstrap.sh")"
"$GRADLE_BIN" --no-daemon --stacktrace -p "$RUNTIME_DIR" test jar stageRuntimeClasspath
RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
KOTLIN_STDLIB_JAR="$(find "$RUNTIME_DIR/build/runtime-classpath" -maxdepth 1 -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$RUNTIME_JAR"
test -s "$KOTLIN_STDLIB_JAR"
javac --release 8 -cp "$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" -d "$TMP_DIR/classes" "$ROOT_DIR/module/test/ds/dsh/DeepSeek/PromptLibraryPolicyJavaInteropRegression.java"
java -cp "$TMP_DIR/classes:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" ds.dsh.DeepSeek.PromptLibraryPolicyJavaInteropRegression
