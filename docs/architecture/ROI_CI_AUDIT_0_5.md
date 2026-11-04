# ROI CI Audit Profile 0.5

Date: 2026-11-04
Branch: `work/roi-agent-core-kotlin`

## Policy

Profile 0.5 is a moderate, faster-feedback audit profile. It relaxes only non-critical review friction; it does not waive compilation, executable resolver tests, discovery safety assertions, native ABI/ELF checks, APK integrity checks, or existing host regression suites.

## Changes in this batch

- Added a parallel `roi-fast-audit` job with a 10-minute timeout.
- The fast job syntax-checks and runs the resolver and DexKit discovery regression scripts independently of the full Android/native regression job.
- Added the resolver test script to the full CI shell syntax preflight.
- Connected each bounded DexKit string-anchor candidate to `RoiHookResolver`.
- Added a bounded DEX method-descriptor parser for owner/name/parameter arity/return descriptor; parser evidence is explicitly structural and does not establish semantic equivalence.
- The current adapter deliberately emits only low-confidence host diagnostic, observed string-marker, and unvalidated descriptor evidence. It uses the default discovery-only policy and reports state, score, authorization flag, and reason.
- Extended static discovery checks to assert the resolver connection, descriptor parsing, and fail-closed evidence provenance.
- Fixed a resolver test fixture defect: the `fullEvidence=false` branch previously still supplied the complete evidence set, invalidating the weak-candidate test.

## What profile 0.5 does not do

- It does not turn lint or compile errors into warnings.
- It does not skip native packaging, ABI/ELF checks, APK validation, or full regression tests.
- It does not authorize unknown-host Hook installation.
- It does not treat string anchors or a method descriptor as proof of semantic equivalence.
- It does not claim real-device runtime verification.

## Current implementation boundary

R1 has started: DexKit candidates now enter the resolver as explicitly weak, unresolved evidence. Real method-shape analysis, call-graph correlation, runtime invocation capture, exact APK/DEX fingerprinting, adapter approval, installation verification, and rollback remain outstanding.

## Audit outcome

Source changes are committed to the working branch. CI execution status must be read from the GitHub Actions run associated with the resulting commit; an empty status response is not a pass.

## R1.2 — bounded method structure and call neighborhood

- DexKit 2.3.0 MethodData is used through documented getters to observe raw access flags, parameter type names, return type name, opcode names, outgoing invokes, and incoming callers.
- Each candidate emits at most 12 outgoing and 12 incoming relation descriptors, 16 parameter type names, and 8 opcode names. Omitted counts are reported explicitly.
- The resulting edges are one-hop static DEX references, not proof that a method executes on a live request path. They are recorded as unconfirmed CALL_GRAPH evidence and cannot satisfy the resolver's confirmed-evidence threshold.
- No host class is loaded and no host method is invoked. The discovery policy remains discoveryOnly.
- The static regression assertion was corrected to match the actual descriptor parser contract (0.55 only when descriptor syntax validates), and new assertions cover bounded call relations and method structure accessors.

## Verification boundary

The source and static assertions have been updated on the working branch. The latest queried commit status returned no status entries; this is pending / unverified, not a CI pass. No local Gradle build or device Hook test is claimed.

## R1.3 — bounded candidate graph aggregation

- Added a pure-Java candidate graph builder scoped to one DexKit bridge scan. Repeated string-anchor observations for the same method descriptor merge into one node while retaining their anchor set and unioning bounded invoke/caller references.
- Static edges are oriented caller → callee. An invoke relation contributes candidate → target; a caller relation contributes caller → candidate. Duplicate edges are deduplicated even when the edge cap has already been reached.
- The graph reports observed candidates, unique nodes, duplicate observations, retained edges, unresolved external references, connected components, and omitted node/edge counts.
- Graph limits are 128 nodes and 256 retained edges per bridge scan; diagnostic output emits at most 32 edges. The graph never loads host classes or invokes host methods.
- Added standalone Java regression coverage for anchor deduplication, edge direction, duplicate edges, edge/node caps, unresolved references, cycles, and disconnected components. The fast audit and full regression workflow both invoke the new test script.
- Graph connectivity is descriptive static DEX structure only. It does not classify Request/Transport/Response roles, prove a live request path, or authorize Hook activation.

