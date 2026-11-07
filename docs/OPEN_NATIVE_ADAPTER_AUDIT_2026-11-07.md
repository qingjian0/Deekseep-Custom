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

## What is already present

- Open direct-Java HTTP server and request parsing.
- OpenAI-compatible `/v1/models` and `/v1/chat/completions` routes.
- Stable `z2` backend/request/result/delta contracts.
- Request validation, Bearer authentication, bounded request handling, and fail-closed backend readiness.
- Non-stream and SSE response formatting at the gateway layer.
- Main-side capture/diagnostic state for the host loader, `liveR92`, and `liveQ71`.
- Code289 diagnostic evidence recorded in project status for the request/transport boundary: `ly1 implements wu1`, `zn3.o(wu1, Long)` enters `jw3.g(wu1, Long)`, and both return a `y60` object. The recorded evidence does not establish that the HTTP request was sent, the response arrived, or response events were decoded and collected.

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
