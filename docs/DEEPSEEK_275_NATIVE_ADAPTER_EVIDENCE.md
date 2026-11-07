# DeepSeek 2.7.5 Native Adapter 证据与实施计划

> 适用工程分支：`work/local-api-protocol-port`
> APK 分析来源：`qingjian0/apps` 的 `work/deepseek-275-apk-audit`
> 状态：请求构造与官方 PoW 路径已有静态代码证据；响应消费、流式生命周期和真机行为尚未闭环。
> 本文不授权 Hook，也不表示 Native Adapter 已实现。

## 1. 当前交付基线

- Local API Gateway 的 HTTP、认证、请求校验、OpenAI Chat Completions JSON/SSE 协议层已通过独立 CI。
- 已验证提交：`420f6cb4b72711f1572d4db0ae032f1afdae572f`。
- 对应 Run：`42334418099`；Android Open build/lint、APK 结构/对齐/签名、ROI fast audit、Regression tests 均成功。
- 当前 Open 原生 Completion Adapter 仍未接通；真实 `POST /v1/chat/completions` 继续返回 `503 native_adapter_unavailable`。
- 2.7.5 APK 分析任务：`qingjian0/apps` Run `42335469685`，输入 APK SHA-256：`ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70`。

## 2. 2.7.5 请求侧静态证据

### 2.1 Challenge 请求

- `wt1` 的 Kotlin serialization descriptor 对应 `ChatChallengeRequest`，字段为 `target_path`。
- `nw3.c(wt1)` 进入 `tz0` 的 Challenge 请求协程分支。
- 请求为 `POST /api/v0/chat/create_pow_challenge`，body 使用 `wt1`。
- 响应模型经 `zt1` 包装 `ts9`；`rs9` 的 serializer descriptor 对应 `ServerChatChallenge`。

### 2.2 官方 PoW 计算与序列化

静态调用关系：

```text
ef0.b(c95)
  -> pb0 分支 5
  -> wt1(target_path)
  -> nw3.c(wt1)
  -> /api/v0/chat/create_pow_challenge
  -> ServerChatChallenge(ts9)
  -> ef0.d(target_path, ts9)
  -> PowCalculator.calculateDeepSeekHashV1Pow(...)
  -> hf0 (AuthedPowSolution)
  -> ff0 serializer
  -> ef0.c(c95) 输出 JSON 字符串
  -> X-DS-PoW-Response
```

- `ef0` 按目标路径管理 PoW 结果缓存与过期。
- `of2` 是受 `pow_prefetch` / `pow_prefetch_count` 控制的另一条预取路径。
- `qy1.k` 是额外 PoW 字符串，不是 Chat Completion JSON body 字段。
- `oq1.c(...)` 设置 `X-DS-Challenge-Response` 与 `X-DS-PoW-Response`，并继续 Ktor client pipeline。
- 不允许自行拼接 hash、伪造 `AuthedPowSolution` 或绕过官方 Challenge。

### 2.3 Completion 请求

- `qy1` 的 serializer descriptor 对应 `ChatFullCompletionRequest`。
- `nw3.e(yu1, Long)` 按 DTO 类型选择路由、serializer 和超时配置；`qy1` 对应 `POST /api/v0/chat/completion`。
- `nw3.e` 将 DTO 设为 POST body，若 PoW 字符串存在则设置 `X-DS-PoW-Response`，再委托给 `l5` 协程执行路径。
- DTO 字段：`chat_session_id`、`parent_message_id`、`prompt`、`ref_file_ids`、`thinking_enabled`、`search_enabled`、`audio_id`、`preempt`、`model_type`、`action`、`shumei_device_id`。

相关路由：

| DTO | 路径 |
|---|---|
| `qy1` | `/api/v0/chat/completion` |
| `rf2` | `/api/v0/chat/regenerate` |
| `jx1` | `/api/v0/chat/continue` |
| `yf2` | `/api/v0/chat/resume_stream` |
| `rx1` | `/api/v0/chat/edit_message` |

辅助请求还包括 `GET /api/v0/chat/history_messages` 与 `POST /api/v0/chat/stop_stream`。

## 3. 响应侧阻塞

