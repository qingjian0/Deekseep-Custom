# DeepSeek Native Completion Adapter 调查与实施边界

> 适用分支：`work/roi-agent-core-kotlin`  
> 当前源码快照：`690d7d7dd63f48e2762e34d001de99e4ae97e590`  
> 宿主目标：`com.deepseek.chat`，versionCode 287 / 289 / 290  
> 当前结论：**Open 原生 Completion Adapter 未完成；Gateway 保持 503 fail-closed。**

## 1. 结论摘要

当前 Open Local API 已具备 HTTP 服务、协议解析、Bearer 认证、请求限制和 SSE 输出，但还没有一条已证明可用的 Open 版 DeepSeek 原生 Completion 执行链。

当前可确认的调用链：

```text
DeepSeek Hook startup
  |
  +-- Main.startz1(appContext)
        |
        +-- OpenNativeCompletionProbe.scheduleHostInspection(hostClassLoader)  [Open only, enabled gate, daemon worker]
        |
        +-- z13.start(appContext, Backend)
              |
              +-- Open build -> OpenLocalApiGateway.start(...)
                    |
                    +-- Backend.isReady()
                    |     -> BuildInfo.LOCAL_API_INCLUDED
                    |        && hostClassLoader != null
                    |        && liveR92 != null
                    |        && liveQ71 != null
                    |
                    +-- Backend.complete(request, sink)
                          -> Open edition && !LOCAL_API_INCLUDED
                             => 503 native_adapter_unavailable
                          -> otherwise module.executeLocalApiCompletion(...)
                                |
                                +-- HookAccountLoginSecurity.executeLocalApiCompletion(...)
                                      |
                                      +-- z14.payloadClass(..., "ds.dsh.DeepSeek.z18")
                                      +-- reflect executeLocalApiCompletion(CompletionRequest, DeltaSink)
                                      +-- missing method/class => 503 engine_missing
```

注意：Open 版的 `z14` 是空实现，`detect()` 返回 null、`available()` 返回 false、`payloadClass()` 返回 null。因此当前 Open 版不会取得该反射执行目标。Gateway 对此返回 not-ready，而不是伪造成功响应。

## 2. 已确认的源码事实

### 2.1 Gateway Backend 门禁

`module/src/ds/dsh/DeepSeek/Main.java` 的 Backend 实现：

- `isReady()` 要求 `BuildInfo.LOCAL_API_INCLUDED`、宿主 ClassLoader、`liveR92` 和 `liveQ71` 均可用。
- Open 且未包含本地适配载荷时，`readinessDetail()` 返回“Open 版原生模型适配器尚未接通”。
- `complete()` 在 Open + 未包含适配器时直接抛出 HTTP 503 / `native_adapter_unavailable`。
- 只有通过上述门禁后才会进入 `module.executeLocalApiCompletion()`。

### 2.2 执行门面

`HookAccountLoginSecurity.executeLocalApiCompletion()`：

1. 为缺少 session scope 的请求补充稳定 scope；
2. 调用 `z14.payloadClass(Main.hostApplicationContext, "ds.dsh.DeepSeek.z18")`；
3. 在返回类上查找 `executeLocalApiCompletion(CompletionRequest, DeltaSink)`；
4. 类或方法不存在时返回 503 `engine_missing`；
5. 反射调用异常转换为明确 Gateway 错误。

当前 Open `z14.java` 明确是 stub，故第 2 步返回 null。该路径不是 Open 原生适配器。

### 2.3 命名与类加载风险

- 执行门面请求 `z14.payloadClass(Main.hostApplicationContext, "ds.dsh.DeepSeek.z18")`，随后查找静态方法 `executeLocalApiCompletion(CompletionRequest, DeltaSink)`。
- 当前仓库普通源码 `module/src/ds/dsh/DeepSeek/z18.java` 定义的是 Android Doze / App Standby 豁免辅助类，公开包内类本身没有上述 Completion 方法。
- Open 版 `z14.java` 是 stub，`payloadClass()` 固定返回 null；因此 Open 路径 fail-closed，不会解析该类。
- 这构成一个必须核实的类名碰撞/隔离边界：Closed payload 可能通过独立 ClassLoader 提供同名类，但当前仓库可见源码不足以证明其定义、加载器身份、父子委派策略或实际解析结果。不得将普通源码的 `z18` 误认成 Closed Completion Engine，也不得仅凭类名断言 Closed 路径正确或错误。
- 在获得 Closed payload 的构建产物和 `z14.payloadClass()` 完整实现之前，执行引擎定位状态记为“未证实”。后续应记录解析所得 Class 的定义加载器、代码来源（若可安全取得）、方法声明类与静态签名；不记录 payload 内容、凭据或用户数据。

