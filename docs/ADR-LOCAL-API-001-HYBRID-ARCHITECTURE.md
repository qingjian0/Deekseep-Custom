# ADR-LOCAL-API-001: Hybrid protocol gateway and native backend

- Status: Accepted for implementation
- Date: 2026-11-06
- Target: Open edition, DeepSeek Android mainland 2.7.2 / code289
- Implementation branch: work/local-api-hybrid-api
- Stable branches: main and dev are not modified by this ADR

## Context

The current Open gateway has a deliberately narrow HTTP surface. The shared z2.Backend contract already separates HTTP/protocol handling from the native completion engine, but the code289 native path remains fail-closed. Existing observations identify request/transport/continuation boundaries; they do not yet prove request construction, challenge preparation, event semantics, cancellation, or terminal/error behavior.

The MIT-licensed Xposed-Modules-Repo/com.dsmod.probe project provides a mature protocol gateway and tool bridge. Its reported device validation targets an older DeepSeek client and does not validate this project's native adapter. Reimplementing all protocol edge cases from scratch duplicates mature HTTP, SSE, tool-call, and compatibility work. Reusing its native host hooks would be unsafe.

## Decision

1. Port and adapt host-version-independent protocol code: HTTP framing, OpenAI Chat and Responses mapping, Anthropic Messages mapping, SSE serialization, tool schema/choice validation, tool-call envelopes, usage/error mapping, and protocol regression cases.
2. Keep native DeepSeek integration project-owned: request construction, challenge/PoW, transport invocation, Flow collection, event decoding, reasoning/text separation, cancellation, terminal/error mapping, and native conversation/session isolation.
3. Preserve z2.Backend as the protocol-to-runtime seam. HTTP/protocol code must not reference obfuscated host classes or native payload-only types.
4. Do not report backend readiness until the code289 adapter is installed and runtime checks pass. Model discovery must distinguish configured protocol surface from actual backend readiness.
5. Do not merge this branch into dev or main without an explicit request.

## Target architecture

```text
OpenAI Chat / Responses / Anthropic Messages
                    |
             HTTP + Auth Layer
                    |
          Protocol Request Adapters
                    |
        Tool Bridge + Session State
                    |
          SSE / Usage / Error Mapper
                    |
               z2.Backend
                    |
          Agent / Completion Runtime
                    |
       OpenNativeCompletionAdapter
                    |
 Request Builder -> Challenge/PoW -> Transport
                    |
             Native Flow Collector
                    |
             Host Event Decoder
                    |
          DeepSeek 2.7.2 / code289
```

## Reuse boundaries

### Reuse/port with attribution and license notice

The reference repository declares MIT. Every substantial copied/adapted source file must retain its original copyright and permission notice and identify local modifications. Confirm compatibility with this repository's GPL-3.0-only distribution before porting. Port in small reviewable units; do not wholesale-copy the gateway before package, dependency, threading, and contract differences are understood.

- HTTP request framing, bounded body parsing, and authentication
- OpenAI Chat Completions and Responses protocol mapping
- Anthropic Messages protocol mapping
- SSE event framing and terminal-event rules
- Tool definition, tool-choice, JSON Schema, and result mapping
- Usage and protocol error mapping
- Fake-backend protocol regression tests

### Implement here

- code289 Request factory and verified payload mapping
- challenge/PoW acquisition and serialization
- zn3.o(wu1, Long) / jw3.g(wu1, Long) integration only after exact call semantics are proven
- Flow interface and collector verification
- callback/event DTO decoding based on exact APK field and call-site evidence
- reasoning/text/tool event normalization
- native cancellation and cleanup
- native conversation/session isolation and fair account-wide admission control
- version gating and fail-closed readiness

## Compatibility rules

- OpenAI Chat, Responses, and Anthropic adapters normalize into one internal completion request model.
- Protocol-specific response objects are produced from normalized completion events; protocol code must not know obfuscated host classes.
- Tool execution remains client-side unless a separately configured and authorized local tool executor is selected. A tool call is never treated as ordinary assistant text.
- stream_options.include_usage reports measured usage or explicitly labeled estimates; never fabricate exact token counts.
- previous_response_id and Anthropic tool-result continuation are scoped to an authenticated client session.
- HTTP chunked request bodies are bounded and parsed correctly; malformed or oversized bodies fail before native execution.
- SSE emits exactly one terminal event, enforces bounded buffering, and propagates client-disconnect cancellation.
- LAN access remains opt-in and binds only to eligible private IPv4 interfaces; never bind to 0.0.0.0.
- Secrets, prompt bodies, cookies, authorization headers, and native message contents never enter diagnostics.

## Three-pass delivery and acceptance

### Pass 1 — Protocol foundation
- Freeze normalized request/event/error contracts around z2.Backend.
- Inventory and port the reference protocol modules in dependency order.
- Add isolated fake-backend tests for OpenAI Chat, Responses, Anthropic, SSE, tools, usage, malformed input, chunked input, and session isolation.
- Keep unsupported features explicit; no silent parameter dropping.

Exit criteria: protocol regression suite passes with fake backend; Android source compiles; native readiness remains accurately fail-closed.

### Pass 2 — code289 native adapter
- Prove request builder and challenge/PoW contract from APK/device evidence.
- Prove transport method choice and invocation semantics.
- Decode callback/Flow events using field/call-site evidence, not guessed DTO names.
- Implement text/reasoning/tool/terminal/error normalization and cancellation.
- Integrate adapter behind z2.Backend without exposing host types.

Exit criteria: real DeepSeek 2.7.2 device returns a genuine response through the adapter; cancellation and terminal/error paths are observed; no mock is counted as device acceptance.

### Pass 3 — integration and release acceptance
- Run all protocol regressions against fake backend and integration regressions against native backend.
- Verify Chat, Responses, Anthropic, tools, tool-result continuation, SSE, reasoning, usage, concurrency, cancellation, and network recovery.
- Build installable Open APK; record CI and device evidence.
- Update API documentation and acceptance report.

Exit criteria: APK builds; required protocol tests pass; real-device native generation passes. If device evidence is unavailable, report the exact blocker and do not label the API production-ready.

## Safety and branch policy

- Keep this work on work/local-api-hybrid-api.
- No direct writes to dev/main and no automatic merge.
- Avoid competing with unrelated API/CI work; run targeted tests first and avoid redundant CI-triggering pushes.
- Unknown host versions remain read-only by default. Diagnostic probes remain disabled by default. Static analysis alone never authorizes functional hooks.
- Keep localhost as the default listener. LAN remains opt-in and limited to 192.168.x.x.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Reference protocol implementation has old host assumptions | Port protocol-only modules; do not port native hook code |
| GPL/MIT notice handling is incomplete | Preserve MIT notice and record source provenance per adapted file |
| code289 event semantics are incomplete | Require field/call-site/device evidence before decoding |
| fake backend masks native failures | Separate protocol and real-device acceptance gates |
| oversized or chunked requests cause resource exhaustion | Strict body/header bounds and bounded worker queues |
| client disconnect leaves native work running | Cancellation propagation and lifecycle tests |
| concurrent requests corrupt host conversation state | Fair account-wide admission and isolated session state |

## Source provenance

- Reference: https://github.com/Xposed-Modules-Repo/com.dsmod.probe
- Gateway: https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/main/module/src/com/dsmod/probe/LocalApiGateway.java
- Tool bridge: https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/main/module/src/com/dsmod/probe/OpenAiToolBridge.java
- Reference protocol plan: https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/main/docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md
