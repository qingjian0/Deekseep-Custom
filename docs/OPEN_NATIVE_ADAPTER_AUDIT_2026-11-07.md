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

### Previous Main gate and current request dispatch

The earlier implementation had two Open-specific preflight blocks: `isReady()` required `BuildInfo.LOCAL_API_INCLUDED`, and `complete(...)` threw `native_adapter_unavailable` before reaching the execution facade. These edition-level blocks have now been removed on `dev` per the user's explicit direction.

Current behavior:

1. For Open builds without the bundled Local API payload, `isReady()` returns true so the HTTP surface can dispatch requests.
2. `Backend.complete(...)` no longer throws `native_adapter_unavailable` before execution.
3. The request reaches `Main.executeLocalApiCompletion(...)` and then `HookAccountLoginSecurity.INSTANCE.executeLocalApiCompletion(...)`.
4. That facade still resolves `ds.dsh.DeepSeek.z18` from the protected payload and invokes `executeLocalApiCompletion(CompletionRequest, DeltaSink)`. If absent, it reports `engine_missing` for that request.

This is the requested open-dispatch policy: API exposure is not blocked by a global readiness gate, and unsupported native execution is reported by the execution path. It does not claim that the native adapter has been implemented.

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

- The probe installs pass-through observers on the verified `zn3.o(wu1, Long)` / `jw3.g(wu1, Long)` transport boundaries only after explicit diagnostic enablement. The original host call is still executed exactly once through `chain.proceed()`.
- When a transport call returns, the probe attempts to install a pass-through observer on the uniquely selected `y60.b(..., ...)` continuation method. Ambiguous or unavailable method resolution is logged and is not guessed.
- At the continuation boundary it records callback argument identities, argument runtime types, shallow field-name/type shapes, thread identity, and stack. It then hooks concrete callback implementation methods on the runtime class and its superclass chain.
- For each hooked callback invocation it records the full method descriptor, receiver identity, event sequence number, thread, argument runtime types, stack, exit/throw outcome, duration, and exception class. Aggregate per-method invocation/failure/timing counters are also emitted.
- The observer excludes static, abstract, bridge, and Object identity methods. It does not read callback argument values or instance field values. In particular, it cannot directly distinguish two callback invocations whose semantic event is encoded only in a string/object payload value.
- Every observer line is synchronously appended and flushed to the app-private `files/dsh_open_native_diagnostics/open-native-<epoch>.log` session file. The in-memory preview is capped at 48,000 characters, but the persisted file is the complete capture unless the log writer reports `persistent-log-write-failed`. The status output includes the persistent file path and callback aggregate summary.
- `OPEN_NATIVE_API_TRACE` events use the same persisted session log, allowing time-based correlation with gateway request lifecycle records without capturing prompt/response content.

### Consequence for the pending log analysis

The requested raw session log is not needed to discover whether the probe *can* record callback descriptors and call order: the current source proves that it can. The log is still indispensable to determine which concrete descriptors actually fired, their order/frequency, and whether they correlate with a real API request. Even after parsing it, callback semantics must remain unclassified when the only evidence is a method name, parameter type, or opaque object shape; a real decoder/collector contract still needs a safe semantic discriminator or additional APK/runtime evidence.

The capture itself has a diagnostic cost: each event is synchronously flushed to disk and also forwarded to the module log. Keep the probe explicitly user-enabled and do not leave it running as a normal production hot path.

## Recovered code289 evidence from the apps archive

The earlier statement that the raw code289 log was unavailable is superseded. The user's evidence repository `qingjian0/apps` contains the full source log and a focused evidence archive:

- `deekseep_vision.log`: blob `dd7b52291befe7052db8c6f7632e39f033e079de`, 6,440,972 bytes, 19,747 lines.
- `deepseek-sse-hook-evidence.zip`: blob `2764d640be7252c02d4f3527018e6bdd91992765`, 329,086 bytes.
- `deepseek-2-7-2.apk`: 22,205,160 bytes; Git blob `b94ed9c0b2ea38cb2f50948b09ba20cf9b22575e`. Its binary body was not returned by `fetch_file`, but the `apps` repository's successful code289 Actions run `42342447740` produced the full static audit artifact, which has now been downloaded and unpacked locally. APK SHA-256: `3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff`.
- `apps/README.md`: blob `49e566eef32ae0b6c89411523a9b9444ca60bff3`, contains the evidence inventory and cross-check statistics.