### 2.5 2.7.x 旧对象捕获链的安装缺口

本轮沿 `HostCompat.initialize()`、`Main.installAllFeatureHooks()`、`installNetworkPayloadCapture()` 和 `HookChatPipeline.installPowManagerCapture()` 继续追踪，确认当前源码存在一个 2.7.x Backend readiness 阻塞：

- `HostCompat.initialize()` 将 287、289、290 都识别为 `v271=true`，并按各自映射表决定 full / limited。
- 对 287 且映射表不可用的 limited 路径，`Main.installAllFeatureHooks()` 直接进入 `HostV271.installFeatureHooks()` 并返回。当前 `HostV271` 白名单只安装设置入口、聊天 Request 构造器和历史清理器，没有安装旧 q71 / Transport 捕获。
- 对 287/289/290 的 full 路径，主功能安装流程继续执行，但 `network.payload-capture` 与 `chat.pow-manager-capture` 两项都被 `if (!HostCompat.isV271())` 排除。因此不能因为 289/290 使用 full 映射表，就认为旧捕获 Hook 会被安装。
- `liveQ71` 的旧赋值入口是 `HookChatPipeline.captureApiManagers()`；它由 `installPowManagerCapture()` 安装的构造器 / 方法 Hook 触发。该安装步骤在全部 `v271` 版本上被跳过。
- `liveR92` 的旧赋值入口包括 `hookTransport()` 捕获 transport this，以及 `captureApiManagers()` 从 q71 的字段 `f` 取得 transport；对应的两个安装入口同样被跳过。
- 但 `Main.startz1()` 的 Backend `isReady()` 仍要求 `liveR92 != null && liveQ71 != null`。按目前可见安装路径，2.7.x 无法满足这两个旧字段的 readiness 前置条件。

**判定：** 这是源码层面的捕获链断层，不是“2.7.x 运行时等待初始化慢”。在找到并证明其他赋值路径之前，不能声称 287/289/290 的 Local API Backend 已具备就绪条件。后续应为 2.7.x 建立独立的 Request / Challenge / Transport 发现与对象绑定路径，或明确禁用该版本 Local API；不得通过删除 readiness 条件、填充伪对象或复用旧版字段名绕过。

### 2.4 旧 Backend 与运行时对象

仓库源码中也存在一个源文件 `module/src/ds/dsh/DeepSeek/z18.java`，其内容是 Android Doze / App Standby 白名单辅助逻辑，并不声明 `executeLocalApiCompletion`。

因此后续实现不得只凭 `z18` 这个短类名或文件名认定执行目标。必须核实：

- `z14.payloadClass()` 在不同构建变体中的实际 ClassLoader；
- payload class 是否由独立 ClassLoader 加载，以及父子委派规则；
- 反射返回类的完整名称、ClassLoader 身份和方法签名；
- 是否存在类名碰撞、重复定义或错误命中普通模块类的可能。

不要通过改名、复制 Closed 载荷或放宽反射查找来掩盖类身份问题。

### 2.4 旧 Backend 与运行时对象

当前 Backend readiness 仍检查 `liveR92` 与 `liveQ71`。这些字段来自旧版原生 Transport / PoW 捕获路径。它们不能自动证明 2.7.x 版本已正确捕获对应对象，也不能作为新版本适配的唯一依据。

## 3. 宿主版本与请求模型

`HostCompat.initialize()` 对 2.7.x 的识别使用 Completion Request 类与 transport marker interface 组合：

| versionCode | 宿主渠道 | Request 类 | Marker interface |
|---:|---|---|---|
| 287 | 国内版 2.7.1 | `sx1` | `du1` |
| 289 | 国内版 2.7.2 | `ly1` | `wu1` |
| 290 | Google Play / 国际版 2.7.2 | `c02` | `nw1` |

