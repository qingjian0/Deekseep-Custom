# 项目状态与交付基线

> 状态日期：2026-11-03  
> 适用分支：`work/roi-agent-core-kotlin`  
> 最近一次 Runtime 修复提交：`c87ce1b67e57788ff8f53777f4d583f5a00a6151`；后续提交用于文档校准  
> 宿主：官方 DeepSeek Android `com.deepseek.chat`  
> 宿主版本目标：国内版 2.7.1（versionCode 287）、国内版 2.7.2（289）、Google Play / 国际版 2.7.2（290）

本文记录当前工作分支的实现状态，不代表 `main` 或公开 Release 已包含这些改动。状态只在有源码或 CI 证据时标记为完成。

## 1. 当前结论

项目正从传统 Java Hook 模块逐步演进为 **Kotlin Runtime + Agent Core + Local API Gateway + DeepSeek 原生宿主适配**。这是渐进迁移，不是整体重写：

- 既有 Java/Xposed Hook 与宿主反射边界继续保留。
- Kotlin 承担新增 Runtime 契约、Agent 调度、确定性策略、持久化与部分 UI。
- Local API 的 HTTP、认证、请求限制、SSE 和 LAN 监听已有实现。
- **Open 构建的 DeepSeek 原生 Completion Adapter 尚未打通**。当前 `/v1/models` 与 `/v1/chat/completions` 在适配器不可用时应返回 503；不能把 HTTP 路由可访问描述成模型调用成功。

## 2. 分支与提交

| 项目 | 当前值 |
|---|---|
| 工作分支 | `work/roi-agent-core-kotlin` |
| 最近一次 Runtime 修复 | `c87ce1b67e57788ff8f53777f4d583f5a00a6151` |
| 最近一次文档整理提交（提交前快照） | `20320a2d36a2da456f0c768bc36ced15d1fae856` |
| 前一提交 | `4f80b5a658e9f22910292cf569f403e8b6f540d8` |
| Kotlin 架构基线 | `refactor/kotlin-hook-architecture` |
| 与架构基线关系 | 领先 77 个提交、落后 0 个提交（最近一次 compare 结果） |
| 主干策略 | 不自动合并 `main`；不重置或覆盖工作分支 |

每次批量开发前应重新读取远端 HEAD。本文中的提交和比较数字是本次状态快照，不应被当作永久值。

## 3. 模块状态

### 3.1 Hook 与宿主兼容

- Xposed 入口和现有 Java Hook 继续作为宿主兼容边界。
- `HostCompat` / `HostRemap` 负责宿主版本识别和混淆映射。
- 287 / 289 / 290 映射资产存在并纳入回归检查；映射文件存在不等于对应版本真机验证通过。
- 不整体翻译 `Main.java` 或 `HookChatPipeline.java`。Flow 对象身份、原方法 exactly-once、异常传播、Hook 时序和混淆符号都属于高风险兼容契约。

### 3.2 Kotlin Runtime / Agent Core

主要实现位于 `module-kotlin-runtime/`，包括：

- Agent 请求、事件、模型适配器和运行契约；
- 运行状态机、deadline、取消和异常隔离；
- request ID 幂等；
- 持久化 Repository、版本化存储、恢复与容量限制；
- 工具权限策略：未知工具默认拒绝；只读工具按策略处理；文件写入、设备控制和外部网络等副作用需要逐次确认。

当前应继续验证故障注入、并发、重复调用、持久化损坏和恢复行为，并确认策略层真正接入执行器。接口和策略存在不等于所有工具已经端到端可执行。

### 3.3 Local API Gateway

实现入口：`module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java`

| 路由 | 当前状态 |
|---|---|
| `GET /v1/models` | HTTP 路由已实现；原生适配器未就绪时返回 503 |
| `POST /v1/chat/completions` | HTTP/JSON/SSE 路由已实现；原生适配器未就绪时返回 503 |
| `POST /v1/responses` | 未实现 |
| `POST /v1/agent/run` | 未实现 |

网络与安全边界：

