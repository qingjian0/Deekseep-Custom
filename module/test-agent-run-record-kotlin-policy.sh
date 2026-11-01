#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POLICY="$ROOT/module-kotlin-runtime/src/main/kotlin/ds/dsh/DeepSeek/AgentRunRecordPolicy.kt"
TEST="$ROOT/module-kotlin-runtime/src/test/kotlin/ds/dsh/DeepSeek/AgentRunRecordPolicyTest.kt"
JAVA_TEST="$ROOT/module/test/ds/dsh/DeepSeek/AgentRunRecordPolicyJavaInteropRegression.java"
STORE="$ROOT/module/src/ds/dsh/DeepSeek/AgentRunStore.java"

test -s "$POLICY"
test -s "$TEST"
test -s "$JAVA_TEST"
grep -q 'AgentRunRecordPolicy.clean(value, max)' "$STORE"
grep -q 'AgentRunRecordPolicy.cleanEvent(value)' "$STORE"
grep -q 'AgentRunRecordPolicy.callKey(scope, callId)' "$STORE"
grep -q "it <= ' '" "$POLICY"
grep -q '512 \* 1024' "$POLICY"
echo "Agent run record Kotlin policy source guard passed"