- `l5.z(Object)` 是请求执行协程的关键方法，但 JADX 在该方法发生 StackOverflowError，未能恢复方法体。
- 当前静态证据不足以确认 HttpResponse 读取、响应 DTO、消息事件类型、流式事件解码、Continuation 实际实现、取消传播及错误映射。
- 不能仅凭 `b70` 包装器或 Ktor 类型推断它是 Flow、SSE 或特定事件流。
- 2.7.5 必须使用自己的 APK 符号与版本指纹；禁止复用 2.7.2/code289 的 `ly1/wu1/jw3/zn3/y60` 混淆符号。

## 4. 对现有诊断实现的影响

当前 ROI 的 `OpenNativeCompletionProbe` 有明确的 code289 专用门禁，并观察 `jw3.g`、`zn3.o`、`y60`。该观察器不能直接用于 2.7.5。

下一项源码工作应是新增**独立的 2.7.5 只读诊断 profile**，而不是修改或放宽 code289 门禁：

1. 先以 2.7.5 APK 的 package/version/channel 元数据建立精确宿主指纹；未确认 versionCode 前不得写猜测值。
2. 将静态确认的候选符号作为只读观察目标：`nw3.e`、`nw3.c`、`ef0` 相关方法、`PowCalculator`、`oq1.c`、`l5.z`。
3. 探针只记录方法描述符、参数/返回类型、线程、调用栈、耗时、异常类型、对象 identity 关联；不读取参数/字段值。
4. 不主动调用候选方法，不主动订阅 Flow/Channel，不二次消费响应，不修改原始参数、返回值、异常或 Continuation。
5. 对 JNI PoW 只记录包装方法进入/退出及参数类型、返回类型和耗时，不记录输入字符串、Challenge 内容或 PoW 原始值。
6. 诊断独立开关默认关闭；关闭后已安装 observer 必须立即透明透传。
7. 采集日志应显式提示敏感字段不采集，并限制到应用私有目录；诊断开销与日志增长必须有清晰边界。

## 5. 2.7.5 运行时证据采集验收

使用受控测试设备和非敏感测试对话，分别验证：

- Request DTO 构造完成后，哪个真实调用者将同一 Request 实例交给 `nw3.e`。
- `nw3.e` 的 `Long` 参数语义只能由调用点与运行时证据确定，不能预先命名为 timeout。
- Challenge、PoW 计算、序列化和 header 插件是否处于同一请求会话，以及其先后关系。
- `l5.z` 的真实执行边界、Continuation 实现类、resumeWith/回调入口及跨线程关系。
- 响应体读取者、解码器、事件顺序、终止事件、异常路径。
- 用户取消、客户端断连、deadline 是否能停止上游工作，且不破坏宿主原始调用。
- `stop_stream` 与本地取消的对应关系是否一致。

所有证据按 APK SHA、宿主渠道、versionCode、设备系统、LSPosed 版本和诊断 schema 归档。静态调用关系、CI 通过和运行时观测必须分开标注。

## 6. Adapter 实施门槛

只有在同一版本证据链闭合后，才按窄边界实现：

- Request factory：仅从已验证的宿主会话上下文构造官方 DTO。
- Challenge/PoW bridge：复用官方 Challenge、缓存、计算与 serializer。
- Transport bridge：调用已验证的官方入口。
- Event decoder：按真实响应模型将事件转换为稳定 Delta。
- Cancellation bridge：验证并桥接 Continuation / coroutine Job 生命周期。
- Session bridge：明确新建会话、已有会话、父消息 ID 和账号切换隔离。
- Error mapper：区分网络错误、Challenge 错误、上游业务错误、取消与超时。

每个版本独立 capability gate。不得以 2.7.5 的证据授权 2.7.2，也不得把 code289 的旧观察器当作通用适配器。

## 7. 保持不变的安全状态

在响应消费、取消和 Session 语义得到验证并通过真机验收前：

- Gateway 继续返回 `503 native_adapter_unavailable`。
- 不删除 readiness 条件，不伪造 `liveR92/liveQ71` 或其他宿主对象。
- 不调用 Closed-only payload，不把仓库普通 `z18` 类误认为执行引擎。
- 不采集 Prompt、Completion、Token、Cookie、Authorization、PoW/Challenge 原始值。
- 不声称 Local API 已可真实生成回复。

## 8. 可追溯来源

- APK 分析归档：`qingjian0/apps` / `work/deepseek-275-apk-audit` / README 第 8 节。
- APK audit workflow run：`42335469685`。
- Local API 协议层绿色 CI：`42334418099`，commit `420f6cb4b72711f1572d4db0ae032f1afdae572f`。
