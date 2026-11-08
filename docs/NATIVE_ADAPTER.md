# DeepSeek Native Completion Adapter 调查与实施边界

 > 适用分支：`dev`（唯一开发基线；Local API 按功能范围选择性移植，不导入旧分支无关提交）  
 > 当前源码快照：以 `dev` 最新 HEAD 为准；本文件随 Local API 移植更新  
> 当前宿主验证目标：`com.deepseek.chat` 国内版 2.7.2 / versionCode 289（287 / 290 仅保留独立兼容调查，不继承 code289 证据）  
> 当前结论：**协议网关已扩展；schema-7 只读诊断探针已加入请求到异步 Flow 的弱身份关联，但 null-name DeltaCommand / `ready` / `close` 运行时事件尚未取得完整证据；Open 原生 Completion Adapter 未完成，所有 Completion 协议路由保持开放并进入执行门面；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503），不得在 Open 版本级别提前关闭路由。**  
> 验证安排：按工程要求，PR CI、Open APK 构建与 code289 真机验证统一留到本阶段源码工作完成后执行；此前不宣称通过。

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
                          -> module.executeLocalApiCompletion(...)
                                -> missing Open payload => 503 engine_missing per request
                                |
                                +-- HookAccountLoginSecurity.executeLocalApiCompletion(...)
                                      |
                                      +-- z14.payloadClass(..., "ds.dsh.DeepSeek.z18")
                                      +-- reflect executeLocalApiCompletion(CompletionRequest, DeltaSink)
                                      +-- missing method/class => 503 engine_missing
