# Open Local API Native Adapter Gap Audit — 2026-11-07

## Scope

This audit is based on the current `dev` source tree. It is a source-level control-flow audit only; it does not claim a successful Android build, CI run, or device execution.

Reviewed source files and blob identities:

| File | Blob SHA |
|---|---|
| `module/src/ds/dsh/DeepSeek/z13.java` | `58fc28881245678684ab41337f3684c12e72c4f2` |
| `module/src/ds/dsh/DeepSeek/z2.java` | `7628af72d9f746899777bc4b6980e2a9a64df3ff` |
| `module/src/ds/dsh/DeepSeek/Main.java` | `2dc5399fc42414129f8c22b3b0490a9d7cda8f74` |
| `module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java` | `fd11cf1918529bc1d6df103d027d3171cc5c5728` |
| `module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java` | `ae770b9b74f37b4cb542f1509ff6fae250a37da0` |

## Confirmed call path

### Open gateway selection

`z13.start(context, backend)` selects `OpenLocalApiGateway.start(appContext, backend)` when the build is Open and `BuildInfo.LOCAL_API_INCLUDED == false`. The Open gateway is therefore a real direct-Java HTTP implementation, not merely an unused duplicate of the payload gateway.

### Stable backend contract

`z2.Backend` exposes:

- `isReady()`
- `readinessDetail()`
- `complete(CompletionRequest, DeltaSink)`

The `CompletionRequest`, `CompletionResult`, and `DeltaSink` contracts are in the main DEX. This is the intended boundary between HTTP/protocol handling and the native host adapter.

### Current hard stop in Main

The backend registered by `Main.startz1(...)` currently has two Open-specific hard stops:

1. `isReady()` begins with `BuildInfo.LOCAL_API_INCLUDED && ...`. For the Open path selected by `z13`, `LOCAL_API_INCLUDED` is false, so the backend can never report ready, regardless of whether the host loader, transport, or PoW manager has been observed.
2. `complete(...)` explicitly throws HTTP 503 `native_adapter_unavailable` for Open before it delegates to the module execution method.

This is intentional fail-closed behavior, but it means transport/PoW object capture alone cannot make the Open gateway ready.

### Current execution method is payload-dependent

`Main.executeLocalApiCompletion(...)` delegates to `HookAccountLoginSecurity.INSTANCE.executeLocalApiCompletion(...)`.

That method currently resolves `ds.dsh.DeepSeek.z18` from the protected payload and reflectively invokes its `executeLocalApiCompletion(CompletionRequest, DeltaSink)`. If the payload class or method is absent, it throws 503 `engine_missing`.

Therefore, simply removing the Open readiness/503 guards would not create a working Open adapter. It would only move the failure from the explicit Open guard to the missing `z18` engine lookup. Do not make that change as a shortcut.

## Prior code289 callback-observation handoff

The previous runtime-diagnostic handoff records these concrete callback implementation classes discovered at the `y60.b(fl4, yb3)` boundary:

- `cr9`
- `ec0`
- `s5`
- `qa`
- `k41`
- `x0`

It also records `zgb` as an observed related type. These names are runtime observations, not semantic classifications. In particular, do not infer that `cr9` or `ec0` is a text delta, that `s5` is a terminal event, or that `zgb` is a collector solely from the names.

The handoff lists five diagnostic session logs, with one reported as 1,676 lines / approximately 532 KB. The raw session logs were not available in the current Library retrieval results during this audit, so the exact callback method descriptors, callback-enter/exit ordering, and event payload schemas have not been independently re-parsed in this commit.

The next evidence pass must extract from the complete session log:

1. `continuation-enter` callback0/callback1 implementation names, identity hashes, argument type lists, field-name/type shapes, and stack.
2. Every `callback-class-observed` record and its installed method count.
3. `callback-enter` / `callback-exit` / `callback-throw` records grouped by full method descriptor and receiver identity.
4. Per-method aggregate invocation/failure counts and timing.
5. Correlation between those records and the gateway's `OPEN_NATIVE_API_TRACE` lifecycle, without logging or exposing prompt/response contents.

Until that extraction is complete, the class list narrows the investigation but does not establish response event semantics or authorize a functional Hook.

## What is already present

- Open direct-Java HTTP server and request parsing.
- OpenAI-compatible `/v1/models` and `/v1/chat/completions` routes.
- Stable `z2` backend/request/result/delta contracts.
- Request validation, Bearer authentication, bounded request handling, and fail-closed backend readiness.
- Non-stream and SSE response formatting at the gateway layer.
- Main-side capture/diagnostic state for the host loader, `liveR92`, and `liveQ71`.
- Code289 diagnostic evidence recorded in project status for the request/transport boundary: `ly1 implements wu1`, `zn3.o(wu1, Long)` enters `jw3.g(wu1, Long)`, and both return a `y60` object. The recorded evidence does not establish that the HTTP request was sent, the response arrived, or response events were decoded and collected.

## SSE gateway lifecycle notes

A source review of `OpenLocalApiGateway.streamCompletion(...)` confirms:

- The gateway writes HTTP 200 / `text/event-stream` headers before calling `Backend.complete(...)`. Any later upstream failure must therefore be represented as an SSE error event; it cannot be converted back into an HTTP 5xx status.
- Text and reasoning are emitted as separate delta fields. The gateway emits a final finish-reason chunk and `[DONE]` only after a non-null result and only while the client is not marked disconnected.
- The gateway marks the sink cancelled when an SSE write throws. A disconnect during a silent upstream period is not independently detected by this method before the next attempted write.
- `DeltaSink.onUpstreamStarted()`, `isSatisfied()`, and `publishedTextSnapshot()` are available in the shared contract but are not implemented by this gateway sink.

