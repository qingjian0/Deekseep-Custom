#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$MODULE_DIR/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
# Optional Agent response hooks must report partial discovery/registration failures
# to the install registry instead of logging them and returning success.
AGENT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookAgentPipeline.java"
# Catch-variable regressions in fatal-error passthrough must fail before Android compilation.
if awk '
  /catch[[:space:]]*\([[:space:]]*Throwable[[:space:]]+t[[:space:]]*\)/ { in_t_catch = 1; remaining = 12; next }
  in_t_catch && /error[[:space:]]+instanceof[[:space:]]+(VirtualMachineError|ThreadDeath)/ {
    print "fatal-error passthrough references error inside catch(Throwable t) at line " NR > "/dev/stderr"
    exit 1
  }
  in_t_catch { remaining--; if (remaining <= 0) in_t_catch = 0 }
' "$AGENT_PIPELINE"; then :; else exit 1; fi
grep -Fq 'native proactive visible-thread filter method not found' "$AGENT_PIPELINE"
grep -Fq 'native proactive visible-thread filter hook installation failed' "$AGENT_PIPELINE"
grep -Fq 'native proactive stream completion hooks were only partially discovered' "$AGENT_PIPELINE"
grep -Fq 'native proactive stream completion hook installation failed' "$AGENT_PIPELINE"
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
grep -Fq 'quick-registration captcha callbacks were only partially installed' "$ACCOUNT_PIPELINE"
grep -Fq 'int generationHooks = 0;' "$ACCOUNT_PIPELINE"
grep -Fq 'if (generationHooks > 0) break;' "$ACCOUNT_PIPELINE"
grep -Fq 'Local API account routing hooks were only partially installed' "$ACCOUNT_PIPELINE"
grep -Fq 'code257 Local API upload route hooks were incomplete' "$ACCOUNT_PIPELINE"
grep -Fq 'native fake mute renderer discovery returned no installable methods' "$ACCOUNT_PIPELINE"
grep -Fq 'native training opt-out hook installation failed' "$ACCOUNT_PIPELINE"
grep -Fq 'candidate password-login error mapper hook installation failed' "$ACCOUNT_PIPELINE"
grep -Fq 'regional login hook installation failed' "$ACCOUNT_PIPELINE"
grep -Fq 'login entry password hook installation failed' "$ACCOUNT_PIPELINE"
grep -Fq 'candidate password login capture hook installation failed' "$ACCOUNT_PIPELINE"
grep -Fq 'quick-registration captcha hook installation failed' "$ACCOUNT_PIPELINE"
grep -Fq 'region override hook unavailable for all mapped host classes' "$ACCOUNT_PIPELINE"
grep -Fq 'Local API keepalive receiver hook installation failed' "$ACCOUNT_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$ACCOUNT_PIPELINE")" -ge 5
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$ACCOUNT_PIPELINE")" -ge 2
grep -Fq 'if (uploadRouteError instanceof VirtualMachineError)' "$ACCOUNT_PIPELINE"
grep -Fq 'if (uploadRouteError instanceof ThreadDeath)' "$ACCOUNT_PIPELINE"
UI_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookUiSurface.java"
grep -Fq 'theme color provider hooks were not discovered' "$UI_PIPELINE"
grep -Fq 'host theme color hook installation failed' "$UI_PIPELINE"
grep -Fq 'theme draw background and surface hooks were not discovered' "$UI_PIPELINE"
grep -Fq 'host theme draw hook installation failed' "$UI_PIPELINE"
grep -Fq 'settings navigation hooks were only partially installed' "$UI_PIPELINE"
grep -Fq 'rm5.n/2 navigation route method not found' "$UI_PIPELINE"
grep -Fq 'rm5 navigation state methods were not found' "$UI_PIPELINE"
grep -Fq 'gf8.A0/1 navigation pop method not found' "$UI_PIPELINE"
grep -Fq 'private int hookNavStateMethod(Class<?> nav, String name)' "$UI_PIPELINE"
grep -Fq 'welcome whale painter motion hook installation failed' "$UI_PIPELINE"
grep -Fq 'Compose text wave hook installation failed' "$UI_PIPELINE"
grep -Fq 'native settings entry hook installation failed' "$UI_PIPELINE"
grep -Fq 'home greeting hook installation failed' "$UI_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$UI_PIPELINE")" -ge 4
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$UI_PIPELINE")" -ge 2
SESSION_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookSessionManagement.java"
grep -Fq 'local session directory merge hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'local native session refresh hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'local session remote reload hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'native detail request hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'local session deleted-response hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'local session deleted-flow hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'code249 feedback deletion hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'native session navigator hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'history-load diagnostics hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'active chat session capture hook installation failed' "$SESSION_PIPELINE"
grep -Fq '2.3.4 local deletion-flow hook installation failed' "$SESSION_PIPELINE"
grep -Fq '2.3.4 deletion presenter hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'sidebar multi-select delete hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'code257 native long-press menu suppression hook installation failed' "$SESSION_PIPELINE"
grep -Fq 'sidebar toggle cleanup hooks were only partially installed' "$SESSION_PIPELINE"
grep -Fq 'sidebar toggle composable' "$SESSION_PIPELINE"
grep -Fq 'DrawerState.c float offset getter' "$SESSION_PIPELINE"
grep -Fq 'sidebar appearance click action getter' "$SESSION_PIPELINE"
grep -Fq 'compose visible-thread state hook installation failed' "$SESSION_PIPELINE"
test "$(grep -Fc 'if (t instanceof VirtualMachineError)' "$SESSION_PIPELINE")" -ge 11
test "$(grep -Fc 'if (t instanceof ThreadDeath)' "$SESSION_PIPELINE")" -ge 11