```

注意：Open 版的 `z14` 是空实现，`detect()` 返回 null、`available()` 返回 false、`payloadClass()` 返回 null。因此当前 Open 版不会取得该反射执行目标。协议路由仍保持开放；实际 Completion 请求进入执行门面后返回 `engine_missing`（HTTP 503），不会伪造成功响应。

## 2. 已确认的源码事实

### 2.1 Gateway Backend 门禁

`module/src/ds/dsh/DeepSeek/Main.java` 的 Backend 实现：

- Open edition 的 `isReady()` 返回 true，以便 HTTP 协议路由保持开放；这不代表原生模型执行能力就绪。
- Open 的 `readinessDetail()` 明确说明 API 路由已开放，原生执行能力按请求检测。
- `complete()` 不再按 Open edition 预先拒绝；请求直接进入 `module.executeLocalApiCompletion()`。
- Open stub 缺少 `z18` payload 时，执行门面按请求返回 HTTP 503 / `engine_missing`。

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

### 2.6 Request 构造观察不等于真实 Transport 捕获

进一步逐行检查 `HookChatPipeline.hookChatRequest()` 与 `OpenNativeCompletionProbe.inspectHost()`：

- `hookChatRequest()` 在 Request 构造器上安装 Hook，读取并可能改写 prompt 参数，再执行原构造器；当前实现没有把构造完成的 Request 实例保存为可关联的运行时对象，也没有记录该实例随后进入哪个调用者。
- `inspectHost()` 从已加载 DEX 类中筛选两参数方法：第一个参数可接收当前 Request 类型，第二个参数为 `Long`，返回值为非 primitive / 非 void。它只输出方法声明、泛型返回类型和候选类字段；不实例化类、不读取实例字段、不调用候选方法，也不证明候选方法在真实发送路径上被执行。
- 因此 `transport.candidateCount > 0` 只能说明存在符合结构过滤条件的方法，不能说明它是实际 Transport；`manager.candidateCount > 0` 也不能说明存在真实存活的 Manager 实例。
- 当前尚无 Request 实例身份与 Transport 方法调用之间的关联 ID，也没有真实 Challenge / PoW 对象、网络请求边界或响应流事件的同一会话证据。

**下一阶段的正确顺序：** 先在真实 Request 构造完成处记录受限的实例身份（仅进程内 identity，不输出对象内容）；再对静态候选方法安装只读、限量的运行时观察，确认哪些方法实际收到同一 Request 实例；随后追踪其返回对象 / Flow 的订阅与消费点。Challenge / PoW 必须从实际调用参数和对象关系独立确认。观察 Hook 不得修改参数、返回值、认证数据或网络行为；未获得设备日志前，所有候选继续保持未验证状态。

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
- 方法参数/返回类型、泛型返回类型、构造器参数和 Manager 字段元数据按单个成员隔离异常；方法签名、泛型返回类型及 Manager 字段元数据失败会写入诊断字段并计入 `classLoadFailures`，使 DEX 清单保持可重试；构造器参数及附加字段诊断错误单独记录，目前不计入 `classLoadFailures`；
- Request、Transport、PoW 附加字段的元数据错误使用独立的遍历成员序号；连续多个字段异常不会复用同一个诊断键而相互覆盖；
- 仅输出声明签名，不实例化宿主类、不调用候选方法、不读取对象字段值。
- 报告增加 schema 版本、符合扫描条件的类名数量、成功加载数量、类加载失败数量和扫描耗时，便于比较不同宿主版本的覆盖范围。

限制：

1. 候选筛选条件只是启发式结构特征，可能包含误报；候选数量不等于真实 Transport 数量。方法声明类也不必然等于实际运行时 Transport 实例类。
2. 返回类型可能是 Flow、泛型包装或其他对象；签名本身不证明其语义。
3. `Long` 参数的语义尚未被确认，不能直接称为 timeout、deadline 或 PoW 参数。
4. Manager 字段的正向类型可赋值关系只说明声明类型能够容纳该候选类实例，不证明字段实际持有该实例，也不证明其生命周期、初始化顺序或线程约束。
5. 当前 `inspectHost()` 的只读扫描不取得 live PoW manager / Transport 实例。
6. 旧版 `inspect(loader, powManager, transport)` 保留了对象捕获入口，但 2.7.x 启动路径注释明确说明旧 `q71` 捕获 Hook 不可用；不能把该入口视为新版本已完成的捕获机制。
7. 旧版对象捕获诊断入口使用独立的 `INSPECTING` 锁；只有完成报告才设置 `REPORTED`，顶层反射失败会保留可重试状态。附加字段父类遍历失败会记录 `superclassError` 并停止该条遍历，不会继续假定父类元数据可用。

探针结果应作为候选清单，不得作为调用协议的最终证据。

## 2.7.x mapping fallback isolation

The hand-verified `HostV271` whitelist is specific to mainland code287 / 2.7.1. If the generated mapping resource for code289 or code290 is unavailable, the runtime must not fall back to code287 obfuscated names (`sx1`, `ey1`, `gy`, etc.). The Main installer and `HostV271.installFeatureHooks()` both reject that fallback. In that state, the 2.7.2 host is not reported as a maintained feature runtime; the module leaves host hooks disabled rather than guessing symbol identities. A successful 289/290 mapping activation continues through the generated per-version table.

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

The Open inventory and code289 runtime observer are disabled by default. The Local API enable switch alone does not authorize diagnostic scanning or Hook installation. In the Local API page, the user must separately enable **启用原生适配诊断探针**. Only then may `Main.startz1()` schedule `OpenNativeCompletionProbe.scheduleHostInspection(hostClassLoader)`; the scheduler also checks the explicit diagnostic marker. The inventory runs on a daemon worker and makes at most three attempts (initial, +1 s, +3 s) until a complete non-empty DEX inventory is obtained. A partial or empty inventory remains retryable; a complete inventory becomes terminal for the current process.

To collect a report on a test device:

1. Install an Open build containing the probe diagnostics and the separate opt-in switch.
2. In DeepSeek module settings, open **工程 → 本地 API · 实验性** and enable Local API if it is not already enabled.
3. In the same page, separately enable **启用原生适配诊断探针**. This authorizes only the reviewed, read-only diagnostic inventory and code289 call-boundary observer; it does not authorize feature Hook installation and does not make Native Completion available.
4. Force-stop DeepSeek, launch it again, and allow startup to settle. The probe is passive and does not send a completion request.
5. Send one ordinary, non-sensitive test message in DeepSeek so the reviewed `jw3.g` / `zn3.o` boundaries are actually exercised.
6. Return to the Local API page and tap **复制原生适配诊断与运行轨迹**. The copied text includes the structural inventory plus a bounded in-memory `[OPEN_NATIVE_CALL_OBSERVER_BUFFER]` containing runtime enter/exit/throw traces. The buffer retains at most 12,000 characters; older trace lines are discarded when full.
7. For a complete external log, optionally retrieve `/storage/emulated/0/deekseep_vision_m.log`. The primary private app file is `/data/data/com.deepseek.chat/files/deekseep_vision.log` and normally requires an authorized app-private/log collection path. The external log may contain more observer events than the in-memory copy.

Turning the diagnostic switch off makes any already-installed call-observer wrapper immediately call the original host method exactly once and return its original result without recording further observation events. The wrapper is not uninstalled from the process, but it becomes transparent.

The report currently records `host`, `loader.chain.*`, `dex.scan.scope`, `dex.classCount`, `dex.eligibleClassNameCount`, `dex.scannedClassCount`, `dex.classLoadFailureCount`, `transport.candidateCount`, `manager.candidateCount`, and `probe.elapsedMs`. The scan covers only the supplied `BaseDexClassLoader.pathList.dexElements`; parent and separate split/dynamic loaders are not recursively enumerated. Candidate entries are structural hints, not proof of a live Transport or manager.

Before sharing, inspect the file and redact unrelated log records, account identifiers, tokens, authorization headers, prompts, message bodies, and personal data. The probe block is designed to contain declarations and signatures only; share the complete probe block rather than the entire log file.

If the marker is absent, first confirm that the installed APK is an Open build with the probe changes and that Local API was explicitly enabled before restarting DeepSeek. Absence of a report is not evidence that the host has no matching classes.

## 2.7.2/code289 runtime call-boundary observation update (2026-11-06)

The Open probe now includes a narrowly scoped, passive runtime observer for the mainland
2.7.2 / versionCode=289 host. This is separate from the generic ROI unknown-host discovery
pipeline and does not authorize any unknown-version feature Hook.

### Reviewed observation points

The observer is installed only when all of the following hold:

- Open edition, with no Closed payload;
- Local API is enabled and the separate **启用原生适配诊断探针** switch is explicitly on;
- HostCompat.hostCode271() == 289;
- the host loader resolves ly1 and wu1, and wu1.isAssignableFrom(ly1) is true;
- each target method is unique and has the exact reviewed shape.

The two current observation targets are:

| Role label | Owner / method | Required descriptor shape |
|---|---|---|
| transport-low-level | jw3.g | (wu1, java.lang.Long) -> reference |
| transport-dispatch | zn3.o | (wu1, java.lang.Long) -> reference |

The observer does not call either method itself. It wraps the host's normal invocation with one
chain.proceed() call and returns the original result object unchanged. Installation is
individually guarded to avoid duplicate callbacks. An observer-side diagnostic failure is
isolated from the host call.

### Runtime trace fields

For each observed invocation, diagnostics record only:

- opaque process-local observation ID;
- role label and complete reflected method descriptor;
- synchronous parent method and bounded stack symbols;
- thread name / ID and call depth;
- argument runtime type names;
- whether the first argument is the same object identity as the root Request in the current
  synchronous observation frame;
- returned object's runtime class name and at most eight directly declared interface names;
- exception class name on an exceptional exit.

No argument values, Request fields, return-object fields, Prompt, Completion, URL, headers,
Authorization, cookies, or Token are serialized. The observer does not inspect a Flow by
collecting it, subscribe to it, invoke methods on the result, or alter the original result.

### Evidence limits

- ThreadLocal correlation proves only nested synchronous call relationships on one thread.
  It does not correlate coroutine resumption, executor handoff, or callbacks across threads.
- Matching Request identity at both methods is evidence that the same in-memory object crossed
  both observed boundaries; it does not by itself prove the complete network request/response
  lifecycle.
- A runtime return class and its direct interfaces are type metadata only. They do not prove
  that a returned object is a Kotlin Flow, identify its event schema, or establish where and
  how it is collected.
- The observer is enabled only for code289. It must not be generalized to code287 or code290
  without their own APK-derived exact descriptors and evidence.
- Real-device evidence is available on the diagnostic branch [fix/native-probe-concrete-callback-hooks](https://github.com/qingjian0/Deekseep-Custom/tree/fix/native-probe-concrete-callback-hooks): [open-native-1793943559724.log](https://github.com/qingjian0/Deekseep-Custom/blob/fix/native-probe-concrete-callback-hooks/open-native-1793943559724.log). The 1,676-line capture records four Request/Transport entries, nested `zn3.o -> jw3.g` calls using the same `ly1` object, and three observed continuation callback pairs: `cr9/k41`, `ec0/mm4`, `s5/g0`.
- That capture records 833 callback-enter, 823 callback-return, and zero callback-throw events; ten enter events have no matching terminal event in this file, so the capture is not a fully closed callback ledger. Most recorded calls came from inherited `zb3` / `rj0` methods, demonstrating that the former superclass-wide observer generated substantial unrelated traffic. The bounded concrete-class-only observer is a corrective diagnostic change, not new runtime evidence.
- Re-reading the capture at event granularity shows three `y60.b(fl4,yb3)` collect invocations: `cr9/k41`, `ec0/mm4`, and `s5/g0`. `cr9` and `ec0` implement `fl4`; their observed `a(Object,yb3)` calls received argument types `av1/jf9` and `vd6/kl4`, respectively, and both returned `zgb` (suspended). The first `y60.b` later reported `zgb`; another reported `cd3`; the third collect window has no matching exit in this capture.
- The capture contains no direct `cv1`, `sha`, or `r3.c` dispatch record. It proves that the returned Flow is collected and that concrete collector/continuation pairs execute; it does **not** prove that the DeltaCommand reducer received `ready` / `delta` events or completed message assembly. This is the specific gap addressed by the new code289-only `r3.c` observer, which records allowlisted event names and never reads `sha.b`.
- Runtime evidence does not prove that a complete HTTP response was decoded into UI messages or that a Local API request can drive the host. Source presence and CI success are not runtime evidence.

### Next bounded work

1. Install the current schema-6 Open diagnostic APK on the code289 host build (the diagnostic
   observer is excluded when `BuildInfo.LOCAL_API_INCLUDED=true`). Explicitly enable the probe,
   perform one ordinary host chat, and export the app-private session log.
2. Verify the direct `r3.c(dv1, yb3)` observer reports the actual `cv1` event-name sequence
   (`ready`, one or more `delta`, terminal `close`, or an explicit `parse_error`). Confirm
   each dispatch preserves the host result/exception and that no `sha.b` payload is read.
3. Correlate those dispatch records with the existing `y60.b` collector/Continuation records
   and the completed host UI message. Validate `response.fragments`, fragment type via
   `nk0.k()`, text via `nk0.f()`, and the RESPONSE / THINK / SEARCH / tool fragment mapping.
4. Only after the runtime event ledger is complete, implement the Open adapter around the
   verified `ly1` Request and `zn3.o -> jw3.g -> Ly60` path. Prove Flow collection, cancellation,
   deadline propagation, session creation/reuse, PoW association, and error mapping. Do not
   subscribe a second time or infer the meaning of the `Long` argument.
5. Keep every protocol route exposed. Until code289 runtime tests prove non-streaming, streaming,
   cancellation, error propagation, and session isolation, report `engine_missing` (HTTP 503)
   per request through the execution facade; do not add an edition-level readiness gate or
   fabricate output.

The Gateway keeps its protocol routes exposed. Until a separately reviewed Open native adapter
implements and verifies Request construction, Challenge/PoW, Transport, event decoding,
cancellation, session isolation, and error mapping, the execution facade reports
`engine_missing` (HTTP 503) per request.

## 2026-11-07：code289 回调探针限域修正

- 依据 2026-11-06 五份真实设备日志，Transport 与 `y60.b(fl4, yb3)` 边界已经确认；日志还显示沿回调父类链安装观察器会引入大量无关调用（例如 `zb3.t()`）和同步写盘开销。
- 当前 Local API 工作分支将回调观察范围收窄到 `y60` continuation 实际传入的具体回调类，仅检查该类自身声明的实例方法；排除 static、abstract、bridge、synthetic、0 参数、超过 2 参数及 void 方法；每个具体回调类最多尝试 12 个方法，不遍历父类。
- 选中方法仍完整记录 entry/exit/throw 与类型、线程、耗时，保留逐方法聚合计数；Hook 始终只执行原方法一次。探针关闭后立即透传。
- 报告 schema 更新为 5，显式声明覆盖边界。该修正只降低无关 Hook 和诊断开销，不代表响应 DTO / 事件语义或 Native Adapter 已完成。


## Xiaomi MIUI / HyperOS background retention

The foreground keeper runs in the module package `ds.dsh.DeepSeek`, while the hooked gateway runs in `com.deepseek.chat`. Battery optimization must therefore be checked and configured for both packages independently. MIUI / HyperOS users should allow autostart for both apps, set both battery policies to unrestricted, lock both in Recents, and keep the module's foreground-service notification category enabled. OEM menu names vary. The UI now shows a device-specific guide and marks a missing host heartbeat as a keepalive failure after 15 seconds instead of waiting forever. This is not a substitute for on-device validation.


### Boot-time host delay

A missing host acknowledgement is not equivalent to a user disabling Local API. The keeper must retain the persisted opt-in and continue retrying after boot when MIUI / HyperOS delays host startup or broadcast delivery. It may stop only after an explicit host-disabled acknowledgement or a user stop request; a 90-second acknowledgement timeout is diagnostic, not a shutdown condition.


## Open Gateway protocol audit (supersedes 2026-11-06 snapshot)

The current `dev` implementation supersedes the older protocol snapshot below. The Open Gateway currently exposes:

- `GET /v1/models`: configured model catalog only; this does not imply native execution readiness.
- `POST /v1/chat/completions`: request validation, non-streaming response mapping, text/reasoning SSE, and non-streaming structured tool-call bridge.
- `POST /v1/responses`: input/output mapping, bounded process-local `previous_response_id` continuation state, and Responses-style SSE lifecycle/delta events.
- `POST /v1/messages`: Anthropic Messages request/response mapping and SSE.
- Tool calls are returned to the API client as structured calls; the module does not execute client tools. Tool-call streaming is not supported.
- Usage is estimated and explicitly marked with `dsh_usage_estimated=true`; it is not an exact DeepSeek tokenizer count.
- Image/audio inputs and non-text JSON output formats remain unsupported.

These are protocol-layer capabilities only. The Open native Completion Adapter remains unimplemented and every completion route must continue returning HTTP 503 until the verified code289 host request, Challenge/PoW association, Transport, Flow event decoding, response assembly, cancellation, session isolation, and error mapping are implemented and validated. A passing CI run does not establish real-device native completion.

## 2026-11-07：code289 启动安装失败隔离修复

### 直接证据

```text
hostGeneration=2.7.2/code289-cn
installAllFeatureHooks: 2.7.1 full mode via HostRemap classes=7636
code257 agent tool-log Canvas renderer unavailable:
  ClassNotFoundException: Didn't find class "deekseep unmapped mf6"
