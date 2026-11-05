# 项目状态与交付基线

 > 项目名称：ROI  
> 状态日期：2026-11-05  
> 适用分支：`dev`  
> 最近一次历史完整 CI 成功：`c0cec5256e84917781b10591f4268c13a49ab42d`；Run `41836377762`（历史记录，不代表当前 HEAD）  
> R2.5.3 当前代码已提交至 `dev`；以分支最新 HEAD 为准。最新 GitHub Actions 检查仍在排队，尚无完成结果  
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
| 工作分支 | `dev` |
| 最近一次 Runtime 观察修复 | R2.5.3 sourceScope 精确归属（见当前 HEAD） |
| 最近一次 ROI 扫描预算实现 | `cca035d6b319a5ed0ab897bee6b1fb2a3c36589b` |
| 最近一次历史完整 CI | [Run `41836377762`](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41836377762) — success；R2.5.3 CI pending |
| Kotlin 架构基线 | `refactor/kotlin-hook-architecture`（历史基线） |
| 与架构基线关系 | 不作为当前 `dev` 的实时 ahead/behind 指标 |
| 主干策略 | 不自动合并 `main`；不重置或覆盖工作分支 |

每次批量开发前应重新读取远端 HEAD。本文中的提交和比较数字是本次状态快照，不应被当作永久值。旧工作分支和早期 CI 记录仅作历史记录，不代表当前工作分支。

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

Run `41649071834` 是较早阶段的绿色基线。最新完整绿色 CI 为 [Run `41836377762`](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41836377762)，对应 `dev` 提交 `c0cec525`，Regression、ROI fast audit、Android Open build/Lint 均通过。该 Run 验证的是 JVM/脚本回归和 Open Android 构建，不是宿主真机 Completion。

## 5. 下一批任务顺序

1. **R2 运行时证据**：在 code289 真机上验证 `zn3.o` 与 `jw3.g` 是否出现真实嵌套调用，以及同一请求对象是否透传；只记录结构元数据，不读取请求字段。
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

## 7. ROI 自动发现引擎

- 项目名称统一为 **ROI**；仓库名、Android 包名、Xposed 入口和既有命名空间保持原样，避免无关的兼容性变更。
- 已新增 [ROI 自动发现与跨版本 Hook 适配引擎](architecture/ROI_AUTO_DISCOVERY_ENGINE.md) 架构决策草案。
- 当前未知版本仍会在功能 Hook 安装前被拒绝；只读发现入口尚未接入，自动发现、结构指纹、调用图分析和自动映射均未实现。
- 最新探针回归修复提交 `41a569b` 的 CI Run `41783005479` 已通过；架构文档提交 `83f8c9e` 的 CI 正在排队/运行，不能与前一 Run 混为一谈。

## 7A. Version-independent Hook Resolution（2026-11-04）

- 已新增纯 Java `RoiHookResolver` 决策核心：按证据族去重、加权评分、冲突拒绝、关键证据硬门槛，以及默认 discovery-only 策略。
- 已新增独立可执行回归脚本 `module/test-roi-hook-resolver.sh`，并接入 CI Regression job。
- 自动激活授权还要求适配规则批准、精确宿主指纹匹配、语义角色 allowlist 与显式开启策略；Resolver 只输出 installer authorization，不把候选标记为 ACTIVE，也不直接安装 Hook。
- 当前仅完成 R0。真实 DEX 候选接入、跨版本调用链相关性验证、Adapter Registry 集成、安装后验证与回滚仍未完成；不得宣称任意版本自动 Hook 已实现。

## 7B. ROI R1 / CI Audit 0.5（2026-11-04）

- DexKit 字符串锚点候选已接入 `RoiHookResolver`，但只提供低置信度、未确认的宿主诊断与方法形状证据；策略仍为 discovery-only，不允许安装 Hook。
- 候选报告增加 resolver state、score、activationAuthorized 和 reason；新增 DEX 方法描述符解析，提取 owner、method、参数数量和返回描述符。
- R1.2 增加 DexKit 方法结构观察（access flags、参数/返回类型、opcode 名称）及每个候选最多 12 条 incoming/outgoing 静态调用关系；查询显式初始化 DexKit 的 invoke/caller lazy native indexes，避免未初始化索引时读取 MethodData 关系。
- 静态调用关系仍未确认运行时执行；宿主身份、调用链执行、参数/返回契约和生命周期证据仍未完成，未知版本继续只读。
- 描述符解析只证明结构格式有效，不证明业务语义等价；候选仍处于 DISCOVERED，未知宿主不安装 Hook。
- 新增并行 `roi-fast-audit`，独立运行 Resolver 与 DexKit discovery 回归；完整构建和原有安全回归仍保留。
- 修复 Resolver 弱候选测试夹具，补充重复证据、角色不匹配、非法置信度和阈值校验。
- CI Audit 0.5 仅降低非关键审查摩擦，不豁免编译、核心测试、ABI/ELF、APK 校验或 Hook 安全门槛。
- 详细记录见 [ROI CI Audit 0.5](architecture/ROI_CI_AUDIT_0_5.md)。CI 是否通过以本批次 GitHub Actions 实际运行结果为准。

## 8. 相关文档

