# Local API reference provenance

This document records source lineage for the hybrid Local API implementation.

## Upstream reference

- Repository: https://github.com/Xposed-Modules-Repo/com.dsmod.probe
- License: MIT (verify against the exact source revision before each port)
- Candidate gateway: module/src/com/dsmod/probe/LocalApiGateway.java
- Candidate tool bridge: module/src/com/dsmod/probe/OpenAiToolBridge.java
- Candidate regression suite: module/tests/com/dsmod/probe/LocalApiGatewayProtocolRegressionTest.java
- Candidate plan: docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md

## Porting record

No upstream source files have been copied into this branch by this commit. This file is a provenance ledger to be updated for each subsequent port.

| Local path | Upstream path | Upstream revision | License notice retained | Local modifications |
|---|---|---|---|---|
| module/src/ds/dsh/DeepSeek/OpenAiToolBridge.java | module/src/com/dsmod/probe/OpenAiToolBridge.java | 6b5847d68c30a6525193f3187dac57fb92564d06 | Yes; full MIT notice retained in file header | Package migration; LocalApiGateway.GatewayException mapped to z2.GatewayException; Plan implements z2.ToolPlan |

## Required handling

For each adapted file, retain the upstream MIT copyright and permission notice, identify modifications, and review compatibility with the repository's GPL-3.0-only distribution. Do not copy upstream native hook logic: only protocol-layer code is in scope.