`HostCompat` 的 legacy symbol `ew0` 在 287 的显式映射为 `sx1`；289 / 290 通过对应生成映射表处理。映射检测成功仅代表版本识别与符号映射路径成立，不代表请求构造、PoW、网络发送或流式响应已端到端验证。

`HostRemap` 对 287 / 289 / 290 选择各自映射表。任何新符号必须注明 APK 来源、版本、渠道、类/方法签名和验证证据；禁止跨版本直接复用未经核验的混淆名称。

## 4. OpenNativeCompletionProbe 当前能力

文件：`module/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java`

当前探针是只读结构盘点工具：

- 在 Open 版 Local API 显式启用后，通过 `scheduleHostInspection(hostClassLoader)` 调度 daemon worker；探针不在启动/UI 调用线程同步执行。
- 通过 `HostCompat.load(loader, "ew0")` 获取当前版本映射后的 Request 类型；
- 输出 Request 类名、接口、构造器签名和非静态字段声明；
- 扫描 DEX 类名，寻找声明方法满足以下结构条件的候选：恰有两个参数、第一参数类型可接收当前 Request（`parameter[0].isAssignableFrom(requestType)`）、第二参数严格为 `Long`、返回类型既非 primitive 也非 `void`；
- Manager 字段候选仅接受 `field.getType().isAssignableFrom(transportOwner)`，即字段声明类型能够持有候选方法声明类的实例；反向可赋值关系不接受；
- Request 与各 Manager 候选的构造器报告使用独立命名空间，避免诊断 key 相互覆盖；
- 方法参数/返回类型、泛型返回类型、构造器参数和 Manager 字段元数据按单个成员隔离异常；方法/字段签名失败会写入诊断字段并计入部分扫描，避免一个损坏签名中断其余候选枚举；
- 仅输出声明签名，不实例化宿主类、不调用候选方法、不读取对象字段值。
- 报告增加 schema 版本、符合扫描条件的类名数量、成功加载数量、类加载失败数量和扫描耗时，便于比较不同宿主版本的覆盖范围。

限制：

1. 候选筛选条件只是启发式结构特征，可能包含误报；候选数量不等于真实 Transport 数量。方法声明类也不必然等于实际运行时 Transport 实例类。
2. 返回类型可能是 Flow、泛型包装或其他对象；签名本身不证明其语义。
3. `Long` 参数的语义尚未被确认，不能直接称为 timeout、deadline 或 PoW 参数。
4. Manager 字段的正向类型可赋值关系只说明声明类型能够容纳该候选类实例，不证明字段实际持有该实例，也不证明其生命周期、初始化顺序或线程约束。
5. 当前 `inspectHost()` 的只读扫描不取得 live PoW manager / Transport 实例。
6. 旧版 `inspect(loader, powManager, transport)` 保留了对象捕获入口，但 2.7.x 启动路径注释明确说明旧 `q71` 捕获 Hook 不可用；不能把该入口视为新版本已完成的捕获机制。

探针结果应作为候选清单，不得作为调用协议的最终证据。

## 5. 尚未证实的关键环节

| 环节 | 当前证据 | 仍需证明 |
|---|---|---|
| Request DTO | 版本识别与构造器签名可盘点 | 字段语义、必填字段、会话/模型/附件上下文 |
| PoW / Challenge | 旧版有 `liveQ71` 概念；探针可列候选方法 | 2.7.x 实际管理器、初始化、挑战刷新、并发安全 |
| Transport | 探针可列结构候选 | 实际调用方法、返回类型语义、认证头、错误映射 |
| Flow / SSE | 旧链路有增量事件抽象 | 2.7.x Flow 类型、collector、事件顺序、取消传播 |
| 响应消费 | Gateway 有 DeltaSink | 原生响应 DTO、reasoning/content 分流、结束标记与 usage |
| Session | Gateway request 有 session scope | 宿主会话对象如何创建/关联，避免污染用户现有会话 |
| 生命周期 | Hook 启动路径存在 | 冷启动、重启、网络切换、账号切换和对象重建行为 |

没有 APK 静态分析记录、运行时诊断输出或设备抓取证据时，不得填入推测的混淆类名和方法语义。

## 6. 建议的实现拆分

### Phase A：证据采集（不发送请求）

