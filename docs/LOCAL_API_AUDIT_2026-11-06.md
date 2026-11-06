# Local API Audit — 2026-11-06

## Scope

Audit baseline: `qingjian0/Deekseep-Custom`, Open edition, DeepSeek Android mainland
2.7.2 / `versionCode=289`. The development baseline is `dev`; diagnostic work is kept on
`work/local-api-event-decoder-diagnostics` and is not merged into `main` or `dev`.

This document separates source-confirmed defects, device-observed facts, and work that still needs
runtime proof. A green source-level test is not evidence that the native model path works.

## Executive finding

The current Open Local API is not a working model gateway. It can start an HTTP listener and
authenticate requests, but the Open backend is deliberately fail-closed because the 2.7.x native
completion adapter is not implemented. The current HTTP surface also rejects common client
features before they reach the backend.

The first real-device API capture contains six HTTP 400 rejections before native execution:
five requests with `tools`, and one request with `stream_options.include_usage`. Those are
protocol-surface limitations, not transport failures.

Separately, five committed device log files are referenced by `docs/NATIVE_ADAPTER.md`. The
non-empty full observer capture establishes real code289 transport and continuation boundaries,
but does not establish a complete response lifecycle or a callable adapter.

## Findings

### P0 — Native completion is unavailable in the Open edition

Source-confirmed:

- `z14.available()` returns false and `z14.payloadClass(...)` returns null in the Open build.
- `Main` backend readiness requires `BuildInfo.LOCAL_API_INCLUDED`, a host ClassLoader,
  `liveR92`, and `liveQ71`.
- The legacy q71/transport capture path is skipped for the 2.7.x family.
- `HookAccountLoginSecurity.executeLocalApiCompletion` delegates to the Closed payload class
  `ds.dsh.DeepSeek.z18`; the Open stub cannot resolve it.
- The gateway therefore returns HTTP 503 `native_adapter_unavailable` instead of generating a
  fake response.

This is the primary blocker. Do not relax readiness or synthesize model output.

### P0 — code289 native call boundary is identified, but not yet callable

Device-observed in the committed capture:

- Request implementation: `ly1 implements wu1`.
- Dispatch: `zn3.o(wu1, Long) -> y60`.
- Low-level transport: `jw3.g(wu1, Long) -> y60`.
- The same Request object is observed across the nested synchronous boundary.
- The observed invocation arguments are `[ly1, null]`; the Long argument's meaning is unknown.
- Returned object: `y60 implements el4`.
- Continuation boundary: `y60.b(fl4, yb3) -> Object`.
- Concrete callback implementations observed include `cr9`, `ec0`, `s5`, `qa`,
  `k41`, and `x0`.
- Callback return wrappers include `zgb`; continuation results include `cd3`.

Evidence limits:

- The observer does not prove that `el4` is the Kotlin Flow interface until the exact interface
  mapping is checked against the target APK.
- The callback event schema is not decoded. Runtime types such as `av1` and `vd6` are candidates,
  not established text/reasoning/error event types.
- Request construction, Challenge/PoW, session identity, response terminal events, cancellation,
  and error mapping remain unverified.
- The Long argument must not be treated as a timeout or Challenge without call-site evidence.

### P1 — Current Open HTTP surface is intentionally limited

Source-confirmed in `OpenLocalApiGateway.java` and
`test-local-api-network-regression.sh`:

- Implemented routes: `GET /v1/models` and `POST /v1/chat/completions`.
- Only text and text-part message content is accepted.
- `tools`, `tool_choice`, `response_format`, named/tool messages, and
  `stream_options.include_usage` are rejected with HTTP 400.
- `Transfer-Encoding: chunked` requests are rejected.
- Responses API, Anthropic Messages API, model detail, health route, and count-token route are
  absent from the Open gateway.
- Usage accounting and Agent tool-result continuation are not implemented.
- Model discovery returns configured model IDs even while the native backend is unavailable.

The existing regression script currently treats several of these rejections as expected behavior.
Those assertions must change when the corresponding feature is implemented; do not weaken tests by
silently ignoring unsupported request fields.

### P1 — Compatibility and reliability gaps

- No verified end-to-end SSE lifecycle from native Flow start through terminal event.
- No proven socket-disconnect-to-native-Flow cancellation path.
- No verified per-client session isolation or hidden-session cleanup for code289.
- No verified fair account-wide generation permit or recovery after upstream 429/session expiry.
- No chunked request-body support, which some HTTP clients use.
- No Responses `previous_response_id` continuation or Anthropic `tool_use/tool_result` bridge.
- No OpenAI tool envelope parser, schema validator, forced-choice handling, or duplicate side-effect
  suppression in the current Open gateway.

