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
grep -Fq 'heartbeat JSON Patch dispatcher installation failed' "$AGENT_PIPELINE"
grep -Fq 'heartbeat tracked State hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'heartbeat fragment render hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'heartbeat Markdown input hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'native Markdown privacy hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'agent tool-log Canvas hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'code257 agent tool-log Canvas hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'agent tool-log Activity touch hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'agent tool Compose touch hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'heartbeat tool status Compose hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'heartbeat tool status BasicText hook installation failed' "$AGENT_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$AGENT_PIPELINE")" -ge 11
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$AGENT_PIPELINE")" -ge 11
MAIN_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/Main.java"
grep -Fq 'code249 Local API routing hooks were only partially installed' "$MAIN_SOURCE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$MAIN_SOURCE")" -ge 1
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$MAIN_SOURCE")" -ge 1
ACCOUNT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java"
grep -Fq 'Local API account routing hooks were only partially installed' "$ACCOUNT_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$ACCOUNT_PIPELINE")" -ge 3
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$ACCOUNT_PIPELINE")" -ge 3
UI_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookUiSurface.java"
grep -Fq 'welcome whale painter motion hook installation failed' "$UI_PIPELINE"
grep -Fq 'Compose text wave hook installation failed' "$UI_PIPELINE"
grep -Fq 'native settings entry hook installation failed' "$UI_PIPELINE"
grep -Fq 'home greeting hook installation failed' "$UI_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$UI_PIPELINE")" -ge 4
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$UI_PIPELINE")" -ge 2
ATTACHMENT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookAttachmentPipeline.java"
grep -Fq 'model-file link hooks were only partially installed' "$ATTACHMENT_PIPELINE"
grep -Fq 'native model-file message card hooks were only partially installed' "$ATTACHMENT_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$ATTACHMENT_PIPELINE")" -ge 3
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$ATTACHMENT_PIPELINE")" -ge 3

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
