#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POLICY="$ROOT/module-kotlin-runtime/src/main/kotlin/ds/dsh/DeepSeek/ChatBackupRecordPolicy.kt"
TEST="$ROOT/module-kotlin-runtime/src/test/kotlin/ds/dsh/DeepSeek/ChatBackupRecordPolicyTest.kt"
JAVA_TEST="$ROOT/module/test/ds/dsh/DeepSeek/ChatBackupRecordPolicyJavaInteropRegression.java"
STORE="$ROOT/module/src/ds/dsh/DeepSeek/ChatBackupStore.java"

test -s "$POLICY"
test -s "$TEST"
test -s "$JAVA_TEST"
grep -q 'ChatBackupRecordPolicy.isValidSessionId(sid)' "$STORE"
grep -A2 'private static boolean validSid' "$STORE" | grep -q 'return ChatBackupRecordPolicy.isValidSessionId(sid);'
grep -q 'MAX_SESSION_ID_LENGTH = 128' "$POLICY"
grep -q "c in 'a'..'z'" "$POLICY"
grep -q "c in 'A'..'Z'" "$POLICY"
grep -q "c in '0'..'9'" "$POLICY"
grep -q "c == '-'" "$POLICY"
grep -q "c == '_'" "$POLICY"

echo "Chat backup record Kotlin policy source guard passed"
