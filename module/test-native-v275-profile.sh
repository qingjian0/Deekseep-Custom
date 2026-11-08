#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" \
  "$ROOT/src/ds/dsh/DeepSeek/HostV275.java" \
  "$ROOT/tests/nativev275/ds/dsh/DeepSeek/HostV275ProfileTest.java"
java -cp "$TMP" ds.dsh.DeepSeek.HostV275ProfileTest
