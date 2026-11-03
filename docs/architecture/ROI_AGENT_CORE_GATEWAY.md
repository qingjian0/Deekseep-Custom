# ROI Agent Core + API Gateway：批量实施设计

> 状态：Architecture baseline / implementation plan  
> 基线仓库：`qingjian0/Deekseep-Custom`  
> 基线版本：YunSeek 1.8.1（versionCode 37）  
> 目标宿主：DeepSeek Android 2.7.1 / 2.7.2（287 / 289 / 290）  
> 工作分支：`work/roi-agent-core-batch`

## 1. 目标与边界

在现有开源 LSPosed 模块上增加独立的 Agent Core 与统一 API Gateway。Hook 层只做宿主接入、请求/响应转换和结果回传；Agent 的会话、任务、工具调度、策略和状态管理不继续堆入 Hook 类。

第一阶段坚持：

- 以现有 GPL-3.0 开源代码为基线，保留原作者及衍生代码署名。
- 不将闭源 payload、私有服务 URL、私有签名配置或受保护构建逻辑复制进 Open 版本。
- 不启用账号/凭据捕获、风控绕过、设备指纹伪装、地区/登录解锁、防封或反检测。
- API 默认关闭；仅监听 loopback（127.0.0.1），使用独立随机 Bearer Token；不默认开放 LAN、公网或隧道。
- 所有设备操作和有副作用的工具均受用户确认策略约束；保留半自动默认值、限速和审计记录。
- 不宣称已验证的宿主兼容性超出实际测试证据。

## 2. 当前仓库审计结论

### 已有可复用能力

- LSPosed/Xposed 入口与宿主识别：`HostCompat`、`HostRemap`、`Main`。
- Agent Hook 与工具流程：`HookAgentPipeline`、`HeartbeatToolProtocol`、`AgentToolConfig`。
- Agent 状态与回传：`AgentRunStore`、`AgentManualGate`、`AgentRateLimiter`。
- 外部 MCP 客户端：`AgentMcpManager`（这是 Agent 连接外部 MCP 服务的客户端，不是对外 API Gateway）。
- 原生 DeepSeek 请求执行入口：`z2` 契约、`Main.executeLocalApiCompletion` 及相关 Hook 管线可供后续评估，不应未经隔离直接复用其所有 Local API 分支。

### 当前缺口与容易混淆之处

- `docs/FEATURES.md` 将 Local API 与隧道列为未移植能力。
- `compose-launcher/app/build.gradle.kts` 和公开 CI 明确以 `protectedBuild=false`、`localApiIncluded=false` 构建 Open 版本；Manifest 的 Local API 组件也受 Closed 构建门控。
- 仓库内存在 `z2`、`LocalApiFloatingOverlay`、`PublicTunnelManager` 等兼容/历史源码，但这不代表 Open APK 含有可启动的完整 HTTP Gateway。完整 Gateway 运行实现依赖独立 payload；不能将这些文件误判为已开放的 API 服务。
- `AgentMcpManager` 是出站 MCP Streamable HTTP / SSE 客户端，与本项目需要新增的入站 OpenAI 兼容 API 是两个方向。
- README 对 287/289/290 的映射支持说明与旧版 `PORT_NOTES.md`、`docs/FEATURES.md`、`INSTALLATION.md` 存在版本状态陈旧/不一致，需要单独统一文档，不据 README 单独推定真机通过。

## 3. 目标逻辑架构

```text
External clients
    |
    | HTTP: /v1/*
    v
Local API Gateway (loopback only; auth; validation; streaming)
    |
    v
Agent Core
    |-- Session / conversation scope
    |-- Task & run state machine
    |-- Model routing and capability registry
    |-- Tool registry / permission policy
    |-- Memory & context assembly interfaces
    |-- Event stream / cancellation / deadlines
    |-- Persistence abstraction
    |
    v
DeepSeek Host Adapter
    |-- request translation
    |-- native transport invocation
    |-- delta / reasoning translation
    |-- cancellation and result delivery
    |
    v
DeepSeek Android process (LSPosed Hook)
```

### 分层职责

1. **Agent Core**：纯业务层；不依赖 Android UI、Xposed API、HTTP Server 或 DeepSeek 混淆类名。
2. **API Gateway**：协议层；处理 HTTP、认证、请求体限制、SSE、错误码、速率限制和取消，不直接反射宿主类。
3. **Host Adapter**：唯一允许依赖 Hook/宿主反射的边界；把稳定 DTO 转换为 DeepSeek 原生请求并回传增量。
4. **Persistence**：定义接口后再选择实现；第一阶段先用本地原子文件/SQLite 评估，不把宿主聊天数据库作为 Agent Core 的私有状态库。
5. **UI**：只负责配置、授权、运行状态和用户确认；不承载 Agent 调度逻辑。

进程内调用优先使用窄接口；如果 Gateway 与 Hook/宿主需要跨进程，再引入 Binder/AIDL。第一阶段不提前搭建复杂 SPI、DI 或插件框架。

## 4. API 兼容目标

按阶段实现，不一次性承诺全部兼容：

