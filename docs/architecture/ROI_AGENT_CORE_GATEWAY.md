# ROI Agent Core + Local API Gateway 实施状态

> 工作分支：`work/roi-agent-core-kotlin`  
> 架构基线：`refactor/kotlin-hook-architecture`  
> 宿主目标：DeepSeek Android 2.7.x（287 / 289 / 290）  
> 最新状态：见 [PROJECT_STATUS.md](../PROJECT_STATUS.md)

## 1. 设计目标

在不整体重写既有 Java/Xposed Hook 的前提下，建立可独立测试的 Kotlin Agent Runtime，并通过 Local API Gateway 暴露本机兼容接口。DeepSeek 原生模型调用只能经由经过验证的 Host Adapter 完成；Gateway 不应直接依赖混淆类名。

```text
OpenAI-compatible clients
        |
        v
Local API Gateway
(loopback / optional LAN / bearer auth / validation / SSE)
        |
        v
Kotlin Agent Core
(request scope / run state / model contract / tool policy)
        |
        v
Model Adapter contract
        |
        v
DeepSeek Host Adapter (Java / Xposed boundary)
        |
        v
DeepSeek native request / response lifecycle
```

## 2. 职责边界

1. **Agent Core**：纯 Kotlin/JVM 业务协调，不直接依赖 Android、Xposed、Compose、HTTP Server 或宿主混淆类型。
2. **Gateway**：处理协议、认证、请求校验、限流、SSE 和生命周期；不直接持有宿主反射对象。
3. **Host Adapter**：唯一接触宿主内部符号的边界，负责稳定 DTO 与原生请求/响应转换。
4. **Persistence**：保存 Agent 私有运行状态，不复用 DeepSeek 聊天数据库作为 Agent 状态库。
5. **UI**：负责启停、诊断、Token 管理、任务状态和逐次用户确认，不承担模型调度。

## 3. 已实现与未完成

### 3.1 Kotlin Agent Core

已落地的核心构件包括：

- 请求、模型适配器、事件和运行结果契约；
- 确定性运行状态机；
- deadline、取消、增量事件转发与异常隔离；
- request ID 幂等；
- 持久化 Run Repository、版本化记录、容量控制和恢复路径；
- 工具权限策略，未知工具默认拒绝；有副作用的工具默认逐次确认。

仍需持续验证：持久化故障注入、并发竞争、重复调用、终态恢复，以及工具策略与真实执行器的端到端连接。不得仅凭接口存在就宣称工具可执行。

### 3.2 Local API Gateway

入口：`module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java`

| 方法 / 路由 | 当前实现状态 |
|---|---|
| `GET /v1/models` | HTTP 路由与错误响应已实现；原生模型适配器未就绪时 503 |
| `POST /v1/chat/completions` | HTTP/JSON/SSE 路由已实现；原生模型适配器未就绪时 503 |
| `POST /v1/responses` | 未实现 |
| `POST /v1/agent/run` | 未实现 |

网络、安全与资源约束：

- API 默认关闭，默认只监听 `127.0.0.1`。
- 显式开启 LAN 后，只监听当前检测到的精确 `192.168.x.x` IPv4 地址；不绑定 `0.0.0.0`。
- 远端 IPv4 来源限制在 `192.168.0.0/16`。
- Android 默认网络回调监听可用网络和 LinkProperties 变化；新地址绑定成功后再关闭旧 LAN listener。失去合适地址时关闭 LAN listener，保留 loopback。
- 每安装实例随机 Bearer Token，支持轮换；请求体、Prompt、并发和队列均有上限。
- 拒绝 chunked request body，客户端需提供 Content-Length。
- SSE 增量写出，不把 reasoning delta 混入公开 content。
- 不持久化 Prompt / Completion，不记录 Token 明文或工具参数。
- LAN 使用明文 HTTP，不提供 TLS、公网转发或隧道。

### 3.3 Native Completion Adapter：首要阻塞

Open 构建尚未完成独立于 Closed payload 的 DeepSeek 原生 Completion 适配。

当前源码中：

- `z2` 定义 Backend / CompletionRequest / CompletionResult / DeltaSink 等契约；
- `z13` 是反射门面，不是完整原生执行引擎；
- 旧的 `HookAccountLoginSecurity.executeLocalApiCompletion()` 路径仍依赖 Closed 专属 `z18` payload；
- Open 构建不包含该执行载荷，不能通过简单开启某个 BuildInfo 标记解决。

下一阶段调查必须以实际调用链证据为准，依次核验：

1. 官方客户端请求参数和会话上下文的构造；
2. PoW / challenge 管理器及请求前置流程；
3. 原生网络 Transport 的创建、认证和调用线程；
4. Flow / SSE 增量事件的类型、顺序和对象身份；
5. 响应消费、错误传播、取消、超时和资源释放；
6. 287、289、290 各版本的符号差异和真机证据。

在上述路径被独立实现并验证前，Gateway 必须保持 not-ready / 503，不得伪造模型列表或成功响应。

## 4. 不变量

- 不整体重写 `Main.java`、`HookChatPipeline.java` 等高耦合宿主边界。
- 保留原方法 exactly-once、异常传播和 Hook 顺序。
- 保留宿主原始 Flow 对象身份；不得用不兼容 Proxy 替换声明返回类型。
- 未知宿主版本不执行未经验证的 Hook。
- 未知工具默认 DENY；文件写入、设备控制、外部网络等副作用工具逐次确认。
- 任务取消、deadline、幂等 request ID 和权限确认必须具有明确作用域。
- 不复制 Closed 专属载荷或私有实现来填补 Open 版能力。

## 5. 批次与验收状态

| 批次 | 内容 | 状态 |
|---|---|---|
| A | Kotlin Runtime / Agent Core 核心契约与状态机 | 已实现主要骨架；持续补回归 |
| B | 持久化、恢复、工具权限策略 | 已有实现；故障注入与执行器集成仍需验收 |
| C | DeepSeek Host Adapter | 调查与诊断阶段；Open 原生 Completion 未完成 |
| D | Local API HTTP / Auth / SSE / LAN | 服务层已实现；模型端到端调用受 C 阻塞 |
| E | UI、CI、文档和发布 | Local API 诊断 UI 已加入；CI 修复与构建矩阵持续进行 |

## 6. 验收方式

所有交付按 [TESTING.md](../TESTING.md) 的 L0–L5 分级记录。至少区分：

- Kotlin/JVM 回归；
- Open Android 构建与 Lint；
- Legacy / Universal 构建；
- APK 结构检查；
- 模拟器或沙盒；
- 287 / 289 / 290 对应宿主、渠道和 LSPosed 环境下的真机验证。

CI 绿色不代表真机 Hook 成功；映射解析通过不代表对应宿主版本全功能可用。
