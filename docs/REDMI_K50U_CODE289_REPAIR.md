# Redmi K50 Ultra / DeepSeek 2.7.2 code289 repair

## Scope

This change set addresses the device-reported failures from the Redmi K50 Ultra running mainland DeepSeek 2.7.2 (PackageManager versionCode 289), with LSPosed installed.

It is intentionally scoped to `dev`; `main` is unchanged.

## Findings from uploaded device logs

- The host was identified as `2.7.2/code289-cn`, and the ROI host identity recorder independently captured PackageManager versionName `2.7.2`, versionCode `289`.
- The optional Agent tool-log renderer attempted to resolve the code257-only `mf6` mapping. In the uploaded build the exception escaped the feature-hook step and aborted the remainder of the feature-hook batch. The separate Agent render group also encountered missing `rw2` / `tw2` mappings.
- The OpenAI gateway rejected `stream_options.include_usage=true` even though the streaming implementation already emits an estimated-usage chunk.
- The earlier OpenAPI request log reports `unsupported_feature` for `tools`. Current source already routes Chat Completions function tools through `OpenAiToolBridge.chatPlan`; this log is therefore treated as evidence from an older installed APK, not proof that the current source lacks the tool bridge. Streaming tool calls remain explicitly unsupported.
- A diagnostic-copy action attempted to place a 1,095,388-byte report on the Android clipboard and triggered `TransactionTooLargeException`.
- The floating overlay companion accepted only code249/code257 host identities. It did not recognize code289/code290, and the permission route lacked MIUI / HyperOS-specific fallback.
- The target activation heartbeat was gated behind `PROTECTED_BUILD`; Open/universal builds therefore could load hooks without reporting target injection to the launcher activation page.

## Changes

1. Treat the Agent tool-log renderer as best-effort, and isolate every optional Agent response / patch / Markdown / status / attachment renderer so a missing `mf6`, `rw2`, or `tw2` mapping cannot prevent later independent render hooks or unrelated feature hooks from installing. Each optional render boundary is attempted once per host process to avoid duplicate partial registrations.
2. Report target-process injection heartbeat in Open/universal and protected builds. The module provider still validates the caller UID against `com.deepseek.chat`.
3. Recognize exact 2.7.2 versionCode 289/290 identities in the overlay companion and permit the 2.7.2 version name in the host version allowlist.
4. Add MIUI / HyperOS overlay permission editor fallback, Android overlay settings fallback, Xiaomi per-app power manager / autostart routes, Android per-package battery-exemption routes, and app-details fallback.
5. Declare `SYSTEM_ALERT_WINDOW` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the universal, legacy, and launcher manifests.
6. Bound diagnostic clipboard payloads to 64 KiB and keep full-report export as the lossless path.
7. Accept boolean `stream_options.include_usage` values because the SSE implementation already supports estimated usage output.
8. Extend the Local API regression script to guard these behaviors and the existing Chat Completions tool bridge.

## Deliberate boundaries

- No speculative code289 obfuscation mapping is added for `mf6`, `rw2`, or `tw2`. Those optional render hooks must degrade independently until exact code289 evidence is reviewed.
- Streaming tool calls remain rejected with a specific unsupported-feature response; no fake tool execution is introduced.
- HTTP listener startup is not treated as proof that the native completion engine is ready. A successful `/v1/models` response does not prove `/v1/chat/completions` can execute. If the native backend still reports `engine_missing` / `model_not_ready`, that remains a separate code289 native-engine integration blocker.
- The uploaded `tools` 400 trace came from a prior runtime build; install the APK produced from this branch's CI before comparing that exact result again.
- **Important Open-edition blocker:** `module/src/ds/dsh/DeepSeek/z14.java` is the Open-edition stub: `available()` returns false and `payloadClass(...)` returns null. `HookAccountLoginSecurity.executeLocalApiCompletion` therefore returns `503 engine_missing` in this edition. The HTTP gateway and protocol validation can be repaired here, but actual Local API generation requires a separately implemented and reviewed Open native execution engine; this PR does not pretend otherwise.
- MIUI / HyperOS settings intents vary by ROM build. The implementation tries OEM routes and then Android fallbacks; final permission-page behavior must be verified on the physical Redmi K50 Ultra.
- No physical-device result is marked PASS by this source-only change. LSPosed injection, overlay authorization, battery policy, API client interoperability, and end-to-end native completion require a new device run.

## Device acceptance after CI artifact install

1. Confirm launcher shows DeepSeek target injection after opening DeepSeek once.
2. Confirm `dsprobe.log` reports `activation heartbeat accepted by module provider`.
3. Enable Local API floating keeper; confirm the `z20` trampoline opens the MIUI permission editor for the module app package, then confirm the overlay appears after permission is granted.
4. Open battery settings; verify module and host background-power / autostart settings are reachable.
5. Test `GET /v1/models`.
6. Test Chat Completions with a plain text request, then a non-streaming function-tool request.
7. Test streaming with `stream_options.include_usage=true`; verify final estimated usage chunk.
8. Test streaming tool call and verify the documented unsupported response rather than a false success.
9. Copy the native diagnostic report and verify no clipboard transaction-size crash; use file export for the full report.
10. Verify ordinary DeepSeek chat still works and inspect logs for optional Agent renderer failures without feature-batch aborts.