| API | 目标 | 第一阶段行为 |
|---|---|---|
| `GET /v1/models` | 模型能力发现 | 返回本地可用模型/能力，不伪造云端模型 |
| `POST /v1/chat/completions` | OpenAI Chat Completions | 非流式与 SSE 流式；先支持文本 |
| `POST /v1/responses` | OpenAI Responses | 先实现文本输入/输出与 response id |
| `POST /v1/agent/run` | 本地扩展接口 | 创建任务、查询状态、取消任务；工具执行按权限策略确认 |

统一约束：

- 严格解析 JSON；未知字段可按兼容策略忽略，但非法类型必须返回明确错误。
- 对请求大小、上下文长度、输出长度、并发数和执行时长设上限。
- 每个请求具备 request id、conversation scope、deadline 和 cancellation token。
- SSE 事件顺序稳定；客户端断开时取消上游收集，除非任务已进入明确的后台执行策略。
- 统一错误 envelope；不得把内部堆栈、密钥、宿主私有类名或聊天数据库路径返回给客户端。
- Bearer Token 使用安全随机数生成、只保存必要的本地凭据材料；支持轮换与撤销。
- 默认绑定 `127.0.0.1`；不开放公网隧道，不自动配置端口转发。

## 5. 批量任务拆分

### Batch 0 — 基线与审计

- [x] 建立独立工作分支。
- [x] 获取仓库树与当前 README、功能范围、构建、CI、宿主兼容文档。
- [x] 确认 Open 构建禁用 Local API 与隧道组件。
- [x] 区分出站 MCP 客户端与入站 API Gateway。
- [ ] 完成 Hook 安装点、Agent 状态机、取消、回传与持久化的逐文件审计。
- [ ] 对照 CI 最近一次真实运行与 Release 产物，不把工作流配置等同于构建成功。

### Batch 1 — 稳定契约

- [ ] 新建 Android/Hook 无关的请求、响应、模型、消息、增量事件、错误和任务状态 DTO。
- [ ] 定义 `AgentRuntime`、`ModelAdapter`、`ToolExecutor`、`RunRepository` 接口。
- [ ] 用 JVM 单元测试锁定序列化、边界值、取消、超时和状态迁移。
- [ ] 不迁移宿主混淆符号到 Core。

### Batch 2 — Agent Core

- [ ] 实现任务状态机：queued → running → awaiting_confirmation / streaming → completed / failed / cancelled。
- [ ] 实现会话作用域、上下文组装接口、模型路由和工具权限策略。
- [ ] 实现幂等 request id、重复工具调用防护、限速与 deadline。
- [ ] 接入现有 `AgentRunStore` 前先完成存储格式审计和迁移策略。

### Batch 3 — API Gateway

- [ ] 建立 loopback HTTP 服务与生命周期管理。
- [ ] 实现认证、请求校验、限流、SSE、断连取消和健康检查。
- [ ] 实现 `/v1/models`、`/v1/chat/completions`，再实现 `/v1/responses`。
- [ ] API 关闭时不启动监听、不保留可被外部访问的后台服务。

### Batch 4 — DeepSeek Host Adapter

- [ ] 将当前 native request / delta 逻辑包成窄适配器。
- [ ] 仅由适配器访问 Hook、宿主反射和 DeepSeek 内部请求管线。
- [ ] 287、289、290 分开做符号解析与失败即放行验证。
- [ ] 不复用受保护 Local API payload；不引入闭源依赖。

### Batch 5 — UI、CI 与发布

- [ ] 增加 API 开关、端口显示、Token 轮换、复制与撤销。
- [ ] 增加任务列表、运行详情、确认/取消入口。
- [ ] CI 增加 Core 单元测试、Gateway 协议测试、APK 构建和产物检查。
- [ ] 分别报告 JVM 测试、CI 构建、模拟宿主验证、真机验证；不混为一个“通过”。

## 6. 本阶段验收标准

- Open 构建不依赖 Closed payload、私有 URL、私有密钥或受保护构建输入。
- Core 可在 JVM 测试中独立运行。
- Gateway 仅 loopback、默认关闭、必须认证。
- Chat Completions 文本请求可通过 Host Adapter 调用 DeepSeek 原生能力，并正确返回非流式/SSE 响应。
- 断连、取消、超时、重复请求和宿主符号缺失均有确定行为。
- 不改变 DeepSeek 正常聊天流程；Hook 安装失败时 fail-open。
- 每个阶段有可重复测试和单独提交。

## 7. 首批技术债务

1. README 声称 287/289/290 支持，与旧文档只写 287 的状态不一致。
2. `docs/FEATURES.md`、`INSTALLATION.md`、`SECURITY.md` 的版本描述未同步到 1.8.1。
3. Local API 相关源码、Closed payload 与 Open build flag 的边界需要自动化检查，防止错误打包。
4. 公开 CI 依赖四个签名 Secrets；签名失败应与源码编译/测试失败分开报告。
5. `module/src` 中仍有历史 Local API / Tunnel 源码，应先分类、确认引用链后再决定保留、隔离或清理，禁止批量删除。