## R2.0 — conservative network role hints

- Added a separate `RoiSemanticRoleHints` classifier over a candidate's directly invoked method descriptors. It recognizes a narrow set of explicit OkHttp / Retrofit / URLConnection / Gson / Moshi / Jackson / JSONObject references as request-builder, transport, or response-parser hints.
- The classifier emits only positive hints and matched descriptor samples; unknown or obfuscated references remain unresolved. A method may carry multiple hints, and absence of a hint is not evidence that a role is absent.
- Hints are reported per candidate with provenance `direct_static_invoke_reference`. They are not fed into `RoiHookResolver`, do not change candidate role from `unresolved`, and cannot authorize activation.
- Added isolated JVM tests for request-builder, transport, response-parser, and unknown-reference cases. Added the test to both the parallel ROI fast audit and full regression job.

## R2.1 — bounded payload-free runtime invocation recorder

- Added a standalone Java recorder for observations emitted only by an explicitly trusted existing callback. The recorder does not install hooks, load host classes, invoke host methods, or infer runtime evidence from DEX hints.
- Events contain only an opaque request correlation key, approved host fingerprint, candidate method descriptor, phase, timestamp, thread ID, and trusted source marker. Prompt text, request/response bodies, headers, authorization values, and tokens are not accepted or stored.
- Recording is disabled by default and requires a known/approved host plus explicit trusted-observation enablement; read-only policy rejects all events. Unknown versions therefore remain read-only.
- The synchronized buffer is bounded by capacity and TTL. Correlation permits thread changes for asynchronous transport, but requires the same opaque request key and host fingerprint. Only observed REQUEST_BUILT + TRANSPORT_ENTERED + RESPONSE_RECEIVED phases produce a confirmed request/transport/response chain.
- RESPONSE_PARSED is supplemental and is not required for the minimum chain. Missing phases remain unconfirmed. The recorder is not yet connected to a host callback; no live runtime evidence is claimed.
- Added standalone JVM tests for read-only/unknown host rejection, cross-thread correlation, missing phases, capacity, TTL, invalid/untrusted events, and concurrent recording. The test script is wired into ROI fast audit and full regression.
- Verification remains pending until the associated GitHub Actions run is inspected; no local or device execution is claimed.


## R2.2 follow-up — trusted callback connection (2026-11-05)

- Connected the bounded recorder to two already-existing callbacks: the non-synthetic completion-request constructor hook and the legacy transport hook. No new host Hook target was introduced.
- A successful request-constructor return emits REQUEST_BUILT using the actual reflected constructor descriptor. The transport callback emits TRANSPORT_ENTERED only when the exact same request object is found in an IdentityHashMap correlation table; correlation uses a process-local monotonic opaque key.
- Observation is disabled by default. It requires a maintained host adapter and the explicit file switch `/data/data/com.deepseek.chat/files/deekseep_roi_runtime_observation_enabled`. Unknown/unmaintained hosts remain read-only. The host identity string is an adapter/channel label, not a cryptographic APK fingerprint. The opt-in file is checked during process initialization, so it must exist before starting/restarting the host process.
- The observer records no URL, request/response body, headers, prompt, authorization value, or token. Observer exceptions are isolated from constructor and transport execution.
- The legacy network-payload installation is skipped for the 2.7.x family. Therefore those builds do not gain a transport observation from this path. The expert Flow collect hook is limited to registered expert-image relay flows and is not treated as a general response callback.
- RESPONSE_RECEIVED and RESPONSE_PARSED are not emitted by this integration. The recorder can currently show a partial build→transport trace only; it cannot confirm a complete request/transport/response chain.
- Added a regression assertion that same request keys from different host identities never merge, plus static integration guards for opt-in, identity correlation, constructor callback, transport callback, and initialization wiring.
- CI and device verification for these commits remain pending until the actual workflow result and controlled device evidence are available. No local build or real-device capture is claimed.


