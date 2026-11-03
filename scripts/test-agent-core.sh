#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" \
  "$ROOT/module/src/com/dsmod/probe/agentcore/AgentCoreContract.java" \
  "$ROOT/module/src/com/dsmod/probe/agentcore/AgentRunStateMachine.java" \
  "$ROOT/module/tests/com/dsmod/probe/agentcore/AgentCoreContractRegressionTest.java"
java -cp "$TMP" com.dsmod.probe.agentcore.AgentCoreContractRegressionTest
