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

## Not yet completed

- Responses API endpoint is not yet implemented in the Open gateway.
- Anthropic Messages and count_tokens endpoints are not yet implemented in the Open gateway.
- HTTP chunked decoder has source assertions but has not yet been executed in an Android/JVM test harness.
- No Android build or CI run is claimed. The latest commit has no reported workflow runs or status checks.
- code289 native request construction, challenge/PoW, Flow collection, semantic event decoding, cancellation, and genuine device generation remain unverified.

## Current acceptance status

| Area | Status |
|---|---|
| Architecture decision and work breakdown | Recorded |
| MIT provenance | Recorded; first bridge port retains notice |
| Shared Backend contract | Existing contract retained |
| Chunked HTTP request framing | Implemented; execution test pending |
| OpenAI tool parser | Ported and wired to non-stream Chat; execution/build verification pending |
| OpenAI Chat non-stream tool handoff | Implemented; execution/build verification pending |
| OpenAI Responses | Not yet accepted |
| Anthropic Messages | Not yet accepted |
| code289 native generation | Not yet accepted |
| Android build / CI / real device | Not yet verified |

## Next parallel batch

1. Adapt the reference Responses request/response path to the existing z2.CompletionRequest / CompletionResult contract.
2. Adapt the Anthropic Messages request/response path and content-block SSE lifecycle.
3. Extend the tool-plan contract to Responses after its request/response adapter is in place; keep tool execution client-side unless an explicitly authorized local executor is configured.
4. Add focused runtime tests for HTTP framing, tool schemas, tool-call continuation, SSE terminal events, and session scoping.
5. Continue code289 APK/device evidence work independently; do not enable the native backend until the exact request and event contracts are proven.
