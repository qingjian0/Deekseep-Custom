#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$MODULE_DIR/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
# Optional Agent response hooks must report partial discovery/registration failures
# to the install registry instead of logging them and returning success.
AGENT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookAgentPipeline.java"
grep -Fq 'Throwable firstFailure = null;' "$AGENT_PIPELINE"
grep -Fq 'heartbeat response hooks were only partially installed' "$AGENT_PIPELINE"
grep -Fq 'if (liveHooks == 0 || staticHooks == 0)' "$AGENT_PIPELINE"
ATTACHMENT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookAttachmentPipeline.java"
grep -Fq 'model-file link hooks were only partially installed' "$ATTACHMENT_PIPELINE"
grep -Fq 'native model-file message card hooks were only partially installed' "$ATTACHMENT_PIPELINE"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

GRADLE_BIN="$(bash "$ROOT_DIR/scripts/gradle-bootstrap.sh")"
"$GRADLE_BIN" --no-daemon --stacktrace -p "$RUNTIME_DIR" jar stageRuntimeClasspath

RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
KOTLIN_STDLIB_JAR="$(find "$RUNTIME_DIR/build/runtime-classpath" -maxdepth 1 \
  -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$RUNTIME_JAR"
test -s "$KOTLIN_STDLIB_JAR"

javac --release 8 \
  -cp "$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  -d "$TMP_DIR/classes" \
  "$MODULE_DIR/test/ds/dsh/DeepSeek/FeatureHookInstallRegistryRegression.java"
java -cp "$TMP_DIR/classes:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  ds.dsh.DeepSeek.FeatureHookInstallRegistryRegression
