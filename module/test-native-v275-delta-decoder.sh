#!/usr/bin/env bash
set -euo pipefail
MODULE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -source 8 -target 8 -nowarn -d "$TMP" \
  "$MODULE/src/ds/dsh/DeepSeek/NativeV275DeltaCommandDecoder.java" \
  "$MODULE/src/ds/dsh/DeepSeek/NativeV275DeltaMapper.java" \
  "$MODULE/tests/nativev275/ds/dsh/DeepSeek/NativeV275DeltaCommandDecoderTest.java" \
  "$MODULE/tests/nativev275/ds/dsh/DeepSeek/NativeV275DeltaMapperTest.java"
java -cp "$TMP" ds.dsh.DeepSeek.NativeV275DeltaCommandDecoderTest
java -cp "$TMP" ds.dsh.DeepSeek.NativeV275DeltaMapperTest
