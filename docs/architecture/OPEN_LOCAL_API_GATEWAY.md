# Open Local API Gateway

## Current implementation

The Open edition now has an in-process loopback HTTP gateway implemented in
`module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java`. It uses only Android/JDK APIs and the
stable `z2.Backend` contract. It does not load the Closed payload (`z14` / `z1`) and does not
depend on a payload ABI.

The existing `Main.startz1()` backend remains the native model adapter. It waits for the host
class loader, native transport and PoW objects to become ready before accepting completions.

## Routes

| Method | Route | Status |
|---|---|---|
| GET | `/v1/models` | Implemented; requires a ready native backend |
| POST | `/v1/chat/completions` | Implemented for text messages |
| POST | `/v1/responses` | Not implemented |
| POST | `/v1/agent/run` | Not implemented |

Supported model IDs are `deepseek-chat` and `deepseek-reasoner`. The current adapter maps both
to the host's default native model lane and enables reasoning mode for the reasoner ID.

## Security and resource limits

- Disabled by default; the existing Local API enable switch is the opt-in.
- Binds exclusively to `127.0.0.1`; LAN and public binding are intentionally unavailable.
- Uses a per-install random Bearer token stored in the host app's private files directory.
- Supports token rotation from the existing Local API controls.
- Default port is 8765; the existing port setting persists a custom port in private preferences.
- Accepts request bodies up to 1 MiB and prompt text up to 128 KiB.
- Uses two active request workers and a bounded queue of eight requests.
- Rejects chunked request bodies; clients must send Content-Length.
- SSE writes deltas directly and suppresses reasoning deltas from the public content stream.
- Does not persist prompts, completions, API tokens in logs, or tool arguments.

## Known limitations before release

- No Responses API, Anthropic protocol, tools/function calling, image input, or audio input.
- No LAN access, TLS, external tunnel, or remote port forwarding.
- Native model readiness still depends on the official DeepSeek host process having initialized
  its transport and PoW objects.
- Client disconnect is observed when the next stream write fails; a quiet upstream operation may
  continue until its request deadline.
- The API response currently omits token usage and several optional OpenAI fields.
- Android build/CI and real-device verification are required before treating this as released.

## Verification levels

Do not equate a successful JVM test or source compilation with a working native Hook. Record
separately: Kotlin/JVM tests, Legacy/Universal compilation, Open APK build, emulator smoke test,
and real-device DeepSeek/LSPosed validation.