- 对 287 / 289 / 290 分别记录 `HostCompat.diagnosticSummary()`；
- 导出探针报告，记录 Request 构造器、字段、候选 Transport 与管理器；
- 给每个候选方法附上完整参数、返回类型、泛型签名、声明类和 APK 渠道；
- 明确探针扫描的类数量、失败类数量和扫描边界；
- 增加诊断报告 schema version，避免不同版本报告混淆；
- 报告不得包含 Prompt、Token、账号信息或字段运行时值。

### Phase B：静态与动态语义确认

- 使用对应版本 APK 的反编译结果追踪真实调用点；
- 从官方聊天提交入口追踪 Request 构造、PoW、Transport、Flow collector 和响应消费；
- 以调用者、被调用者、字段读写、线程与 coroutine context 形成调用链；
- 在隔离测试账号/设备上观察生命周期，不读取或导出用户聊天内容；
- 287 / 289 / 290 独立建立证据，禁止默认认为混淆结构一致。

### Phase C：最小 Host Adapter

建议新增独立窄边界接口，不把反射散落进 Gateway：

- `HostRequestFactory`：将稳定 DTO 转换为宿主 Request；
- `HostChallengeProvider`：取得并刷新宿主要求的 challenge / PoW；
- `HostCompletionTransport`：调用真实 Transport；
- `HostEventDecoder`：将宿主 Flow 事件映射为稳定增量事件；
- `HostCancellationBridge`：将客户端断开、deadline 和取消传播至宿主协程；
- `HostSessionBridge`：明确新建会话与已有会话的边界。

上述是目标职责拆分，不代表这些类已存在。实现时应优先复用已验证的宿主对象和生命周期，不另造不兼容的网络协议。

### Phase D：逐级验收

1. JVM 单测：Request 转换、事件分类、取消和错误映射；
2. 静态回归：287 / 289 / 290 符号表与签名锚点；
3. Open Android 构建、APK 结构检查；
4. 探针运行：确认报告完整且无敏感值；
5. 受控设备验证：单次非流式调用、流式调用、取消、超时、网络切换；
6. 多版本真机矩阵：记录宿主 versionCode、渠道、Android 版本、LSPosed 版本和结果。

任何阶段失败都保持 Gateway 503，不允许以模拟响应替代原生调用。

## 7. 安全与兼容不变量

- 不绕过官方登录、风控、PoW 或访问控制；只复用宿主正常请求链路。
- 不捕获或记录账号凭据、Authorization、Prompt、Completion 或工具参数。
- 不伪造宿主响应，不修改用户现有会话内容。
- 不在主线程执行网络或阻塞式反射。
- 不替换宿主 Flow 原始对象身份，除非签名与消费者契约已被证明允许。
- 不改变原始 Hook 方法的调用次数、异常传播和时序。
- 每个宿主版本采用显式能力声明；未识别版本 fail-closed。
- Gateway 的 not-ready / 503 是正确的安全状态，不是应被隐藏的错误。

## 8. 本阶段交付判定

| 项目 | 状态 |
|---|---|
| Open Gateway HTTP / Auth / SSE | 服务层已实现 |
| Open Native Completion | 未实现 |
| Open Native Probe | 只读结构扫描已实现 |
| 287 / 289 / 290 HostCompat 识别 | 有源码映射与识别逻辑 |
| 三版本原生请求端到端验证 | 未证实 |
| 真机 Completion 验证 | 未证实 |

项目整体状态见 [PROJECT_STATUS.md](PROJECT_STATUS.md)，Gateway 服务层见 [OPEN_LOCAL_API_GATEWAY.md](architecture/OPEN_LOCAL_API_GATEWAY.md)。


## 2.7.x 捕获入口复核补充（2026-11-03）

复核 `Main.java` 中约 9312、9327、9337 行的 `HookChatPipeline.hookTransport(...)` 调用后，确认这些并不是独立的 2.7.x 安装入口，而是 `Main.installNetworkPayloadCapture()` 内部的三种旧版 Transport 发现分支（特定版本映射、候选混淆类名、结构签名兜底）。该方法本身由 `network.payload-capture` 安装步骤触发，而该步骤在 `HostCompat.isV271()` 时被显式跳过。

