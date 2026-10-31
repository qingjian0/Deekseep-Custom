#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Guard the Xposed entry class against eager Handler construction before the target main Looper
# exists. This is a source-level regression check because the failure happens at class loading.
MAIN="src/ds/dsh/DeepSeek/Main.java"
if grep -Fq "private static final Handler ACTIVATION_HEARTBEAT_HANDLER" "$MAIN"; then
  echo "FAIL: activation heartbeat Handler must not be eagerly initialized" >&2
  exit 1
fi
grep -Fq "private static volatile Handler ACTIVATION_HEARTBEAT_HANDLER;" "$MAIN"
grep -Fq "private static Handler activationHeartbeatHandler()" "$MAIN"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" \
  src/ds/dsh/DeepSeek/ActivationStatusPolicy.java \
  tests/ds/dsh/DeepSeek/ActivationStatusPolicyRegressionTest.java
java -cp "$TMP" ds.dsh.DeepSeek.ActivationStatusPolicyRegressionTest
