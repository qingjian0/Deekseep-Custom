#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$MODULE_DIR/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# Source-level contracts for the Android-only IPC/storage path. These checks complement
# JVM regressions; they do not replace an Android Gradle build or device verification.
QUEUE_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/DshLogIpcQueue.java"
PROVIDER_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/XposedActivationProvider.java"
REMOTE_SINK_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/DshRemoteFileLogSink.java"
STORE_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/DshFileLogStore.java"
SETTINGS_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/SettingsActivity.java"
LEGACY_MANIFEST="$ROOT_DIR/module-legacy/AndroidManifest.xml"
UNIVERSAL_MANIFEST="$ROOT_DIR/module-universal/AndroidManifest.xml"

grep -Fq 'private_queued' "$QUEUE_SOURCE"
grep -Fq 'public_queued' "$QUEUE_SOURCE"
grep -Fq 'partial' "$QUEUE_SOURCE"
grep -Fq 'return DshLogIpcQueue.enqueue(context, category, line);' "$PROVIDER_SOURCE"
# All three log IPC operations (write, status, crash read) must enforce the exclusive UID.
test "$(grep -Fc 'uidOwnsExclusivePackage(context, callingUid, TARGET_PACKAGE)' "$PROVIDER_SOURCE")" -eq 3
grep -Fq 'result.getBoolean("partial", false)' "$REMOTE_SINK_SOURCE"
grep -Fq 'result.getBoolean("private_written") != result.getBoolean("public_written")' "$REMOTE_SINK_SOURCE"
grep -Fq 'Manifest.permission.WRITE_EXTERNAL_STORAGE' "$STORE_SOURCE"
grep -Fq '!readGranted || !writeGranted' "$SETTINGS_SOURCE"
grep -Fq 'METHOD_GET_DSH_LOG_STATUS' "$MODULE_DIR/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java"
grep -Fq 'privateQueue=' "$MODULE_DIR/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java"
grep -Fq 'publicQueue=' "$MODULE_DIR/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java"
# Diagnostics aggregation is owned by the Kotlin runtime; Java retains Android presentation only.
grep -Fq 'RECORDER.record(raw);' "$MODULE_DIR/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java"
! grep -Fq 'private static final Pattern BEARER' "$MODULE_DIR/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java"
! grep -Fq 'private static final ArrayDeque<String> EVENTS' "$MODULE_DIR/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java"
grep -Fq 'DshDiagnosticsRecorder' "$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/DshDiagnosticsRecorder.kt"
# Navigation parsing/policy is Kotlin-owned; Android version detection remains in the bridge.
NAV_CATALOG_SOURCE="$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/HostNavigationCatalog.kt"
NAV_BRIDGE_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/HostNavigationBridge.java"
test -f "$NAV_CATALOG_SOURCE"
test ! -e "$MODULE_DIR/src/ds/dsh/DeepSeek/HostNavigationCatalog.java"
grep -Fq 'HostNavigationCatalog.obfuscatedRouteClass(route, HostCompat.isV236(), HostCompat.isV241())' "$NAV_BRIDGE_SOURCE"
grep -Fq 'fun extractRoutes(input: InputStream?)' "$NAV_CATALOG_SOURCE"
# Pure lifecycle and relay policies are Kotlin-owned; do not restore parallel Java implementations.
grep -Fq 'object AutoContinuePolicy' "$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/LifecyclePolicies.kt"
grep -Fq 'object ReplyReadyPolicy' "$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/LifecyclePolicies.kt"
grep -Fq 'object ExpertRelayGate' "$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/relay/ExpertRelayGate.kt"
! test -e "$MODULE_DIR/src/ds/dsh/DeepSeek/AutoContinuePolicy.java"
! test -e "$MODULE_DIR/src/ds/dsh/DeepSeek/ReplyReadyPolicy.java"
! test -e "$MODULE_DIR/src/ds/dsh/DeepSeek/relay/ExpertRelayGate.java"
grep -Fq 'object AgentRateLimiter' "$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/AgentRateLimiter.kt"
! test -e "$MODULE_DIR/src/ds/dsh/DeepSeek/AgentRateLimiter.java"
# The exported provider is required for cross-process IPC; both package variants must declare it.
grep -A4 -Fq 'android:name="ds.dsh.DeepSeek.XposedActivationProvider"' "$LEGACY_MANIFEST"
grep -A4 -Fq 'android:name="ds.dsh.DeepSeek.XposedActivationProvider"' "$UNIVERSAL_MANIFEST"

GRADLE_BIN="$(bash "$ROOT_DIR/scripts/gradle-bootstrap.sh")"
"$GRADLE_BIN" --no-daemon --stacktrace -p "$RUNTIME_DIR" test jar stageRuntimeClasspath

RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
KOTLIN_STDLIB_JAR="$(find "$RUNTIME_DIR/build/runtime-classpath" -maxdepth 1 \
  -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$RUNTIME_JAR"
test -s "$KOTLIN_STDLIB_JAR"

javac --release 8 \
  -cp "$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  -d "$TMP_DIR/classes" \
  "$MODULE_DIR/test/ds/dsh/DeepSeek/FeatureHookInstallRegistryRegression.java" \
  "$MODULE_DIR/test/ds/dsh/DeepSeek/DshLogRegression.java" \
  "$MODULE_DIR/test/ds/dsh/DeepSeek/KotlinPolicyJavaInteropRegression.java"

java -cp "$TMP_DIR/classes:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  ds.dsh.DeepSeek.FeatureHookInstallRegistryRegression
java -cp "$TMP_DIR/classes:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  ds.dsh.DeepSeek.DshLogRegression
java -cp "$TMP_DIR/classes:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  ds.dsh.DeepSeek.KotlinPolicyJavaInteropRegression