## code289 原生 Completion 调用链（静态证据与运行时观察分层）

本节将现有 code289 深挖结果汇总到修复主文档。静态 Smali 结论与被动运行时日志分别标注；不得把 Hook 观察到对象经过某个边界，等同于模块已经可以安全主动调用该边界。

### A. 请求构造链（静态 Smali）

```text
nn0.d(ys, ...)
  -> new mw1(nn0, ys, ...)
      -> mw1.y(...)
          -> new ly1(...)
          -> zn3.o(ly1, null)
              -> jw3.g(ly1, null)
                  -> y60
```

- `nn0.d` 是 suspend 状态机入口，构造并捕获 `mw1` 所需上下文，不是同步 DTO factory。
- `mw1.y` 把捕获的宿主状态整理成 `ly1` 请求对象，再调用 `zn3.o`。
- `zn3.o` 将请求委托给其 `jw3` 字段；`jw3.g` 根据 `wu1` 请求类型分派。普通 completion 命中 `ly1`，读取其序列化配置和超时配置，并构造 `/api/v0/chat/completion` 请求路径。
- `jw3.g` 返回 `y60`，不是已完成的文本响应。

### B. Session Bootstrap（静态 Smali）

```text
fx1.m(Continuation)
  -> 预取队列 pollFirst()
     -> 命中：构造 ys / tn2 / pt7
     -> 未命中：ru7.N(...) -> mb0
          -> pi.c : zn3
          -> ch2.b(Continuation)
              -> rz0.y()
                  -> /api/v0/chat_session/create
              -> bi5 网络执行/响应解码
          -> zs9.a(true, Continuation)
              -> business code == 0
                  -> j41 解码 ok2 / ss9
                      -> new ys(...)
                      -> tn2(ys, false)
                      -> pt7(...)
```

- 已有 17 项 Smali 静态断言覆盖该 miss 回退与业务成功分支。
- `rz0` 分支构造的请求 builder method 初值为 GET，当前分支未显式改写；最终 method 是否被后续网络拦截器改写仍未验证。
- 不能只复制 URL 和 DTO 绕过 `bi5` 网络栈、认证拦截器、设备上下文或挑战流程。

### C. Transport / Flow / reducer 边界

```text
ly1 : wu1
  -> zn3.o(wu1, Long) : y60
      -> jw3.g(wu1, Long) : y60
          -> y60.b(fl4 collector, yb3 continuation)
              -> upstream Flow / callback collection
                  -> dv1 event
                      -> r3.c(dv1, yb3)
                          -> DeltaCommand / named-event reducer
                              -> doc state tree
```

- `y60` 实现 `el4`；其 `b(fl4, yb3)` 才是 collector 与 suspend continuation 边界。
- `r3.c` 是事件 reducer。必须保留命名事件、DeltaCommand 反序列化、BATCH 顺序、失败传播和状态隔离；不能把 SSE data 当普通字符串拼接。
- 宿主使用 Kotlin suspend/Continuation。模块侧必须结构化取消：客户端断开、超时、宿主异常要传递到对应任务；不得在 HTTP accept/UI 线程使用 `runBlocking`。

### D. 被动运行时观察（用户上传的 code289 日志）

日志 `open-native-1793943559724.log` 显示：

- `zn3.o` 与 `jw3.g` 均被观察到，参数是同一个 `ly1` 对象，返回 `y60`。
- `y60.b(fl4, yb3)` 实际进入过协程收集路径；日志捕获到多个 callback 实现类（包括 `cr9`、`ec0`、`s5` 及其配套 continuation 类）。
- 观察器只记录回调方法签名、类型形状、线程、调用栈和结果类型；没有记录消息正文、凭据、Cookie 或 Authorization header。
- 日志包含多个 Flow / callback 实例，不能把不同实例合并为同一个请求生命周期，也不能仅凭类名推断每个 callback 的业务角色。

### E. 当前实现缺口与安全边界

- Open 版 `z14.java` 的 `available()` 固定返回 false，`payloadClass(...)` 固定返回 null；它不提供 Closed-only `z18` 或 `z12decoder`。
- 当前 `HookAccountLoginSecurity.executeLocalApiCompletion` 只会通过 `z14.payloadClass(..., "ds.dsh.DeepSeek.z18")` 寻找执行入口；找不到时按设计返回 HTTP 503 `engine_missing`。
- `NativeApiPatchDecoder` 也依赖 `z12decoder`，Open stub 下不会解码。不能把空 Delta 当成成功响应。
- 静态调用链和被动 Flow 观察已足以指导后续适配器设计，但还没有证明模块可以安全创建/复用宿主 Session、构造 `ly1`、启动真实 Flow、处理所有事件并正确取消。
- 因此本 PR 继续保持 fail-closed：协议与 HTTP 层可测试；原生 completion 不伪造成功。下一阶段需补齐宿主依赖获取、Session 生命周期、认证/challenge、Flow collect、事件 reducer、取消与错误映射，并通过设备端真实请求验收。

