# 功能范围与实现状态

> 本文描述当前工作分支的功能边界。功能代码、设置入口、CI 回归和真实宿主可用性是不同层次；未经 L5 真机验证的项目不会标记为“已在宿主可用”。

## 1. Hook 与聊天功能

仓库保留并维护多类 Java Hook，包括：

- 聊天请求/响应管线、提示词注入与编辑重发相关修复；
- 思考内容与正文分离、消息显示与复制辅助；
- 会话管理、历史消息、导出/备份相关工具；
- 附件、图片及专家流相关兼容逻辑；
- UI、主题、输入框与宿主界面增强；
- 账号/登录安全边界及相关 Hook；
- Agent 与工具交互旧管线。

具体功能是否适配某一宿主版本，必须以 `HostCompat` / `HostRemap`、对应测试和设备验证为准。映射表包含 287 / 289 / 290 不代表全部 Hook 在三种宿主上均可用。

## 2. Kotlin Runtime 与 Agent Core

当前工作分支已加入 Kotlin-first Runtime 构件：

- 请求、事件、模型适配器与运行结果契约；
- Agent 运行状态机；
- request ID 幂等、deadline 与取消；
- 增量事件传递和异常隔离；
- 持久化运行记录、版本化格式和恢复路径；
- 工具权限策略：未知工具默认拒绝；只读工具按策略执行；文件写入、设备控制、外部网络等副作用操作需要逐次确认。

这些构件仍处于持续验收阶段。特别是工具权限策略与真实工具执行器的集成、持久化故障注入和并发恢复，不能仅凭类或接口存在就标记为完整端到端功能。

## 3. Local API Gateway

已实现的服务层能力：

- loopback HTTP 服务；
- Bearer Token 认证与轮换；
- 请求体、Prompt 长度、并发数和队列上限；
- OpenAI Chat Completions 风格 JSON 请求；
- SSE 增量输出；
- 显式启用的 `192.168.x.x` LAN listener；
- Android 网络变化监听与 LAN listener 重绑定；
- 诊断状态导出。

路由状态：

| 路由 | 当前状态 |
|---|---|
| `GET /v1/models` | HTTP 路由已实现；Native Adapter 未就绪时返回 503 |
| `POST /v1/chat/completions` | HTTP/JSON/SSE 已实现；Native Adapter 未就绪时返回 503 |
| `POST /v1/responses` | 未实现 |
| `POST /v1/agent/run` | 未实现 |

**重要限制：** Open 构建的 DeepSeek 原生 Completion Adapter 尚未完成。当前 Gateway 不应被描述为已经能够调用宿主模型。LAN 使用明文 HTTP，仅限可信网络；不绑定 `0.0.0.0`，不提供公网隧道。

详见 [Local API Gateway 架构说明](architecture/OPEN_LOCAL_API_GATEWAY.md)。

## 4. Conversation Relay

仓库保留面向外部 Provider 的 Conversation Relay 逻辑，并已将部分确定性策略迁移到 Kotlin，包括：

- Endpoint / 认证头 / 默认模型 / timeout 策略；
- SSE line framing；
- OpenAI reasoning/content 与 Anthropic thinking/text 事件分类。

Conversation Relay 是外部 Provider 转发能力，与本地 Gateway 调用 DeepSeek 原生模型不是同一条链路。

## 5. UI 与诊断

- 模块内设置页提供功能入口与工程设置。
- Local API 页面包含监听、Token、请求统计等配置。
- 新增 Open 原生 Completion Probe 诊断状态与复制入口；该诊断为只读，不会自动执行 Completion。
- 安装包必须包含对应提交，旧版 APK 不会显示新诊断 UI。

## 6. 安全边界

- 未知宿主版本不执行未经验证的 Hook。
- 未知工具默认拒绝；副作用工具逐次确认。
- 不记录 Prompt、Completion、Bearer Token 明文或工具参数。
- 不实现密码/凭据捕获、风控绕过、设备标识伪装、登录/地区解锁、防封或反检测能力。
- LAN 明文 HTTP 仅限可信局域网，不得暴露到公网。

## 7. 如何判断功能是否完成

请同时核对：

1. 源码是否实现；
2. 对应回归是否通过；
3. 目标变体是否构建成功；
4. APK 是否通过结构检查；
5. 是否在模拟环境验证；
6. 是否在对应 DeepSeek 版本、渠道与 LSPosed 设备上完成真机验证。

验证等级定义见 [TESTING.md](TESTING.md)，当前状态见 [PROJECT_STATUS.md](PROJECT_STATUS.md)。
