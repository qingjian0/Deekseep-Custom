#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

OUT="build/expert-relay-test"
rm -rf "$OUT"
mkdir -p "$OUT/classes"

javac -source 8 -target 8 -d "$OUT/classes" \
    src/ds/dsh/DeepSeek/relay/ExpertRelayGate.java \
    tests/ds/dsh/DeepSeek/relay/ExpertRelayGateRegressionTest.java

java -cp "$OUT/classes" ds.dsh.DeepSeek.relay.ExpertRelayGateRegressionTest
