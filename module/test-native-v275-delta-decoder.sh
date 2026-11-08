#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -source 8 -target 8 -nowarn -d "$TMP" \
  "$ROOT/src/ds/dsh/DeepSeek/NativeV275DeltaCommandDecoder.java" \
  "$ROOT/tests/nativev275/ds/dsh/DeepSeek/NativeV275DeltaCommandDecoderTest.java"
java -cp "$TMP" ds.dsh.DeepSeek.NativeV275DeltaCommandDecoderTest