The complete log blob was retrieved and parsed directly. It is a code289-cn / DeepSeek 2.7.2 runtime capture, timestamp range 02:37:11.549–02:38:55.863. It records four request constructions, four `zn3.o` dispatch entries, four `jw3.g` low-level entries, and 129 `y60.b(fl4,yb3)` continuation entries (87 exits, 42 throws). The four transport dispatches receive `ly1` and pass the same request object through `zn3.o(wu1,Long)` → `jw3.g(wu1,Long)` → `y60`.

### Callback implementation and method evidence

The log confirms 21 concrete callback implementation classes and 35 distinct hooked method descriptors. Roles are observer argument positions, not semantic event names.

| Role | Concrete classes |
|---|---|
| callback-0 | `cr9`, `ec0`, `r3`, `b5`, `qa`, `s5`, `kr1`, `k13`, `hm4` |
| callback-1 | `k41`, `mm4`, `il5`, `qj3`, `x0`, `f0`, `g0`, `je2`, `ir1`, `pg1`, `d0`, `jm4` |

Highest-volume methods (enter + exit + throw records):

| Method descriptor | Records | Enters | Exits | Throws |
|---|---:|---:|---:|---:|
| `qj3.y(Object)->Object` | 5,692 | 2,846 | 2,075 | 771 |
| `qa.a(Object,yb3)->Object` | 2,136 | 1,068 | 1,068 | 0 |
| `qj3.w(yb3,Object)->yb3` | 1,858 | 929 | 929 | 0 |
| `qj3.q(Object,Object)->Object` | 1,836 | 918 | 918 | 0 |
| `k13.a(Object,yb3)->Object` | 1,150 | 575 | 575 | 0 |
| `cr9.a(Object,yb3)->Object` | 1,132 | 566 | 566 | 0 |
| `qa.d(n88,yb3)->Object` | 660 | 330 | 330 | 0 |
| `s5.a(Object,yb3)->Object` | 556 | 278 | 278 | 0 |
| `hm4.a(Object,yb3)->Object` | 458 | 229 | 226 | 3 |
| `b5.a(Object,yb3)->Object` | 450 | 225 | 225 | 0 |

Additional callback-1 exception counts: `pg1.y(Object)` 72, `x0.y(Object)` 39, `f0.y(Object)` 35, `il5.y(Object)` 15, `ir1.y(Object)` 5, and a small remainder across other methods. Across all callbacks, 9,578 enters close exactly with 8,634 exits and 944 throws. Exception types are obfuscated (`e06`, `us4`, `ck7`, `zt2`, `o`); they are not mapped to HTTP or business errors.

### Continuation return evidence

- `y60.b` returned `cd3` 71 times and `zgb` 16 times; it threw `us4` 39 times and `o` 3 times.
- The observed `cd3` method inventory contains `valueOf(String)` and `values()` and its four fields are self-typed / enum-array-shaped. This is strong structural evidence that `cd3` is an enum, but the log does not expose its enum constant names or meanings.
- `zgb` has only a self-typed field and `toString()` in the observed inventory. Do not assign it a success/error/completion meaning from this shape.
- The most frequent continuation pair is `qa` + `f0` (49 entries). Other observed pairs include `ec0` + `mm4` (8), `k13` + `pg1` (5), `cr9` + `k41` (4), and `s5` + `g0` (4).
- Callback-1 exceptions are concentrated in `qj3.y`; 771 of 944 callback throws (81.7%). This is a reverse-engineering priority, not proof that `qj3.y` is the root cause.

### Critical evidence boundary

The code289 log contains no `OPEN_NATIVE_API_TRACE` records and no gateway request IDs. It proves the official app's chat transport/callback behavior for four requests, but it does not prove that a request initiated through the module's Open Local API reaches that same chain. It also logs callback argument types and object shapes, not their values. Therefore it does not yet map callback calls to text/reasoning/error/finish payloads or establish a safe collector/decoder contract.

