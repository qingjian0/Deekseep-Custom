#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" \
  src/ds/dsh/DeepSeek/ActivationStatusPolicy.java \
  tests/ds/dsh/DeepSeek/ActivationStatusPolicyRegressionTest.java
java -cp "$TMP" ds.dsh.DeepSeek.ActivationStatusPolicyRegressionTest