Adapter implications: enforce `CompletionRequest.deadlineAtMs` independently of client writes; propagate cancellation into the actual upstream coroutine/job; preserve exactly-once Flow collection and cancellation; and define the semantics of `onUpstreamStarted()` before using it for first-token timing. Do not treat a successful HTTP 200 SSE header as proof that native generation started.

## Probe implementation audit (schema 4)

A follow-up source inspection of `OpenNativeCompletionProbe.java` confirms that the current diagnostic implementation is materially more complete than a class-name-only inventory:

- The probe installs pass-through observers on the verified `zn3.o(ew0, Long)` / `jw3.g(ew0, Long)` transport boundaries only after explicit diagnostic enablement. The original host call is still executed exactly once through `chain.proceed()`.
- When a transport call returns, the probe attempts to install a pass-through observer on the uniquely selected `y60.b(..., ...)` continuation method. Ambiguous or unavailable method resolution is logged and is not guessed.
- At the continuation boundary it records callback argument identities, argument runtime types, shallow field-name/type shapes, thread identity, and stack. It then hooks concrete callback implementation methods on the runtime class and its superclass chain.
- For each hooked callback invocation it records the full method descriptor, receiver identity, event sequence number, thread, argument runtime types, stack, exit/throw outcome, duration, and exception class. Aggregate per-method invocation/failure/timing counters are also emitted.
- The observer excludes static, abstract, bridge, and Object identity methods. It does not read callback argument values or instance field values. In particular, it cannot directly distinguish two callback invocations whose semantic event is encoded only in a string/object payload value.
- Every observer line is synchronously appended and flushed to the app-private `files/dsh_open_native_diagnostics/open-native-<epoch>.log` session file. The in-memory preview is capped at 48,000 characters, but the persisted file is the complete capture unless the log writer reports `persistent-log-write-failed`. The status output includes the persistent file path and callback aggregate summary.
- `OPEN_NATIVE_API_TRACE` events use the same persisted session log, allowing time-based correlation with gateway request lifecycle records without capturing prompt/response content.

### Consequence for the pending log analysis

The requested raw session log is not needed to discover whether the probe *can* record callback descriptors and call order: the current source proves that it can. The log is still indispensable to determine which concrete descriptors actually fired, their order/frequency, and whether they correlate with a real API request. Even after parsing it, callback semantics must remain unclassified when the only evidence is a method name, parameter type, or opaque object shape; a real decoder/collector contract still needs a safe semantic discriminator or additional APK/runtime evidence.

The capture itself has a diagnostic cost: each event is synchronously flushed to disk and also forwarded to the module log. Keep the probe explicitly user-enabled and do not leave it running as a normal production hot path.

## Actual implementation gap

The missing component is not another HTTP route. It is an Open-only implementation of the `z2.Backend` contract that does not depend on `z18`.

The adapter must establish and implement, with exact code289 APK/runtime evidence:

1. Request DTO construction, including the exact required fields and session/parent-message semantics.
2. Challenge/PoW lifecycle and account-bound authentication, without relying on Closed payload helpers.
3. The exact native transport invocation and correct coroutine/continuation semantics.
4. Response event schema and the real consumer/collector path.
5. Mapping of text, reasoning, finish reason, upstream errors, and cancellation into `z2.DeltaSink` / `z2.CompletionResult`.
6. Per-request isolation, concurrency limits, deadlines, disconnect cancellation, and cleanup.
7. Explicit version/channel capability gating. Code289 evidence must not silently authorize code287 or code290.

The observed `y60.b(fl4, yb3)` callback boundary is not by itself proof of a Flow collector or response event schema. Do not proxy or replace the host's returned object, subscribe a second time, invoke guessed methods, or infer callback semantics from obfuscated names.

## Required next engineering batch

### A. Separate the adapter from the Closed payload

Introduce an Open-only adapter implementing `z2.Backend` (or a dedicated native engine injected into the existing backend). Keep the gateway independent of host symbols. The adapter must default to not-ready until every required capability has been verified.

### B. Split capability readiness

Do not use `BuildInfo.LOCAL_API_INCLUDED` as a proxy for native adapter readiness. Replace the current all-or-nothing build flag only after the Open adapter exists, with explicit readiness checks for:

- exact supported host/channel/version;
- host ClassLoader identity;
- request factory / serializer contract;
- challenge/PoW capability;
- transport capability;
- response collector/decoder capability;
- cancellation and cleanup capability.

A partial capability set must remain not-ready and return 503.

### C. Implement from proven contracts

Build a version-specific code289 implementation first. Keep request construction, PoW, transport, event decoding, and cancellation as separate components with independently testable contracts. Do not generalize the adapter to code287/code290 until each has independent APK-derived descriptors and device evidence.

### D. Validation gates

1. Pure JVM tests for DTO mapping, readiness, error mapping, exactly-once invocation, and cancellation state.
2. Android Open build and APK structural checks.
3. Static audit that Open adapter has no reference to `z18` or Closed-only payload classes.
4. Controlled code289 device test with a non-sensitive prompt.
5. Verify both non-stream and SSE paths, including reasoning, error, disconnect, and cancellation.
6. Only then mark the Open native adapter ready. Until then, keep 503.

## Corrected status

- **HTTP/protocol layer:** implemented at source level.
- **Open gateway selection:** implemented at source level.
- **Stable backend contract:** implemented at source level.
- **Native host observations:** partial code289 evidence exists.
- **Open native Completion adapter:** not implemented.
- **End-to-end model generation through Open Local API:** not verified and must not be claimed.
- **This audit:** documentation-only; no runtime behavior changed.
