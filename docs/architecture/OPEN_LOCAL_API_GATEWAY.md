# Open Local API Gateway

## Implementation status

The Open edition contains an in-process HTTP gateway in
`module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java`. It uses Android/JDK APIs and the stable
`z2.Backend` contract. It does not load the Closed payload (`z14` / `z1`) or depend on a
Closed-payload ABI.

The gateway's HTTP and protocol layers are implemented independently of native model execution.
The Open native completion adapter is still unavailable: the current backend is fail-closed and
returns HTTP 503 until a real, reviewed adapter is connected. A successful protocol response
mapping or a passing JVM/Android build is not evidence that DeepSeek can generate a reply through
the Local API.

## Routes

| Method | Route | Protocol-layer status | Native execution |
|---|---|---|---|
| GET | `/v1/models` | Implemented; reports configured IDs, not engine readiness | Not applicable |
| POST | `/v1/chat/completions` | OpenAI Chat Completions JSON and SSE mapping implemented | 503 while backend is not ready |
| POST | `/v1/responses` | Core Responses JSON/SSE mapping, input normalization, tool-call mapping, and bounded response-state continuation implemented | 503 while backend is not ready |
| POST | `/v1/messages` | Anthropic Messages JSON/SSE mapping and request normalization implemented | 503 while backend is not ready |
| POST | `/v1/agent/run` | Not implemented | Not available |

Canonical model IDs are `deepseek-chat` and `deepseek-reasoner`. The legacy alias
`deepseek-flash` is advertised for compatibility and canonicalized to `deepseek-chat`; it does
not select a separate model lane.

### Protocol capabilities and limits

- **Chat Completions:** text messages, non-stream function-tool mapping, and text/reasoning SSE
  deltas are supported at the protocol layer. Streaming tool calls are explicitly rejected.
- **Responses:** string and supported item-array input, text output, function/custom tool mapping
  in non-stream mode, SSE text events, and `previous_response_id` continuation state are
  implemented. Response state is in-memory, bounded by count/size/TTL, and cleared on service
  stop/restart. Streaming tool calls are explicitly rejected.
- **Anthropic Messages:** system/user/assistant text, text content blocks, and non-stream
  `tool_use` / `tool_result` history normalization are implemented. SSE emits the message
  lifecycle and text content-block events. Streaming tool calls and signed Anthropic thinking
  blocks are not implemented; a non-null `thinking` request option is rejected.
- Anthropic usage values are estimates and carry `dsh_usage_estimated=true`; they are not exact
  DeepSeek tokenizer counts. The other protocol responses do not currently provide exact usage.
- Image/audio input, arbitrary multimodal content, and unsupported response formats/options are
  rejected rather than silently ignored.
- Tool calls are returned to the API client; the gateway does not execute client-side tools.

## Security and resource limits

- Disabled by default; the existing Local API enable switch is the opt-in.
- The primary listener binds only to `127.0.0.1`. Explicit LAN opt-in creates a separate listener
  bound to a detected `192.168.x.x` interface address. The remote source is independently
  restricted to `192.168.0.0/16`. It never binds to `0.0.0.0`; other private ranges, public
  IPv4, and IPv6 are rejected.
- LAN mode uses plain HTTP. Enable it only on a trusted network; there is no TLS, external tunnel,
  or remote port-forwarding support.
- A per-install cryptographically random Bearer token is stored in the host app's private files
  directory. Generated tokens use an `sk-` prefix followed by 32 random bytes encoded with
  URL-safe Base64. The prefix is only a local format convention and does not mean the key belongs
  to DeepSeek's hosted API.
- Custom keys accept 8–256 visible ASCII characters without whitespace/control characters and
  survive process restart. Key rotation replaces the in-memory and persisted credential.
- Default port is 8765; a custom port is persisted in private preferences.
- Request bodies are limited to 1 MiB and prompt text to 128 KiB.
- The gateway uses two active request workers and a bounded queue of eight requests.
- Chunked request bodies are rejected; clients must send `Content-Length`.
- Prompts, completions, API tokens, and tool arguments are not written to diagnostic logs.
- API diagnostics record lifecycle, stage, duration, and output-size metadata rather than message
  content.

## LAN lifecycle

While LAN mode is enabled, Android network callbacks trigger address re-discovery. The gateway
selects an eligible non-VPN IPv4 address in `192.168.0.0/16`, binds a replacement socket to the
exact address before closing the previous listener, and closes the LAN listener if no eligible
address remains. Callback registration or rebind failures do not stop the loopback listener.
Connection diagnostics expose the current LAN binding state and exception class on bind failure.

## Native adapter blocker

The protocol gateway is not the native model adapter. The current Open backend remains fail-closed
because the existing completion path reaches Closed-only payload code. Runtime observation has
confirmed the code289 transport/continuation boundary, but the request-construction, PoW/Challenge,
Flow event semantics, and response mapping still require evidence from the current schema 7
on-device diagnostic session. Do not infer obfuscated field values or fabricate a native response.

## Verification levels

Report these independently:

1. Source-only regression checks.
2. Kotlin/JVM unit tests.
3. Legacy/Universal compatibility compilation.
4. Open APK build, lint, APK structure, alignment, and signing checks.
5. Emulator smoke tests.
6. Real-device DeepSeek 2.7.2 / LSPosed validation, including a real native completion through
   the Local API.

A passing earlier level does not imply that a later level passed. In particular, protocol-layer
coverage and a successful APK build do not establish end-to-end model generation.