The focused ZIP evidence package has now been unpacked in-memory: its 37 central-directory entries were parsed and five selected Markdown/JSON/Java files were inflated and read successfully. This corrects the earlier note that the archive had not yet been unpacked. The archive contains **2.7.3 and 2.7.5 evidence only**; it does not contain the 2.7.2/code289 APK, DEX, or callback smali. Its relevant contents include:

- `evidence-map.md`: cross-version request/serializer, HTTP response, SSE parser, stream adapter, event dispatcher, auto-resume, lifecycle telemetry, and PoW semantic mapping.
- `comparison-report.md`: route and marker counts for 2.7.3 vs 2.7.5, with explicit static-analysis limits.
- `2.7.3/smali/` and `2.7.5/smali/`: selected request, response, SSE, PoW, and lifecycle classes.
- `2.7.5/java/PowCalculator.java`: Java/JNI contract `nativeCalculateDeepSeekHashV1Pow(String base, String challenge, long difficulty): long`.
- `native/librscrypto.so`: one native library shared by the two packaged APK evidence sets.

The archive establishes useful **2.7.3↔2.7.5** mappings, not code289 mappings. For example, the 2.7.5 parser is `vs1.z` while the 2.7.3 parser is `jz0.A`; event dispatch and transport symbols also differ. The evidence map documents that the parser recognizes `event: ` and `data: ` lines, emits each data line as an event/data pair, and handles named events including `ready`, `finish`, `update_session` and `update_parent_message`. This is a static contract for those APKs only.

Both archived versions report the same `librscrypto.so` SHA-256 (`4571b9bcf08d5f070a47a495bdcd4c77f4a088feee560ed11701ca93c7b56053`), with JNI export `Java_com_deepseek_crypto_PowCalculator_nativeCalculateDeepSeekHashV1Pow`. The archive's own analysis still leaves exact PoW preimage concatenation and acceptance predicate unproven. Do not assume the same native binary or JNI contract is present in code289 until the 2.7.2 APK is independently checked.

Next static work remains code289-specific: obtain the 2.7.2 DEX/source evidence and inspect `ly1`, `zn3`, `jw3`, `y60`, callback constructors/field writes, and `cd3` enum constants. Keep all 2.7.2 mappings isolated from 2.7.3/2.7.5.


## Code289 static audit recovered from the apps Actions artifact

The earlier statement that code289 DEX had not been extracted is superseded. The apps branch work/deepseek-272-static-audit contains a successful static-audit workflow:

- Run: 42342447740
- Head commit: 7364dd010a4141d63127e52a51e62b2e4e4edecf
- Artifact: deepseek-272-static-evidence
- Artifact ID: 13561683064
- Artifact SHA-256: 923b1f319803f87e27d0f3e44b65cc7148cdcd82c7713826ec9631f0bfb0d61e
- Job conclusion: success; all ten audit steps completed, including APK identity check, JADX, APKTool, descriptor/call-site extraction, semantic-profile validation, and artifact upload.

The artifact contains all-smali.zip plus candidate class descriptors, 3,369 call-site records, method inventory, marker hits, report and summary. The verified APK identity is com.deepseek.chat, versionName 2.7.2, versionCode 289; 3 DEX files, 15,365 smali files, 72,258 method declarations, 23/23 dynamic candidate classes found, 117 candidate methods, and 3,369 candidate call sites. JADX exited with code 3 and 346 diagnostics; the audit correctly treats smali/DEX as authoritative for failed methods.

### Exact request and transport chain

The code289 static evidence now confirms:

1. nn0 is the request repository. Its 11-argument constructor receives a zn3 instance and stores it in field b.
2. nn0.d(Lys,String,Integer,List,boolean,boolean,String,Lada,Lnr2,Continuation) creates the lazy request factory coroutine mw1.
3. mw1.y(Object) constructs the code289 ly1 request model and calls zn3.o(Lwu1,Long).
4. zn3.o delegates to jw3.g(Lwu1,Long).
5. jw3.g selects the endpoint by concrete request model, serializes ly1, adds X-DS-PoW-Response when its PoW field is non-null, and returns a y60 Flow wrapper.
6. The request is lazy: creating y60 does not itself complete the HTTP operation. Its Flow must be collected; the host uses the configured bi5 Ktor client.

