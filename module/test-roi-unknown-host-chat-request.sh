#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-unknown-host-chat-request-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostChatRequestResolver.java" \
  "$ROOT/module/tests/roiunknownhostchatrequest/ds/dsh/DeepSeek/RoiUnknownHostChatRequestResolverTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUnknownHostChatRequestResolverTest
echo "ROI unknown-host chat request resolver passed"