对应地，`HookChatPipeline.hookTransport()` 内对 `Main.liveR92` 的赋值只有在该方法被安装并实际执行时才会发生；`liveQ71` 的已知赋值仍来自 `captureApiManagers()`，其 Hook 安装入口 `installPowManagerCapture()` 同样在 2.7.x 被跳过。由此，本次新增检查没有发现这几处 Main.java 调用构成独立的 2.7.x 捕获通道。

本结论的范围是上述源码安装链与字段写入点；它不等于已取得官方 APK 的完整反编译证明，也不排除未来新增代码路径。后续仍应以完整仓库赋值点检索、各渠道官方 APK 静态证据和受控设备验证收敛结论。


## 2.7.x probe scheduling and channel classification update (2026-11-03)

### Read-only inventory scheduling

The Open build now schedules `OpenNativeCompletionProbe.inspectHost()` only after the explicit Local API enabled marker is present. Scheduling is delegated to `scheduleHostInspection()`, guarded by a one-shot atomic flag, and runs on a daemon worker thread. This prevents the DEX inventory from running on the Activity/startup caller thread and avoids creating repeated worker threads when `startz1()` is re-entered.

The probe remains diagnostic-only: it does not instantiate host classes, invoke candidate host methods, or read live object field values. No real-device timing result is available yet; the change removes synchronous startup work but does not claim a measured latency improvement. Gateway readiness remains unchanged and the Open adapter still fails closed with 503.

### Google Play / international code290 classification

The 2.7.x marker path identifies code290 by its `c02` Request and `nw1` marker interface. Unlike older Google Play builds, this host does not expose the PairIP marker used by the initial channel probe. Keep the private `googlePlay` flag unchanged: it selects the legacy 2.3.x symbol-mapping chain, which must remain on the reviewed 2.4.1 base symbols before `HostRemap`. The public `isGooglePlay()` accessor now returns `googlePlay || isIntl27()`, exposing code290 as Google Play to channel-dependent feature decisions without changing the legacy symbol-mapping path.

Regression coverage in `module/test-hostmap-regression.sh` checks that code290 sets the channel flag. The mapping tables are still selected independently by versionCode; channel classification does not establish Native Completion support.


## 2.7.x probe report collection (2026-11-04)

The probe is not scheduled on every app launch. In the Open build, `Main.startz1()` first checks the explicit Local API enabled marker; only then does it call `OpenNativeCompletionProbe.scheduleHostInspection(hostClassLoader)`. The inventory runs on a daemon worker and makes at most three attempts (initial, +1 s, +3 s) until a complete non-empty DEX inventory is obtained. A partial or empty inventory remains retryable; a complete inventory becomes terminal for the current process.

To collect a report on a test device:

1. Install an Open build containing the probe diagnostics.
2. In DeepSeek module settings, open **工程 → 本地 API · 实验性** and explicitly enable Local API. This enables the diagnostic scheduling gate; it does not make Native Completion available and does not mean a model request will succeed.
3. Force-stop DeepSeek, launch it again, and allow startup to settle. The probe is passive and does not send a completion request.
4. Retrieve the external mirror file `/storage/emulated/0/deekseep_vision_m.log`. The primary private app file is `/data/data/com.deepseek.chat/files/deekseep_vision.log` and normally requires an authorized app-private/log collection path.
5. Extract the complete block beginning `[OPEN_NATIVE_COMPLETION_PROBE]`, including all lines through the final `probe.elapsedMs` field. Do not trim candidate or loader-chain lines.

The report currently records `host`, `loader.chain.*`, `dex.scan.scope`, `dex.classCount`, `dex.eligibleClassNameCount`, `dex.scannedClassCount`, `dex.classLoadFailureCount`, `transport.candidateCount`, `manager.candidateCount`, and `probe.elapsedMs`. The scan covers only the supplied `BaseDexClassLoader.pathList.dexElements`; parent and separate split/dynamic loaders are not recursively enumerated. Candidate entries are structural hints, not proof of a live Transport or manager.

Before sharing, inspect the file and redact unrelated log records, account identifiers, tokens, authorization headers, prompts, message bodies, and personal data. The probe block is designed to contain declarations and signatures only; share the complete probe block rather than the entire log file.

If the marker is absent, first confirm that the installed APK is an Open build with the probe changes and that Local API was explicitly enabled before restarting DeepSeek. Absence of a report is not evidence that the host has no matching classes.
