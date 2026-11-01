#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$MODULE_DIR/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
POLICY_SOURCE="$RUNTIME_DIR/src/main/kotlin/ds/dsh/DeepSeek/AccountCredentialFieldPolicy.kt"
POLICY_TEST="$RUNTIME_DIR/src/test/kotlin/ds/dsh/DeepSeek/AccountCredentialFieldPolicyTest.kt"
CODEC_SOURCE="$MODULE_DIR/src/ds/dsh/DeepSeek/AccountCredentialCodec.java"
INTEROP_SOURCE="$MODULE_DIR/test/ds/dsh/DeepSeek/AccountCredentialFieldPolicyJavaInteropRegression.java"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

test -f "$POLICY_SOURCE"
test -f "$POLICY_TEST"
test -f "$INTEROP_SOURCE"
grep -Fq 'object AccountCredentialFieldPolicy' "$POLICY_SOURCE"
grep -Fq 'AccountCredentialFieldPolicy.isValidIdLength(id.length())' "$CODEC_SOURCE"
grep -Fq 'AccountCredentialFieldPolicy.isValidTokenLength(token.length())' "$CODEC_SOURCE"
grep -Fq 'AccountCredentialFieldPolicy.isIntegralNumber(value)' "$CODEC_SOURCE"
grep -Fq 'AccountCredentialFieldPolicy.trim((String) o.opt(key))' "$CODEC_SOURCE"
grep -Fq 'AccountCredentialFieldPolicy.shortId(id)' "$CODEC_SOURCE"
! grep -Fq 'import java.util.Locale;' "$CODEC_SOURCE"
! grep -Fq 'Double.isNaN(value)' "$CODEC_SOURCE"
! grep -Fq 'toLowerCase(Locale.US)' "$CODEC_SOURCE"

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
  "$INTEROP_SOURCE"

java -cp "$TMP_DIR/classes:$RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
  ds.dsh.DeepSeek.AccountCredentialFieldPolicyJavaInteropRegression
