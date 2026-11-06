# Local API hybrid implementation — Pass 1 progress

Date: 2026-11-06  
Branch: work/local-api-hybrid-api  
Status: In progress; not a release acceptance report.

## Completed in this pass

- Recorded ADR-LOCAL-API-001: protocol-layer reuse plus project-owned code289 native adapter.
- Added parallel implementation inventory and source provenance ledger.
- Ported the upstream OpenAI tool bridge and its OmniRoute parser dependency into the project package.
  - Upstream blob: 6b5847d68c30a6525193f3187dac57fb92564d06
  - Preserved the complete MIT copyright and permission notice.
  - Migrated package to ds.dsh.DeepSeek.
  - Mapped reference gateway exceptions to z2.GatewayException.
  - Connected Plan to the existing z2.ToolPlan interface.
  - Ported OmniRoute's strict tool-envelope and loose-JSON parser; retained diegosouzapw's MIT notice and upstream commit provenance.
- Added bounded HTTP/1.1 chunked request-body decoding to OpenLocalApiGateway.
  - Rejects Content-Length plus Transfer-Encoding ambiguity.
  - Accepts only the chunked transfer coding.
  - Enforces the decoded MAX_BODY_BYTES limit.
  - Requires strict CRLF framing.
  - Bounds and validates trailers; rejects framing/authentication-sensitive trailer fields.
- Extended source-regression assertions for chunked framing and tool bridge/license contracts.

## Newly completed in this batch

- Wired OpenAI Chat Completions non-stream tool handoff: request tool definitions and choice are validated, tool instructions are included in the prompt, model output is parsed against the request-scoped schema, and valid calls are returned as `message.tool_calls` with `finish_reason=tool_calls`.
- Added client-side tool continuation history parsing: assistant tool-call records are validated and tool results must match a preceding call ID. The gateway does not execute client tools.
- Explicitly rejects streaming tool calls until SSE redaction can guarantee that private tool syntax is never leaked.
- Updated Open-build UI capability disclosures to distinguish implemented non-stream tool handoff from the still-unavailable native adapter.


- Added `POST /v1/responses` for text input and message/function-call/function-call-output input items.
- Added Responses JSON output items for assistant text, function calls, and custom tool calls; `web_search_*` declarations map to the native search switch, reasoning effort is validated/mapped, and unsupported hosted tool types are rejected.
- Added text-only Responses SSE lifecycle (`response.created`, `response.in_progress`, output/content events, `response.completed`); tool-bearing streaming is rejected explicitly.
- Added bounded in-memory `previous_response_id` continuation state: up to 64 responses, 30-minute TTL, 256 KiB transcript cap, and automatic loss on service restart. Function/custom tool call IDs remain pending until matching tool outputs arrive.

## Anthropic Messages implementation update

- Added a dedicated AnthropicMessagesAdapter to normalize Anthropic request bodies into the existing z2.CompletionRequest and shared OpenAiToolBridge.Plan contract.
- Registered POST /v1/messages; supports x-api-key (and bearer fallback), requires anthropic-version, and emits Anthropic-shaped JSON errors.
- Supports system text, user/assistant text blocks, declared function tools, tool_use / tool_result history with call-ID correlation, non-streaming tool handoff, and text SSE lifecycle.
- Thinking mode is deliberately rejected until signed thinking-block support exists; reasoning data is not emitted as an unsigned Anthropic thinking block.
- Tool-call streaming, image blocks, /v1/messages/count_tokens, and exact token usage remain unsupported. usage fields are present as explicitly marked rough estimates (dsh_usage_estimated=true) because the current z2.Backend result has no tokenizer counters; exact usage remains unavailable.
- Open UI and connection details now disclose both protocol families and show the separate Anthropic root Base URL (without /v1).

## Native adapter investigation update

- Analyzed four non-empty code289 schema-4 runtime logs plus one API-validation-only log.
- Confirmed the observed host path enters zn3.o and then jw3.g with the same ly1 request instance; both return y60, whose b(fl4, yb3) continuation is entered asynchronously.
- Recorded the observed ly1 field shape and callback class/payload-type families without exposing field values or message content.
- Added a schema-7 diagnostic extension: inventory ly1 constructors and the observed request-factory stack methods (mw1.y/e, jr2.y/m, mr2.h); capture at most 16 constructor argument class/field-shape events.
- The diagnostic extension remains Open/code289-only, explicit-toggle-only, bounded, and transparent to host behavior. It does not invoke transport candidates or capture argument/field values.
- The log evidence still does not establish request field semantics, Challenge/PoW preparation, Flow terminal semantics, or successful user-visible generation.

## CI failure analysis and corrective changes

- The first Android compile attempt found a malformed Java character literal in the chunk-line parser; corrected it to the valid tab character literal.
- The package identity regression check rejected two provenance comments that embedded the retired source package path. The full MIT notices remain intact; source headers now identify the reference gateway without retaining the retired package namespace. Exact upstream paths remain in the provenance documentation.
- The failure run also confirmed the Kotlin runtime unit-test stage completed successfully. Android Java compilation and package identity checks are being rerun on the corrected branch head.

## Not yet completed

- OpenAI Responses endpoint is implemented for text JSON/SSE and non-streaming function/custom tool handoff; `previous_response_id` uses bounded in-memory state (64 entries, 30-minute TTL, 256 KiB transcript cap, cleared on stop/restart); streamed tool calls remain unsupported.
- Anthropic Messages text JSON/SSE and non-streaming tool handoff are implemented; thinking signatures, count_tokens, images, and streamed tool_use remain unsupported; usage is a clearly marked estimate, not an exact tokenizer count.
- HTTP chunked decoder has source assertions but has not yet been executed in an Android/JVM test harness.
- The Android build and regression script have not been executed in this environment. CI was not triggered in this batch to avoid competing with the existing API CI work; the latest commit currently has no reported commit status checks.
- code289 native request construction, challenge/PoW, Flow collection, semantic event decoding, cancellation, and genuine device generation remain unverified. HookAccountLoginSecurity.executeLocalApiCompletion now explicitly rejects Open builds before attempting the protected-only z18 payload lookup.

## Current acceptance status

| Area | Status |
|---|---|
| Architecture decision and work breakdown | Recorded |
| MIT provenance | Recorded; first bridge port retains notice |
| Shared Backend contract | Existing contract retained |
| Chunked HTTP request framing | Implemented; execution test pending |
| OpenAI tool parser | Ported and wired to non-stream Chat; execution/build verification pending |
| OpenAI Chat non-stream tool handoff | Implemented; execution/build verification pending |
| OpenAI Responses text + non-stream tool handoff | Implemented; execution/build verification pending |
| Anthropic Messages text + non-stream tool handoff | Implemented; source assertions added; build/SDK verification pending |
| code289 native generation | Not yet accepted; protected z18 path is guarded; native adapter evidence still incomplete |
| Android build / CI / real device | Not yet verified |

## Next parallel batch

1. Build and install the schema-7 diagnostic build, then capture a fresh ordinary generation to identify the actual ly1 constructor/factory and argument-shape mapping; do not infer field semantics from obfuscated names.
2. Verify the zn3/jw3 transport call and y60 Flow collection contract, then decode callback events and cancellation.
3. Add a fake-backend JVM harness for Chat/Responses, HTTP framing, tool continuation, SSE, and session isolation.
4. Add a fake-backend JVM harness and protocol fixtures for Chat, Responses, and Anthropic Messages; verify SDK parsing and SSE event order.
5. Keep the native backend fail-closed until a genuine code289 device completion and cancellation test pass.