- 默认关闭；默认只监听 `127.0.0.1`。
- 用户显式开启 LAN 后，监听当前发现的精确 `192.168.x.x` IPv4 地址，不绑定 `0.0.0.0`。
- 远端来源限制在 `192.168.0.0/16`；网络变化由 Android 默认网络回调触发重新绑定。
- 每安装实例独立 Bearer Token，支持轮换；请求体、Prompt、并发与队列均有限制。
- LAN 使用明文 HTTP，仅适用于可信局域网；不提供 TLS、公网监听或隧道。
- 不记录 Prompt、Completion、Token 明文或工具参数。

### 3.4 Native Completion Adapter（当前首要阻塞）

现有 Java 侧包含 `z2` Backend/Completion 契约、`z13` 反射门面，以及 `HookAccountLoginSecurity.executeLocalApiCompletion()` 等旧路径。旧路径依赖 Closed 专属 `z18` payload；Open 构建不包含该执行载荷。

因此当前不得：

- 将 `z13` 反射门面描述成已经完成的原生执行引擎；
- 将 Closed payload 当成 Open 版可复用实现；
- 仅修改 `BuildInfo.LOCAL_API_INCLUDED` 就宣称完成适配；
- 将 503 `model_not_ready` 视为模型服务可用。

下一阶段必须追踪官方客户端的请求构造、PoW 管理、网络 Transport、Flow/SSE 事件、响应消费、会话上下文、取消与超时，找出可在 Open 版合法实现的最小原生调用边界。

### 3.5 诊断 UI

最近提交为 Local API 页面加入 Open 原生 Completion Probe 状态与复制诊断入口。位置在 DeepSeek 模块设置的“工程”→“本地 API · 实验性”页面中。诊断只读，不会自动发起 Completion，也不读取聊天正文或账号凭据。

只有安装包含相应提交的 APK 后才会出现该入口；旧安装包不包含此 UI。

## 4. 最近 CI 记录

| Run | Commit | 结果 | 说明 |
|---|---|---|---|
| [41647101079](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41647101079) | `4f80b5a` | Failure | Android Open build and lint 成功；Regression 因两条新 Toast 文案缺少精确译文失败 |
| [41648546478](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41648546478) | `c87ce1b` | Cancelled | 后续同分支提交触发并发取消 |
| [41649071834](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41649071834) | `c34d8ba` | Success | Regression tests 与 Android Open build and lint 均成功；Open APK 结构、对齐和 CI 临时签名检查通过 |

Run `41649071834` 是本轮已核实的完整绿色 CI。它验证的是 JVM/脚本回归和 Open Android 构建，不是宿主真机 Completion。

## 5. 下一批任务顺序

1. **CI 回归闭环**：确认 `c87ce1b` 工作流结果；若失败，读取失败 Job 日志并修复根因，不删除测试、不降低断言。
2. **Native Adapter 证据调查**：见 [NATIVE_ADAPTER.md](NATIVE_ADAPTER.md)，继续核验 287/289/290 的请求、PoW、Transport、Flow 和响应消费。
3. **Native Adapter 调查**：围绕 287/289/290 的官方请求链路建立符号、调用顺序、线程与生命周期证据；优先只读诊断和可验证的窄边界。
4. **Agent Core 验收**：补充故障注入、恢复、幂等、权限确认和执行器集成测试。
5. **构建矩阵**：Open、Legacy、Universal 分别验证；再分别记录模拟环境与真机结果。
6. **交付**：提交 SHA、CI Run、构建产物、校验和及未验证范围分开记录。

## 6. 验证口径

遵循 [测试与验证规范](TESTING.md) 的 L0–L5 分级：

- L1 JVM/脚本回归通过，不代表 Android Hook 已生效。
- L2 Android 构建通过，不代表 APK 已在设备安装。
- L3 APK 结构检查通过，不代表宿主行为正确。
- L4 模拟器/沙盒通过，不代表真实 DeepSeek/Xposed 兼容。
- 只有明确记录设备、宿主渠道与 versionCode、LSPosed 环境和测试步骤的 L5 结果，才可称为对应组合的真机验证。

## 7. 相关文档

- [架构总览](ARCHITECTURE.md)
- [功能范围](FEATURES.md)
- [构建说明](BUILDING.md)
- [分支约定](BRANCHING.md)
- [测试与验证规范](TESTING.md)
- [Local API Gateway](architecture/OPEN_LOCAL_API_GATEWAY.md)
- [Native Completion Adapter 调查](NATIVE_ADAPTER.md)
- [ROI Agent Core 与 Gateway](architecture/ROI_AGENT_CORE_GATEWAY.md)


