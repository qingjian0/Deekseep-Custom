# Local API Audit — 2026-11-06

## Scope and evidence boundary

- Repository: `qingjian0/Deekseep-Custom`
- Audited branch: `work/local-api-hybrid-api`
- Audited source revision: `ffa30eec0e38dd5d1f4e60898aad97eb9ad42ba8`
- Host target: official DeepSeek Android mainland 2.7.2, `versionCode=289`
- Build edition: Open
- Stable branches `main` and `dev` are not modified by this work.

This report distinguishes protocol implementation, CI verification, and real-device native execution. A passing build or fake/source regression is not evidence that the code289 model path can generate a response.

## Executive finding

The Open Local API now has a substantially implemented HTTP/protocol layer:

- OpenAI Chat Completions: text JSON/SSE, reasoning field/deltas, non-stream function-tool handoff and client-side tool-result history.
- OpenAI Responses: text JSON/SSE, function/custom tool handoff without streaming, and bounded in-memory `previous_response_id` continuation state.
- Anthropic Messages: text JSON/SSE and non-stream `tool_use/tool_result` handoff, with estimated usage explicitly marked.
- HTTP/1.1 request parsing includes bounded Content-Length and chunked-body handling, strict framing validation, authentication, and loopback/LAN listener controls.

However, the Open native completion backend remains deliberately fail-closed. The gateway can accept and validate requests, but cannot yet generate real model output because the code289 request builder, Challenge/PoW, Flow event decoder, and lifecycle contract are not fully proven and integrated.

## Verification status at audited revision