## R2.3 — correlated Flow emission observation (2026-11-05)

- The legacy network-payload path already installs a narrowly scoped collect hook on the host's concrete completion Flow class (b41 / code257 remap d81), with the collector interface (q03 / code257 remap ja3). It was previously used only to detect registered Expert image relay Flows.
- Extended that existing collect callback without adding another host Hook target. After the transport method returns its original Flow, the observer associates that exact Flow object with the request's opaque key. At collect time, only an identity-matched Flow is eligible for observation.
- For a non-relay Flow, the observer wraps the collector only when the host collector type is an interface and the original collector is an instance of it. The proxy delegates to the original collector; after a successful emit call it records RESPONSE_RECEIVED with the actual reflected emit descriptor. It never reads, serializes, logs, or stores the emitted value.
- If the collector type is not an interface, proxy creation fails, or the Flow identity does not match, the original collector is passed through unchanged and no response event is recorded. Expert image relay Flows keep their existing path and are not attributed to the original request Flow.
- This event means at least one response item was successfully emitted into the correlated collector; it does not mean the stream completed. RESPONSE_PARSED is still not emitted. A complete request→transport→response-item chain can now be observed on legacy versions where this exact Flow collect hook is installed, but parser-level confirmation and stream completion remain open.
- 2.7.x remains outside this legacy transport/collect path and does not gain this response observation.
- The recorder still stores only opaque request keys, approved adapter/channel identity, phase, timestamps, thread IDs, and method descriptors. No request/response payload, URL, headers, prompt, authorization value, or token is captured.
- Static CI guards now require transport-result Flow association, interface-guarded collector wrapping, and the successful emit observation call. CI and real-device verification remain pending; no local build or live capture is claimed.

- The collector proxy changes the collector object's identity for that single correlated collection. Although it preserves the interface and delegates every method, host Flow identity/context assumptions have not yet been validated on-device; keep observation opt-in until controlled runtime verification confirms no regression.


## R2.4.1 — suspend emit evidence guard (2026-11-05)

- 审计 R2.3 collector proxy 后发现 Kotlin suspend 方法的反射调用可能返回 `COROUTINE_SUSPENDED`；这只表示控制权已挂起，不表示响应项已经成功交付。
- 观察器现检查实际 emit 方法参数。若参数包含 `kotlin.coroutines.Continuation`，不再仅凭反射调用返回就记录 `RESPONSE_RECEIVED`。在尚未安全观察 continuation 成功恢复之前，宁可不产生响应证据。
- 对无 Continuation 参数的同步 emit，仍仅在原 collector 委托调用成功返回后记录；异常继续原样传播，不产生事件。
- 这会使标准 Kotlin suspend Flow 的响应阶段保持未确认；R2.3 的“普通聊天响应项可观察”结论应收窄为：Flow identity 关联与 collector 代理接线已实现，但异步 emit 成功交付尚未被可靠证明。
- 未实现 RESPONSE_PARSED，也未证明流完成。代理改变 collector identity 的真机兼容性风险仍然存在，开关继续默认关闭且必须显式 opt-in。
- 新增静态回归断言，要求 Continuation guard 保留。尚未运行本地测试、Android 构建或真机验证；CI 状态需以本提交对应的 Actions 结果为准。

- 进一步收紧：若 collector 接口暴露带 `kotlin.coroutines.Continuation` 参数的 `emit`，观察器现在直接原样传递 collector，不创建代理。当前实现没有包装 continuation 的成功恢复语义，因此也不应为了一个无法确认的事件改变 collector identity。


