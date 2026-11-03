# ROI Agent Core + API Gateway 批量实施基线

> 工作分支：`work/roi-agent-core-kotlin`  
> 上游开发基线：`refactor/kotlin-hook-architecture`  
> 仓库：`qingjian0/Deekseep-Custom`  
> 宿主目标：DeepSeek 2.7.x（287 / 289 / 290）  
> 当前状态：Agent Core 第一阶段实现中；API Gateway 尚未实现。

## 1. 为什么基于 Kotlin Runtime 分支

仓库已有 `refactor/kotlin-hook-architecture` 分支，较 `main` 包含一整套 Kotlin-first 重构：独立 `module-kotlin-runtime`、Legacy/Universal 构建接入、Hook 安装注册表、纯 JVM 回归和 Compose 生命周期拆分。

本任务以该分支为实际开发基线，避免在旧 `main` 上重复建立另一套 Java Runtime。继续遵循该分支既定原则：

- Kotlin 负责新运行时契约、确定性策略和业务协调。
- Java/Xposed 入口继续作为兼容边界，不整体重写 Hook 引擎。
- 宿主混淆映射、Hook 回调顺序、Flow 身份和原方法调用语义不因 Agent Core 开发而改变。
- 新抽象必须有真实调用场景与测试，不提前搭建大型 SPI、DI 或插件系统。

## 2. 目标架构

```text
OpenAI-compatible clients
        |
        v
Local API Gateway
(loopback HTTP / bearer auth / validation / SSE / cancellation)
        |
        v
Kotlin Agent Core
(request scope / run state / model routing / tool policy / events)
        |
        v
Model Adapter contract
        |
        v
DeepSeek Host Adapter (Java/Xposed boundary)
        |
        v
DeepSeek native request / response lifecycle
```

### 职责边界

1. **Agent Core**：纯 JVM/Kotlin，不依赖 Android、Xposed、Compose、HTTP Server 或 DeepSeek 混淆类名。
2. **API Gateway**：负责协议、认证、请求校验、限流、SSE 和断连取消；不直接访问宿主反射对象。
3. **Host Adapter**：唯一允许访问 Hook 与宿主内部符号的适配层，负责稳定 DTO 与原生请求/响应转换。
4. **Persistence**：先定义最小接口，后续再选原子文件或 SQLite；不把 DeepSeek WCDB 当作 Agent 私有运行状态库。
5. **UI**：只提供启停、Token 管理、任务查看和用户确认，不承担调度。

### API 目标

| 路径 | 目标 |
|---|---|
| `GET /v1/models` | 返回真实可用的本地模型/能力 |
| `POST /v1/chat/completions` | OpenAI Chat Completions，文本及 SSE |
| `POST /v1/responses` | OpenAI Responses，分阶段兼容 |
| `POST /v1/agent/run` | 本地任务创建、查询、取消扩展接口 |

## 3. 当前仓库中不能混淆的组件

- `AgentMcpManager` 是 Agent 连接外部 MCP Server 的出站客户端，不是本地 API Gateway。
- `ConversationRelay` 是面向外部 OpenAI/Anthropic Provider 的适配/转发逻辑，不等同于接管 DeepSeek 原生能力的 Local API Gateway。
- `z2` 是 Local API 的主 DEX 契约；`HookAccountLoginSecurity.executeLocalApiCompletion` 将调用转发到 `z14` 加载的 `z18` 受保护 payload。Open 构建排除该完整执行载荷，不能将其当成可直接复用的开源模型适配器。
- Open 构建默认 `protectedBuild=false`、`localApiIncluded=false`；不得通过复制私有 payload、私有 URL 或签名配置来“补齐”开源版本。

## 4. 安全与运行不变量

- API 默认关闭；启用后默认只监听 `127.0.0.1`。
- 每个安装实例生成独立随机 Bearer Token；支持轮换、撤销；不得记录 Token 明文。
- 默认不开放 LAN、公网、反向隧道或端口转发。
- 请求体、输入长度、输出长度、并发、执行时间和 SSE 缓冲均有明确上限。
- 设备操作、文件写入、剪贴板等副作用工具遵循用户确认策略；半自动为默认模式。
- 保留限速、幂等 request ID、任务作用域、deadline 和 cancellation。
- 不实现凭据捕获、风控绕过、设备标识伪装、登录/地区解锁、防封或反检测。
- 未知宿主版本不执行未经验证的 Hook；可选 Hook 失败即放行。

## 5. 批量任务

### Batch A — Kotlin Agent Core（当前）

- [x] 选择 Kotlin Runtime 分支作为基线。
- [x] 新增纯 Kotlin 请求、事件、模型适配器、取消和 Repository 契约。
- [x] 新增确定性任务状态机。
- [x] 新增最小 AgentCore 调度器：适配器调用、增量事件转发、deadline、取消、失败隔离。
- [ ] 运行 Kotlin/JVM 回归测试并修复编译/行为问题。
- [ ] 通过 Open、Legacy、Universal 构建矩阵。

### Batch B — 持久化与工具调度

- [ ] 定义可恢复的 RunRepository 实现和版本化存储格式。
- [ ] 将现有 AgentRunStore 状态映射到新状态机，先做兼容适配，不直接替换旧存储。
- [ ] 接入工具注册表、权限策略、确认状态和幂等执行记录。
- [ ] 测试崩溃恢复、重复调用、并发竞争和取消。

### Batch C — Host Adapter

- [ ] 追踪 DeepSeek 原生请求/响应链路，区分公开源码与受保护 payload。
- [ ] 提取最小可复用的原生模型调用边界。
- [ ] 让 Java/Xposed 层只承担宿主符号定位、请求转换、Flow/增量回传和取消。
- [ ] 分别核验 287、289、290；不以 JVM 测试替代真机验证。

### Batch D — Local API Gateway

- [ ] 实现 loopback HTTP 服务、Bearer Token、请求校验和健康状态。
- [ ] 实现 `/v1/models`、`/v1/chat/completions`。
- [ ] 实现 SSE、断连取消、超时、限流和统一错误格式。
- [ ] 再扩展 `/v1/responses` 和 `/v1/agent/run`。
- [ ] Open 构建独立可用，不依赖 Closed payload。

### Batch E — UI、CI 与发布

- [ ] 增加 API 启停、端口、Token 轮换/撤销和状态展示。
- [ ] 增加任务历史、运行详情、确认与取消入口。
- [ ] CI 覆盖 Kotlin Runtime、Open、Legacy、Universal 构建和 APK 结构。
- [ ] 分别记录 L1 JVM、L2 构建、L3 APK、L4 模拟环境、L5 真机结果。

## 6. 第一阶段验收

- Kotlin Agent Core 在纯 JVM 中独立运行。
- Core 不导入 Android、Xposed、Compose、HTTP Server 或 DeepSeek 混淆类型。
- request ID 重复时拒绝二次创建；状态迁移非法时拒绝。
- 超时、取消、模型适配器异常和事件下游异常均有确定终态。
- 下游断开不继续无意义地收集上游响应。
- 事件与 metadata 有大小上限；内部异常详情不泄露给外部客户端。
- Kotlin Runtime 的 Open/Legacy/Universal 编译路径均包含新增类。

## 7. 验证报告

必须标明提交 SHA、CI Run、JDK/Gradle/SDK、宿主版本与渠道、测试级别、失败项和未验证范围。测试文件存在或某个 JVM job 成功，不代表 Hook 在真实 DeepSeek/LSPosed 设备上已生效。
