# com.dsmod.probe Local API 参考分析与 ROI 移植决策

> 研究日期：2026-11-07  
> 参考仓库：[`Xposed-Modules-Repo/com.dsmod.probe`](https://github.com/Xposed-Modules-Repo/com.dsmod.probe)  
> 参考分支：`main`  
> 本次核对的参考提交：`72f1d290860247defd3825d5a447133d45a876c3`  
> ROI 目标分支：`work/local-api-protocol-port`  
> 结论：**参考其架构和已验证的旧版原生适配经验；不能复制其混淆符号直接用于 DeepSeek 2.7.2/code289。**

## 1. 结论

`com.dsmod.probe` 确实实现了可工作的 DeepSeek Android Local API，不只是 HTTP Server 示例。它的关键实现分布在：

| 参考文件 | 作用 |
|---|---|
| `module/src/com/dsmod/probe/LocalApiGateway.java` | HTTP/1.1、认证、协议解析、SSE、状态、响应转换 |
| `module/src/com/dsmod/probe/Main.java` | 原生 Request 构造、PoW、Transport 调用、Flow collect、会话与限流 |
| `module/src/com/dsmod/probe/NativeApiPatchDecoder.java` | DeepSeek DeltaCommand / patch 到 text 与 reasoning 增量的解码 |
| `module/src/com/dsmod/probe/OpenAiToolBridge.java` | function/custom/shell/apply_patch/namespace 工具协议桥 |
| `module/tests/com/dsmod/probe/LocalApiGatewayProtocolRegressionTest.java` | Chat、Responses、Anthropic、SSE、工具续写等协议回归 |
| `module/tests/com/dsmod/probe/LocalApiCoroutineCancellationRegressionTest.java` | 协程取消相关回归 |
| `docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md` | 设计决策、已完成能力、设备验收记录与剩余事项 |
| `docs/LOCAL_DEEPSEEK_API.md` | SDK、Codex、Claude Code 和微型 Agent 使用说明 |

参考项目 README 当前标注版本 1.7.2，但其明确支持的官方 DeepSeek 构建仍是 2.2.2：国内版 `versionCode=233`、Google Play `versionCode=236`。这与本项目目标 2.7.x（287/289/290）不是同一宿主 ABI。

## 2. 参考项目的真实调用架构

```text
OpenAI SDK / Codex / Claude Code / local Agent
       |
       | HTTP + JSON / SSE + Gateway Key
       v
LocalApiGateway
       |  Backend / CompletionRequest / DeltaSink
       v
Main.executeLocalApiCompletion
       |  fair native permit
       |  reusable hidden session
       |  serialized PoW + retry + deadline
       v
DeepSeek native request DTO
       |
       v
DeepSeek transport -> cold Flow.collect
       |
       v
native event wrapper -> DeltaCommand decoder
       |
       v
response.fragments -> text / reasoning / tool signal
       |
       v
DeltaSink -> Chat / Responses / Anthropic encoder
```

重要设计不是“把 HTTP 转发到某个 URL”，而是在 DeepSeek 目标进程中复用已经初始化的原生网络栈、会话、Challenge/PoW 和流式事件处理。HTTP 网关只负责协议边界；原生模型生成由独立 Backend 契约承接。

## 3. 值得移植到 ROI 的设计

### 3.1 Gateway 与宿主 ABI 隔离

参考项目的 `LocalApiGateway.Backend` 只暴露：

- `isReady()`
- `readinessDetail()`
- `complete(CompletionRequest, DeltaSink)`

`CompletionRequest` 是稳定 DTO；`DeltaSink` 提供 `onUpstreamStarted()`、`onText()`、`onReasoning()`、`isCancelled()` 和 `isSatisfied()`。

**ROI 决策：继续保留现有 Gateway / Backend 边界。** 不把 `ly1`、`zn3`、`jw3`、`wu1` 等宿主混淆符号写入协议层，也不把反射散落到 HTTP handler。为 2.7.x 增加独立 Host Adapter 实现，通过能力声明和版本映射选择适配器。

### 3.2 原生调用必须是完整闭环

参考项目旧宿主实现的真实步骤：

1. 从已捕获的 live transport / PoW manager 取得宿主运行时对象。
2. 创建或复用 API 专用隐藏会话。
3. 通过宿主 PoW 管理器获取有效 challenge response。
4. 构造宿主原生 Request DTO。
5. 调用真实 transport 方法，取得 cold Flow。
6. 通过 Kotlin Flow collector 收集原生事件。
7. 解码响应状态与 DeltaCommand patch。
8. 将 response / reasoning 增量交给 DeltaSink。
9. 将终态、异常、取消和超时转换为稳定 CompletionResult / GatewayException。

**ROI 决策：上述步骤作为 Native Adapter 的验收闭环。** 只找到 Transport 候选、只构造 Request、只得到 Flow 对象或只让 SSE 输出数据，都不能单独算完成。

### 3.3 DeltaCommand 是状态补丁，不是文本 token

参考项目的 `NativeApiPatchDecoder` 将原生 patch 中的 path / operation / value 作为状态变更处理：

- 跟踪 THINK / REASONING 与 RESPONSE fragment 的活动通道；
- 区分 `SET` 与 `APPEND`；
- 处理省略 operation 的后续 token；
- 对 snapshot / fragment 创建事件更新当前活动通道；
- 将最终 text 与 reasoning 分离；
- 对 `SET` 维护当前完整状态，并只向 SSE sink 发出可追加部分。

ROI 的 code289 静态调查已记录 DeltaCommand 的 `p/o/v` 语义、`st3`、`doc.W`、patch engine 和 `response.fragments` 路径。可借鉴其“每条 Flow 一个有状态 decoder”的实现模型，但必须按 ROI 自己的 code289 DTO 与 serializer 证据重写，不可照搬参考项目的 `xs0/ws0` 事件类或字段名。

### 3.4 会话隔离与复用

参考项目不为每个 HTTP 请求创建/删除一次 DeepSeek session，而是按模型、工作负载、客户端会话 scope 复用 API 专用隐藏会话：

- 每次请求仍发送自己的完整 transcript，避免把上次请求的隐式上下文拼接进来；
- 不同客户端会话以哈希 scope 隔离；
- `/clear`、`/new` 产生新 scope，切换隐藏分支；
- session ID 失效时清除映射、重建并只在未输出 delta 时重试；
- 映射有数量上限、TTL、持久化和原生清理路径；
- API 专用会话从侧栏/普通历史 UI 隐藏。

**ROI 决策：** 保留 Gateway 已有 `clientSessionScope` / `previous_response_id` 概念；Native Adapter 需要另建经过验证的 HostSessionBridge。必须确认 code289 创建会话、parent ID、删除路径和账号切换行为后才能实现持久复用。不得把参考项目旧版 session DTO 或会话创建方法映射到 2.7.x。

### 3.5 单通道并发、PoW 串行和有限重试

参考项目基于真机观察，将同一 DeepSeek 账号的原生生成统一放入公平 `Semaphore(1)`，并配套：

- Agent/Task 工具请求优先于辅助标题/摘要请求；
- PoW 签发单独串行化；
- 原生请求起点 pacing；
- 有界排队、总 deadline 和有限重试；
- 检测到原生限流后冷却；
- 只有尚未向客户端输出任何 delta 才能自动重试；
- 已输出正文后禁止重放，避免工具副作用和重复 token。

**ROI 决策：** 将“账号级原生生成是否只能单并发”作为设备验证项。在得到 code289 实测前，不盲目复制并发数或 2.5 秒间隔等常数；但应保留公平调度、deadline、PoW 单飞、输出后不重放这些架构约束。

### 3.6 上游生命周期与取消

参考项目在进入 Flow `collect()` 后才触发 `onUpstreamStarted()`，防止请求仍在排队、PoW 或建连时就向 Anthropic 客户端伪造 thinking 生命周期。

其 collector 通过 continuation context 关联 cancellation job；检测客户端断开、deadline 或 sink 不再接受数据时取消本地 Flow 收集。工具调用完整捕获后则继续排空上游 Flow，避免服务端仍在生成而下一轮触发并发限制。

需要注意：参考项目计划文档仍将“socket 到宿主 Flow 的显式取消双向确认”列为后续强化项。因此应区分“本地 collector 取消已有代码/测试”和“socket 断开到宿主服务端生成完全终止的端到端确认”，不能混为一谈。

**ROI 决策：** 使用当前 Gateway 的 DeltaSink 作为取消边界；为 2.7.x 单独验证 coroutine context、Job 类型、取消传播和 collector 终止。不能用关闭 Socket 或中断 Java worker 冒充宿主 Flow 已取消。

### 3.7 工具桥与协议层

参考项目把工具协议抽离到 `OpenAiToolBridge`，不依赖宿主原生工具 JSON 协议。它负责：

- 将请求方工具 schema / namespace / custom grammar 加入模型提示；
- 解析模型生成的结构化调用；
- 校验工具名、forced choice、参数 schema 和副作用调用；
- 把 tool call 映射回 Chat / Responses / Anthropic；
- 把客户端工具结果续写给下一轮；
- 对已成功的副作用调用做结构化去重。

**ROI 决策：** 可借鉴分层与测试方法，不直接引入参考项目的 OmniRoute 提示词或工具解析语料。ROI 现有工具策略、授权与 Agent Core 必须继续作为执行权限的唯一边界。Gateway 的工具桥不能擅自执行 shell、文件写入或其他副作用。

## 4. 不能直接照搬的内容

| 参考项目内容 | 不可直接复用的原因 |
|---|---|
| `r92`、`q71`、`ew0`、`xs0`、`ws0`、`w02`、`fx6`、`ui8` 等混淆符号 | 参考项目适配 DeepSeek 2.2.2 / code233、236；ROI 目标为 2.7.x |
| 旧版 Request 的 11 参数构造器与字段顺序 | code289 Request 类型为 `ly1`，必须依据 code289 构造器/调用点独立确认 |
| 旧版 PoW manager 的获取方式 | code289 ChallengePlugin / PoW 链不同；ROI 已有 code289 的 `nq1`、`zn3/bi5` 调查证据 |
| 旧版 transport 的反射方法名 | ROI code289 候选为 `zn3.o(wu1, Long)` / `jw3.g(wu1, Long)`，但仍需运行时语义确认 |
| 旧版 Flow wrapper / collector / event 类 | code289 的 `Ly60`、`fl4`、`cv1`、`sha` 等应按自己的 DEX/Smali 证据适配 |
| 旧版 Delta decoder 字段/事件名 | patch 的通用思想可复用，宿主 DTO 和 patch path 必须以 code289 为准 |
| 参考项目 `0.0.0.0` 监听策略 | ROI 已采用默认关闭、精确 LAN 地址绑定和来源网段限制；维持 ROI 更严格的安全边界 |
| 参考项目的线程池、超时、并发和 pacing 常数 | 依赖其设备、账号、宿主版本的实测，不应未经 ROI 设备验证直接复制 |

## 5. ROI 当前状态与差距

| 层次 | ROI 当前证据 | 参考项目可提供的方向 |
|---|---|---|
| HTTP / Auth / SSE | 已有 | 继续保持 Gateway 与 Backend 解耦 |
| Chat / Responses / Anthropic 协议映射 | 协议层已有，原生执行仍 503 | 用 fake Backend 做完整协议回归 |
| code289 Request | `ly1` marker 与候选结构已盘点 | 追踪真实 Request 构造调用点和字段语义 |
| Challenge / PoW | 已有 `nq1` / PowCalculator / ChallengePlugin 静态链调查 | 捕获并复用真实存活的宿主 client/plugin，不另造 PoW |
| Transport | `zn3.o` / `jw3.g` 候选 | 用同一 Request 实例关联真实调用和返回对象 |
| Flow | 候选 `Ly60` 与回调边界已有静态证据 | 建立 collector、continuation、Job 和终态行为 |
| Delta / Response | `cv1` / `sha` / DeltaCommand / `gy.fragments` 静态路径已有记录 | 每条 Flow 一个 patch decoder，分离 text/reasoning |
| Session | Gateway 有 scope 概念 | 确认 code289 session create / parent / delete / rotation |
| 取消 / deadline | Gateway 有请求 deadline 与取消接口 | 验证从 socket 到宿主 Job 的真实传播 |
| 真实执行 | 未完成，保持 503 | 完成 code289 adapter 后再开放 |

## 6. 推荐实施顺序

### R1：只读运行时证据（不得发起 API 生成）

1. 在显式诊断授权下观察 code289 官方聊天正常提交。
2. 将 Request 实例 identity 与 `zn3.o` / `jw3.g` 调用关联。
3. 确认真正的 Transport owner、参数顺序、Long 参数语义与返回类型。
4. 观察返回 Flow 的 collect 入口、continuation context 和 callback 实现类。
5. 只记录类型、方法签名、对象 identity、调用父子关系和异常类别；不记录 Prompt、Token、Cookie、Authorization 或字段值。
6. 所有 observer 必须透明调用原方法一次、保留原返回对象和异常传播；诊断关闭后立即透明放行。

### R2：独立 Host Adapter 与纯 JVM 测试

建立窄边界组件（名称可按项目结构调整）：

- `HostRequestFactory`
- `HostChallengeProvider`
- `HostCompletionTransport`
- `HostFlowCollector`
- `HostEventDecoder`
- `HostCancellationBridge`
- `HostSessionBridge`

先用 fake host objects / fake Flow 覆盖构造、patch、事件顺序、终态、错误、取消、deadline 和重复请求。Gateway 不应直接依赖这些宿主实现类。

### R3：code289 受控设备验证

按顺序验证单次非流式、SSE、reasoning/text 分离、错误响应、客户端断开、超时、网络变化、账号切换、会话失效重建和连续请求。确认原生生成并发约束后再确定调度参数。

### R4：开放协议入口

只有 code289 原生执行链完成并通过设备验证，才将 Gateway Backend 的 503 门禁切换为该版本能力就绪。未知版本继续 fail-closed；287、289、290 分别验收，不能因 289 成功而默认支持 287/290。

## 7. 许可与来源

参考仓库声明 MIT License；ROI 仓库声明 GPL-3.0。当前文档仅记录架构分析和独立实现建议，不复制参考源码。若后续确需移植具体代码，必须保留 MIT 版权与许可声明，并审查对应文件是否含第三方依赖/独立许可。

## 8. 参考链接

- [参考项目 README](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/README.md)
- [LocalApiGateway.java](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/module/src/com/dsmod/probe/LocalApiGateway.java)
- [Main.java](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/module/src/com/dsmod/probe/Main.java)
- [NativeApiPatchDecoder.java](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/module/src/com/dsmod/probe/NativeApiPatchDecoder.java)
- [OpenAiToolBridge.java](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/module/src/com/dsmod/probe/OpenAiToolBridge.java)
- [Local API implementation plan](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md)
- [Local API usage guide](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/docs/LOCAL_DEEPSEEK_API.md)
- [Protocol regression test](https://github.com/Xposed-Modules-Repo/com.dsmod.probe/blob/72f1d290860247defd3825d5a447133d45a876c3/module/tests/com/dsmod/probe/LocalApiGatewayProtocolRegressionTest.java)