## R2.4.2 — parser-correlation audit (2026-11-05)

- 审计现有 `installServerCapture`：SSE 事件构造器只接收 `String event` 与 `String data`，回调没有 request object、opaque request key 或 Flow identity。即使它位于 SSE 解析链，也不能安全归因到某次请求，因此不记录 `RESPONSE_PARSED`。
- 审计 `hookFinalMessageApply` / `hookFinalMessageMerge`：可见的是会话对象、SID、消息对象/消息 ID 和替换/合并时机；它们不是请求身份。历史同步、重试、编辑重发及同会话并发都可能导致误关联，不能用“最近请求”或 SID 推断 request key。
- 因此当前没有可安全接入的 parser-level callback。保留 `RESPONSE_PARSED` 未确认；不增加新的宿主 Hook target，也不把 SSE 构造或 UI 消息落库当成请求解析证据。
- 静态回归增加禁止在当前 HookChatPipeline 中无关联地产生 `RESPONSE_PARSED` 的检查。下一步需要找到同时持有精确 Flow/request correlation 的解析回调，或让既有请求 Flow collector 的下游回调携带可验证的同一身份；否则维持未确认。
- 未运行本地脚本、Android 构建或真机验证；GitHub Actions 对最新提交仍需核验。


## R2.5.1 — ordered evidence tiers (2026-11-05)

- 修正 recorder 原先仅按“阶段是否出现”确认请求链的问题。现在必须存在时间戳非递减的 `REQUEST_BUILT → TRANSPORT_ENTERED → RESPONSE_RECEIVED` 序列，才返回 `confirmsRequestTransportResponse()`。
- `RESPONSE_PARSED` 单独提供 `confirmsParsedResponse()`，且必须在已确认的响应接收阶段之后出现；解析事件先于响应接收时不构成解析确认。
- 同毫秒事件允许非递减，避免毫秒时钟粒度造成正常快速回调被误拒；这不是严格的因果顺序证明，仍依赖可信 callback 的真实调用顺序与同一 opaque request key。
- 本次只收紧纯 Java recorder 的证据判定，不新增宿主 Hook，不产生任何新的 `RESPONSE_PARSED` 事件，也不宣称流完成、成功终止或取消状态已被观察。
- 新增乱序、解析先后关系和同毫秒阶段测试，并在脚本中增加静态断言。当前工具环境未执行该脚本、Android 构建或真机验证；Actions 结果待核验。


## R2.5.2 — sanitized runtime evidence snapshots and exact candidate matching

- Added a read-only `snapshotEvidence()` surface to the existing opt-in runtime observer. It is unavailable unless the maintained host adapter is approved and the explicit runtime-observation enable file was present at initialization.
- The recorder exposes immutable per-descriptor evidence entries only: adapter identity label, exact method descriptor, phase, and ordered-chain flags. It does not expose request keys, request/response content, URLs, headers, prompts, or credentials.
- Evidence remains bounded by the existing 512-event capacity and 120-second TTL; snapshots prune expired events before returning.
- DexKit reports a separate runtime-evidence section. Matching requires both exact method-descriptor equality and exact adapter-identity equality. No method-name, prefix, approximate descriptor, or cross-host matching is permitted.
- The identity label `adapter:generation:channel` identifies the maintained adapter generation/channel only; it is not an APK or DEX fingerprint.
- Runtime evidence is diagnostic only and is not added to `RoiHookResolver` scoring, role resolution, adapter approval, or Hook activation. Resolver remains discovery-only.
- Tests were added for immutable sanitized snapshots, disabled-policy gating, TTL expiry, and no request-key field exposure; existing cross-host isolation coverage remains.
- Verification boundary: source was updated on `dev`, but this batch has not been run locally or on a device. GitHub CI must be checked for the resulting commit; an empty status/run response is not a pass.
