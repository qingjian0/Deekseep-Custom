# DeepSeek Native Completion Adapter 调查与实施边界

> 适用分支：`work/roi-agent-core-kotlin`  
> 当前源码快照：`c34d8bac95238e99f0c88b19209a5151b9098e3b`  
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
        +-- OpenNativeCompletionProbe.inspectHost(hostClassLoader)  [Open only, read-only]
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

- 在 Open 版启动路径中调用 `inspectHost(hostClassLoader)`；
- 通过 `HostCompat.load(loader, "ew0")` 获取当前版本映射后的 Request 类型；
- 输出 Request 类名、接口、构造器签名和非静态字段声明；
- 扫描 DEX 类名，寻找形状符合“第一个参数可接收 Request、第二个参数为 Long、返回非 primitive / 非 void”的候选方法；
- 进一步按字段类型寻找可能持有候选 Transport 的管理类；
- 仅输出声明签名，不实例化宿主类、不调用候选方法、不读取对象字段值。
- 报告增加 schema 版本、符合扫描条件的类名数量、成功加载数量、类加载失败数量和扫描耗时，便于比较不同宿主版本的覆盖范围。

限制：

1. 候选筛选条件只是启发式结构特征，可能包含误报；候选数量不等于真实 Transport 数量。
2. 返回类型可能是 Flow、泛型包装或其他对象；签名本身不证明其语义。
3. `Long` 参数的语义尚未被确认，不能直接称为 timeout、deadline 或 PoW 参数。
4. 管理类字段的类型关联不证明其生命周期、初始化顺序或线程约束。
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
