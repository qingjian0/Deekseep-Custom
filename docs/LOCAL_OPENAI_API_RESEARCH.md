# DeepSeek Android 本地 OpenAI 兼容 API：实现调研与设计依据

> 状态：调研文档；不是功能已实现声明。  
> 目标分支：`refactor/full-package-ds-dsh-deepseek`  
> 调研日期：2026-11-01

## 1. 目标与边界

在 Android DeepSeek 客户端进程中复用用户当前登录态和原生会话/传输能力，向本机其他客户端提供 OpenAI-compatible HTTP API。目标不是启动 Python/Node 服务，也不是将请求转发到第三方云 API。

首期目标接口：

- `GET /health`：服务状态、宿主注入/原生传输 readiness。
- `GET /v1/models`：列出当前确实支持的模型/模式。
- `POST /v1/chat/completions`：OpenAI Chat Completions，支持非流式和 SSE 流式。
- 后续按验证结果扩展：DeepThink/reasoning、联网搜索、图片输入、Responses API、工具调用。

安全默认值：

- 默认只绑定 `127.0.0.1`，不默认暴露到局域网。
- 使用可轮换 API Key；比较使用常量时间，日志不得记录 Key、Cookie、授权头或完整敏感请求体。
- 每个客户端会话映射到独立的 DeepSeek 原生会话；不能让不同 API 客户端共享一个固定会话 ID。
- 请求体、图片、并发数、连接数和超时均设上限；客户端断开时取消上游收集。
- 原生聊天不依赖 API Server；API 服务失败不能影响 DeepSeek 原生 UI。
- LAN/TLS/公网隧道必须是显式可选功能，不能作为首期默认行为。

## 2. 当前仓库现状（必须先看清楚）

本分支不是从零开始，但公开源码也不能证明完整网关已经可审计：

1. `Main.startz1(Context)` 已定义 API 启动入口，并通过 `z13.start(appContext, Backend)` 将 HTTP 网关与宿主原生 completion backend 分离。
2. `z2.Backend` / `CompletionRequest` / `DeltaSink` 已定义主 DEX 与网关之间的接口：ready 检查、completion 调用、文本/推理增量、取消、会话标识、原生会话和附件等。
3. `z13` 是反射门面，目标类名为 `ds.dsh.DeepSeek.z1`，把 start/stop、endpoint、port、protocol、API key、状态和流式 sink 包装方法转发给 payload。
4. `z14` 在公开 Open edition 源码中是 stub：`available() == false`、`payloadClass(...) == null`。因此当前公开分支无法从这些文件审计或运行隐藏 payload 内部的 HTTP Server 实现。
5. `LocalApiFloatingOverlay` 已有 API 开关、端口、协议、HTTPS 和运行状态 UI；`z21` 是 companion foreground keep-alive service。
6. `HookAccountLoginSecurity` 中存在会话表、会话上限、并发许可、路由状态和 completion 适配逻辑；应复用并审查这些原生能力，而不是重写 DeepSeek 登录或网络协议。

源码定位：

- [Main.java：startz1 与 Backend 接线](../module/src/ds/dsh/DeepSeek/Main.java)
- [z2.java：主 DEX / gateway 接口契约](../module/src/ds/dsh/DeepSeek/z2.java)
- [z13.java：payload gateway 反射门面](../module/src/ds/dsh/DeepSeek/z13.java)
- [z14.java：Open edition payload stub](../module/src/ds/dsh/DeepSeek/z14.java)
- [LocalApiFloatingOverlay.java：本地 API 控制 UI](../module/src/ds/dsh/DeepSeek/LocalApiFloatingOverlay.java)
- [z21.java：companion keep-alive service](../module/src/ds/dsh/DeepSeek/z21.java)
- [HookAccountLoginSecurity.java：原生 completion/session 相关实现](../module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java)