installAllFeatureHooks failed: ClassNotFoundException ...
```

该启动记录来自 `qingjian0/apps/README.md` 所归档的 `dsprobe.log`。它同时证明宿主识别结果为 code289、安装诊断标签错误地固定显示 2.7.1，以及旧 code257 Agent Tool Log / Canvas 映射在该宿主上不可解析。该记录不能证明 `mf6` 在 code289 中存在其他可替代类。

### 源码根因

`Main.installAllFeatureHooks()` 将 `agent.tool-log.rounded-rect` 作为普通 `installHookStep()` 执行。该安装步骤抛出异常时会进入外层安装异常处理并中止本轮后续 Hook 安装，而不是仅将该可选渲染功能标记为降级。

### 已提交修复

- `Main.java`：将 Agent Tool Log / rounded-rect 渲染器安装改为 `installBestEffortHook()`。安装失败只标记该可选 Hook degraded，并记录明确日志，不再中断其后的独立功能 Hook。
- `Main.java`：2.7.x full-mode 诊断行改用 `HostCompat.hostLabel()`，不再把 code289/code290 误标为 2.7.1。
- `module/test-hostmap-regression.sh`：新增回归断言，要求该可选 Hook 必须走 best-effort 路径，并要求诊断使用实际宿主标签。
- 提交：`6827f709`（源码）；`eef5a18e`（回归断言），均位于 `work/local-api`。

### 验证边界

本轮只完成源码修改与回归断言编写；尚未获得这两个提交对应的 GitHub Actions 完成结果，也未在 code289 真机重跑启动日志。因此不能声称该修复已通过 CI 或设备验证。

此修复仅隔离可选 UI Hook 的安装失败，不会自动接通 Native Completion。Local API 协议入口保持开放；在 Request Factory、Challenge/PoW、真实 Transport、响应事件解码、取消/超时和 Session 隔离全部具备证据并通过验证前，执行门面按请求返回 `engine_missing`（HTTP 503），不得伪造回复。

### code289 APK 静态证据资产边界（已更正，2026-11-07）

此前“没有 code289 / 2.7.2 APK 静态审计 artifact”的判断只覆盖了 apps 仓库的双版本审计工作流 `42339059934`（2.7.3 / 2.7.5），没有覆盖后续静态审计分支。该结论已由以下独立证据更正：

- apps workflow：`42342447740`，分支 `work/deepseek-272-static-audit`，artifact：`deepseek-272-static-evidence`。
- APK：`deepseek-2-7-2.apk`；Manifest：package `com.deepseek.chat`、versionName `2.7.2`、versionCode `289`。
- APK SHA-256：`3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff`；大小 22,205,160 bytes。
- DEX：3 个；Smali classes：15,365；method declarations：72,258；候选类 23/23 命中、117 个候选方法、3,369 个候选调用点。
- JADX 退出码 3、346 条诊断；关键调用链以 APK DEX/Smali 和 Serializer descriptor 为准。

因此 code289 的静态 Request / Challenge / Transport / Flow / SSE / Delta reducer 链路已有直接 APK 证据。该证据只证明静态结构，不代表模块已经运行时 collect Flow，也不代表 Native Adapter 或真机 Completion 已完成。不得把 2.7.3 / 2.7.5 的混淆符号映射到 code289。


## 2026-11-07：code289 Callback 结构诊断 schema 6

### 问题

schema 5 记录了 Callback 方法描述符、参数类型、返回类型、执行耗时和异常类型，但不记录参数/返回对象的结构。对 qj3.y(Object) 等高频方法，仅凭 Object 参数类型无法区分事件 envelope、消息 fragment、协程状态或其他包装对象。

### 修改

- Callback 方法按完整描述符排序后再应用每类最多 12 个方法的上限，避免反射返回顺序不确定导致不同运行选择不同方法。
- 每个 Callback 方法第一次执行时，记录 arg0/arg1 的对象类名以及最多 16 个声明字段的字段名/字段类型。
- 每个 Callback 方法第一次正常返回时，记录返回对象相同范围的结构。
- 结构记录只读取反射元数据，不读取字段值、不调用 getter、不序列化对象；不记录消息正文、凭据、Cookie、Token 或请求头。
- 参数结构和成功返回结构分别按方法签名去重；新诊断 session 清空结构去重状态，但保留已安装 Hook 注册状态，避免重复注册。
- UI 说明同步明确结构采集边界和方法排序策略。

### schema

- report.schema=6
- session-start schema=6
- diagnostic.callback_hook_limits 声明 descriptor 排序、每类最多 12 个方法、不遍历父类。
- diagnostic.callback_shape_capture 声明每方法一次参数/成功返回对象字段名和类型采集，不读取字段值。

### 影响与边界

该修改用于让下一轮 code289 真机日志可以识别 callback 的数据对象形状，帮助定位事件包装层和响应解码边界。它不会自行解释混淆字段的业务语义，也不会把 qj3.y 直接认定为 SSE parser 或 DeltaCommand reducer。

相关提交位于 work/local-api：4212bf84（结构采集）、20ccdc7f（session 重置）、c9a79ef4（UI）、94a75ed4 / 2465873f（回归契约）。

本次源码和回归契约已提交；尚未执行 Android 构建、脚本或设备验证，不声称 schema 6 已通过运行验证。


## 2026-11-07：code289 APK 静态证据已找到并完成核心调用链还原

### 证据来源更正

此前本文件曾记录 apps 仓库未找到 2.7.2/code289 APK。该结论只检查了 apps/main，范围不完整。后续枚举 apps 全部分支后，在 qingjian0/apps 的 work/deepseek-272-static-audit 分支找到 deepseek-2-7-2.apk，并从 APK Manifest 的 Android binary XML 确认 package=com.deepseek.chat、versionName=2.7.2、versionCode=289。此前“code289 APK 不存在”的记录已被本节取代。

- APK SHA-256：3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff
- DEX：3 个；class files：15,365；method declarations：72,258
- 动态候选：23/23 个静态类匹配；117 个候选方法；3,369 个候选调用点
- apps 审计 workflow run：42342447740，成功
- 完整报告：[apps/docs/deepseek-272-native-callgraph.md](https://github.com/qingjian0/apps/blob/work/deepseek-272-static-audit/docs/deepseek-272-native-callgraph.md)

### code289 真实 Request / Transport

```text
nn0 repository request factory
  -> mw1 request coroutine
  -> ly1 implements wu1
  -> zn3.o(wu1, Long)
  -> jw3.g(wu1, Long)
  -> Ly60 (cold Flow)
  -> collect via b(fl4, Continuation)
  -> l5 coroutine
  -> bi5.b (Ktor request execution)
  -> g0 (HTTP response handler)
  -> ts1 (SSE ByteReadChannel line parser)
  -> s5 (event wrapper)
  -> r3.c (business event decoder/reducer)
```

- ly1 Serializer jy1 的 JSON 字段：chat_session_id、parent_message_id、prompt、ref_file_ids、thinking_enabled、search_enabled、audio_id、preempt、model_type、action。
- ly1.k 不属于 JSON serializer 字段；jw3.g 将其作为 X-DS-PoW-Response header 输入。
- zn3.o / jw3.g 返回 Flow；调用入口不等于网络请求已经执行。必须 collect Ly60。
- y60.b(fl4, Continuation) 是 Flow collect 边界，不是 SSE parser。
- g0 处理 HTTP status / Content-Type 和 x-ds-sse-heartbeat-timeout-secs（默认 10 秒）。ts1 才负责读取 ByteReadChannel 并解析 SSE event/data。
- s5 将 sha(eventName,data) 包装为 cv1；r3.c 负责 ready、hint、title、update_session、close 和 DeltaCommand 等业务事件分支。

### code289 Challenge / PoW

```text
/api/v0/chat/create_pow_challenge
  -> ut1(target_path)
  -> ms9 ServerChatChallenge
  -> df0.d(targetPath, ms9, Continuation)
  -> bf0 / PowCalculator.a(difficulty, base, challenge)
  -> gf0 AuthedPowSolution
  -> nq1 ChallengePlugin
  -> X-DS-Challenge-Response / X-DS-PoW-Response
  -> request retry
