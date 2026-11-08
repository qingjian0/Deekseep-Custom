# code289-cn (DeepSeek Open 2.7.2) 原生 Completion 静态调用链与适配边界

> 状态：静态证据整理完成；Native Completion 尚未接通。本文用于把 APK 级静态证据、当前 ROI 被动观测点和后续 Adapter 设计边界放在同一处。不得将本文视为设备运行验收。

## 1. 证据来源与适用范围

本报告依据同一工作区的 APK 静态审计产物：
- 审计来源仓库：`qingjian0/apps`
- 来源分支：`work/deepseek-272-static-audit`
- 来源报告：`docs/deepseek-272-native-callgraph.md`
- APK：DeepSeek 国内版 Open 2.7.2，`com.deepseek.chat`，versionCode `289`
- APK SHA-256：`3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff`
- 静态产物记录：DEX 3 个、Smali class 15,365、方法声明 72,258、候选方法 117、候选调用点 3,369、arm64-v8a native libraries 16。

符号均按 code289 APK 证据使用；不得将其他版本混淆符号移植到 code289。JADX 有诊断输出，关键结论应以 DEX/Smali descriptor、serializer 和调用点交叉确认。

## 2. 端到端调用链

### 2.1 Request factory 与请求模型

```text
Product/session state (ys)
  → nn0 repository
  → mw1 coroutine factory
  → ly1 request model (implements wu1)
  → zn3.o(wu1, Long)
  → jw3.g(wu1, Long)
  → cold Flow (y60 / el4)
```

- `nn0` 主构造器注入 `zn3`；其请求方法创建 `mw1` 协程工厂。
- `mw1` 根据会话状态、parent ID、附件、thinking/search/audio 等参数构造 `ly1`。
- `ly1` serializer 字段：`chat_session_id`、`parent_message_id`、`prompt`、`ref_file_ids`、`thinking_enabled`、`search_enabled`、`audio_id`、`preempt`、`model_type`、`action`。
- `ly1.k` 不属于 JSON 字段；调用点将其用于 `X-DS-PoW-Response` header。
- `zn3.o` 委托给其 `jw3`；`jw3.g` 按请求类型选择 endpoint/serializer 并构造 Ktor request。
- 已确认 endpoint：`/api/v0/chat/completion`、`/api/v0/chat/regenerate`、`/api/v0/chat/continue`、`/api/v0/chat/edit_message`、`/api/v0/chat/resume_stream`。
- `jw3.g` 返回 cold Flow；仅调用它不会立即发起网络请求，网络执行发生在 collect 阶段。

### 2.2 Challenge / PoW

```text
Ktor client request
  → nq1 ChallengePlugin
  → create_pow_challenge (/api/v0/chat/create_pow_challenge)
  → hq1 / df0.d(...)
  → gf0 AuthedPowSolution
  → com.deepseek.crypto.PowCalculator
  → retry with X-DS-Challenge-Response / X-DS-PoW-Response
```

- Challenge request model：`ut1`，字段 `target_path`。
- Challenge response：`ms9`，包含 algorithm/challenge/salt/signature/difficulty/expire_at/expire_after/target_path。
- PoW solution：`gf0`，由宿主 ChallengePlugin 流程产生。
- Native library：`librscrypto.so`；Java wrapper 调用 JNI `nativeCalculateDeepSeekHashV1Pow`。
- code289 base 构造形式为 `salt + "_" + expire_at + "_"`。
- `nq1` 管理 challenge headers、并发协调、求解与重试。

**接入原则：** 复用已配置的宿主 `zn3/bi5` Ktor client 和 ChallengePlugin；不得在 Local API Gateway 重写或猜测 PoW 算法，也不得伪造 Challenge/Session。

### 2.3 HTTP response、SSE 与业务事件

```text
Ktor response handler g0
  → success: SSE Flow
  → ts1(ByteReadChannel) parses event/data lines
  → sha(eventName, data)
  → cv1(sha)
  → r3.c(dv1, Continuation)
  → event-specific decoder
```

- `g0` 检查 HTTP status / Content-Type；成功流式响应读取 `x-ds-sse-heartbeat-timeout-secs`（默认 10 秒）。
- 错误响应走错误模型解码路径，不得伪装为正常 delta。
- `ts1` 解析 SSE 的 event/data 行，形成 `sha`。
- `r3.c` 是业务事件分发边界；当前静态证据确认的事件包括 `ready`、`delta`、`hint`、`title`、`update_session`、`close` 和 HTTP 错误事件。

### 2.4 DeltaCommand 与状态树 reducer

```text
SSE delta data
  → DeltaCommand (JsonElement sealed model)
  → st3(path, operation, value)
  → doc.W(st3, gv1)
  → xq1(path, operation, value)
  → ll23.y(...)
  → zca.response
  → gy.fragments
  → xr.B() / gy.B() text projection
```