- [架构总览](ARCHITECTURE.md)
- [ROI 自动发现引擎](architecture/ROI_AUTO_DISCOVERY_ENGINE.md)
- [ROI Version-independent Hook Resolution](architecture/ROI_HOOK_RESOLUTION.md)
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
- 修复 2.7.2 Google Play / 国际版 code290 渠道识别：保留私有 `googlePlay` 作为旧 2.3.x 符号映射选择器；`isGooglePlay()` 保留为旧混淆映射渠道选择器；新增 `isInternationalChannel()` 返回 `googlePlay || isIntl27()`。渠道诊断及仅限国内版的隐藏提示词功能使用实际渠道判断，旧映射分支继续使用原选择器，避免 code290 被误导入旧 Google Play 混淆表。
- `module/test-native-adapter-fail-closed.sh` 与 `module/test-hostmap-regression.sh` 已增加对应源码回归保护。

最新完整 CI：
- [Run 41660387191](https://github.com/qingjian0/Deekseep-Custom/actions/runs/41660387191) — Success。
- Regression tests、传统 Xposed Core 构建、Host compatibility / feature regression、Android Open build and lint、APK 结构/对齐/签名检查、APK 与 Lint 报告上传全部成功。
- CI 成功仅代表当前构建和回归通过，不代表 2.7.x Native Completion 已接通或真机验证完成。

## 7C. ROI R1.2 — bounded method structure and call relations (2026-11-04)

- DexKit 2.3.0 MethodData metadata is now read without resolving host classes: raw access flags, parameter type names, return type name, and opcode names.
- Candidate call neighborhoods now include outgoing invoke descriptors and incoming caller descriptors, each capped at 12 edges per direction. Parameter metadata is capped at 16 values and opcode names at 8; omitted counts are reported.
- Static call edges are explicitly unconfirmed CALL_GRAPH evidence. They do not prove request-path execution and cannot satisfy the resolver's verified evidence gates.
- The resolver remains discovery-only; no Hook installer, host method invocation, automatic adapter approval, or unknown-version activation was added.
- Corrected a stale DexKit discovery assertion that expected the previous method-shape confidence contract. Added static assertions for metadata accessors and bounded call edges.
- Verification status: GitHub combined status currently returns an empty status list for the queried commit. Treat CI as pending/unverified; no local Gradle execution or real-device verification is claimed.

## 7D. ROI R1.3 — bounded candidate graph aggregation (2026-11-04)

- 新增纯 Java `RoiCandidateGraph`，在单个 DexKit bridge 扫描范围内，把多个字符串锚点命中的相同 method descriptor 合并为单一节点，并合并 anchor / invokes / callers 观测。
- 调用边统一为 caller → callee：候选的 invokes 形成 candidate → target；候选的 callers 形成 caller → candidate。重复边在应用边上限前后都只计一次。
- 输出 observed、unique nodes、duplicate observations、retained edges、unresolved external references、connected components、omitted nodes / edges。
- 每个 bridge scan 图限制为 128 个节点、256 条保留边；诊断报告最多输出 32 条边。静态图不加载宿主类、不调用宿主方法。
- 新增独立 Java 回归测试：跨锚点去重、边方向、重复边、节点/边上限、外部引用、循环和孤立节点。ROI fast audit 与完整 Regression 均执行新脚本。
- 当前图只反映静态 DEX 引用关系，不负责 Request / Transport / Response 语义分类，不证明真实请求路径，也不授权 Hook 激活。
- 验证边界：源码与 CI 工作流已更新；尚未取得本批次 GitHub Actions 成功结果，也未在本地或真机执行。

## 7E. ROI R2.0 — conservative network role hints (2026-11-04)

- 新增 `RoiSemanticRoleHints`，仅检查候选方法直接调用的 DEX 方法描述符，对少量可识别的 OkHttp / Retrofit / URLConnection / Gson / Moshi / Jackson / JSONObject API 输出 Request Builder、Transport、Response Parser 提示。
- 只输出正向静态提示和命中的外部 API 描述符；未知或混淆引用保持 unresolved。一个方法可有多个提示；没有命中不代表其不属于该角色。
- 提示带 `direct_static_invoke_reference` 来源，不写入 Resolver 证据，不改变 candidate role=`unresolved`，不授权 Hook。
- 新增独立 JVM 测试覆盖三类提示及未知引用，并接入 ROI fast audit 与完整 Regression。
- 验证边界：GitHub Actions 尚未返回本批次可核实的成功状态；未声称本地编译或真机验证通过。

## 7F. ROI R2.1 — bounded runtime invocation evidence (2026-11-04)

- 新增纯 Java `RoiRuntimeInvocationRecorder`：只接受显式可信既有回调来源的事件，不安装 Hook、不加载宿主类、不调用宿主方法。
- 事件仅含 opaque request key、已批准宿主指纹、候选方法描述符、阶段、时间戳、线程 ID 和来源标记；不接受或保存 prompt、请求/响应正文、headers、Authorization 或 token。
- 默认关闭；必须同时满足已知且批准的宿主身份、显式启用可信观察入口。只读策略拒绝所有事件，未知版本保持只读。
- 记录器采用同步有界缓冲，按容量与 TTL 清理；允许异步调用跨线程，但必须以相同 request key 和宿主指纹关联。
- 只有真实记录到 REQUEST_BUILT、TRANSPORT_ENTERED、RESPONSE_RECEIVED 三个阶段，才确认最小请求链；RESPONSE_PARSED 是补充阶段。缺失任一必要阶段均不确认。
- 已新增独立 JVM 测试覆盖只读拒绝、跨线程关联、缺失阶段、容量、TTL、无效/非可信事件及并发写入，并接入 ROI fast audit 与完整 Regression。
- 当前尚未接入任何宿主回调，不能声称已捕获真实请求链；本批次 CI 尚待核验，未声称本地或真机测试通过。


### ROI R1.3 source identity

Added process-local source scopes for runtime ClassLoader and APK observations. Candidate IDs combine source scope and method signature, allowing same-process rescans to correlate while separating identical signatures from different live loaders or APK sources. IDs are not claimed to persist across process restarts. Discovery remains read-only; activation remains disabled.


## 7G. ROI R1.4 — per-scan DexKit work budgets (2026-11-04)

- Each DexKit scan pass now shares a bounded work budget across ClassLoader and APK bridges: at most 32 native query starts, 96 emitted candidate inspections, and a 45-second soft deadline.
- Query admission is checked before each native query; candidate admission is checked before each candidate is inspected. Budget stop reason and counters are included in the read-only report.
- The last admitted query is allowed to finish processing its bounded candidate results even when it reaches the query-count limit.
- The deadline is soft: it prevents additional work from starting but cannot interrupt a JNI query already in progress. Bridge creation and individual native query latency therefore remain non-preemptible.
- Added pure-Java tests for query/candidate caps, zero limits, deadline behavior, independent counters, and the final admitted query edge case. Tests are wired into ROI fast audit and full Regression.
- Full CI Run 41834504879 on 96baef4a passed ROI fast audit, Regression, traditional Xposed Core build, Android Open build/Lint, APK structure/alignment/signature checks, and artifact upload.

## 7H. ROI R2.2 — synchronous runtime call-stack correlation (2026-11-04)

- Extended the existing narrowly scoped Open mainland 2.7.2/code289 runtime observer. No additional host methods are hooked.
- The observer now reports the direct parent method descriptor and whether the first argument is the same object reference as the root call's first argument.
- The root request reference is retained only inside the current thread-local call frame; only object identity comparison and class/member metadata are used. No request fields or payload values are read.
- This can establish a synchronous nested runtime call edge and same-object pass-through if the two existing observed methods nest on the same thread.
- It does not establish asynchronous Flow execution, socket/network entry, response receipt/parsing, or a complete request chain. No Hook activation or adapter readiness is implied.
- CI for this R2.2 change is pending until its own commit's workflow completes; no device observation is claimed.


## 7I. ROI R2.2 — trusted existing callback connection (2026-11-05)

- 将纯 Java 运行时记录器接入现有 completion request 非合成构造器回调与旧版 transport 回调；没有新增宿主 Hook 目标。
- 构造器成功返回后，以实际反射构造器描述符记录 REQUEST_BUILT；transport 回调仅在同一个请求对象通过 IdentityHashMap 关联成功时记录 TRANSPORT_ENTERED。关联键为进程内单调递增 opaque ID。
- 默认关闭。只有维护中的宿主适配器通过检查，并存在显式开关文件 `/data/data/com.deepseek.chat/files/deekseep_roi_runtime_observation_enabled` 才启用。未知/未维护宿主仍只读。记录中的 host identity 是适配器/渠道标签，不是密码学 APK 指纹。开关文件在进程初始化时读取，需在启动/重启宿主 App 前存在。
- 不记录 URL、请求/响应正文、headers、prompt、Authorization 或 token。观察器异常被隔离，不应改变构造器或 transport 的原始执行结果。
- 2.7.x 仍跳过旧版 network-payload 安装路径；expert Flow collect 回调只覆盖已登记的 expert-image relay Flow，不视为普通聊天通用响应回调。
- 本轮没有发出 RESPONSE_RECEIVED 或 RESPONSE_PARSED。当前最多形成 build→transport 部分轨迹，不能确认完整请求/transport/response 链。
- 增加同 request key 不同 host identity 不得合并的回归测试，以及开关、对象身份关联、构造器回调、transport 回调和初始化接线的静态检查。
- 本轮 CI 与真机验证待对应工作流和受控设备证据；没有声称本地构建或真机捕获成功。


## 7J. ROI R2.3 — correlated Flow response-item observation (2026-11-05)

- 旧版 network-payload 路径已经安装一个范围较窄的宿主 completion Flow collect hook（b41；code257 映射为 d81），collector 接口为 q03（code257 映射为 ja3）。此前它仅用于识别已登记的 Expert 图片中继 Flow。
- 扩展现有 collect 回调，不新增宿主 Hook 目标。transport 方法返回原始 Flow 后，以 Flow 对象身份把它与请求 opaque key 关联；collect 时只有 identity 匹配的 Flow 才可进入观察路径。
- 对非中继 Flow，仅当宿主 collector 类型确实是 interface，且原 collector 是该接口实例时，才使用代理包装。代理把 emit 委托给原 collector；只有委托成功返回后，才使用实际反射 emit 描述符记录 RESPONSE_RECEIVED。不读取、序列化、记录或保存 emit 的值。
- collector 类型不是 interface、代理创建失败或 Flow identity 不匹配时，原 collector 原样传递，不记录响应事件。Expert 图片中继 Flow 保留既有处理路径，不错误归因到原请求 Flow。
- 该事件仅表示至少一个响应项成功进入关联 collector，不表示整个流已完成。当前仍不发出 RESPONSE_PARSED。旧版已安装该 Flow collect hook 的宿主现在可观察 request→transport→response-item 链；parser 层确认和流完成状态仍未实现。
- 2.7.x 不在旧版 transport/collect 路径中，仍不具备这条响应观察能力。
- 记录器仍只保存 opaque request key、已批准适配器/渠道标识、阶段、时间戳、线程 ID 和方法描述符；不采集请求/响应正文、URL、headers、prompt、Authorization 或 token。
- 静态 CI 检查现要求 transport 返回 Flow 关联、collector interface 防护和成功 emit 观察接线。CI 与真机验证待完成；没有声称本地构建或实时捕获成功。

- 代理会在该次关联 collect 中改变 collector 对象身份；虽然保留接口并逐方法委托原 collector，但宿主 Flow 对 collector identity/context 的假设尚未经过真机验证。受控运行验证完成前必须保持显式 opt-in。


## R2.4.1 — suspend emit evidence guard (2026-11-05)

- 审计 R2.3 collector proxy 后发现 Kotlin suspend 方法的反射调用可能返回 `COROUTINE_SUSPENDED`；这只表示控制权已挂起，不表示响应项已经成功交付。
- 观察器现检查实际 emit 方法参数。若参数包含 `kotlin.coroutines.Continuation`，不再仅凭反射调用返回就记录 `RESPONSE_RECEIVED`。在尚未安全观察 continuation 成功恢复之前，宁可不产生响应证据。
- 对无 Continuation 参数的同步 emit，仍仅在原 collector 委托调用成功返回后记录；异常继续原样传播，不产生事件。
- 这会使标准 Kotlin suspend Flow 的响应阶段保持未确认；R2.3 的“普通聊天响应项可观察”结论应收窄为：Flow identity 关联与 collector 代理接线已实现，但异步 emit 成功交付尚未被可靠证明。
- 未实现 RESPONSE_PARSED，也未证明流完成。代理改变 collector identity 的真机兼容性风险仍然存在，开关继续默认关闭且必须显式 opt-in。
- 新增静态回归断言，要求 Continuation guard 保留。尚未运行本地测试、Android 构建或真机验证；CI 状态需以本提交对应的 Actions 结果为准。

- 进一步收紧：若 collector 接口暴露带 `kotlin.coroutines.Continuation` 参数的 `emit`，观察器现在直接原样传递 collector，不创建代理。当前实现没有包装 continuation 的成功恢复语义，因此也不应为了一个无法确认的事件改变 collector identity。


## R2.4.2 — parser-correlation audit (2026-11-05)

- 审计现有 `installServerCapture`：SSE 事件构造器只接收 `String event` 与 `String data`，回调没有 request object、opaque request key 或 Flow identity。即使它位于 SSE 解析链，也不能安全归因到某次请求，因此不记录 `RESPONSE_PARSED`。
- 审计 `hookFinalMessageApply` / `hookFinalMessageMerge`：可见的是会话对象、SID、消息对象/消息 ID 和替换/合并时机；它们不是请求身份。历史同步、重试、编辑重发及同会话并发都可能导致误关联，不能用“最近请求”或 SID 推断 request key。
- 因此当前没有可安全接入的 parser-level callback。保留 `RESPONSE_PARSED` 未确认；不增加新的宿主 Hook target，也不把 SSE 构造或 UI 消息落库当成请求解析证据。
- 静态回归增加禁止在当前 HookChatPipeline 中无关联地产生 `RESPONSE_PARSED` 的检查。下一步需要找到同时持有精确 Flow/request correlation 的解析回调，或让既有请求 Flow collector 的下游回调携带可验证的同一身份；否则维持未确认。
- 未运行本地脚本、Android 构建或真机验证；GitHub Actions 对最新提交仍需核验。


## R2.5.1 — ordered evidence tiers (2026-11-05)

- 修正 recorder 原先仅按“阶段是否出现”确认请求链的问题。现在必须存在时间戳非递减的 `REQUEST_BUILT → TRANSPORT_ENTERED → RESPONSE_RECEIVED` 序列，才返回 `confirmsRequestTransportResponse()`。
- `RESPONSE_PARSED` 单独提供 `confirmsParsedResponse()`，且必须在已确认的响应接收阶段之后出现；解析事件先于响应接收时不构成解析确认。
- 同毫秒事件允许非递减，避免毫秒时钟粒度造成正常快速回调被误拒；这不是严格的因果顺序证明，仍依赖可信 callback 的真实调用顺序与同一 opaque request key。
- 本次只收紧纯 Java recorder 的证据判定，不新增宿主 Hook，不产生任何新的 `RESPONSE_PARSED` 事件，也不宣称流完成、成功终止或取消状态已被观察。
- 新增乱序、解析先后关系和同毫秒阶段测试，并在脚本中增加静态断言。当前工具环境未执行该脚本、Android 构建或真机验证；Actions 结果待核验。


## 7K — R2.5.2 只读运行时证据快照（2026-11-05）

- `RoiRuntimeInvocationObserver.snapshotEvidence()` 仅在维护版宿主适配器获准、且用户显式启用运行时观察后返回快照；默认关闭时返回空列表。
- 快照为不可变、脱敏的逐描述符条目，只包含适配器身份标签、精确方法描述符、阶段和有序证据标志；不暴露 requestKey、Prompt、请求/响应正文、URL、Header 或 Token。
- 继续沿用 Recorder 的 512 事件容量和 120 秒 TTL；快照前执行过期清理。
- DexKit 候选报告新增独立 runtimeEvidence 区段。仅当适配器身份和完整方法描述符都精确相等时才关联；不使用方法名、前缀或近似描述符匹配。
- `adapter:generation:channel` 只是适配器代际/渠道标签，不是 APK/DEX 指纹。
- 运行时证据只用于诊断，不进入 `RoiHookResolver` 评分，不改变 role，不批准 adapter，也不授权 Hook 激活。
- 新增快照不可变性、默认关闭/过期快照为空、脱敏字段测试；跨宿主 request key 隔离测试继续保留。
- 本批次尚未本地执行脚本、Android 构建或真机验证。CI 状态需按最新提交重新核验；空状态不能视为通过。


## R2.5.2 — event-order tie-break for millisecond timestamps (2026-11-05)

- Tightened ordered-chain confirmation to require both recorder event order and non-decreasing callback timestamps: REQUEST_BUILT must be appended before TRANSPORT_ENTERED, which must be appended before RESPONSE_RECEIVED; RESPONSE_PARSED must follow the confirmed receipt.
- Equal-millisecond callbacks remain eligible only when they appear in the recorder's observed append order. A reverse-order same-millisecond sequence no longer passes merely because all timestamps are equal.
- Added regression coverage for same-millisecond reverse ordering and timestamp regression, while preserving the valid same-millisecond forward-order case.
- The append-order tie-break is an observation-order safeguard, not proof of cross-thread causality. The recorder still does not infer parser events, Flow completion, or response success beyond a trusted callback's explicitly observed phase.
- No host Hook targets or runtime collection scope changed. CI result and real-device evidence remain separate verification requirements.

## 7L — R2.5.3 运行时证据的 ClassLoader 精确归属（2026-11-05）

- 运行时 Event 和脱敏 EvidenceSnapshot 新增 process-local `sourceScope`，由实际 callback 方法/构造器所属 ClassLoader 经 `RoiCandidateIdentity.forClassLoader()` 生成。
- DexKit 候选只有在 adapter identity、完整方法描述符、process-local ClassLoader sourceScope 三项全部精确相等时，才会显示匹配的运行时证据。
- APK 路径扫描没有可证明的运行时 ClassLoader 身份，因此不与运行时证据关联；只有内存 ClassLoader scan scope 可进行精确归属。
- 有序 REQUEST_BUILT → TRANSPORT_ENTERED → RESPONSE_RECEIVED / RESPONSE_PARSED 链现在还要求各阶段来自同一个 ClassLoader scope。跨 loader 事件不再拼接为完整证据链。
- 新增同描述符跨 loader 保持分离、跨 loader 阶段不能确认完整链的测试；静态脚本增加 sourceScope 关联检查。
- Event 必须携带非空 sourceScope；空 scope 事件会被 Recorder 拒绝，防止无归属证据进入链路。
- 这会有意产生 false negatives：若真实 transport/collector callback 由不同 ClassLoader 定义，即使属于同一请求，也不会升级为完整链。安全优先，不做近似归并。
- suspend Flow 的 Continuation 成功完成仍未观察；没有引入新 Hook 或代理 Continuation。
- 本批次尚未本地执行 Java 测试脚本、Android 构建或真机验证；当前 HEAD 的 CI 需重新核验。


## ROI R2.5.4 — response observation coverage diagnostics

- Added a separate bounded, TTL-pruned, read-only coverage diagnostic stream alongside runtime evidence. It records only adapter identity, process-local ClassLoader scope, exact callback descriptor, status, and timestamp; it does not contain request keys or payload data.
- A successfully delegated synchronous response emit records `RESPONSE_OBSERVED` coverage; a collector interface exposing suspend `emit(..., Continuation)` records `UNSUPPORTED_SUSPEND_COLLECTOR` and is passed through unchanged. No Continuation proxy is introduced.
- The observer exposes `snapshotCoverage()` separately from `snapshotEvidence()`. Coverage diagnostics do not create recorder phases, do not confirm request/transport/response chains, and do not affect resolver scoring or Hook activation.
- Disabled observation continues to return no coverage diagnostics; callers must consult the explicit observation-enabled gate. An empty coverage snapshot is not interpreted as proof that no response occurred or that a callback was unsupported.
- Coverage entries share the recorder's bounded capacity and 120-second TTL and are cleared with recorder state. The standalone test suite now covers status retention, immutability, disabled-policy rejection, expiry, and clear behavior.
- Static source audit found the existing collect hook is shared with the Expert relay path; non-relay Flow collection passes through this observer only when the exact Flow identity was registered by the existing transport callback. No new host Hook target was added.
- Remaining limitation: no safe suspend-continuation completion observation, parser correlation, stream completion, or explicit scope-mismatch diagnostic has been implemented. Exact ClassLoader matching still intentionally permits false negatives.
- Verification boundary: source and regression assertions were updated on `dev`; this environment did not execute the Java test script, Android build, or device test. Check the latest Actions run before treating CI as passed.


## ROI R2.5.5 — repeated Flow collection and scope diagnostics

- Flow-to-request correlation now retains the exact Flow object identity binding across repeated collection instead of consuming it on the first collect attempt. This supports repeated collection of the same cold Flow while preserving exact identity matching.
- Flow bindings carry only the opaque request key and a local timestamp, are capped at 512 entries, expire after 120 seconds, and are cleared on observer reset. Expired entries are pruned on registration and collection. No URL, payload, headers, or auth data is stored.
- Discovery reports exact-scope coverage counts separately from same-adapter/same-descriptor observations found under other ClassLoader scopes. These are explicitly labelled other-scope diagnostics, not merged evidence and not proof that the candidate is the same runtime implementation.
- Other-scope counts are emitted only for in-memory ClassLoader candidates; APK-path scans are excluded because their source scope cannot be equated with a process ClassLoader identity.
- Exact matching still requires adapter identity + complete descriptor + identical ClassLoader scope. Other-scope diagnostics do not relax this rule or affect resolver scoring / Hook activation.
- Static regression guards cover reusable Flow bindings, TTL/capacity policy, and separate scope diagnostics. No new host Hook or Continuation proxy was added.
- Verification boundary: source and static guards were updated; Java test script, Android build, and device test have not been executed in this environment. Verify current CI before treating the change as passed.


## R2.6.1 — runtime ClassLoader protection-signal inventory (2026-11-05)

- 新增纯 Java `RoiClassLoaderProfile`，对当前可见 ClassLoader 及最多 8 层 parent chain 做只读分类：PathClassLoader、DexClassLoader、InMemoryDexClassLoader、DelegateLastClassLoader、BootClassLoader、custom loader 与 other。
- DexKit discovery 对每个内存 ClassLoader 输出类型链、是否观察到已知 DEX loader、是否观察到 InMemoryDexClassLoader、是否观察到 custom loader。
- **不会仅凭 custom loader、类名或 loader 类型断言 DEX 已加密**。当前加密评估明确为 `unconfirmed_custom_loader_requires_runtime_dex_evidence` 或 `not_observable_from_loader_type`。
- 该阶段没有解密、反射读取宿主私有字段、加载额外宿主类、调用宿主方法或安装 Hook。它只是为下一阶段建立动态加载机制的可审计证据入口。
- 新增独立 JVM 测试与 CI 脚本，覆盖已知 loader 类型、custom loader 非加密证明、链深度上限、快照不可变性和 null/bootstrap。
- 验证边界：文件和 CI wiring 已提交；必须以对应 GitHub Actions 实际结果为准。当前未声称本地测试、Android 构建或真机验证通过。


## R2.6.2 — observed loaded-class metadata inspector (2026-11-05)

- 新增 `RoiLoadedClassInspector`：仅接收已经存在的 `Class<?>` 对象，不通过 ClassLoader 主动加载类、不初始化类、不调用宿主方法、不读取字段值、不修改可访问性。
- 输出类名/descriptor、ClassLoader process-local scope、父类、接口、声明方法签名和字段声明；方法与字段各最多 128 项、接口最多 32 项，并显式标记截断/部分不可用。
- 反射元数据访问可能因缺失依赖或安全限制失败；按部分结果报告，不尝试绕过访问限制。
- `RoiDexKitDiscovery.inspectObservedClass(Class<?>)` 提供给未来受信任的模块自有类加载观察点调用，报告明确标记 metadata-only、no method invocation、no field-value read、no hook activation。
- **当前没有把该入口接到全局 ClassLoader.loadClass/findClass Hook，也没有全宿主类枚举。** 这是刻意的安全边界：下一阶段需设计有范围限制、可关闭、低开销的加载观察点，再把真实加载事件送入 inspector。
- 新增 JVM 回归覆盖 descriptor、父类/接口/方法/字段元数据、快照不可变性、null 输入和不触发类初始化；脚本已接入 CI fast audit 与 regression。
- 尚未取得该 commit 对应的 CI 结果；未声称本地测试、Android 构建或真机验证通过。


## ROI R2.6.3 — opt-in bounded host class-load observation (2026-11-05)

- 新增 `RoiClassLoadObserver`：只接收真实 `BaseDexClassLoader.findClass` 成功返回的 `Class<?>`，并调用既有 `RoiLoadedClassInspector` 读取元数据；不主动调用 `loadClass` / `findClass`，不初始化类，不调用宿主方法，不读取字段值。
- 观察点仅在 `HostCompat.supportsMaintainedFeatureRuntime()` 为真、且用户在宿主 files 目录创建独立开关 `/data/data/com.deepseek.chat/files/deekseep_roi_class_load_observation_enabled` 后安装。开关需在宿主启动前存在；默认关闭。未知/未维护宿主不会安装该 Hook，仍保持只读发现路径。
- Hook 安装在 Android `BaseDexClassLoader.findClass(String)`，这是框架父类方法，因此回调会被进程内其他 BaseDexClassLoader 调用；观察器在回调内只接受 DeepSeek 主 ClassLoader 本身或其最多 16 层子加载器链上的事件，并要求返回类的 defining ClassLoader 与 receiver 完全相同。其他进程内 loader、兄弟 loader 和 parent-delegated 类直接忽略。
- 原始 `findClass` 先执行；观察逻辑只在成功返回后运行。元数据观察异常被隔离，不改变原始类加载结果或异常。
- 观察事件去重采用弱 Class identity map；保留的快照不持有 `Class` 或 `ClassLoader` 引用。上限 128 个快照、每秒最多 16 个新事件、120 秒 TTL；方法/字段/接口仍沿用 Inspector 的 128/128/32 元数据上限。
- 快照只含类名、descriptor、loader scope/type、父类、接口、方法/字段声明和时间戳；不包含字段值、请求/响应正文、URL、headers、prompt、Authorization 或 token。
- 该阶段只证明某个类由受限 loader scope 实际定义并成功返回，不证明该类来自加密 DEX。`custom loader`、动态 DEX 或 InMemoryDexClassLoader 仍不能单独作为“加密已确认”的证据；解密/解包行为需独立、可审计的运行时证据。
- 类加载快照只进入诊断日志，不进入 Resolver 评分，不批准 Adapter，不自动 Hook 新类方法。功能角色识别、运行时验证、Hook plan 与激活仍是后续独立阶段。
- 新增 JVM 回归覆盖 host/child loader 范围、批准/opt-in gate、兄弟与委派类拒绝、重复类、容量、TTL、速率限制、快照不可变性及空输入；CI fast audit 与 regression 已加入脚本。
- 验证边界：本轮仅更新远程源码与 CI 配置；尚未执行本地 Java 测试、Android 构建或真机测试。必须以本 commit 对应 GitHub Actions 的最终结果为准。


## ROI R2.7.1 — conservative functional-role classification (2026-11-05)

- 新增 `RoiFunctionalRoleClassifier`，把 DexKit 候选方法的直接 invoke 引用映射为 `REQUEST_CONSTRUCTION`、`HTTP_TRANSPORT`、`RESPONSE_DECODING` 三类功能角色。
- 仅复用已识别的明确 OkHttp / Retrofit / URLConnection / ResponseBody / Gson / Moshi / Jackson / JSONObject API 描述符；类名、方法名、混淆名称、字符串锚点本身不构成功能角色证据。
- 无明确直接引用时输出 `UNRESOLVED`。当前不推断消息组装、Agent 编排、工具分发、SSE/流消费等角色；这些需要后续独立的精确证据模型。
- 每个候选输出角色、命中引用、来源 `direct_static_invoke_reference_only` 和静态置信度上限 0.45。此值仅供诊断排序，不进入 Resolver evidence，不可授权 Hook。
- `RoiDexKitDiscovery` 已将功能角色诊断接入每个候选方法报告；输出 `functionalRole.activationAuthorized=false`。
- 新增 JVM 测试覆盖请求构造与传输不混淆、响应解码识别、未知混淆方法保持 unresolved、空输入保持 unresolved。
- CI fast audit 与 regression 已接入 `test-roi-functional-role-classifier.sh`；DexKit discovery 静态审计也检查功能角色输出始终不可授权。
- 本阶段只做静态功能角色分类，尚未把加载类元数据与 DexKit 候选做精确类身份关联，也未形成跨候选的完整请求/响应流水线。没有修改 Resolver 授权策略、Adapter 审批或 Hook 激活逻辑。
- 验证边界：源码和 CI 配置已提交；需等待最新 Actions 完成。未声称本地 JVM 测试、Android 构建或真机验证通过。


## ROI R2.7.2 — conservative runtime class/candidate association (2026-11-05)

- 新增纯 Java `RoiClassCandidateMatcher`，为 DexKit 静态方法候选与已观察运行时类建立保守关联判定。
- 判定结果区分 `EXACT_MATCH`、`STRUCTURAL_MATCH`、`PARTIAL_MATCH`、`CONFLICT`、`UNMATCHED`；要求 ClassLoader process-local scope 精确一致，且精确匹配必须同时满足 owner 与完整方法描述符。
- 同 scope 下 owner 不同但完整方法描述符一致，只标记为 `STRUCTURAL_MATCH`，不推断跨版本语义等价；ClassLoader scope 不同直接标记冲突，禁止跨 loader 拼接。
- 方法元数据被截断或部分不可用时返回 `PARTIAL_MATCH`，不把“未找到”误判为不存在。
- 所有匹配结果的 `activationAuthorized` 固定为 false；本次没有接入 Hook Resolver、Adapter Registry 或 Hook installer。
- 新增独立 JVM 测试脚本，覆盖 exact、structural、partial、loader conflict、unmatched 与禁止激活。
- 当前为匹配策略核心和回归基线；尚未把 `RoiLoadedClassInspector` 的运行时快照转换为标准 JVM 方法描述符，也尚未将 matcher 接入 DexKit discovery 报告。因此本阶段不宣称已经完成静态候选与运行时类的端到端自动关联。
- GitHub Actions 结果以本提交对应运行记录为准；尚无真机验证。


## ROI R2.7.3 — explicit unknown-host metadata-hook switch (2026-11-05)

- 新增未知版本独立开关文件：`/data/data/com.deepseek.chat/files/deekseep_roi_unknown_host_hook_enabled`。
- 开关必须是普通文件，且内容严格为单行 `ENABLE`（允许末尾换行）；缺失、空文件、`true`、额外行或读取失败均视为关闭。
- 未知版本默认仍执行只读 DEX/类发现。只有显式开启时，额外允许安装受限的 `BaseDexClassLoader.findClass` 元数据观察器；观察范围仍限定宿主主 ClassLoader 及其子加载器，且只在原始类加载成功后读取类元数据。
- 该开关**不启用旧版功能 Hook、不批准 Adapter、不授权功能方法 Hook**。 `RoiUnknownHostHookPolicy.authorizesFunctionalHook` 当前固定返回 false；未知版本功能 Hook 仍须后续完成证据验证、独立适配器审查和安装回滚机制。
- 已有维护版本的类加载观察开关保持独立，不受此开关影响。
- 新增纯 Java 策略测试，覆盖默认关闭、显式 ENABLE、别名拒绝、多余内容拒绝及功能 Hook 永不授权；接入 ROI fast audit 与 Regression。
- 本功能仅提交到 `dev`，未修改 `main`。CI 和真机结果待对应 Actions / 设备验证，不提前宣称通过。

- 开关运行时行为：观察 Hook 安装后，每次成功的 `findClass` 回调都会重新读取开关；删除文件或内容不再是严格单行 `ENABLE` 后，观察立即停止（Hook 回调仅透传原始结果）。重新写入有效开关后可恢复观察，无需重新安装模块；若要彻底卸载框架 Hook，仍需结束 DeepSeek 进程并重新启动。
- 开关是“未知版本实验 Hook 评估/元数据观察入口”的显式授权，不是“无条件执行功能 Hook”。后续功能 Hook 仍必须逐个通过精确类/方法身份、功能角色、真实调用链、适配器审查、失败关闭和回滚检查；目前没有任何未知版本功能 Hook 被授权或安装。
- 当前控制面为宿主私有目录文件开关，尚未做成模块设置页 UI。启用文件：`/data/data/com.deepseek.chat/files/deekseep_roi_unknown_host_hook_enabled`，内容必须为单行 `ENABLE`；删除该文件即关闭。
- 有 root 的设备可用 ADB 操作：`adb shell su -c 'D=/data/user/0/com.deepseek.chat/files; printf "ENABLE\\n" > "$D/deekseep_roi_unknown_host_hook_enabled"; chown --reference="$D" "$D/deekseep_roi_unknown_host_hook_enabled"; chmod 600 "$D/deekseep_roi_unknown_host_hook_enabled"'`；关闭：`adb shell su -c 'rm -f /data/user/0/com.deepseek.chat/files/deekseep_roi_unknown_host_hook_enabled'`。启用后重启 DeepSeek 进程以确保观察 Hook 安装。

## ROI R2.7.4 — gated unknown-host invocation probe foundation (2026-11-05)

- 新增独立 RoiUnknownHostInvocationProbePolicy，要求独立显式 opt-in、精确 runtime owner、完整 DEX method descriptor、相同 process-local ClassLoader scope、已验证功能角色和已确认调用图证据全部同时成立。
- 仅接受 REQUEST_CONSTRUCTION、HTTP_TRANSPORT、RESPONSE_DECODING；UNRESOLVED、静态提示、类加载 exact match、descriptor 不完整或 owner / method / loader 不一致均拒绝。
- 新增独立 RoiUnknownHostInvocationProbe 诊断记录器：最多登记 8 个方法，每秒最多记录 16 次调用；仅保留完整方法描述符、调用次数、总/最大耗时和异常类名。不会读取参数、返回值、Throwable message、请求/响应正文、URL、Header、Prompt 或 Token。
- invoke() 通过单次 proceed() 包装，保留原返回对象身份和原始异常身份；记录器自身错误被隔离，不替换宿主结果或异常。
- 新增 JVM 回归脚本，覆盖默认关闭、精确 ENABLE、别名拒绝、多余内容拒绝、完整签名、角色/调用图/loader/descriptor 门槛、exactly-once、异常透传、异常消息不落盘、方法数与事件速率上限。
- 新脚本已接入 ROI fast audit 与完整 Regression。
- 当前只是策略与记录器基础设施，尚未在 Main 中安装未知版本方法 Hook，也没有任何候选满足当前已证实的 roleVerified + callGraphConfirmed 双门槛。现有 R2.7.1 角色分类置信度上限为 0.45，静态调用图不证明运行时执行，因此不得把它们升级为已验证角色或调用链。
- 后续只有在真实运行时证据确认精确方法后，才允许接入独立开关 /data/data/com.deepseek.chat/files/deekseep_roi_unknown_host_invocation_probe_enabled；该开关必须与现有未知版本类加载元数据开关分离，默认关闭。
- 当前 dev 最新提交为 868b05b939493ccd876c86f01b704a8587177d2b。GitHub Combined Status 暂无状态项；不得据此宣称 CI 通过。尚未执行本地 JVM 脚本、Android 构建或真机验证。
