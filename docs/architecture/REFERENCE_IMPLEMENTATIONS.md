# DeepSeek Android Hook 参考实现源码审计

> 本文记录对公开仓库的定向源码核验结果，用于指导 `qingjian0/Deekseep-Custom`。仓库说明、源码事实、设备验证是不同证据等级；没有明确设备验证记录的内容不得标记为“真机已验证”。

## 1. 审计范围与结论

| 参考仓库 | 本次检查的源码 | 可借鉴点 | 不能直接推导的结论 |
|---|---|---|---|
| [Baidaofu/deekseep-onlyapi](https://github.com/Baidaofu/deekseep-onlyapi) | `src/com/dsmod/probe/z1.java`、`ci/engine/LocalApiEngine.java`、`tests/com/dsmod/probe/LocalApiTest.java`、`run-tests.sh`、README | HTTP 协议层与原生执行引擎分层；真实 Socket 的 JVM 端到端测试；OpenAI/Responses/Anthropic 路由 | README 声明的设备验证对象是 DeepSeek 2.4.1 / versionCode 257，不能视为 2.7.5/code295 兼容证据 |
| [qingjian0/LSP-Deekseep](https://github.com/qingjian0/LSP-Deekseep) | `ProbeEntry.java`、`SafeTrace.java`、`HostProfile.java`、`HOOK_COVERAGE.md` | 在 `Application.attach` 后取得宿主 ClassLoader；记录完整方法签名、参数类型、返回类型、耗时和异常类型；日志脱敏 | 这是 0.1.0-dev 的候选方法观测探针，不是完整 Flow/SSE 恢复器，也不是通用 Hook 自动解析器 |
| [hxreborn/Deekseep](https://github.com/hxreborn/Deekseep) | README、modern/legacy/test 模块目录与 Manifest | libxposed API 102、传统 Xposed API 82+、诊断/测试构建分开组织 | README 指向的主维护仓库与当前 Fork 名称不同；功能与版本支持必须按实际 commit 核验 |
| [lllucccian/Deekseep](https://github.com/lllucccian/Deekseep) | README_CN、模块目录 | 原始 Deekseep 项目结构、模块加载与发行构建基线 | 当前公开 README 的版本适配描述集中在 2.4.1、2.3.x、2.2.x；不能据此宣称适配 2.7.x |
| [yanyutiantian/deepseek-dekk](https://github.com/yanyutiantian/deepseek-dekk) | README、`DekkEntry.java`、`HotHooks.java` 等 | 模块功能与远端 DEX 更新壳分层；适合研究可选扩展分发 | 远端 DEX/服务端部署引入供应链、签名、回滚和密钥管理风险，不应成为 Local API 核心依赖 |

## 2. OnlyAPI：网关与执行引擎分离

### 已核验事实

- `z1.java` 承担 HTTP 路由、协议解析与响应映射；`LocalApiEngine.java` 独立承接执行层。README 说明该实现位于 DeepSeek 宿主进程内。
- README 列出 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages，以及 JSON/SSE 输出。
- `LocalApiTest.java` 启动真实 HTTP 服务，在本机 Socket 上测试路由、鉴权、非流式响应、SSE、Responses、Anthropic、密钥轮换和错误路由；`run-tests.sh` 可在桌面 JVM 编译执行，无需真机。
- README 声明其设备验证环境为 DeepSeek 2.4.1（versionCode 257），因此这只能证明该版本上的历史验证，不证明新版本兼容。
- OpenAI Chat 参数解析使用 `JSONObject.optInt("max_tokens", optInt("max_completion_tokens", 0))`。这会利用 org.json 的整数转换行为，并在未提供时传入 0；该代码没有复刻本项目 1–8192 的显式上限校验。它先取 `max_tokens`，与本项目新字段优先的策略不同。

### 对 Deekseep-Custom 的采用方式

1. 保留 `OpenLocalApiGateway` 作为纯 HTTP/协议层，通过稳定的 `z2.Backend` 合约调用执行层。
2. 将 Native Completion Adapter 的生命周期、请求构造、Flow 收集、Delta reducer 和错误映射留在独立执行层；HTTP listener running 不代表 native backend ready。
3. 将 OnlyAPI 的真实 Socket JVM 测试方法作为测试目标：不仅检查源码字符串，还要验证真实 HTTP 状态码、响应头、响应体、断连取消与 SSE 结束标记。
4. 参数兼容只吸收“兼容整数数字字符串”的做法，不照搬无上限转换。当前本项目保留 1–8192 的合同：缺失/null 使用默认值 4096；整数与整数字符串接受；小数、零、负数、非数字和超过 8192 明确返回 400；禁止静默 clamp。若调用方同时提供两个字段，优先使用 `max_completion_tokens`，并在测试中固定该优先级。

## 3. LSP-Deekseep：只读运行时探测

### 已核验事实

- `ProbeEntry` 只接受 `com.deepseek.chat`，在 `Application.attach(Context)` 后使用宿主 ClassLoader 安装候选方法观测。
- 候选方法通过类名和方法名发现，并对匹配名称的 declared overload 安装观测 Hook；事件包括候选类是否存在、匹配 overload 数量、方法签名、参数类型、返回类型、耗时和异常类型。
- `SafeTrace` 是独立脱敏日志组件。探针 README 明确说明不声称已完整恢复 SSE、Flow 或消息模型，也不包含绕过认证、PoW、TLS 或服务端访问控制的逻辑。
- 由于按方法名匹配会覆盖多个 overload，这适合初期观测，不应直接升级成生产功能 Hook 的注册策略。

### 对 Deekseep-Custom 的采用方式

1. 保持 Probe 与 Feature Hook 分离：Probe 只收集元数据；只有经过人工审查、版本证据和回归验证的 HookSpec 才能进入功能注册表。
2. 记录宿主 package、versionName、versionCode、渠道、ClassLoader 身份、方法完整签名、线程、调用深度、耗时和异常类型。
3. 不记录 prompt、API key、Authorization、Cookie、请求体、响应正文或用户消息。
4. 对 code295 的静态 marker 只能标记为候选证据。运行时进入某个方法只能证明该方法被调用；必须进一步证明参数语义、Flow/Continuation 关联和结果 Delta 结构，才可启用 Native Adapter。
5. 不把 LSP-Deekseep 当前候选类名/方法名直接复制到 2.7.5；它的候选列表属于探针自身测试版本。

## 4. Hook 框架与发行构建

hxreborn README 展示了 API 102、传统 Xposed API 82+、测试构建和最小加载探针的分层方式。对本项目的价值是把“框架能加载模块”与“宿主功能 Hook 可用”分开验收：

- Loader smoke test：只验证模块被框架加载、目标包过滤、ClassLoader attach。
- Host compatibility test：验证版本识别、类/方法签名和 Hook 安装结果。
- Feature test：验证实际功能副作用与回滚行为。
- Native completion test：验证完整请求、Flow、Delta、SSE/JSON 和取消链。

现代与传统框架构建应独立产物、独立 Manifest/入口与 CI job；不要因编译成功就把另一框架的入口或能力视为已验证。

## 5. 远端 DEX 更新的取舍

deepseek-dekk 的热更新壳展示了远端拉取 DEX 并在宿主进程加载的组织方式。它可作为未来插件分发的研究材料，但不是本地 API 打通的前置条件。若未来采用，至少需要：
- 发布物签名与签名链固定；
- 版本/宿主兼容声明和能力白名单；
- 完整性校验、回滚和失效开关；
- 网络来源、密钥和更新权限隔离；
- 不允许远端配置绕过本地 HookSpec 审核或自动扩大 Hook 范围。

## 6. 本项目当前故障的定位原则

日志中 `http-authenticated` 表示请求已经到达网关并通过该处认证；`invalid_max_tokens` 表示请求在参数校验处被拒绝，不能把它误判成 Socket listener 未启动。反过来，`nw3.e(yu1, Long)` 与 `b70.b(...)` 的运行时观测只证明调用链进入了该方法/Continuation，不能单独证明原生 Completion 成功。

必须分别报告以下状态：

1. Foreground keeper/service running；
2. DeepSeek host heartbeat acknowledged；
3. HTTP loopback socket bound/listening；
4. LAN socket bound（仅用户明确开启时）；
5. Native backend ready；
6. Request dispatched；
7. Native Flow entered / completed；
8. Client response completed or cancelled。

状态面板不得用单一“正在启动监听”覆盖上述状态。HTTP 错误、心跳 ACK 缺失和 Native Flow 异常应分别显示。

### 2026-11-09：code295 运行时日志补充

本次检查 deekseep_vision.log（dev 分支，schema 7）后，证据等级需要进一步细分：

- 04:58:46.211：nw3.e(yu1, Long)->b70 以根调用 id=1 实际进入，运行时请求对象为 qy1，参数类型符合 yu1 接口；日志标记 sameRequestObjectAsRoot=true。
- 04:58:46.225：同一根调用正常返回 b70。
- 04:58:46.229：返回对象对应的 b70.b(jl4, dc3) Continuation 以 flowTraceId=1 进入。
- 04:58:46.266：该关联 Continuation 正常退出，结果类型为 hd3。
- 04:58:47.384：同一 flowTraceId=1 再次进入并于 04:58:47.388 正常退出，结果类型为 ghb。

这证明 code295 的 nw3.e 请求路由和至少一条与根请求关联的 b70 Continuation 在该次宿主交互中真实运行；不再只是静态候选。但它仍然**不证明 Local API 已经调用这条链，也不证明已经解码出最终文本 Delta 或完成 HTTP 响应**。

同一日志中的 continuation-throw exception=ys4 记录对应 flowTraceId=unlinked 的其他 Flow/Continuation。根调用关联的 flowTraceId=1 有正常 exit 记录，因此不能把这些未关联的 ys4 异常归因于该根请求。后续诊断已补充异常栈的类名/方法名元数据，不记录异常消息、请求参数或正文，以便区分不同协程路径。

另一个明确的源码边界：Open 版 z14.payloadClass(...) 当前是返回 null 的 stub；HookAccountLoginSecurity.executeLocalApiCompletion(...) 仍通过它查找 ds.dsh.DeepSeek.z18 执行引擎。因此，即使修正 max_tokens 后请求通过 HTTP 参数校验，Open 版仍不能据此认为 Native Adapter 已就绪；当前执行入口会返回 engine_missing（HTTP 503）。Baidaofu/deekseep-onlyapi 的 LocalApiEngine 是独立重实现，但其 README 验证目标为 2.4.1/code257，不能直接把其 ew0 请求构造、PoW 或 transport 符号移植到 code295。

### 当前修复与待验收边界

- max_tokens / max_completion_tokens：缺失或 null 使用 4096；整数型数值和整数字符串可接受；非整数、零、负数、非数字以及超过 8192 均返回 400，不静默截断。
- UI 的“正在启动监听”反馈现在依据真实 runtime status 更新；比较时使用 UiLanguage.dynamic(...) 生成当前语言的提示文本，避免英文界面永远匹配不到中文字面量。
- code295 观察器仍然是只读、pass-through 诊断；观察到 nw3.e 不会自动启用 HostRemap、Native Adapter 或 Feature Hook。
- 当前代码改动必须等待 dev CI 的 regression、Android build/lint 和 APK job 完成；CI 通过也不等同于真机 API 完成验证。

## 7. 后续验证清单

- [ ] 为 token parser 增加可执行测试：缺失、null、整数、整数字符串、小数、空字符串、布尔、零、负数、8192、8193、字段同时出现。
- [ ] 增加本机真实 Socket JVM 测试，覆盖鉴权、400/401/503、慢客户端、请求断连、SSE `[DONE]` 和 executor 饱和。
- [ ] code295 只读采集完整调用轨迹，确认请求对象字段、Continuation 关联、Flow 事件和最终 Delta；不凭单个方法命中激活功能 Hook。
- [ ] 把 service、heartbeat、listener、backend 和 request 状态分别展示。
- [ ] 将每个支持版本的验证结果与精确 versionCode、渠道、commit SHA、设备测试记录绑定。
