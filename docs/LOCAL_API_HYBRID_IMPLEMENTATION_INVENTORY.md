# Local API hybrid implementation inventory

Branch: work/local-api-hybrid-api  
Date: 2026-11-06  
Purpose: parallel work breakdown for the three-pass delivery defined in ADR-LOCAL-API-001.

## Existing project seam

The Open gateway already delegates completion through z2.Backend:

- Backend readiness: isReady() and readinessDetail()
- Completion: complete(CompletionRequest, DeltaSink)
- Streaming callbacks: onUpstreamStarted(), onText(), onReasoning(), isCancelled()
- Completion request includes request/session IDs, model selection, prompt, reasoning/search flags, token limit, tool plan, Responses continuation state, output format, native conversation IDs, file IDs, images, deadline, and tool history.

This is the correct integration seam. Protocol code should normalize to this contract; native host classes stay behind the backend implementation.

## Current implementation status and remaining limitations

Implemented in the current branch (source assertions added; executable/build verification pending):
- OpenAI Chat Completions non-stream tool handoff and client-side tool-result continuation
- OpenAI Chat estimated usage in non-stream responses and optional SSE `stream_options.include_usage` chunks (marked `dsh_usage_estimated=true`)
- OpenAI Responses text JSON/SSE and non-stream function/custom tool handoff
- Anthropic Messages text JSON/SSE and non-stream tool_use/tool_result handoff
- Bounded in-memory previous_response_id state (64 entries, 30-minute TTL, 256 KiB transcript cap; cleared on gateway stop/restart)
- Bounded HTTP chunked transfer decoding

Not yet accepted as complete:
- Anthropic signed thinking blocks, count_tokens, image blocks, and streamed tool_use
- Exact tokenizer usage accounting (Chat SSE `include_usage` now emits an explicitly marked estimate)
- Full protocol-level session isolation and executable lifecycle tests
- Anthropic SDK response validation (usage fields are marked estimated because backend tokenizer counters are absent)
- Streamed tool-call output and tool-result continuation
- Real code289 native request construction, challenge/PoW, Flow collection, event decoding, cancellation, and genuine device generation

Open edition deliberately does not call the protected z18 payload engine; it returns native_adapter_unavailable until the code289 adapter is proven.

Unsupported fields must fail with explicit protocol errors; never silently discard them.

## Parallel workstreams

| Workstream | Scope | Depends on | Pass |
|---|---|---|---|
| P0 Contract | normalized request, event, usage, error, cancellation contracts | existing z2.Backend | 1 |
| P1 HTTP | request framing, auth, body bounds, chunked input, status mapping | P0 | 1 |
| P2 OpenAI Chat | message normalization, roles, tools, JSON output format, response mapping | P0/P1 | 1 |
| P3 Responses | response objects, items, previous_response_id, streaming events | P0/P1 | 1 |
| P4 Anthropic | Text Messages JSON/SSE and non-stream tool handoff implemented; signed thinking/count_tokens/images/streamed tool_use pending | P0/P1 | 1 |
| P5 Tool Bridge | tool schemas, tool_choice, call IDs, result continuation, session scope | P0/P2/P3/P4 | 1 |
| P6 Protocol tests | fake backend, golden fixtures, malformed/oversize/chunked/SSE/session tests | P0-P5 | 1 |
| N1 Request Builder | verified code289 payload and request object creation | device/APK evidence | 2 |
| N2 Challenge | challenge/PoW lifecycle | N1 evidence | 2 |
| N3 Transport | invocation and response Flow entry | N1/N2 | 2 |
| N4 Event Decoder | callback/Flow event types and semantic mapping | device/APK evidence | 2 |
| N5 Lifecycle | cancel, timeout, terminal/error, cleanup, session isolation | N3/N4 | 2 |
| I1 Integration | protocol-to-native end-to-end | P0-P6, N1-N5 | 3 |
| I2 Release | APK, regression, device acceptance, docs | I1 | 3 |

## Parallelization rules

1. P1, P2, P3, P4 can be analyzed in parallel after P0 contract freeze.
2. P6 test fixtures can be prepared in parallel, but final assertions must follow the implemented contracts.
3. N1/N2/N3/N4 may be investigated in parallel from APK and device evidence; code integration must converge through one adapter owner.
4. Do not have multiple workers edit the same source file concurrently. Integrate changes serially by path.
5. Do not run broad CI for every small commit. Run focused shell/source tests locally where possible; reserve CI for a coherent milestone.
6. Do not treat fake-backend protocol success as proof of native adapter success.

## License and attribution checklist

Before copying source:
- Confirm upstream MIT LICENSE in the source revision.
- Retain copyright and permission notices in copied files.
- Add a provenance header documenting upstream path, revision, and local modifications.
- Review package dependencies, Android API level, threading model, and project conventions.
- Do not copy native host hooks or old host-version assumptions.
- Keep a source mapping table in docs/LOCAL_API_REFERENCE_PROVENANCE.md.

## Evidence required for code289

Required before enabling native completion:
- Exact request constructor/factory and field mapping
- Challenge/PoW request and response semantics
- Exact transport call-site and invocation arguments
- Flow type and collection lifecycle
- Runtime callback/event class names plus declared field names/types
- Proven text/reasoning/tool/terminal/error event semantics
- Cancellation behavior and native cleanup behavior
- At least one genuine successful 2.7.2 device completion

Known observations are leads, not proof: ly1 implements wu1; zn3.o(wu1, Long) and jw3.g(wu1, Long) return y60; y60 implements el4 and exposes continuation b(fl4, yb3). Do not infer the semantic meaning of DTOs from names alone.


## Pass 1 execution update

- Ported `OpenAiToolBridge.java` from upstream blob `6b5847d68c30a6525193f3187dac57fb92564d06`; MIT notice retained and `Plan` now implements `z2.ToolPlan`.
- Added bounded chunked transfer decoding to `OpenLocalApiGateway.readRequest`.
- Added source-regression assertions for framing limits, trailer rejection, tool-plan integration, and attribution.
- These changes are implementation progress, not proof that the protocol endpoints are fully wired or that the Android build/device tests pass.

## Anthropic Messages implementation note

- POST /v1/messages uses x-api-key and requires anthropic-version.
- Anthropic Base URL is the root listener URL, without /v1; the OpenAI Base URL remains /v1.
- Tool calls are client-executed: the gateway returns tool_use blocks and validates subsequent tool_result IDs.
- Streamed tool calls are rejected; text SSE uses the Anthropic message/content-block lifecycle.
- Signed thinking blocks are rejected until signature semantics are implemented. The gateway does not leak reasoning into unsigned thinking blocks.
- usage fields are returned as rough estimates marked dsh_usage_estimated=true; exact tokenizer counters and /v1/messages/count_tokens remain unavailable because z2.CompletionResult carries no tokenizer counters.
- Static source assertions were added, but Android compilation, runtime HTTP tests, and SDK integration tests remain pending.