ATTACHMENT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookAttachmentPipeline.java"
grep -Fq 'model-file link hooks were only partially installed' "$ATTACHMENT_PIPELINE"
grep -Fq 'native model-file message card hooks were only partially installed' "$ATTACHMENT_PIPELINE"
test "$(grep -Fc 'if (error instanceof VirtualMachineError)' "$ATTACHMENT_PIPELINE")" -ge 3
test "$(grep -Fc 'if (error instanceof ThreadDeath)' "$ATTACHMENT_PIPELINE")" -ge 3

CHAT_PIPELINE="$MODULE_DIR/src/ds/dsh/DeepSeek/HookChatPipeline.java"
grep -Fq 'hookSafetyRetraction installation failed' "$CHAT_PIPELINE"
grep -Fq 'server capture hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'content-filter apply hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'message rebuild capture hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'status-write hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'template probe hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'final-message merge hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'final-message apply hook installation failed' "$CHAT_PIPELINE"
grep -Fq 's11 render filter hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'cs1 attachment filter hook installation failed' "$CHAT_PIPELINE"
grep -Fq 'POW manager capture hook installation failed' "$CHAT_PIPELINE"
test "$(grep -Fc 'if (n == 0) throw new NoSuchMethodException' "$CHAT_PIPELINE")" -ge 9
test "$(grep -Fc 'if (installed == 0) throw new NoSuchMethodException' "$CHAT_PIPELINE")" -ge 2
test "$(grep -Fc 'if (t instanceof VirtualMachineError)' "$CHAT_PIPELINE")" -ge 7
test "$(grep -Fc 'if (t instanceof ThreadDeath)' "$CHAT_PIPELINE")" -ge 7
grep -Fq 'local editor image URI hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'expert unlock hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'expert upload gate hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'local API session visibility hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'image credential bridge was only partially installed' "$ATTACHMENT_PIPELINE"
grep -Fq 'history repository hooks were only partially installed' "$ATTACHMENT_PIPELINE"
grep -Fq 'online history bridge hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'native image preview hooks were only partially installed' "$ATTACHMENT_PIPELINE"
grep -Fq '2.3.4 send-point capture hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'send-point fp capture hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'expert flow collect hook installation failed' "$ATTACHMENT_PIPELINE"
grep -Fq 'boolean hookSendPointFps234' "$ATTACHMENT_PIPELINE"
grep -Fq 'boolean hookSendPointFps(' "$ATTACHMENT_PIPELINE"
grep -Fq 'send-point candidate unavailable' "$ATTACHMENT_PIPELINE"
grep -Fq 'send-point method unavailable' "$ATTACHMENT_PIPELINE"
grep -Fq 'no compatible 2.3.4 expert image send-point hook was installed' "$MAIN_SOURCE"
grep -Fq 'no compatible expert image send-point hook was installed' "$MAIN_SOURCE"
grep -Fq 'network payload capture installation failed' "$MAIN_SOURCE"
grep -Fq 'network payload transport hook was not installed' "$MAIN_SOURCE"
grep -Fq 'expert image send-point hooks were only partially installed' "$MAIN_SOURCE"
grep -Fq '2.3.4 expert image send-point hooks were only partially installed' "$MAIN_SOURCE"
grep -Fq 'firstFailure.addSuppressed(error)' "$MAIN_SOURCE"
grep -Fq 'if (resourceHooks != 3 || ownerHooks == 0 || firstFailure != null)' "$ATTACHMENT_PIPELINE"

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
