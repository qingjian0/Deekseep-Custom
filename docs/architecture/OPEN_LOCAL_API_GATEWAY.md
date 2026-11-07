# Open Local API Gateway

## Current implementation

The Open edition now has an in-process local/LAN HTTP gateway implemented in
`module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java`. It uses only Android/JDK APIs and the
stable `z2.Backend` contract. It does not load the Closed payload (`z14` / `z1`) and does not
depend on a payload ABI.

The existing `Main.startz1()` backend dispatches completion requests into the execution facade.
The Open edition exposes the HTTP surface independently of native adapter readiness; unsupported
native capabilities are reported by the execution path for each request rather than pre-rejecting
the entire Open API surface.

## Routes

| Method | Route | Status |
|---|---|---|
| GET | `/v1/models` | Returns the configured model IDs; does not imply native execution readiness |
| POST | `/v1/chat/completions` | HTTP/JSON/SSE route exposed; dispatches to the execution facade and reports unsupported capabilities per request |
| POST | `/v1/responses` | Not implemented |
| POST | `/v1/agent/run` | Not implemented |

The canonical model IDs are `deepseek-chat` and `deepseek-reasoner`. For compatibility with
clients configured against the earlier internal model catalog, `deepseek-flash` is also advertised
as a legacy alias and canonicalized to `deepseek-chat` before request construction. The alias does
not select a separate model lane. The request mapper selects the host's default native model lane
and enables reasoning mode for the reasoner ID. This mapping is not yet executable in the Open edition: the current execution facade still
resolves the Closed-only `z18` payload. The route is nevertheless exposed; until an Open-native
engine is connected, the execution facade reports `engine_missing` rather than hiding the route
behind an edition-level readiness gate.

## Security and resource limits

- Disabled by default; the existing Local API enable switch is the opt-in.
- Defaults to `127.0.0.1`. An explicit UI opt-in creates a second listener bound only to the currently detected `192.168.x.x` interface address; the original listener remains bound to `127.0.0.1`. The LAN listener also validates that the remote IPv4 source is in `192.168.0.0/16`. It never binds to `0.0.0.0`. Other private ranges, public IPv4, and IPv6 are rejected.
- Uses a per-install cryptographically random Bearer token stored in the host app's private files directory. The token is rendered as `sk-` followed by 32 random bytes encoded with URL-safe Base64 (43 characters); the prefix is a format convention, not an assertion that the token belongs to DeepSeek's hosted API.
- Custom Bearer tokens accept 8–256 visible ASCII characters (no whitespace/control characters); the same validator is used when loading the persisted token, so custom keys survive process restart. Newly generated tokens retain the `sk-` prefix.
- Supports token rotation from the existing Local API controls; rotation immediately replaces the in-memory and persisted credential.
- Default port is 8765; the existing port setting persists a custom port in private preferences.
- Accepts request bodies up to 1 MiB and prompt text up to 128 KiB.
- Uses two active request workers and a bounded queue of eight requests.
- Rejects chunked request bodies; clients must send Content-Length.
- SSE writes text deltas as `content` and reasoning deltas as `reasoning_content`; non-stream reasoner responses also expose `reasoning_content`.
- Does not persist prompts, completions, API tokens in logs, or tool arguments.

## Known limitations before release

- No Responses API, Anthropic protocol, tools/function calling, image/audio input, or usage accounting. Plain string and OpenAI `text` / `input_text` message parts are accepted; unsupported tool, response-format, image, and usage-streaming options are rejected explicitly rather than silently ignored.
- LAN access is limited to `192.168.0.0/16`; it uses plain HTTP, so enable it only on a trusted Wi-Fi/LAN. No TLS, external tunnel, or remote port forwarding.
- While LAN access is enabled, the gateway observes Android's default-network callbacks and re-reads the active network's link properties. It binds a replacement socket to the new exact `192.168.x.x` address before closing the previous listener; loss of an eligible address closes the LAN listener while preserving loopback. Callback registration or rebind failures are logged and do not stop the loopback API. Connection diagnostics expose the current LAN binding state and the exception class on bind failure.
- The connection information reports the loopback Base URL as `http://127.0.0.1:<port>/v1`; the raw listener root remains an internal compatibility value.
- The Open-native completion adapter is not implemented yet. Capturing the official host
  transport and PoW objects is insufficient because the current execution facade still delegates
  to the Closed-only payload. The Open HTTP surface is exposed; the request execution path reports
  the missing engine. The code289 dynamic evidence is archived in
  [qingjian0/apps](https://github.com/qingjian0/apps), including the 19,747-line
  `deekseep_vision.log` and `deepseek-sse-hook-evidence.zip`.
- Client disconnect is observed when the next stream write fails; a quiet upstream operation may
  continue until its request deadline.
- The API response currently omits token usage and several optional OpenAI fields. `max_tokens` and `max_completion_tokens` are validated as integers from 1 through 8192; invalid types/ranges return HTTP 400. Malformed HTTP requests return 400/413 and non-JSON request content types return 415.
- Android build/CI and real-device verification are required before treating this as released.

## Verification levels

Do not equate a successful JVM test or source compilation with a working native Hook. Record
separately: Kotlin/JVM tests, Legacy/Universal compilation, Open APK build, emulator smoke test,
and real-device DeepSeek/LSPosed validation.
