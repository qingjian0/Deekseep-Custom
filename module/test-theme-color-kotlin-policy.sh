#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POLICY="$ROOT/module-kotlin-runtime/src/main/kotlin/ds/dsh/DeepSeek/ThemeColorPolicy.kt"
TEST="$ROOT/module-kotlin-runtime/src/test/kotlin/ds/dsh/DeepSeek/ThemeColorPolicyTest.kt"
JAVA_TEST="$ROOT/module/test/ds/dsh/DeepSeek/ThemeColorPolicyJavaInteropRegression.java"
CONFIG="$ROOT/module/src/ds/dsh/DeepSeek/ThemeColorConfig.java"

test -s "$POLICY"
test -s "$TEST"
test -s "$JAVA_TEST"
grep -q 'ThemeColorPolicy.parseColor(text, fallback)' "$CONFIG"
grep -q 'ThemeColorPolicy.validDirection(' "$CONFIG"
grep -q "it <= ' '" "$POLICY"
grep -q 'java.lang.Long.parseLong(value, 16)' "$POLICY"
grep -q 'direction == vertical' "$POLICY"
echo "Theme color Kotlin policy source guard passed"