The audited revision `ffa30eec0e38dd5d1f4e60898aad97eb9ad42ba8` completed [CI run 42209379991](https://github.com/qingjian0/Deekseep-Custom/actions/runs/42209379991) successfully:

- ROI fast audit (0.5): passed.
- Source-only regression checks: passed.
- Host compatibility and feature regression tests: passed.
- Android Open release build and Android Lint: passed.
- APK structure, alignment, and signature verification: passed.
- Verified APK and lint-report artifact uploads: passed.

This validates the source/build checks included in that workflow. It does not establish native model generation, device installation, or end-to-end protocol acceptance on a real code289 device. The Chat usage regression assertions passed, but usage remains estimated and is explicitly marked `dsh_usage_estimated=true`.

## P0 — Native completion remains unavailable

Source-confirmed:

- The Open edition does not enable the protected `z18` completion payload.
- `HookAccountLoginSecurity.executeLocalApiCompletion` returns `native_adapter_unavailable` instead of fabricating a response or calling an unavailable protected payload.
- Gateway readiness is not inferred from the existence of configured model IDs.
- `GET /v1/models` describes the configured API surface; it does not claim that the native engine is ready.
- Unknown host versions remain fail-closed; no static candidate alone authorizes a functional Hook.

The user-facing UI must continue to disclose that the native Completion Adapter is not connected.

## P0 — code289 native boundary is observed but not yet callable

Existing device evidence establishes the following runtime boundary:

- Request implementation: `ly1 implements wu1`.
- Dispatch: `zn3.o(wu1, Long) -> y60`.
- Nested transport: `jw3.g(wu1, Long) -> y60`.
- The same Request object identity is observed across the nested synchronous boundary.
- Returned object: `y60 implements el4`.
- Continuation boundary: `y60.b(fl4, yb3)`.
- Concrete callback implementation families include `cr9`, `ec0`, and `s5`; observed wrappers include `zgb` and `cd3`.

Evidence limits:

- The observed `Long` argument has no proven semantic meaning.
- Runtime callback classes and payload types have not been mapped conclusively to text, reasoning, tool, terminal, or error events.
- Request field names/types and constructor/factory call sites are diagnostic clues, not a verified builder contract.
- Challenge/PoW preparation, session identity, authentication/retry behavior, cancellation, timeout, terminal events, and native cleanup remain unverified.
- Existing full observer capture is schema 4. The newer schema-7 constructor/factory diagnostics still require a fresh device capture; no schema-7 evidence is claimed here.
- No genuine successful 2.7.2/code289 completion has been demonstrated.

Do not synthesize `ly1` fields or infer the meaning of `zgb`/`cd3` from their obfuscated names.

## P1 — Current protocol surface and limitations

### OpenAI Chat Completions

Implemented:

- `POST /v1/chat/completions`, text-only messages.
- JSON and text/reasoning SSE deltas.
- Non-stream estimated usage and optional SSE `stream_options.include_usage` usage chunks; estimates are explicitly marked `dsh_usage_estimated=true` because exact tokenizer counters are unavailable.
- Non-stream function-tool request validation and tool-call handoff.
- Assistant tool-call history and matching client tool-result validation.
- `max_tokens` / `max_completion_tokens` validation and one-choice-only behavior.
- Explicit rejection of unsupported multimodal content, generation controls, response formats, and unsupported streaming tool calls.

Not implemented/accepted:

- Streaming tool-call handoff.
- Images, audio, and other multimodal input/output.
- Actual tool execution inside the gateway; tools are returned to the API client for client-side execution.

### OpenAI Responses

Implemented:

- `POST /v1/responses` with string or item-array input.
- Text JSON/SSE response lifecycle.
- Function/custom tool handoff in non-stream mode.
- `previous_response_id` continuation state: max 64 entries, 30-minute TTL, 256 KiB transcript cap; in-memory only and cleared on service stop/restart.
- Function-call output correlation against pending call IDs.
- Reasoning effort validation and mapping; selected web-search declarations map to the backend search switch.

Not implemented/accepted:

- Streaming tool-call handoff.
- Hosted tool execution and unsupported hosted tool types.
- Durable response state across process/service restart.
- Structured JSON Schema output beyond supported text format.

### Anthropic Messages

Implemented:

- `POST /v1/messages`, `x-api-key` authentication (with bearer fallback), and required `anthropic-version`.
- System text, user/assistant text and text content blocks.
- Non-stream `tool_use/tool_result` history and call-ID correlation.
- Text SSE lifecycle events.
- Estimated usage fields marked with `dsh_usage_estimated=true`.

Not implemented/accepted:

- Signed thinking blocks; reasoning is not leaked as an unsigned thinking block.
- Streaming `tool_use`.
- Image blocks, `/v1/messages/count_tokens`, exact tokenizer usage, and unsupported generation parameters.
- Anthropic SDK conformance has not yet been validated against the full official SDK test suite.

### HTTP, security, and runtime constraints

Implemented in source and covered by regression checks:

- Loopback listener defaults to `127.0.0.1`; LAN access is opt-in and binds only an explicitly detected `192.168.x.x` address, never `0.0.0.0`.
- Per-install bearer token; custom token validation; bounded request body and prompt sizes.
- Strict request-line, header, Content-Length, chunk-size, trailer, and transfer-framing validation.
- HTTP/1.1 chunked request-body decoding with decoded-body size bounds.
- Bounded client executor and explicit overload/error handling.
- Diagnostics avoid logging prompt/completion content, credentials, cookies, authorization headers, or tool arguments.

Still requires real runtime acceptance:

- Socket disconnect to native Flow cancellation.
- Fair account-wide generation permit and verified upstream cooldown/recovery.
- End-to-end SSE terminal/error semantics from the native Flow.
- Session isolation and cleanup against the actual host runtime.
- Network-change and foreground/background behavior on target devices.

## Required next work

1. Add executable protocol tests with a deterministic fake backend for Chat, Responses, Anthropic, tool continuation, malformed requests, chunked framing, SSE event order, and disconnect behavior. Current CI includes source assertions and host compatibility tests, but those are not a complete protocol conformance suite.
2. Continue schema-7 opt-in diagnostics on the exact code289 APK to establish the request constructor/factory and shallow argument shapes.
3. Correlate constructor/factory evidence with APK call sites; establish the exact request field mapping and Challenge/PoW lifecycle.
4. Decode the actual Flow interface and callback DTO semantics, including text/reasoning separation, terminal/error events, cancellation, and cleanup.
5. Implement the Open-only, code289-gated native adapter only after the above evidence is sufficient.
6. Require genuine device acceptance: non-stream Chat, reasoning, SSE, cancellation, tool handoff/continuation, concurrent requests, invalid credentials, malformed/oversized requests, and lifecycle/network-change behavior.

## Acceptance rule

The API must remain visibly and technically unavailable for model generation until every required native adapter component is validated. Protocol compatibility improvements may continue independently, but no configured model ID, static analysis result, successful CI run, or fake-backend test may be presented as proof of real model execution.