The code289 ly1 serializer maps fields to chat_session_id, parent_message_id, prompt, ref_file_ids, thinking_enabled, search_enabled, audio_id, preempt, model_type, and action. Its extra PoW response field is consumed by the transport/header path rather than serialized as ordinary JSON.

### Challenge and PoW

- Challenge endpoint: /api/v0/chat/create_pow_challenge; request DTO ut1 carries target_path.
- Challenge DTO: ms9; serializer keys are algorithm, challenge, salt, signature, difficulty, expire_at, expire_after, and target_path.
- df0.d(String,ms9,Continuation) creates the authenticated PoW solution model gf0.
- The code289 base string is salt + "_" + expire_at + "_"; challenge and difficulty are passed to com.deepseek.crypto.PowCalculator.
- PowCalculator loads librscrypto.so; the JNI symbol is Java_com_deepseek_crypto_PowCalculator_nativeCalculateDeepSeekHashV1Pow.
- The code289 librscrypto.so hash is 4571b9bcf08d5f070a47a495bdcd4c77f4a088feee560ed11701ca93c7b56053, matching the separately audited 2.7.3/2.7.5 library hash.
- nq1 is the Ktor challenge plugin and sets X-DS-Challenge-Response and X-DS-PoW-Response when the relevant values exist.

This is sufficient to plan reuse of the host's configured Ktor client/plugin stack. It is not a reason to duplicate PoW or fabricate challenge headers in the gateway.

### Response, SSE and event reducer

- g0 is the Ktor HTTP response handler. It separates successful streaming responses from non-2xx/JSON error responses and reads x-ds-sse-heartbeat-timeout-secs (default 10 seconds).
- ts1 is the SSE byte-channel reader/parser. It constructs sha(eventName,dataJson), then wraps it as cv1(sha).
- r3.c(Ldv1,Continuation) is a business event reducer. It handles cv1 events and dispatches event names into typed data / patch handling.
- ready uses ChatCompletionReadyEventData with exact JSON keys request_message_id, response_message_id, model_type, and request_content.
- delta uses DeltaCommand / st3(path,operation,value). The first delta initializes the response root through doc.c0(c26); subsequent deltas are applied through doc.W(st3,null), which delegates to the host patch engine ll23.y.
- The audited r3.c reducer explicitly handles ready, delta, hint, title, update_session, and close. It does not establish a code289 finish event branch; do not import the 2.7.3/2.7.5 event list into code289.
- cd3 is the Kotlin coroutine sentinel enum (COROUTINE_SUSPENDED, UNDECIDED, RESUMED), not a response-finish enum. zgb is Kotlin Unit. This resolves the earlier ambiguity from the dynamic continuation log.
- The final response model is gy; xr.B() joins the text exposed by nk0 fragments. This is evidence for visible-text extraction, but it does not yet prove a correct separation between answer text and reasoning content.
- Tool/Search/Find/Open fragment models are present in this APK. They must not be flattened into ordinary text or silently dropped.

The runtime callback observer's y60.b hook is not the parser or business event decoder. The static callgraph now distinguishes the Flow boundary, SSE parser, event wrapper, and reducer. The 21 callback classes from the runtime log are coroutine/lambda implementation classes; their method names alone are not semantic event labels.


### Session and reducer constraints clarified from code289 DEX

- The preferred request entry is the repository factory nn0.d(...), not a bare direct call to zn3.o(...). The factory owns the ys session model, parent-message validation, local user-message creation, model flags, file references and request action.
- nn0.u(ys) checks the session ID against the host's session repository/DAO before treating it as an existing session. A synthetic ys object with a random ID is therefore not equivalent to a registered native session.
- The repository constructor stores its zn3 transport in field b; the corresponding vc3 service is field a.
- The Flow collector can be treated as the boundary for cv1 → sha event/data objects, but a production adapter still needs a request-scoped session lifecycle and a verified way to initialize the host session record. Do not bypass the repository factory until that lifecycle is implemented and tested.
- The static reducer's first-delta root initialization and subsequent patch application are known. Reasoning-fragment classification, finish-reason mapping, server message ID persistence, and cancellation propagation still need explicit implementation evidence.