```

- ms9 字段：algorithm、challenge、salt、signature、difficulty、expire_at、expire_after、target_path。
- gf0 字段：algorithm、challenge、salt、signature、answer、target_path。
- PowCalculator 位于 com.deepseek.crypto，调用 librscrypto 的 nativeCalculateDeepSeekHashV1Pow；code289 构造 base 为 salt + "_" + expire_at + "_"，并传入 challenge 与 difficulty。
- nq1 是 Ktor ChallengePlugin，处理 challenge、PoW 求解、并发协调和 retry。应复用宿主 zn3/bi5 client，不应在 Local API 侧重写 PoW。

### Delta / Response 语义

- SSE 的 sha.a 为 event name，sha.b 为 data JSON。
- 当 `sha.a == null`（没有 SSE event name）时，`r3.c` 将 `sha.b` 按 DeltaCommand 解码；其 `p/o/v` 三个成员分别对应 path / operation / value。
- 第一个 null-name DeltaCommand 通过 `doc.c0()` 建立 `zca` root；后续 null-name DeltaCommand 通过 `doc.W()` 交给 `l23` patch engine。已确认操作包含 SET、APPEND、BATCH。
- `ready` 是独立的 `ChatCompletionReadyEventData` 元数据事件，不负责创建 `zca` root；`hint`、`title`、`update_session`、`close` 也有各自分支。`r3.c` 没有名为 `delta` 或 `parse_error` 的字符串事件分支，不能把它们当成 DeltaCommand 的事件名。
- `zca.response` 映射到 `gy`；`gy` 的 fragments 由 `xr.B()` 拼接可见文本。
- delta 的 data 不能直接当作文本。需要应用 patch，再从 response.fragments 提取 RESPONSE / THINK / tool/search 等不同 fragment。

#### code289 DeltaCommand 的实际 Patch 规则（Smali 已确认）

- `r3.c(Ldv1, Lyb3)` 收到 `cv1(sha)` 后，以 `sha.a` 作为 event name、`sha.b` 作为 data JSON。只有当 `sha.a == null` 时，data 才按 `st3` Serializer 解码为 DeltaCommand；`st3` 的 JSON 成员名确认为 `p`、`o`、`v`，分别对应 path、operation、value。
- 第一个 null-name DeltaCommand 建立初始 `zca` response root；后续 null-name DeltaCommand 进入 `doc.W(st3, gv1)`。`ready` 另行解码为 `uu1` ReadyData。无效事件会走 `InvalidEvent`，Patch 返回失败会走 `InvalidDelta`，不能静默吞掉。
- `doc.W` 保存 path / operation 上下文：初始 path 为空字符串、operation 为 `SET`；命令字段为 null 时沿用上一次上下文。它随后调用 `l23.y`。
- `l23.y` 按 `/` 拆分 path，逐段调用 `s13.c` 定位父节点，再对末段执行操作：`SET` 调用目标节点的 `l(path, value, context)`；`APPEND` 调用 `m(value, path)`。
- `BATCH` 先定位 path 指向的节点，再把 value 解码为 `List<st3>`，按列表顺序递归应用；子命令缺少 path / operation 时继承当前 BATCH 的 path / operation。子命令不是并行应用。
- `zca.c("response")` 返回 `gy`；`gy.c("fragments")` 返回 `Ley1`；`Ley1.c(index)` 支持有效范围内的负索引。
- 对 `gy.fragments`：`SET` 会解码列表并替换现有 fragments；`APPEND` 会解码列表并追加。对文本 fragment（例如实现 `d54` / `nk0` 的 `v44`）：`SET content` 替换当前文本，`APPEND content` 将新文本拼接到已有文本后面；`APPEND references` 追加引用列表。
- `nk0.k()` 返回 fragment 类型，`nk0.f()` 返回该 fragment 的当前文本；`xr.B()` 遍历 fragments，只拼接实现 `nk0` 的 fragment 文本。它不是对 SSE data 字符串做简单提取。
- `gy` 还单独处理 `accumulated_token_usage`、`conversation_mode`、`ban_regenerate`、`incomplete_message`、`thinking_enabled`、`status`、`has_pending_fragment`、`search_enabled`、`quasi_status`、`search_triggered` 和 `extra_search_providers` 等根字段；不能把所有 patch 都简化为 fragments 文本。

**实现约束：** Open Adapter 若自行实现 reducer，必须保留 path/op 上下文、BATCH 顺序、数组索引语义、SET/APPEND 差异和未知字段处理；若复用宿主 reducer，则必须先证明其根状态和 collector 生命周期可安全隔离。仅凭静态证据不能声称任一方案已运行。

### 对 Local API 的实现决策

1. code289 不能使用旧版 liveQ71/liveR92；应捕获 nn0 主构造器注入的 zn3 或 zn3 构造器实例。
2. 通过 ly1 -> zn3.o -> Ly60 collect 进入宿主原生 Ktor client。
3. 复用 nq1 ChallengePlugin 自动完成 challenge / PoW / retry。
4. 在 fl4 collector 中消费 cv1 -> sha；按 event name 解码。
5. DeltaCommand 必须按 p/o/v patch 语义应用到 response state；文本从 gy.fragments / gy.B() 提取。
6. 需要独立实现 request/session ID、parent_message_id 更新、stream cancellation、deadline、错误映射和 tool/reasoning fragment 映射。

### 尚未声称完成的部分

上述静态调用链已由 code289 APK 的 DEX/Smali 和 serializer descriptor 直接确认；但尚未完成在 Deekseep-Custom 中捕获 zn3、collect Ly60、应用 DeltaCommand、映射 CompletionResult 的实现，也没有 code289 真机端到端结果。Native Adapter 仍未完成，Local API 真实回复仍未验收。


## 外部 Local API 参考

已对开源项目 [`Xposed-Modules-Repo/com.dsmod.probe`](https://github.com/Xposed-Modules-Repo/com.dsmod.probe) 的 Local API 实现进行源码级分析，重点核对了 `LocalApiGateway`、`Main.executeLocalApiCompletion`、`NativeApiPatchDecoder`、`OpenAiToolBridge` 和协议回归测试。其宿主版本为 DeepSeek 2.2.2（code233/236），不能直接复用混淆符号到本项目 2.7.x/code289。架构差异、可移植设计、禁止照搬项和 ROI 实施顺序见 [DSMOD_PROBE_LOCAL_API_REFERENCE.md](architecture/DSMOD_PROBE_LOCAL_API_REFERENCE.md)。


## 2026-11-07：code289 Delta dispatch 观察点

- 新增 code289-only `r3.c(dv1, yb3)` 透明 Hook，直接观察静态调用图中的 DeltaCommand reducer，不再仅凭 `y60.b` 的返回值推断事件已被消费。
- 事件日志只读取 `cv1.a -> sha.a`：null event name 记录为 `delta-command`；白名单字符串仅包括 `ready`、`hint`、`title`、`update_session`、`close`；其他字符串记录为 `other`。不读取 `sha.b` 数据 JSON。
- Hook 在禁用时直接透传；启用时原方法只执行一次，保留原始返回值和异常。诊断读取/日志异常不替代宿主执行。Hook 注册尝试一旦开始即保持去重标记，避免框架部分注册后重试造成重复拦截。
- 该观察点只用于补齐真机证据，不构成 Native Adapter。当前仍未证明真实 null-name DeltaCommand、`ready` 与 `close` 的运行时事件序列，也未证明 Local API 能驱动模型执行。


## 2026-11-07：schema-5 设备日志与 schema-6 Delta 探针证据隔离

本节固定区分历史设备证据与当前源码能力，避免把旧日志误记为新观察点已运行。

### 历史设备日志（schema 5）

- 文件：`open-native-1793943559724.log`
- 来源分支：`fix/native-probe-concrete-callback-hooks`
- Git blob：`b9b2acff5df7a1981af9cc0ccbab0f9cfb3a8a59`
- 文件长度：532,434 bytes；1,676 行。
- 该日志可支持此前记录的 Request / Transport 关联、`y60.b(fl4,yb3)` collect 边界和三个具体 continuation/callback 对的观察结论。
- 该文件属于 schema 5；其中没有 `delta-dispatch-enter`、`delta-dispatch-return` 或 `delta-dispatch-throw` 事件。因此它不能证明 `r3.c(dv1,yb3)` Delta dispatch 观察器已经运行，也不能证明 `ready → delta* → close` 的事件序列。

### 当前 schema-6 源码能力

- 当前 `work/local-api` 的 `OpenNativeCompletionProbe` 已增加 code289 专用 `r3.c(dv1,yb3)` 透明观察点。
- 事件名读取严格限制为 `cv1.a → sha.a` 的白名单字符串；不读取 `sha.b` 数据字段，不读取消息正文。
- 观察器在原调用前后记录事件名、返回类型或异常类型，并透传原始调用；这只是源码实现事实，不等于设备上已经产生记录。
- 探针调度明确要求 `BuildInfo.LOCAL_API_INCLUDED == false`。因此应使用带 schema-6 探针、且不包含 Local API Gateway 的独立 Open 诊断构建采集；不能把该诊断构建描述成正式 Local API APK。
- 当前没有从 schema-6 诊断构建取得的设备日志。下一次设备证据必须包含探针安装标记、`delta-dispatch-installed`、实际事件名序列及对应 `y60.b` collect 关联；若只出现安装记录而无事件记录，应判定为“观察点已安装但未捕获目标调用”，不能判定事件链通过。

### 对 Native Adapter 的实施约束

在上述 schema-6 事件日志与响应消费证据补齐前，不实现猜测性的 `cv1/sha` payload 解码、Delta reducer 或响应组装。可以继续完成不依赖宿主私有字段语义的协议边界、取消契约、会话隔离和纯 JVM 测试；任何宿主调用仍须经过官方正常 Request / Challenge / PoW / Transport 路径，Completion 协议入口保持开放；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503）。


## 2026-11-07：Request 构造器与 Repository 注入的 Smali 级复核

已从官方 code289 静态审计 Artifact 的 `ly1.smali`、`nn0.smali`、`mw1.smali` 逐条复核：

- `nn0.<init>(Lvc3;Lzn3;Lilb;Lbd3;Ljf2;Lpi;Lau7;Lcx2;Lmj7;Lgs;Liab;)` 将第二个参数 `zn3` 保存到实例字段 `nn0.b`。这是可用于建立版本专用 Transport 实例捕获点的直接静态证据；捕获后仍需在运行时验证对象确实服务于目标会话。
- `ly1` 的实际构造器是 Kotlin synthetic default-argument constructor：`<init>(String, Integer, String, ArrayList, boolean, boolean, String, boolean, String, String, int)`。
- 构造器最后的 int 是 Kotlin 默认参数 mask，不是业务字段。Smali 明确显示 mask `0x40` 将 audio_id 参数置为 null；mask `0x200` 将 action 默认设为 `retry`。构造器把第十个 String 参数写入字段 `k`，该字段由 `jw3.g` 用作 `X-DS-PoW-Response` header；action 写入字段 `j`。
- `mw1.y()` 的真实产品调用顺序为：session ID、parent message ID、prompt、附件列表、thinking、search、audio ID、preempt、model_type、action、mask `0x200`；随后调用 `zn3.o(request, null)`。因此该产品调用点已证明第二个 Long 参数传 null 是合法的正常调用方式，不必猜测其语义。
- `mw1.y()` 仅在 parent_message_id 为空时从 `ys.k()` 取得 model_type；有 parent 时传 null。preempt 则由 `nn0.h(ys, parent_message_id)` 计算，不是固定常量。
- `nn0.d(ys, sessionId, parentId, attachments, thinking, search, audioId, ...)` 经 `mw1` 创建请求。直接构造 `ly1` 虽然有明确签名，但若没有真实 `ys` 会话模型，就不能从当前证据推出正确的 model_type 和 preempt 值。

**实现结论：** 可以确定 Transport 捕获点、Request 构造器参数顺序、默认 mask 和 `zn3.o(request, null)` 调用方式；但不能把 `model_type`、`preempt` 或 session lifecycle 填成猜测值。下一步应先确定 API session 到宿主 `ys` 状态的创建/复用策略，再实现 Request Factory；不得把随意生成的 session ID 当作已验证的宿主会话。


## 2026-11-07：code289 Request continuation 与 Session Factory 边界复核

本轮对官方 code289 Smali 继续追踪 `nn0.d`、`kw1`、`uj2`、`ik2.j0`、`ss9`、`ys` 与会话选择路径，补充以下边界：

### 1. `kw1` 不是第二个“全 null 新会话调用点”

- `kw1` 是 `nn0.d(...)` 的 Kotlin suspend continuation 类。
- `nn0.d` 在收到普通 Continuation 时创建 `kw1`，并将原始调用参数保存在 continuation 字段中。
- `kw1.y()` 恢复执行时以 null 占位参数重新进入 `nn0.d`；`nn0.d` 先识别 continuation 状态，再从 `kw1` 字段恢复原参数。
- 因此 `kw1.y()` 中表面上的 `nn0.d(null, null, ...)` 是协程状态机恢复调用，不是产品独立发起的空 session 请求。不能据此推断新会话允许任意 null sessionId。
- 可见的业务调用入口是 `ik2.j0(...)` 创建 `uj2`，由 `uj2.y()` 调用 `nn0.d(ys, ...)`；它携带真实 `ys` 会话对象和对应消息参数。

### 2. `ss9` 与 `ys` 的构造关系

- `ss9` primary constructor 接收字段 `a..h`，并将 `i`（model type）初始化为 null、`j` 初始化为 false。
- `qs9` synthetic serializer constructor 可以按字段 mask 反序列化 `ss9` 的可选字段；`fx1` 中的直接 `new ss9` 则是基于已有 `ss9` 复制并更新计时字段，不是生成新的 chat_session_id。
- `ys.<init>(ss9, qs)` 将 `ss9.a` 作为会话键，并从记录复制 title/model metadata、token/时间状态及 model-type state；它是将持久化会话记录装配成请求侧会话状态的一条明确路径。
- `ys` 也存在直接的多参数构造器；`jt1` 的会话导航/选择逻辑会从 `sr7` 接口读取 `a()/b()/c()` 并构造 `ys`。该路径说明宿主允许从导航目标/选择项装配内存会话状态，但当前静态证据尚未把所有 `sr7` 实现映射为新会话创建 API，也没有证明其 `a()` 值可以由 Local API 自行生成。

### 3. UUID 误判排除

- `oe2` 中可见的 `UUID.randomUUID().toString()` 创建的是 `ada(elapsedRealtime, uuid)`，并作为消息/fragment 的本地标识传入后续流程；它不是 `ss9.a` 或 `ys.a` 的赋值点。
- 其他 DEX 中出现的 UUID 调用属于不同对象/业务，不能仅凭同一 APK 中存在 UUID 生成就推断 chat_session_id 由客户端随机生成。

### 4. 当前实现决策

- 不把 `kw1` 的 continuation 占位 null 当作新会话协议。
- 不把消息 fragment UUID 当作 chat_session_id。
- 不从 `ss9` 的复制构造器推断新会话创建规则。
- 下一步需继续沿 `ik2.j0` 的上游调用和 `ys` 直接构造器来源，确定首次用户消息时 `ys.a` 的真实生成/获取位置，以及 `ss9.i` model_type 的选择和回填链路。
- 在此之前，不实现猜测性的 Session Factory；Completion 协议路由保持开放；执行引擎缺失时按请求返回 `engine_missing`，不伪造回复。

本节结论来自 APK DEX/Smali 静态结构；不代表已在设备上验证该会话创建链路。


## 2026-11-07：code289 宿主新会话占位对象与空 sessionId 证据

本轮继续检查完整 APK 的 `oe2`、`ik2`、`ys`、`nn0` 调用关系，发现宿主 UI 确实有一条显式的新会话占位对象路径。

### 1. `oe2.j()` 创建新会话占位组件

`oe2.j()` 的 Smali 直接执行：

- `new ys(...)`；
- 第一个 String 参数（`ys.a`，会话 ID）为字面量空字符串 `""`；
- 第二个 String（标题）也为 `""`；
- 其余会话统计字段以初始值构造；
- 随后创建 `Lik2`（ChatSessionComponent）并返回。

`oe2.j()` 构造 `Lik2` 时，向其回调参数传入一个 Kotlin function reference，引用名为 `sendMessageToNewSession`，签名字符串为：

`sendMessageToNewSession(ChatSessionComponent, MessageForwardRequest, Function0)`

因此，空字符串确实是宿主 UI 新会话占位对象的 session key；这不是 UUID 推测，也不是 continuation 占位参数。

### 2. 首次消息事件进入该回调的证据

`ik2` 处理 `kj2` 事件时：

- 从 `kj2.a` 的 `xr` 读取文本与附件；
- 从当前 `ys` 读取 `model_type`（`ys.k()`）；
- 当 `ys.k()` 为空时，回退到 `Lik2.Z()` 的当前 model selection；
- 读取 `kj2.b` 的 action；
- 生成的 UUID 与 elapsedRealtime 被封装进 `ada`，作为消息/fragment 标识；
- 构造 `Lo97`（MessageForwardRequest），再调用 `Lik2.i` 中保存的回调，参数为当前 ChatSessionComponent、请求对象和完成回调。

`Lik2.i` 正是 `oe2.j()` 创建时注入的 `sendMessageToNewSession` function reference。由此可确认：宿主新会话组件将消息转交给专门的新会话回调，而不是把 fragment UUID 当作 session ID。

### 3. 证据边界与实现约束

- 已证实：宿主 UI 新会话占位对象的 `ys.a == ""`。
- 已证实：新会话消息回调拿到 `Lik2` 与 `Lo97`，请求对象包含从当前会话选择状态得到的 model type；为空时回退当前组件 model selection。
- 后续已将该 function reference 映射到静态目标 `oe2.i(oe2, Lik2, Lo97, Function0)`；其调用 `fx1.m()`，使用宿主预取队列或 `/api/v0/chat_session/create` 获取 `ss9`，装配真实 `ys`，更新 model key 并安排仓库持久化。该发现和完整链路见后续“服务端 Session Factory”章节。
- 尚待确认：回调之后 `jj2` 消息事件进入 `ik2/uj2/nn0.d()` 的精确对象传递与时序，以及 actor 持久化完成是否先于原生请求。Local API 是否安全复用 UI 回调链仍未证明。

### 4. 对 Native Session Factory 的决策

目前最可靠的实现候选是复用宿主 `fx1.m()` Session Factory，并遵循 `oe2.i` 中的 model key 更新和 `fx1.l()` 持久化顺序；不要自行生成 UUID 或复制创建 HTTP。Local API 尚未完成对 `fx1` 的安全获取和 suspend 桥接。

`ys.a == ""` 只证明 UI 新会话占位对象的初始键值，不证明服务端最终请求必须始终携带空字符串。Native Completion 协议入口保持开放；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503）。

本节结论来自 code289 APK 静态 Smali；没有声称真机运行已观察到该过程。


## 2026-11-07：code289 新会话模型选择状态来源

继续追踪 `oe2.q`、`i37.s`、`vi7` 与 `mw1.y` 后，补充 model_type 来源：

1. `oe2` 初始化时通过 `i37.s(mj7)` 从模型配置列表选取 `vi7`：优先取 `vi7.e == true` 的当前选中项；若不存在，回退静态默认配置 `kj7.c`。
2. `oe2.q` 保存的是上述选中项的 `vi7.a` 字符串 StateFlow。
3. `oe2.j()` 创建空 ID 新会话占位对象时，将 `oe2.q` 直接传入 `ys.d`（model type StateFlow），所以新会话初始 model_type 来自当前宿主模型选择，而不是从 API 请求名推导。
4. `ik2` 处理消息事件时优先取 `ys.k()`；若为空则回退 `Lik2.Z()` 当前模型选择，再构造 MessageForwardRequest。
5. `mw1.y()` 构造原生 `ly1` 请求时，只有 parentMessageId 为 null 才把 `ys.k()` 放入 request 的 model_type 字段；有 parent 时传 null。这个行为与之前确认的 `nn0.d` 产品调用链一致。

**实现含义：** 如果要复用宿主新会话模型语义，应读取当前宿主选中模型的 `vi7.a` / `oe2.q` 状态；不能硬编码 `default`，也不能仅凭 API 模型名 `deepseek-chat` 或 `deepseek-reasoner` 自行映射。当前静态证据证明的是宿主内部 model_type 状态来源，不证明 API model ID 与内部 model_type 的映射规则。

静态 Smali 可见 `vi7` 的默认构造字段值为 `default`，且代码存在对 `expert` 值的专门分支；但这不足以推导 Local API 的外部模型映射。Completion 协议入口保持开放；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503）。


### 5. 对共享 sendMessageToNewSession 回调的额外边界

进一步检查 `oe2.k(Lys` 发现：恢复已有会话组件时也注入同一个 `sendMessageToNewSession` function reference。因此不能仅凭函数名就认定该回调只负责“创建新 session”；它是 ChatSessionComponent 的消息转发回调，既被新占位组件 `oe2.j()` 使用，也被已有会话组件 `oe2.k(ys` 使用。当前 R8 Smali 中未定位到该引用对应的函数体，回调内部是否区分空/非空 sessionId 仍待证实。

同时，`oe2.q` 的初始化来源可进一步确定：`oe2.<init>` 调用 `i37.s(mj7)`，取返回 `vi7.a`，封装为 StateFlow 后保存到 `oe2.q`；`i37.s` 从模型配置列表优先选择 `vi7.e == true` 的项，无选中项才回退 `kj7.c`。因此新占位 `ys` 的 model_type 状态初值来自宿主当前选择模型配置的 key。它不是从 API model 字符串直接推导出来的。


## 2026-11-07：code289 服务端 Session Factory 与预取队列已定位

本轮继续沿 `oe2.i`、`fx1.m`、`mb0.F`、`ch2.b`、`rz0`、`j41`、`ft9`、`Lok2`、`ss9` 追踪，已经定位宿主的服务端会话创建实现，而不再只是 UI 空 ID 占位对象。

### 1. 宿主创建流程入口与预取队列

`oe2.i(oe2, Lik2, Lo97, Function0)` 是此前 function reference `sendMessageToNewSession` 对应的实际静态目标方法（签名与回调调用参数一致）。其协程 `a20` 会调用：

`fx1.m(Continuation)`

`fx1.m()` 先检查 `fx1.m` 持有的 `pi` 内部会话预取队列（`pi.e` 为 `ce3` / 队列管理器）：

- 队列有可用 `fn8`：从队列取出已有 `ss9`，更新计时字段，装配 `ys(ss9, qs)`，清空标题，作为缓存命中返回。
- 队列无可用项：通过 `pi.b` 的宿主 coroutine context 启动 `mb0.F()`，走服务端创建分支。
- 两种成功分支都返回 `pt7`，其 `b` 是 `tn2(ys, cacheHit)`；`tn2.b` 用于后续 `is_cache_hit` 埋点。

因此，宿主的“创建新会话”不是每次都裸调 HTTP；它优先消费预取队列，缺少缓存时才走服务端创建。

### 2. 服务端创建请求与响应解析

`mb0.F()` 通过 `pi.c`（`zn3`）进入 `zn3.b`（`ch2`）网络服务；`ch2.b()` 使用 `bi5` 的 serializer/request pipeline。对应 `rz0` request builder 的分支明确包含路径：

`/api/v0/chat_session/create`

响应包装类 `ft9` 明确声明字段：
- `a`：biz_code；
- `b`：biz_msg；
- `c`：biz_data（`Lc26` JSON payload）。

`mb0.F()` 取得并校验响应后，将响应交给 `j41`；`j41` 的对应分支使用 `Lok2.serializer()` 解码 `ft9.c`，取出 `Lok2.a:Lss9`，再执行：

`new ys(ss9, qs)`

随后将该 `ys` 包装为 `tn2(ys, false)` 并返回。真实 session ID 因而来自服务端响应中的 `ss9.a`，而不是客户端 UUID。

### 3. 模型选择更新与会话持久化顺序

`oe2.i` 收到 `fx1.m()` 的 `pt7` 成功结果后：

1. 从 `tn2.a` 取回服务端会话 `ys)；
2. 使用 `ys.G(Lo97.d)` 将当前消息请求选择的模型 key 写入 `ys.d)（model type StateFlow）；这一步发生在持久化之前；
3. 记录 `chat_session_created`，其中 `chat_session_id` 取自 `ys.a)，`sse_transaction_uuid` 取自消息/fragment `ada)；
4. 启动 `Leo2` 对应协程，调用 `fx1.l(ys`；
5. `fx1.l(ys` 委托到 `pi.e(ys`，通过 `wn2` 投递到 `pi.g:Lix0` actor/队列更新流程；
6. 完成 UI 回调后，再把原始 `Lo97` 请求转换为消息事件投递给当前会话组件。

这给出了宿主会话初始化的实际顺序：**服务端/预取会话取得 → 当前模型 key 回填 → 会话仓库异步持久化 → 消息事件进入组件**。

### 4. Native Session Factory 实现决策

- 不再自行构造 `ss9` 或生成 `chat_session_id`。
- 优先研究复用宿主 `fx1.m()` 的预取/服务端创建逻辑，并在成功后按宿主顺序更新 model type 与持久化。
- 不直接复制 `/api/v0/chat_session/create` 请求；必须保留 `zn3/ch2/bi5` 网络栈、响应校验、认证/Challenge/PoW 与宿主错误处理。
- 当前仍未实现 Local API 到该宿主服务的安全桥接。需要确认可稳定取得 `fx1` 实例的生命周期入口、Continuation/取消语义，以及 `fx1.l()` actor 写入完成与失败反馈。
- `fx1.m()` 返回的缓存命中和网络创建结果需要分别测试；不能只测试无缓存分支。
- 由于本轮只有静态 Smali 证据，尚未声称已在真机观察到该 endpoint 或会话回填。Completion 协议入口保持开放；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503）。

本节是 code289 APK 静态调用链结论，不代表运行时验证或 Local API 已可用。


### 5. Local API 取得宿主 Session Factory 的候选接入点

继续检查 `oe2` 构造器可确认：

- `oe2` 是宿主会话 ViewModel/状态持有者（继承 `ksb`）。
- 它通过宿主 DI 容器 `di9.c(...)` 取得 `fx1)，并保存到 final 字段 `oe2.h:Lfx1;`。
- `oe2.j()` 和 `oe2.k(ys)` 均使用该字段构造 `Lik2`，因此 `oe2.h` 与前述 `fx1.m()/fx1.l()` 属于同一会话仓库实例。
- `Main.startz1()` 已经拥有宿主 `hostClassLoader`，且通过 `Main.MODULE.hook(Method)` 安装透明 Hook；同一 Hook API 可对 `oe2` 构造器或 `fx1` 构造器做 after-hook 观察。

**候选方案（尚未实现）：** 在 code289 Open 分支安装只读构造器 Hook，优先从 `oe2` 实例字段 `h` 捕获 `fx1`，必要时辅以 `fx1` 构造器捕获；只保存弱引用或生命周期受控引用。没有捕获到可用仓库实例时，Session Factory 应明确返回 unavailable/503，不扫描任意对象堆、不构造替代 session、不直接请求创建 endpoint。

随后在后台工作线程中桥接 `fx1.m(Continuation)`，解析 `wt7/pt7/tn2` 成功/失败结果；成功后按宿主顺序更新 `ys.G(modelKey)` 并调用 `fx1.l(ys)`。但必须先验证 suspend Continuation 的结果解包、超时、取消传播和 actor 持久化时序，不能用阻塞 UI 线程或无取消能力的永久等待。

该接入点目前只是从静态字段/构造器证据推导出的工程候选，未添加代码、未运行 Hook，也未验证生命周期覆盖率。


## 2026-11-07 code289 静态深挖补充

新增独立审计记录：[NATIVE_ADAPTER_CODE289_STATIC_DEEP_DIVE_2026-11-07.md](NATIVE_ADAPTER_CODE289_STATIC_DEEP_DIVE_2026-11-07.md)。

本补充纠正此前将 `oe2.i()` 视为 Session Factory 的错误归类：当前 Smali 证据支持分别研究 `oe2.j/k/r`、`fx1.m()` 与 `fx1.l()`；其中 `fx1.m()` 是带预取队列快速路径和异步回退的 suspend 方法，不能仅凭该方法体断言其回退已完成服务端会话创建。新增记录同时展开 `zn3.o -> jw3.g -> y60` 与 `r3.c` 的静态边界。

此文档是静态证据，不构成运行时 readiness。Native Completion 协议入口保持开放；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503）。


## 2026-11-07：code289 Session Bootstrap 回退路径静态确认

补充依据：`docs/NATIVE_ADAPTER_CODE289_STATIC_DEEP_DIVE_2026-11-07.md` 第 9 节；可复现检查脚本：`tools/audit_code289_session_fallback.py`。

### 已确认的静态链

```text
fx1.m queue miss
 -> mb0(pi)
 -> pi.c / zn3.b / ch2.b
 -> rz0 branch 2
 -> /api/v0/chat_session/create
 -> ServerResponseBizDataWrapper<ServerDefaultBizCode>
 -> zs9.a(true)
 -> biz_code == 0
 -> j41 decodes ok2 / ss9
 -> ys(ss9, ...)
 -> tn2(ys, false) wrapped by pt7
```

- `rz0` 分支明确包含 `/api/v0/chat_session/create`，并设置 5000 ms 请求超时。请求 builder 默认 method 是 GET，该分支没有显式覆盖；`rh5.d` 随后交给 `cf5.a` 客户端管线，因此最终线上 method 仍需排除管线改写后再定论。
- `ch2.b` 使用 `ServerResponseBizDataWrapper` 与 `ServerDefaultBizCode` 的 serializer。
- `dt9` descriptor 明确包含 `biz_code`、`biz_msg`、`biz_data`。
- `j41` 从 biz_data 解码 `ok2`，提取 `ss9` 并构造 `ys`。
- 业务码非零及传输/解码异常有独立错误结果，不可把 HTTP 200 直接视为业务成功。
- `ru7.N` 是协程上下文执行/切换层，不是该业务请求 API 本身。

### 仍未证明

- `rz0` 请求的 HTTP method 尚未从本段调用链确认；`z2c.V` 仅设置 `Content-Type`，不得误标为 POST。
- `bi5.e` 的完整网络执行、认证拦截器、challenge/PoW 和公共 header 依赖尚未全部追完。
- `ys` 后续写入、宿主当前会话绑定、`tn2` 消费者及取消清理语义尚未证明。
- 该静态链不授权模块直接构造 `rz0/ch2` 或在外部上下文调用 `fx1.m`。

本轮脚本对 code289 artifact 的 17 项断言全部通过（17/17）；这只是 Smali 静态结构验证，不是 Android 构建或真机运行验证。Gateway 协议入口保持开放；原生引擎缺失时按请求返回 `engine_missing`（HTTP 503）。


## 2026-11-07：code289 Ktor ChallengePlugin / PoW 网络管线静态追踪

本轮直接检查 code289 APK 的 `all-smali.zip`，继续展开此前尚未完成的 `bi5.e -> rh5 -> cf5` 网络执行路径。新增证据表明，宿主请求不是简单交给裸 HTTP client：其 Ktor Client 配置包含独立的 `ChallengePlugin`，请求阶段会协调 challenge attempt，并按需处理 challenge / PoW response header。

### 1. 插件注册与调用入口

- `p61` 的 Ktor client 配置路径读取 `Lnq1.a` 插件实例，并通过 `gf5.a(Plugin, Configuration)` 将其注册到 client builder。
- `nq1.<clinit>` 为插件声明名 `ChallengePlugin`，并声明 `ChallengeAttempt` 属性键。
- `y10` 的请求执行协程调用 `nq1.a(cf5, requestBuilder, hq1, continuation, ...)`，因此 challenge 处理属于宿主原始请求管线的一部分，不是 `jw3.g` 外部的可选补丁。
- `nq1.a` 在请求阶段读取已有的 `X-DS-PoW-Response` header，进入 `nq1.c` 的 challenge/header 处理，并通过 `hq1.a(pq1, Continuation)` 协调 challenge attempt。该方法同时持有 `AtomicReference`，用于并发请求间的 attempt 协调；其具体生命周期和失败恢复语义仍需继续逐分支审计。

### 2. Challenge / PoW header 处理

`nq1.c(cf5, requestBuilder, ...)` 的静态行为：

- 复制原始 `ng5` request builder，而不是直接修改传入 builder。
- 在 challenge context 对应分支中移除或设置 `X-Debug-Recaptcha-Challenge`。
- 有 challenge response 时写入 `X-DS-Challenge-Response`。
- 有 PoW response 时写入 `X-DS-PoW-Response`。
- 随后继续调用 `cf5.e` 执行宿主 client pipeline，并将响应交给 `nq1.b` 解析。

`jw3.g(ly1, Long)` 的 completion request 构造路径也会在 PoW response 非空时写入 `X-DS-PoW-Response`。因此 PoW header 既出现在通用 challenge plugin 的处理阶段，也出现在 completion request builder 的显式分支；不能把其中任一处孤立复制为完整认证实现。

### 3. PoW 算法调用证据

`df0.d(String, ms9, Continuation)` 按 `ms9.a` 中的算法标识分支。对 `DeepSeekHashV1`，Smali 通过反射方法引用指向：

`com.deepseek.crypto.PowCalculator.calculateDeepSeekHashV1Pow(String, String, long): long`

这证明 code289 APK 包含该算法入口及其调用适配，不代表模块可以绕过 challenge 流程自行生成可用响应。算法输入来源、challenge 生命周期、attempt 失败/重试、服务端校验和线程资源仍应按宿主完整链路验证。

### 4. 更新后的静态网络调用链

```text
ly1 request DTO
  -> zn3.o(ly1, null)
  -> jw3.g(ly1, ...)
       -> completion route + timeout + optional PoW header
       -> y60 cold Flow
  -> bi5.e(request lambda, serializer)
  -> rh5.d(builder copy)
  -> cf5.a(Ktor HttpClient pipeline)
       -> ChallengePlugin (nq1)
            -> challenge attempt (hq1 / pq1)
            -> challenge / PoW response headers
       -> transport execution
  -> ef5 response decoding
  -> y60.b(collector, Continuation)
  -> r3.c(delta event reducer)
```

Session bootstrap 的 `ch2.b -> bi5.e` 与 completion request 的 `jw3.g -> y60` 都进入同一类 `bi5/cf5` 请求执行体系。此处“同一网络体系”是 Smali 静态结构结论；尚未证明模块能安全取得并长期持有该 client 或 plugin 实例。

### 5. 对 Local API 实现的约束

1. 不自行拼接 `X-DS-Challenge-Response` / `X-DS-PoW-Response`，也不复制 challenge plugin 的局部逻辑来替代宿主。
2. 不绕过登录、Challenge、PoW、服务端业务码或官方网络拦截器；Native Adapter 必须复用宿主正常请求链路。
3. 优先继续确认 `zn3/jw3`、`bi5/cf5` 和 `hq1` 的实例来源、生命周期、并发边界与取消传播。
4. `y60` 仍是 cold Flow；不得在诊断观察器中额外 collect，也不得替换原始 Flow 对象。
5. challenge plugin 的静态注册和 header 写入不等于已证明所有 challenge 分支都能成功；HTTP 状态、业务码、challenge failure、PoW failure、serializer failure 必须分别保留。
6. 本结论只适用于当前 code289-cn APK。287 与 290 必须有各自 APK 的 descriptor 和调用链证据。

### 6. 可复现审计

`tools/audit_code289_session_fallback.py` 已扩展为同时检查 session fallback 与 challenge/client pipeline 的静态锚点。新增检查覆盖插件注册、请求阶段插件调用、challenge attempt 调用、challenge/PoW header 写入、completion request 的 PoW header 分支，以及 DeepSeekHashV1 算法方法引用。

该脚本只检查 Smali 静态结构；它不执行 challenge、不发送网络请求、不验证账号状态或设备运行时。Native Completion 协议入口保持开放；在独立运行时对象、Flow collect、DeltaCommand reducer、取消和真实 Completion 验收全部完成前，执行门面按请求返回 `engine_missing`（HTTP 503）。


## 2026-11-08：schema-7 请求到异步 Flow 的弱身份关联

本轮只增强诊断关联能力，不实现原生 Completion，也不改变宿主网络调用：

- zn3.o / jw3.g 的同步观察帧在返回 Flow 时，将 Flow 实例与原始 observation ID 建立关联。
- 使用 ReferenceQueue + identity-based weak reference，按对象身份而不是 equals() 关联；Flow 被释放后映射自动清理，不长期持有宿主 Flow。
- y60.b collector 观察点记录 Flow 的 identity hash 与原始 flowTraceId；continuation-enter/exit/throw 均保留同一 trace ID，使异步收集边界的正常返回与异常也可按请求回溯。
- y60.b 收到的 Continuation 实例按弱身份引用继承该 flowTraceId；r3.c 只在第二参数与该 Continuation 为同一对象时关联 DeltaCommand 事件。未匹配时明确输出 `unlinked`，不按线程、时间或事件顺序推断。
- Continuation 仅做身份关联，不再对其通用方法安装 callback Hook；旧设备日志显示该类方法会被 Session、Ktor 和 Challenge 等无关协程路径复用，扩大 Hook 面并产生大量噪声。
- y60.b 的 collector 对象也按身份继承同一 flowTraceId；collector 具体实现类的 enter/exit/throw 诊断事件输出该 ID。Continuation 仍不安装通用方法 Hook。
- 每个返回的 Flow 都会登记；`y60.b` 观察 Hook 仍只对已确认的具体 Flow 实现类安装一次。该 Hook 是 Flow 的 collector 边界观察，不是对 Continuation 实例安装方法 Hook。
- 每次显式重启诊断会话时递增 session generation 并清空旧 Flow 关联；Flow 和 Continuation 登记都在映射锁内复核 session generation，防止会话重置与登记并发时旧请求重新写入；observation ID 保持进程内单调递增。
- 不读取 Request 字段值、Prompt、消息内容、Token、Cookie、Authorization、PoW 或 Challenge 响应。
- 报告 schema 更新为 7；新增 source-only 回归脚本 module/test-open-native-flow-correlation.sh 并接入主 CI。

**证据边界：** 该改动只提供请求与 Flow 实例的诊断关联。它不证明 Flow 一定被 collect、不证明 r3.c 的 DeltaCommand reducer 已正确应用，也不证明 response assembly、取消传播或真实设备 Completion 已完成。必须在 code289 受控设备上启用诊断后，看到同一 flowTraceId 对应的 continuation-enter 与 delta-dispatch 事件，才能确认运行时链路关联成功。


### code289 reducer 的终止语义边界

基于当前 code289-cn 静态调用图，`r3.c` 明确分派 `ready`、`delta`、`hint`、`title`、`update_session` 和 `close`；当前证据没有建立独立的 `finish` 事件分支。因此：

- 不得把 `close` 直接等同于 OpenAI `finish_reason`，也不得从其他版本移植未在 code289 证实的事件名。
- `close` 是否代表 reducer 收尾、服务端正常结束或某种会话状态变更，必须结合完整 `r3.c` 状态转换和上游 Flow 完成信号判断。
- 最终响应模型 `gy` 与 `xr.B()` 的可见文本拼接路径已在静态资料中定位，但尚未证明 answer/reasoning 的完整分离及 Tool/Search/Find/Open fragment 的正确映射。
- Gateway 的 `finish_reason` 与 `[DONE]` 只能在原生 reducer/Flow 已确认正常完成、结果模型构造成功后发送；不能以 `close` 单个事件或 HTTP 200 代替完成证明。

在获得真机事件序列前，Native Adapter 保持 `engine_missing`（HTTP 503）fail-closed，不构造虚假的 CompletionResult。


## 2026-11-08：与 apps/code289 静态证据仓库交叉核对

本节以 `qingjian0/apps` 的 `work/deepseek-272-static-audit` 分支为独立证据源，不将 2.7.3 / 2.7.5 的混淆类名直接套用到 code289。

**证据固定点**

- apps commit：`3b7c383edb4092bb18257a85afe50cc8c652c590`
- 静态审计 workflow：`42343582039`（success）
- artifact：`deepseek-272-static-evidence`，artifact ID `13562119133`
- artifact ZIP SHA-256：`173c69bcae1a0e428d54cfd4fe684b859b6286a88a84b4224d3ec7425e105dce`
- APK：DeepSeek 2.7.2 / versionCode 289 / `code289-cn`，SHA-256 `3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff`
- 证据包含完整 `all-smali.zip`、候选调用点、类描述符、方法 inventory 和静态报告；本节结论来自该 artifact 的 Smali 复核，而不只是 README 摘要。

### 1. code289 响应链已可精确到的层次

```text
HTTP response handler g0
  -> SSE ByteReadChannel parser ts1
  -> sha(eventName, data)
  -> cv1(sha)
  -> r3.c(dv1, Continuation)
  -> DeltaCommand / named-event reducer
  -> doc.W(st3, gv1)
  -> ll23.y(...) patch application
  -> zca.response -> gy
  -> gy.fragments / Ley1
```

apps 的 `docs/deepseek-272-native-callgraph.md` 与 artifact Smali 对应，明确区分了 SSE 行解析、事件封装和业务 reducer。尤其不能把 `y60.b` 的 collector 边界当成底层 SSE parser。

### 2. Delta 不是文本增量，而是结构化 JSON Patch

- `r3.c` 对 `delta` 的 data 使用 `st3` serializer 解码。
- `st3` 的字段为 `path`、`operation`、`value`，其中 `value` 是多态 `c26`。
- `c26` 的 Smali 类型层级包含 primitive、map、list 形态；不能把 value 一律转成 String。
- `doc.W` 将 path / operation / value 归一化为 patch 对象，再调用 `ll23.y` 更新 reducer 状态树。
- 当前静态报告列出的操作类别为 `SET`、`APPEND`、`BATCH`。Native Adapter 必须按 patch 语义更新状态，不能把 SSE data 直接当作 OpenAI delta。

### 3. Response 文本投影存在重要限制

- 根状态 `zca.a` 指向 `gy` response model。
- `gy.m(..., "fragments")` 将 fragment patch 解码并更新 `Ley1` fragment 集合。
- `xr.B()` 遍历 fragment 列表，但只对 `nk0` 类型调用 `f()` 并拼接；其他 fragment 被投影为空字符串。

因此，`xr.B()` 只能作为宿主可见文本的一个投影，**不能作为完整 Completion 内容模型**。Reasoning、Tool、Search、Find、Open、ReadLink、Tip 等非普通文本 fragment 必须保留独立结构，不能从 `B()` 的结果反推或补造。

### 4. code289 reducer 事件与结束语义

当前 `r3.c` 的已确认事件分支：

| Event | 行为 |
|---|---|
| `ready` | 解码 ready data，并初始化/绑定 response 状态 |
| `delta` | 解码 `st3` 并应用状态树 patch |
| `hint` | 解码 hint 数据并更新 reducer |
| `title` | 解码标题事件 |
| `update_session` | 解码 Session 更新事件 |
| `close` | 向 reducer 投递 close 事件 |

同一方法中还存在 `parse_error`、`InvalidEvent`、`InvalidDelta`、`UnexpectedMessage` 等错误路径。

**没有在 code289 的 `r3.c` 中确认独立 `finish` 事件分支。** `close` 会被转换为 reducer 内部 close action；它本身不足以证明上游 Flow 正常完成，也不能直接映射为 OpenAI `finish_reason` 或触发 `[DONE]`。终止必须由 reducer 状态和 Flow 的正常完成信号共同确认；异常、取消和客户端断开必须走不同出口。

### 5. 对当前实现的修正方向

1. 不再把“从 `gy.B()` 取完整回答”作为 Adapter 方案；应从 `gy` 的 fragment snapshot 构造有类型的中间模型。
2. 将 patch reducer 独立为可单测组件：支持 path、SET / APPEND / BATCH、primitive / object / array value、无效 path 和 malformed patch fail-closed。
3. 为 Answer、Think/Reasoning、ToolOpen、ToolFind、Search、ReadLink、Tip 等 fragment 建立显式类型映射；未识别类型必须保留为 unsupported evidence，不能静默拼成文本。
4. 将 `close`、Flow complete、Flow error、Flow cancellation 分开建模；只有正常终止且结果快照完整时才允许输出 OpenAI terminal chunk。
5. 运行时仍需复用宿主的 `zn3/jw3/bi5/cf5` 与 ChallengePlugin 管线；不自行拼接 Challenge / PoW header，不额外 collect 诊断用 Flow。

### 6. 尚未被静态 artifact 证明的事项

- 本模块通过反射启动 code289 请求并安全 collect 冷 Flow。
- reducer 的全部 fragment 类型与每个 JSON discriminator 的一一映射。
- Reasoning 与 Answer 在实际流式时序中的边界。
- Flow 正常完成、异常、取消、resume 和并发 Session 的设备行为。
- 从宿主 reducer snapshot 构造稳定 OpenAI delta 的端到端正确性。

本节把 apps 仓库的静态调用链和 Smali artifact 转化为当前 Adapter 的实现约束；不代表上述运行时能力已实现或通过设备验证。


## 2026-11-08 — normalized JSON Patch reducer boundary

Added `NativeJsonPatchReducer` as an isolated, pure Java reducer for **normalized RFC 6902** operations. It supports add/remove/replace/move/copy/test, JSON Pointer escaping, object members, array insertion/removal, and atomic batch failure (the caller's source tree is not mutated and a failed batch returns the original tree).

Important evidence boundary:

- The code289 static investigation establishes a DeltaCommand carrying path / operation / value and identifies SET / APPEND / BATCH behavior in the host path. It does **not** yet establish a complete, lossless mapping from the host's obfuscated operation representation to RFC 6902 operation names.
- Therefore this reducer is not wired into `NativeApiPatchDecoder`, `OpenLocalApiGateway`, or the native completion path. The existing Open decoder remains fail-closed.
- No host Fragment type names, Answer/Think/Tool schemas, event terminators, or completion lifecycle transitions are inferred from this reducer.
- The executable regression script `module/test-native-json-patch-reducer.sh` is added to the CI Regression job. It compiles against the Android platform `org.json` implementation and tests nested object/array edits, escaped JSON Pointer tokens, copy/move/test, immutability, and atomic failure.

This is an independently testable state-tree primitive, not a completed code289 wire decoder or Native Adapter. Integration is gated on an evidence-backed translation of every host operation, path convention, batch ordering rule, and value representation, followed by code289 device verification.




## code289 原生调用链

完整的 code289-cn (Open 2.7.2) Request → Transport → Challenge/PoW → HTTP/SSE → DeltaCommand → Response 状态树调用链、证据范围、接入顺序和未完成验收项，见 [CODE289_NATIVE_CALLCHAIN.md](CODE289_NATIVE_CALLCHAIN.md)。该文档仅记录静态证据，不代表 Native Adapter 已接通。

## 2026-11-08：区分 suspend 调用返回与异步完成

源码审计发现，`y60.b(fl4, Continuation)` 与 `r3.c(dv1, Continuation)` 都是 Kotlin suspend 形态：方法体可以先返回 `COROUTINE_SUSPENDED`，后续工作再由协程恢复继续。因此诊断日志中的同步调用返回不能命名或解释为 reducer/Flow 已完成。

- 将 `continuation-return`、`delta-dispatch-return`、`callback-return` 统一改名为 `*-return`，准确表示被观察方法本次同步调用已经返回。
- `*-return` 不代表 Flow collect 正常结束、Continuation 已成功恢复、Delta patch 已应用、response snapshot 已完整或客户端可安全发送终止 chunk。
- `*-throw` 仍表示本次同步调用直接抛出异常；若 suspend 方法先返回挂起标记，后续异步失败不一定会在该调用边界表现为同步 throw。
- 真正的完成语义仍须分别观察 suspend Continuation 恢复结果、Flow 完成/异常/取消，以及 reducer 状态快照；当前没有引入通用 Continuation Hook，也没有新增宿主 Hook 目标。

该改动只修正诊断事件命名，不改变宿主参数、返回值、调用次数、Flow 身份或执行路径。