**结论：** 本项目已有清晰的 backend contract 和 UI/保活框架，但公开版的 payload loader 是关闭态 stub。不能把隐藏 payload 的功能描述成当前公开源码已实现，也不能把 UI 开关等同于 HTTP Server 已启动。若要让 Open edition 真正拥有本地 API，需在公开源码中提供可审计、可构建的 gateway/server 实现，并连接现有 `z2.Backend`。

## 3. 同类项目调研

### 3.1 MiclawApiBridge（最接近的 Xposed / LSPosed 形态）

仓库：[guocheng1378/MiclawApiBridge](https://github.com/guocheng1378/MiclawApiBridge)  
许可证：仓库声明 MIT。  
关键源码：

- [HttpServer.java](https://github.com/guocheng1378/MiclawApiBridge/blob/main/app/src/main/java/io/github/guocheng1378/miclawbridge/HttpServer.java)
- [OpenAiCompat.java](https://github.com/guocheng1378/MiclawApiBridge/blob/main/app/src/main/java/io/github/guocheng1378/miclawbridge/OpenAiCompat.java)
- [HookEntry.java](https://github.com/guocheng1378/MiclawApiBridge/blob/main/app/src/main/java/io/github/guocheng1378/miclawbridge/HookEntry.java)
- [BridgeStarter.java](https://github.com/guocheng1378/MiclawApiBridge/blob/main/app/src/main/java/io/github/guocheng1378/miclawbridge/BridgeStarter.java)
- [README / API endpoints / security](https://github.com/guocheng1378/MiclawApiBridge#readme)

可借鉴的设计：

- 在目标 App 的 `Application.attach` 后启动 bridge，并用 package-ready fallback 兜底；启动入口使用 AtomicBoolean 防止重复启动。
- HTTP listener 与目标 AI 的内部调用拆开；OpenAI JSON 转换单独放在 adapter。
- `/v1/chat/completions`、`/v1/models`、`/health`、API Key、限流、日志、会话 reset 等 API 产品面。
- 默认绑定 `127.0.0.1`，端口冲突时递增避让。
- 支持 SSE 和非流式响应；上游 AI 失败时返回结构化 OpenAI error。

不应照搬的部分（源码审查注意事项）：

- 它使用手写 Socket HTTP parser，读取 `Content-Length` 的简化实现；没有充分展示 chunked request、header/body 上限、keep-alive、多种异常断连等完整 HTTP 兼容处理。新实现应使用成熟、轻量的 Android 可嵌入 HTTP server。
- 它的示例把会话标识默认回退到固定 API_CHAT_ID 的风险值得避免；本项目必须做每客户端会话隔离。
- 示例代码有广泛 CORS、可选空 Token、root 执行端点等产品特性；本项目不需要远程执行 shell，也不应把这些默认带入。
- `HookEntry` 的双入口和 attach 时机可作参考，但本项目已经有自己的 hook 生命周期，不应再引入第二套 Xposed entry lifecycle。

### 3.2 Google AI Edge Gallery Edge Server（Android 原生服务结构）

参考 PR：[google-ai-edge/gallery PR #647 — Edge Server](https://github.com/google-ai-edge/gallery/pull/647)  
代码来源分支：[XR-stb/gallery](https://github.com/XR-stb/gallery)  
许可证：相关仓库及源文件声明 Apache-2.0；若复制代码，必须保留版权与许可证声明并核对依赖许可证。  
关键源码：

- [EdgeServer.kt](https://github.com/XR-stb/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/edgeserver/EdgeServer.kt)
- [EdgeServerManager.kt](https://github.com/XR-stb/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/edgeserver/EdgeServerManager.kt)
- [EdgeServerService.kt](https://github.com/XR-stb/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/edgeserver/EdgeServerService.kt)
- [Edge Server README](https://github.com/XR-stb/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/edgeserver/README.md)

可借鉴的设计：

- HTTP server、manager、foreground service、UI 分层；server 只负责 HTTP/OpenAI 协议，不负责管理宿主模型或登录。
- `/health`、`/v1/models`、`/v1/chat/completions` 三个最小端点。
- SSE 使用流式 response body / pipe 将上游增量传给客户端；非流式和流式分别处理。
- 使用互斥锁限制模型推理并发，并用 CountDownLatch/timeout 将异步推理结果桥接到 HTTP response。
- 显式处理模型未就绪、请求体解析失败、忙碌、超时和上游错误。

需要针对本项目调整：

- Edge Server 服务的是本地加载的 LiteRT 模型；本项目后端是 DeepSeek App 的原生网络/会话能力，不能复用其 prompt formatter 或推理引擎。
- 其示例配置默认 host 为 `0.0.0.0`，没有体现 API Key 鉴权；本项目必须改为 `127.0.0.1` + 强制 Token。
- 其单推理锁适合单模型本地推理；本项目应按原生 DeepSeek 并发限制、会话锁及取消语义设计，不应盲目全局串行化。

### 3.3 sums001/Deepseek-API（DeepSeek 会话与协议语义）

仓库：[sums001/Deepseek-API](https://github.com/sums001/Deepseek-API)  
许可证：MIT。  
说明：[README](https://github.com/sums001/Deepseek-API#readme)

可借鉴的概念：

- 将消费者 DeepSeek 会话封装成 OpenAI-compatible `/v1/chat/completions`。
- 对 `stream`、多轮 `conversation_id`、reasoning/DeepThink、search 做显式映射。
- 明确记录不支持或被忽略的 OpenAI 参数，不假装所有参数都有原生语义。
- 视觉输入、token usage 等能力应在实际实现后才声明支持。

边界与风险：

- 这是 Python/FastAPI 方向的 Web 会话逆向项目，不是 Android/Xposed 可直接移植实现。
- 其 README 声明通过自动化消费者网页会话工作；不能把它当作官方 API，也必须评估 DeepSeek 服务条款、账号风控和接口变动风险。
- 本项目优先调用已注入客户端中的原生 backend，不复刻网页登录、Cookie 抓取或网页登录协议。

## 4. 建议的目标架构

```text
OpenAI-compatible client
        |
        | HTTP / JSON / SSE
        v
LocalApiHttpServer
  - bind 127.0.0.1
  - API key authentication
  - request limits / rate limits
  - /health, /v1/models, /v1/chat/completions
        |
        v
OpenAiProtocolAdapter
  - validate messages / parameters
  - map model, reasoning, search, images
  - normalize OpenAI response / error / SSE
        |
        v
LocalApiSessionRegistry
  - client key -> native conversation
  - per-session lock / expiry / max sessions
        |
        v
z2.Backend (existing contract)
        |
        v
Main.executeLocalApiCompletion(...)
        |
        v
DeepSeek native transport + logged-in app session
```

职责划分：

- `LocalApiHttpServer`：生命周期、HTTP 解析、连接、状态码、超时、鉴权；不接触 DeepSeek 混淆类。
- `OpenAiProtocolAdapter`：OpenAI schema 与 `z2.CompletionRequest` / `CompletionResult` 转换；参数只在有真实原生能力时生效。
- `LocalApiSessionRegistry`：把客户端提供的 session/user 标识映射为内部 native conversation；外部标识需规范化/散列后存储，不能直接把任意客户端字符串作为路径或文件名。
- `Backend`：复用当前 `Main.startz1` 注入的 `z2.Backend`。`DeltaSink.onText/onReasoning` 映射 SSE delta；客户端断开时触发取消。
- `z21`：继续负责现有后台保活职责；Server 的真实运行状态必须由 gateway 回报，不能只看 UI 开关或 foreground service 存活。

## 5. 首期 API 合约建议

| 接口 | 首期行为 |
|---|---|
| `GET /health` | 返回 gateway running、backend ready、native transport ready、端口；不泄露账号/Token |
| `GET /v1/models` | 返回已验证支持的模型 ID；不能返回虚构模型 |
| `POST /v1/chat/completions` | 支持 messages、model、stream、max_tokens（仅后端可控时）、reasoning/search（经明确扩展字段或模型映射） |
| `Authorization: Bearer <key>` | 首期强制鉴权；Key 可轮换；错误统一返回 OpenAI error envelope |

SSE 行为：

- `Content-Type: text/event-stream`、`Cache-Control: no-cache`。
- 每个请求使用稳定的 completion ID。
- 按增量发 `choices[0].delta`，最后发送 finish chunk 与 `data: [DONE]`。
- 文本和 reasoning 分别映射；不把内部调试字段发给客户端。
- 客户端断开后停止上游收集并释放会话/并发许可。
- 错误发生在 HTTP headers 发送前返回正确 HTTP 状态；headers 发送后通过 SSE error event 结束，不能再尝试发送第二个 HTTP response。

## 6. 实施顺序与验收

1. 审计 `z2.Backend` 与 `Main.executeLocalApiCompletion` 的真实行为，补齐单元测试；确认 Open edition 能访问原生 backend。
2. 在公开源码中新增可审计的 server，不依赖 `z14` 隐藏 payload；优先选轻量成熟 HTTP 库，先评估依赖体积、许可证、Android API 兼容和 SSE chunked response。
3. 先实现 loopback + 强制 API Key + health/models + 非流式 chat。
4. 再实现 SSE、断连取消、请求超时、并发/会话隔离。
5. 最后接 reasoning/search、图片、Responses API 等扩展。
6. 增加 instrumentation / regression tests：无 token、错误 token、非法 JSON、超大 body、端口占用、并发请求、会话隔离、stream 正常结束、stream 中断、backend 未就绪、宿主进程重启、服务 stop/start。
7. 在真实 Android + LSPosed + DeepSeek 登录态环境验证；CI 编译成功不等于注入和原生 completion 实测成功。

## 7. 参考来源与许可证记录

| 来源 | 用途 | 许可证 / 注意事项 |
|---|---|---|
| [MiclawApiBridge](https://github.com/guocheng1378/MiclawApiBridge) | Xposed 目标进程启动、loopback listener、OpenAI adapter、鉴权和 API 面 | MIT；如复制代码需保留版权与许可证 |
| [XR-stb/gallery Edge Server](https://github.com/XR-stb/gallery/tree/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/edgeserver) | Android server/manager/foreground service 分层、SSE、超时与并发控制 | Apache-2.0；复制代码需保留 NOTICE/许可证，NanoHTTPD 另有 BSD-3-Clause |
| [sums001/Deepseek-API](https://github.com/sums001/Deepseek-API) | DeepSeek 消费者会话到 OpenAI schema 的语义映射 | MIT；Python Web 逆向实现，不直接移植；需注意服务条款与风控 |
| [OpenAI Chat Completions API reference](https://platform.openai.com/docs/api-reference/chat) | 兼容性字段与请求/响应语义 | 协议参考，不代表本地实现获得 OpenAI 认证 |

## 8. 当前结论

- 最接近模块注入形态的是 MiclawApiBridge；最值得借鉴 Android 生命周期/后台服务结构的是 Edge Server。
- 本项目已有 `z2.Backend` 抽象，适合把 HTTP 层与 DeepSeek 原生 transport 解耦。
- 当前公开 Open edition 的 `z14` stub 不提供 payload class；`z13` facade 因此不能证明 gateway 已在公开版可用。
- 推荐实现一个公开、可测试的本地 gateway，并复用现有 backend/session 逻辑；不移植 Python、Node、网页登录自动化或 root shell 执行功能。
- 本文仅为调研与设计记录；在完成实现、构建和真机验证前，不得将本地 API 标注为已交付。