### Implementation consequences

1. Capture the exact code289 nn0 repository or its zn3 dependency using a version-gated hook; do not borrow code287/code290 transport instances.
2. Prefer the host repository factory when constructing requests because it owns ys session state, parent-message rules, model selection, attachment flags and request action.
3. Reuse the bi5-backed Ktor client and nq1 challenge plugin so challenge caching, PoW and retry remain host-owned.
4. Collect the returned Flow exactly once per request. Do not subscribe twice or proxy/replace the host Flow object.
5. Decode cv1 → sha event/data pairs; apply delta patch operations before extracting text/reasoning.
6. Keep text, reasoning, finish, error, tool/search fragments and cancellation as distinct result paths.
7. Preserve request-scoped cancellation and cleanup; cancelling the HTTP socket alone is not sufficient if the Flow coroutine remains active.

The verified semantic profile is vendored at docs/evidence/deepseek-2.7.2-code289-semantic-profile.json; the apps-side detailed report is docs/deepseek-272-native-callgraph.md on branch work/deepseek-272-static-audit. The full evidence artifact was downloaded in this pass and its nested smali archive was unpacked and inspected. Static success still does not prove runtime Hook installation or end-to-end Local API generation.

## Open API dispatch policy update

The Open edition no longer rejects the entire completion route at the edition-level backend gate:

- `Main.startz1()` now reports the Open backend as dispatchable so `/v1/chat/completions` reaches the execution facade.
- The early `native_adapter_unavailable` throw has been removed from `Backend.complete()`.
- The existing execution facade is still payload-backed and will report `engine_missing` if the `z18` engine is absent. This is now a per-request execution result, not an API-wide preflight block.
- Closed/protected readiness behavior is unchanged.

This change exposes the route as requested; it does not claim that the native Open adapter or end-to-end generation is implemented.

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

Introduce an Open-only adapter implementing `z2.Backend` (or a dedicated native engine injected into the existing backend). Keep the gateway independent of host symbols. The HTTP surface stays exposed; the adapter reports missing capabilities per request and must not fabricate a successful completion.

### B. Split capability readiness

Do not use `BuildInfo.LOCAL_API_INCLUDED` as a proxy for native adapter capability. The Open route is now dispatchable independent of adapter readiness. Track the following capabilities individually and report the first unsupported stage (and any partial progress) in the request result:

- exact supported host/channel/version;
- host ClassLoader identity;
- request factory / serializer contract;
- challenge/PoW capability;
- transport capability;
- response collector/decoder capability;
- cancellation and cleanup capability.

A missing capability is a per-request unsupported result, not a reason to hide the HTTP route.

### C. Implement from proven contracts

Build a version-specific code289 implementation first. Keep request construction, PoW, transport, event decoding, and cancellation as separate components with independently testable contracts. Do not generalize the adapter to code287/code290 until each has independent APK-derived descriptors and device evidence.

### D. Validation gates

1. Pure JVM tests for DTO mapping, readiness, error mapping, exactly-once invocation, and cancellation state.
2. Android Open build and APK structural checks.
3. Static audit that Open adapter has no reference to `z18` or Closed-only payload classes.
4. Controlled code289 device test with a non-sensitive prompt.
5. Verify both non-stream and SSE paths, including reasoning, error, disconnect, and cancellation.
6. Keep the HTTP route exposed throughout development. Report native execution support only after the corresponding capability has actually been implemented and verified.

## Corrected status

- **HTTP/protocol layer:** implemented at source level.
- **Open gateway selection:** implemented at source level.
- **Stable backend contract:** implemented at source level.
- **Native host observations:** partial code289 evidence exists.
- **Open native Completion adapter:** not implemented.
- **End-to-end model generation through Open Local API:** not verified and must not be claimed.
- **Open request dispatch:** implemented at source level; Open readiness no longer blocks the HTTP route, and the old `native_adapter_unavailable` preflight rejection was removed.
- **Current request failure boundary:** the execution facade still depends on `z18`; when absent it reports `engine_missing` for that request.
- **Validation:** source-level assertions were updated, but the shell regression script, Android build, CI, and device execution have not been run in this pass.
