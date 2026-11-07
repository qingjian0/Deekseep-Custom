# ROI

ROI 是面向官方 DeepSeek Android 客户端（`com.deepseek.chat`）的 LSPosed / Xposed 项目。当前开发分支正在渐进引入 Kotlin Runtime、Agent Core 与本地 API Gateway，同时保留既有 Java Hook 和宿主兼容边界。

> **开发状态提醒：** 当前开发分支已扩展 OpenAI Chat Completions、Responses、Anthropic Messages、SSE 与工具调用协议桥接；但 Open 版 DeepSeek 原生 Completion Adapter 仍未接通。`GET /v1/models` 仅返回模型目录，三个 Completion 协议入口均保持开放并进入执行门面；原生引擎缺失时按请求返回明确的 `engine_missing`，不在 Open 版本级别隐藏或预先关闭协议入口。工具调用目前只将结构化调用结果返回客户端，不在模块内执行工具；Usage 为估算值并显式标记。请勿将协议路由可访问理解为模型已经可调用。

## 当前工程基线

| 项目 | 当前值 |
|---|---|
| 当前开发基线 | `dev`（唯一日常开发分支） |
| 宿主包名 | `com.deepseek.chat` |
| 模块包名 | `ds.dsh.DeepSeek` |
| Xposed 入口 | `ds.dsh.DeepSeek.Main` |
| Provider authority | `ds.dsh.DeepSeek.XposedService` |
| 目标宿主 | DeepSeek Android 2.7.1 / 2.7.2（versionCode 287 / 289 / 290） |
| 开发架构基线 | `refactor/kotlin-hook-architecture` |

当前开发基线的精确提交和 CI 状态见 [项目状态与交付基线](docs/PROJECT_STATUS.md)。自动发现与跨版本适配方案见 [ROI 自动发现引擎](docs/architecture/ROI_AUTO_DISCOVERY_ENGINE.md)。

## 核心模块

- **Java Hook / Host Compatibility**：保留 Xposed 入口、宿主反射、混淆映射和既有功能 Hook。高风险宿主边界不做机械式整体重写。
- **Kotlin Runtime / Agent Core**：新增任务契约、运行状态机、deadline、取消、事件流、持久化和工具权限策略。
- **Local API Gateway**：本机 HTTP、Bearer Token、请求限制、SSE，以及显式开启后的 `192.168.x.x` 局域网访问和网络变化重绑定。
- **Compose Launcher**：Android 管理应用与工程入口。
- **Legacy / Universal**：保留不同 Xposed 构建与兼容路径，分别验证。

## Local API 当前能力边界

| 接口 | 状态 |
|---|---|
| `GET /v1/models` | 返回配置模型清单；不代表原生执行就绪 |
| `POST /v1/chat/completions` | HTTP/JSON/SSE 路由已实现；原生引擎未就绪时按请求返回 `engine_missing`（HTTP 503） |
| `POST /v1/responses` | 请求/响应与 SSE 协议映射已实现；原生适配器未就绪时返回 503 |
| `POST /v1/messages` | Anthropic Messages 请求/响应与 SSE 映射已实现；原生适配器未就绪时返回 503 |
| `POST /v1/agent/run` | 未实现 |

LAN 默认关闭。开启后只绑定当前检测到的精确 `192.168.x.x` 地址，不绑定 `0.0.0.0`；远端来源限制在 `192.168.0.0/16`。LAN 使用明文 HTTP，只应在可信网络中使用。

协议边界：Chat Completions 与 Responses 的工具调用仅解析并向 API 客户端返回结构化 `tool_calls`；模块不会代替客户端执行工具，且工具调用暂不支持流式模式。Responses 的 `previous_response_id` 状态仅保存在进程内，最多 64 条、30 分钟过期，服务重启后清空。Usage 是估算值，以 `dsh_usage_estimated=true` 标记，不是 DeepSeek tokenizer 的精确计数。 Chat Completions 流式请求只接受 `stream_options.include_usage=false`；`true` 和非 Boolean 值明确返回 HTTP 400。图像、音频和非 text JSON 输出格式仍不支持。

详细说明：[Open Local API Gateway](docs/architecture/OPEN_LOCAL_API_GATEWAY.md)。 [dsmod.probe 源码参考分析](docs/architecture/DSMOD_PROBE_LOCAL_API_REFERENCE.md)。

## 构建与验证

构建入口和环境要求以 [构建说明](docs/BUILDING.md) 及当前分支 Gradle 工程为准。测试状态必须按 L0–L5 分级记录，不能以 JVM 测试或 CI 编译替代真机 Hook 验证。

- [构建说明](docs/BUILDING.md)
- [测试与验证规范](docs/TESTING.md)
- [架构总览](docs/ARCHITECTURE.md)
- [功能范围](docs/FEATURES.md)
- [项目状态](docs/PROJECT_STATUS.md)
- [分支约定](docs/BRANCHING.md)

## 兼容与安全原则

- 未知宿主版本不执行未经验证的 Hook。
- 不猜测或批量改写宿主混淆映射。
- 不改变 Hook 原方法调用次数、异常传播和 Flow 对象身份等兼容契约。
- 未知工具默认拒绝；有副作用的工具按调用逐次确认。
- 不记录 Prompt、Completion、Bearer Token 明文或工具参数。
- 不实现凭据捕获、风控绕过、设备标识伪装、登录/地区解锁或反检测能力。

## 许可

本项目沿用仓库中的 GPL-3.0 许可与上游致谢。完整许可文本见 [LICENSE](LICENSE)。
