#!/usr/bin/env bash
set -euo pipefail
MODULE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -source 8 -target 8 -nowarn -d "$TMP" \
  "$MODULE/src/ds/dsh/DeepSeek/HostV275.java" \
  "$MODULE/tests/nativev275/ds/dsh/DeepSeek/HostV275ProfileTest.java"
java -cp "$TMP" ds.dsh.DeepSeek.HostV275ProfileTest