- `DeltaCommand` 是 JsonElement 结构，不是普通字符串。
- `st3` 保存 path、operation、value。
- 已确认的操作类型包括 `SET`、`APPEND`、`BATCH`。
- 根状态 `zca.response` 映射到 `gy`；`gy` 保存 fragments。
- `gy.B()` 是文本 fragment 投影，不是完整响应语义：Reasoning、Tool、Search/Find/Open 等 fragment 不能未经分类就压成普通文本。

**适配器原则：** 必须先实现并测试 code289 专用 DeltaCommand decoder/reducer，再映射到统一 CompletionResult。不得把 SSE data 原样当作用户文本；不得把宿主 patch 假设为 RFC 6902；未知 operation/fragment 必须显式失败或标记 unsupported，不得静默丢弃。

## 3. 当前 ROI 探针与宿主链的关系

当前模块中的被动观测已覆盖以下候选边界：
- `jw3.g(wu1, Long)`：transport-low-level；
- `zn3.o(wu1, Long)`：transport-dispatch；
- 返回 Flow 与 observation ID 的弱身份关联；
- `y60.b(fl4, Continuation)`：Flow collect / suspend 边界；
- collector callback 与 Flow trace ID 关联；
- `r3.c(dv1, Continuation)`：DeltaCommand dispatch 边界。

这些观测只证明方法调用和关联线索。suspend 方法的同步返回可能是 `COROUTINE_SUSPENDED`，不等于 continuation 已恢复、Flow 已完成、delta 已应用或 response snapshot 已就绪。日志统一使用 `*-return`，避免把同步返回误称为异步完成。

## 4. Native Adapter 实施顺序

1. **Session/Repository discovery：** 在 code289 profile 下识别 `nn0` 与其注入的 `zn3`；先只验证对象身份和生命周期，不触发额外请求。
2. **Request construction：** 优先复用宿主 `nn0` 请求路径与 `ly1` 构造语义，避免模块自行补造 session/parent/model/action 字段。
3. **Flow execution：** 确认 collect 所需 dispatcher、scope、Job 与取消边界；不得把单纯反射调用 `zn3.o` 当作网络执行。
4. **Transport reuse：** 复用宿主 `bi5` client/plugin 链，尤其 `nq1` ChallengePlugin。
5. **Event decode：** 分离 ready/delta/hint/title/update_session/close/error；严格按 code289 serializer 和 JSON 类型解码。
6. **Reducer：** 独立实现 SET/APPEND/BATCH 与 path 语义，建立状态树一致性测试；未知路径/操作 fail-closed。
7. **Projection：** 分别映射 answer、reasoning、tool/search/find/open、finish reason 与 usage；不支持类型显式返回 unsupported。
8. **Cancellation/isolation：** request ID、chat_session_id、Flow Job、timeout、resume state 必须隔离；取消请求时取消对应协程 Job。
9. **End-to-end：** 验证非流式/流式、PoW challenge、resume、cancel、HTTP error、并发 session 和宿主版本差异。

## 5. 禁止的错误推断

| 观察到的事实 | 不允许推断 |
|---|---|
| `zn3.o` 返回 Flow | 网络请求已执行 |
| `y60.b` 同步返回 | Flow 已正常完成 |
| `r3.c` 被调用 | Delta 已成功应用 |
| `gy.B()` 有文本 | 完整 response/tool/reasoning 已覆盖 |
| 静态链完整 | Adapter 已运行 |
| CI 通过 | 287/289/290 真机验收通过 |

## 6. 当前完成度与未完成项

**已完成：**
- code289 APK 身份和静态证据范围固定；
- Request factory、request model、transport、Challenge/PoW、HTTP/SSE、event dispatch、DeltaCommand/reducer 的静态调用链整理；
- ROI 被动观察点与 suspend 返回语义边界明确；
- Native Adapter 的安全接入顺序和 fail-closed 原则明确。

**未完成：**
- 尚未在设备上反射获取 `nn0/zn3` 并安全 collect Flow；
- 尚未验证实际宿主 client/plugin 是否能从目标运行时对象复用；
- 尚未实现 DeltaCommand 完整 reducer；
- 尚未完成 reasoning/tool/search/find/open fragment 映射；
- 尚未完成 Flow Job cancel/resume/timeout 与并发隔离；
- 尚未完成 Local API 到 CompletionResult 的端到端输出；
- 尚未完成 code289 真机验收；287/290 也不能由 code289 静态证据代替。

因此，本报告是静态调用链和实施设计的阶段性成果，不代表 Native Completion 已可用。Local API 在真实 Adapter 接通前必须继续 fail-closed，返回 `engine_missing`。

## 7. 可复现静态证据

来源审计记录：
- Workflow：`.github/workflows/deepseek-272-static-audit.yml`
- Source branch：`qingjian0/apps:work/deepseek-272-static-audit`
- Successful run：`42342447740`
- Artifact：`deepseek-272-static-evidence`
- Artifact SHA-256：`923b1f319803f87e27d0f3e44b65cc7148cdcd82c7713826ec9631f0bfb0d61e`

该产物是静态分析证据包，不包含真机运行验收结论。
