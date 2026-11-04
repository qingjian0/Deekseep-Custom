# ROI

ROI 是面向官方 DeepSeek Android 客户端（`com.deepseek.chat`）的 LSPosed / Xposed 项目。当前开发分支正在渐进引入 Kotlin Runtime、Agent Core 与本地 API Gateway，同时保留既有 Java Hook 和宿主兼容边界。

> **开发状态提醒：** 本 README 对应当前工作分支，不等同于稳定 Release。Local API 的 HTTP 服务层已经实现，但 Open 构建的 DeepSeek 原生 Completion Adapter 尚未打通；当前模型路由在适配器不可用时返回 HTTP 503。请勿将“端口可连接”理解为“已经能够调用 DeepSeek 模型”。

## 当前工程基线

| 项目 | 当前值 |
|---|---|
| 工作分支 | `work/roi-agent-core-kotlin` |
| 宿主包名 | `com.deepseek.chat` |
| 模块包名 | `ds.dsh.DeepSeek` |
| Xposed 入口 | `ds.dsh.DeepSeek.Main` |
| Provider authority | `ds.dsh.DeepSeek.XposedService` |
| 目标宿主 | DeepSeek Android 2.7.1 / 2.7.2（versionCode 287 / 289 / 290） |
| 开发架构基线 | `refactor/kotlin-hook-architecture` |

当前工作分支的精确提交和 CI 状态见 [项目状态与交付基线](docs/PROJECT_STATUS.md)。自动发现与跨版本适配方案见 [ROI 自动发现引擎](docs/architecture/ROI_AUTO_DISCOVERY_ENGINE.md)。

## 核心模块

- **Java Hook / Host Compatibility**：保留 Xposed 入口、宿主反射、混淆映射和既有功能 Hook。高风险宿主边界不做机械式整体重写。
- **Kotlin Runtime / Agent Core**：新增任务契约、运行状态机、deadline、取消、事件流、持久化和工具权限策略。
- **Local API Gateway**：本机 HTTP、Bearer Token、请求限制、SSE，以及显式开启后的 `192.168.x.x` 局域网访问和网络变化重绑定。
- **Compose Launcher**：Android 管理应用与工程入口。
- **Legacy / Universal**：保留不同 Xposed 构建与兼容路径，分别验证。

## Local API 当前能力边界

| 接口 | 状态 |
|---|---|
| `GET /v1/models` | HTTP 路由已实现；原生模型适配未就绪时返回 503 |
| `POST /v1/chat/completions` | HTTP/JSON/SSE 路由已实现；原生模型适配未就绪时返回 503 |
| `POST /v1/responses` | 未实现 |
| `POST /v1/agent/run` | 未实现 |

LAN 默认关闭。开启后只绑定当前检测到的精确 `192.168.x.x` 地址，不绑定 `0.0.0.0`；远端来源限制在 `192.168.0.0/16`。LAN 使用明文 HTTP，只应在可信网络中使用。

详细说明：[Open Local API Gateway](docs/architecture/OPEN_LOCAL_API_GATEWAY.md)。

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