## 8. 2026-11-03 后续状态补记

### 8.1 2.7.x 旧捕获链断层

提交 `4cf1d98` 与 `2f23d0c` 补充了 `docs/NATIVE_ADAPTER.md` 和 `module/test-native-adapter-fail-closed.sh`，记录并保护以下源码事实：

- 2.7.x（versionCode 287/289/290）不再安装旧版 `network.payload-capture` 与 `chat.pow-manager-capture` 路径。
- `liveQ71` / `liveR92` 的已知旧赋值入口依赖上述旧捕获链；2.7.x limited 路径也没有安装对应旧捕获。
- Backend `isReady()` 仍要求 `BuildInfo.LOCAL_API_INCLUDED`、宿主 ClassLoader、`liveR92` 与 `liveQ71`。因此不能把 2.7.x 的版本映射识别或 full mapping 状态等同于 Backend 已就绪。
- 回归保护覆盖 readiness 条件、旧 Hook 安装限制、HostV271 limited 白名单和旧入口锚点。未通过删除 readiness 条件、伪造对象或猜测混淆字段来绕过。

### 8.2 CI 记录更新

| Run | Commit | 结果 | 说明 |
|---|---|---|---|
| [41654682315](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41654682315) | `2f23d0c2ea435e0269eba0fb2757fc4e7ce19b99` | Success | Android Open build and lint、APK 结构/对齐/签名检查、产物上传、Regression tests、传统 Xposed Core 构建及宿主兼容回归全部成功 |

该 Run 只证明当前源码回归与 Open Android 构建通过，不证明 2.7.x 原生 Completion 已接通，也不构成模拟器或真机验证。

### 8.3 当前 Native Adapter 阻塞与下一步

- Open Native Completion Adapter 仍未实现；Gateway 在适配器不可用时继续 fail-closed 返回 503。
- 2.7.x 需要独立的 Request / Challenge / Transport / Flow 发现与绑定证据，不能复用未经证明的旧版 q71 / R92 对象路径。
- Closed payload 中 `z18` 的定义加载器、真实声明类与执行签名仍未证实；不得与仓库普通 `z18` 类混淆。
- 下一步优先建立 287、289、290 分渠道证据矩阵，并检查仓库内所有 `liveQ71` / `liveR92` 写入点；若无官方 APK 静态证据或设备验证，不实现猜测性调用。


### 8.4 Transport 调用点复核补充

复核 `Main.java` 约 9312、9327、9337 行的 `HookChatPipeline.hookTransport(...)` 调用后，确认它们属于 `installNetworkPayloadCapture()` 内部的旧版候选发现分支，不是独立的 2.7.x 安装入口。该安装步骤本身在 `HostCompat.isV271()` 时被跳过。因此这些调用不会自动形成新的 2.7.x `liveR92` 捕获通道。详细边界已补入 `docs/NATIVE_ADAPTER.md`。

该复核收窄了源码结论，但仍不替代完整仓库赋值点检索、官方 APK 静态证据或受控设备验证。


### 8.5 探针调度与 2.7.2 国际版识别修正（2026-11-03）

- Open 版只在 Local API 显式启用后调度宿主结构探针。
- 新增 `OpenNativeCompletionProbe.scheduleHostInspection()`，通过原子标志保证只调度一次，并在 daemon worker 上执行，避免 DEX 全量扫描占用 Activity/startup 调用线程。
- 探针仍只输出结构签名与扫描计数，不实例化宿主类、不调用候选宿主方法、不读取对象字段值。尚无真机耗时数据，不宣称已测得启动提速。
- 修复 2.7.2 Google Play / 国际版 code290 渠道标志：识别 `c02 + nw1` 后显式设置 `googlePlay = true`；287/289 保持国内版标志。
- `module/test-native-adapter-fail-closed.sh` 与 `module/test-hostmap-regression.sh` 已增加对应源码回归保护。

最新完整 CI：
- [Run 41660387191](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41660387191) — Success。
- Regression tests、传统 Xposed Core 构建、Host compatibility / feature regression、Android Open build and lint、APK 结构/对齐/签名检查、APK 与 Lint 报告上传全部成功。
- CI 成功仅代表当前构建和回归通过，不代表 2.7.x Native Completion 已接通或真机验证完成。
