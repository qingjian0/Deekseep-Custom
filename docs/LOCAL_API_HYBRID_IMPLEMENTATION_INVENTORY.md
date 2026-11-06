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

## Existing limitations to close

The current gateway implementation and regression checks show a narrow Chat Completions surface. The following are not yet accepted as complete:
- Responses API endpoint and response lifecycle
- Anthropic Messages endpoint and content-block lifecycle
- Tool/function-call parsing and tool-result continuation
- Complete request-body framing including bounded chunked transfer encoding
- Exact usage accounting / include_usage semantics
- Cross-request previous_response_id and client session isolation
- Protocol-complete SSE terminal/error/cancellation behavior
- Real code289 native request construction and event decoding

Unsupported fields must fail with explicit protocol errors; never silently discard them.

## Parallel workstreams

| Workstream | Scope | Depends on | Pass |
|---|---|---|---|
| P0 Contract | normalized request, event, usage, error, cancellation contracts | existing z2.Backend | 1 |
| P1 HTTP | request framing, auth, body bounds, chunked input, status mapping | P0 | 1 |
| P2 OpenAI Chat | message normalization, roles, tools, JSON output format, response mapping | P0/P1 | 1 |
| P3 Responses | response objects, items, previous_response_id, streaming events | P0/P1 | 1 |
| P4 Anthropic | Messages request/response, content blocks, tool use/result, SSE | P0/P1 | 1 |
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
