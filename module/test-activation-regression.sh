#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" \
  "$ROOT/src/com/dsmod/probe/ActivationStatusPolicy.java" \
  "$ROOT/tests/com/dsmod/probe/ActivationStatusPolicyRegressionTest.java"
java -cp "$TMP" com.dsmod.probe.ActivationStatusPolicyRegressionTest
# Entry bootstrap regression: Looper absence must defer instead of returning permanently.
grep -Fq "deferBootstrapUntilApplicationAttach(param);" "$ROOT/src/com/dsmod/probe/Main.java"
grep -Fq "Application.class.getDeclaredMethod(\"attach\", Context.class)" "$ROOT/src/com/dsmod/probe/Main.java"
if grep -Fq 'if (mainLooper == null) {' "$ROOT/src/com/dsmod/probe/Main.java" && \
   grep -A3 -F 'if (mainLooper == null) {' "$ROOT/src/com/dsmod/probe/Main.java" | grep -Fq 'return;'; then
  echo "FAIL: main Looper absence must not permanently abort Xposed bootstrap" >&2
  exit 1
fi
# Target heartbeat must be emitted in both Open and Protected builds.
grep -Fq "if (act == null) return;" "$ROOT/src/com/dsmod/probe/Main.java"
echo "PASS: Xposed bootstrap source guards"
