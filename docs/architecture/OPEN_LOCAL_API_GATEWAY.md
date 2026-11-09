# Open Local API Gateway

## Current implementation

The Open edition has an in-process local/LAN HTTP gateway in
`module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java`. It uses Android/JDK APIs and the stable
`z2.Backend` contract. The HTTP surface does not depend on the Closed payload (`z14` / `z1`)
or its payload ABI.

**Open-surface policy:** the gateway starts and exposes its protocol routes even when a native
completion engine is unavailable. A request is dispatched through the execution facade; if the
engine is absent, the facade reports the concrete capability error `engine_missing` (HTTP 503)
for that request. Do not hide routes behind an edition-level readiness gate, and do not fabricate
a model completion.

## Routes

| Method | Route | Status |
|---|---|---|
| GET | `/v1/models` | Returns the configured model catalog; this does not imply native execution readiness |
| POST | `/v1/chat/completions` | OpenAI Chat request validation, non-stream response mapping, text/reasoning SSE and non-stream tool-call bridge |
| POST | `/v1/responses` | Responses input/output mapping, bounded `previous_response_id` continuation state and Responses SSE |
| POST | `/v1/messages` | Anthropic Messages request/response mapping and Anthropic SSE |
| POST | `/v1/agent/run` | Not implemented |

The three Completion protocol routes remain exposed in Open. Until an Open-native execution engine
is connected, an actual completion request reaches the facade and receives `engine_missing`
(HTTP 503). This is a per-request execution result, not a route/readiness preflight that disables
the API surface.

## Protocol behavior and boundaries

- **OpenAI Chat Completions:** validates messages, model IDs, token budgets, response format
  and tool schemas; supports non-streaming and SSE text/reasoning deltas. Tool-call decoding is
  non-streaming only. Missing/null token budgets default to 4096; positive integer numeric strings
  are accepted for compatibility, values above 8192 are rejected rather than silently clamped,
  and fractional, zero, negative or non-numeric values are rejected. When both OpenAI aliases
  are present, non-null `max_completion_tokens` takes precedence; a null value falls back to
  `max_tokens`. Diagnostics record only the input type and applied budget, never the raw value
  or request body.
- **OpenAI Responses:** accepts string/array input, instructions, text output format, tools and
  `previous_response_id`; emits Responses-style output and SSE lifecycle/delta events. Continuation
  state is process-local, bounded to 64 entries with a 30-minute TTL, and cleared on service stop.
- **Anthropic Messages:** exposed at `POST /v1/messages`; requires `anthropic-version`, accepts
  `x-api-key` (Bearer fallback is also accepted), and maps text/tool-use output to Anthropic JSON
  or SSE events.
- **Tool calls are not local tool execution:** caller tool definitions are converted into a
  constrained prompt protocol; decoded calls are validated against the request-scoped schema and
  returned as structured `tool_calls` / `tool_use`. The module does not execute client tools.
  Tool-call streaming is not supported.
- **Usage is estimated:** returned counts carry `dsh_usage_estimated=true`; they are not exact
  DeepSeek tokenizer counts. Chat streaming accepts `stream_options.include_usage=false`;
  `true` and non-boolean values return HTTP 400 because a final usage chunk is not implemented.
- Image/audio input and non-text structured output formats remain unsupported and are rejected
  explicitly rather than silently ignored.
- Canonical model IDs are `deepseek-chat` and `deepseek-reasoner`. The legacy alias
  `deepseek-flash` canonicalizes to `deepseek-chat`; it does not select a separate model lane.

## Native execution boundary

The Open native Completion Adapter is not implemented yet. The existing execution facade still
resolves the Closed-only `z18` payload; when it is absent, the facade reports `engine_missing`
per request. Static code289 evidence identifies Request, Challenge/PoW, Transport, Flow, SSE and
Delta reducer boundaries, but does not prove a working Open adapter.

The remaining implementation work is to establish verified session creation/reuse, construct the
official Request from real host session state, enter the host's Challenge/PoW and Transport path,
collect the returned Flow exactly once, apply DeltaCommand events, assemble the response, and
preserve cancellation, deadlines, error propagation and session isolation. No fake response or
synthetic host session may be used as a substitute.

## Security and resource limits

- Disabled by default; the existing Local API enable switch is the opt-in.
- Defaults to `127.0.0.1`. An explicit UI opt-in creates a second listener bound only to the
  currently detected `192.168.x.x` IPv4 address; the original listener remains loopback-only.
  The LAN listener validates remote sources in `192.168.0.0/16`; it never binds to
  `0.0.0.0`. Other private ranges, public IPv4 and IPv6 are rejected.
- Uses a per-install cryptographically random Bearer token stored in the host app's private files
  directory. Generated tokens use an `sk-` prefix plus 32 random bytes encoded with URL-safe
  Base64. The prefix is only a format convention.
- Custom Bearer tokens accept 8–256 visible ASCII characters without whitespace/control
  characters; persisted tokens use the same validator. Token rotation immediately replaces the
  in-memory and persisted credential.
- Default port is 8765; the port setting persists a custom port in private preferences.
- Request bodies are limited to 1 MiB and prompt text to 128 KiB.
- Uses two active request workers and a bounded queue of eight requests.
- Rejects chunked request bodies; clients must send Content-Length.
- LAN uses plain HTTP and is intended only for trusted Wi-Fi/LAN. No TLS, public listener,
  external tunnel or remote port forwarding is provided.
- Network callbacks refresh the exact eligible LAN address. Loss of an eligible address closes
  the LAN listener while preserving loopback; callback/rebind failures do not stop loopback.
- Prompts, completions, API tokens and tool arguments are not written to logs.

## Verification requirements

A successful source check or JVM test does not establish a working native Hook. Record separately:
JVM/script regression, Legacy/Universal compilation, Open APK build, emulator smoke test, and
real-device DeepSeek/LSPosed validation. The native adapter is not complete until the code289
runtime path and end-to-end completion have been verified.