## Reference implementation research

The strongest directly relevant reference is
[Xposed-Modules-Repo/com.dsmod.probe](https://github.com/Xposed-Modules-Repo/com.dsmod.probe).

Public files reviewed:

- `module/src/com/dsmod/probe/LocalApiGateway.java`: HTTP framing, authentication, Chat,
  Responses, Anthropic, SSE lifecycle, usage, status and session continuation.
- `module/src/com/dsmod/probe/OpenAiToolBridge.java`: request-scoped tool schemas, tool-choice
  validation, prompt protocol, parsing and result mapping.
- `module/src/com/dsmod/probe/OmniRouteToolBridge.java`: tolerant tool-envelope extraction and
  recovery.
- `module/tests/com/dsmod/probe/LocalApiGatewayProtocolRegressionTest.java`: fake-backend
  protocol tests.
- `docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md`: architecture and device acceptance report.

The reference design keeps the HTTP gateway in the injected DeepSeek process so it can reuse the
host's authenticated transport and coroutine objects. It uses one fair account-wide native permit,
request-scoped tool plans, hashed client-session scopes, a bounded SSE writer, deadlines, 429
cooldown, invalid-session recovery, and explicit cancellation accounting.

Important limitation: the reference's public gateway and protocol bridge are inspectable, but its
native execution payload is not fully available as portable Open-edition source. Its reported
2.2.2 device results do not validate this project's 2.7.2/code289 adapter.

Licensing note: the inspected `Xposed-Modules-Repo/com.dsmod.probe` repository declares MIT;
this project declares GPL-3.0. MIT-licensed portions can be adapted into this GPL-3.0 project while
retaining the required MIT copyright and permission notice for copied substantial portions. Prefer
that repository as the source for protocol-layer porting rather than copying GPL-only files from
other forks without checking their provenance.

## Repair sequence

### Stage A — complete code289 evidence

1. Use the opt-in schema 6 probe to capture only the first argument's shallow field names/types
   at the first Flow collector callback.
2. Correlate the observed event DTOs with the exact code289 APK's declared fields and call sites.
3. Capture bounded type-only transport receiver shapes and exception stack symbols if required;
   never capture field values, message content, credentials, cookies, or headers.
4. Establish the exact Request constructor/factory, Challenge/PoW lifecycle, transport receiver,
   Flow interface, event types, and terminal/error semantics.

### Stage B — native adapter

Implement an Open-only, code289-gated adapter with:

- verified Request builder;
- verified Challenge/PoW preparation and serialization;
- exact transport invocation;
- single-collection Flow consumer and event decoder;
- reasoning/text separation and terminal/error mapping;
- cancellation propagation and cleanup;
- request-scoped native session isolation;
- one fair account-wide generation permit and bounded deadline;
- fail-closed behavior for all unverified host versions.

Readiness must become true only after every required adapter component is installed and validated.
Do not reuse legacy `liveR92/liveQ71` as a proxy for code289 readiness.

### Stage C — protocol compatibility

After native text/reasoning streaming is verified:

1. Add Chat tool/function parsing and tool-result continuation.
2. Add truthful `stream_options.include_usage` support (estimated usage must be identified as
   approximate; never fabricate exact token counts).
3. Add chunked HTTP request framing and request-body limits.
4. Add OpenAI Responses and `previous_response_id` state.
5. Add Anthropic Messages, `tool_use/tool_result`, thinking blocks and count_tokens.
6. Add JSON Schema output and supported generation parameter mapping.
7. Add protocol-specific error objects and bounded SSE heartbeat/backpressure.

### Stage D — acceptance

Required on a real DeepSeek 2.7.2/code289 device:

- `GET /v1/models` and authenticated health status;
- non-stream Chat text and reasoning;
- SSE text/reasoning with exactly one terminal event;
- client disconnect stops upstream collection;
- tool call → client tool execution → tool result → model continuation;
- `include_usage` behavior and documented estimate semantics;
- chunked request body;
- concurrent requests serialize without `parallel_chat_limit`;
- invalid API key, malformed JSON, oversized body, unsupported model and upstream errors map
  correctly;
- background/foreground retention and network change;
- no secrets or prompt content in diagnostic logs.

## Current branch state

- Branch: `work/local-api-event-decoder-diagnostics`.
- Based on: `fix/native-probe-concrete-callback-hooks`.
- This branch adds value-free callback argument shape capture and aligns the diagnostic report
  schema/test expectations to schema 6.
- It has not been merged to `dev` or `main`.
- No CI, Android build, APK install, or schema 6 real-device capture is claimed by this audit.
- The gateway remains fail-closed; the Local API is not yet repaired or accepted as usable.
