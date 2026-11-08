# Code289 真机验收与旧 Hook 迁移执行单

> 基线分支：`dev`  
> 本执行分支：`work/roi-code289-device-acceptance-and-migration`  
> 基线提交：`bdb7edd24af3565aff258ccd248afd6b147df1cd`  
> 首轮设备目标：官方 DeepSeek 国内渠道，HostCompat adapter code 289（2.7.2）；LSPosed 已安装并运行  
> 验收级别：L5 真机；当前尚未执行，本文是执行规程，不是通过报告。

## 1. 设备与构建身份

开始前记录以下信息，不能用内部映射编号替代 Android PackageManager 的真实版本信息：

- 设备型号、Android 版本、API level、ABI。
- LSPosed 版本、作用域启用状态、模块版本。
- DeepSeek 包名必须为 `com.deepseek.chat`。
- DeepSeek 的真实 `versionName`、`versionCode`（从 PackageManager 读取）。
- ROI 内部 HostCompat adapter code 应识别为 289；该值是项目映射标识，不自动等于 APK manifest 的真实 versionCode。
- 已安装 DeepSeek base APK 与 split APK 集合、签名证书摘要、模块 APK SHA-256。
- CI Run、artifact 名称、artifact 对应 commit SHA。测试 APK 必须来自 `bdb7edd24af3565aff258ccd248afd6b147df1cd` 的 `dev` CI 构建，不混用旧产物。

记录表：

| 字段 | 实测值 |
|---|---|
| 设备型号 / Android | 待填写 |
| LSPosed 版本 | 待填写 |
| DeepSeek 包名 | `com.deepseek.chat` |
| DeepSeek versionName / versionCode | 待填写 |
| HostCompat adapter code | 289（目标） |
| 模块版本 / APK SHA-256 | 待填写 |
| CI Run / commit | Run 42452649937 / `bdb7edd` |
| 测试日期 | 待填写 |

## 2. 安装前检查

1. 从 [CI Run 42452649937](https://github.com/qingjian0/Deekseep-Custom/actions/runs/42452649937) 下载 `yunseek-open-release-apk` artifact。
2. 解压 artifact，确认 APK 文件名、包名、版本和签名信息；记录 SHA-256。
3. 注意：CI 测试签名仅用于构建验证，不等同于正式发布签名。若设备上已有同包名但不同签名的安装包，先确认数据备份与替换影响，不要直接覆盖。
4. 安装模块 APK；在 LSPosed 中启用模块，并只将作用域授予 `com.deepseek.chat`。
5. 确认模块已激活、作用域生效；强制停止 DeepSeek 后重新启动。
6. 若安装失败、签名冲突或 LSPosed 不识别模块，先记录原始错误，不清除用户数据、不更换宿主 APK。

## 3. L5 验收矩阵

每项记录 PASS / FAIL / BLOCKED，并附时间戳与脱敏日志片段。

| ID | 验收项 | 操作 | 通过条件 |
|---|---|---|---|
| D01 | 模块加载 | 冷启动 DeepSeek | 目标进程加载模块入口；无模块初始化崩溃 |
| D02 | 宿主准入 | 检查 HostCompat 诊断 | 包名、渠道分类、adapter code 与预期一致；未知/不匹配版本不安装功能 Hook |
| D03 | 基础设置 Hook | 打开模块所接入的 DeepSeek 设置入口 | 对应 UI 正常显示；不重复注入、不崩溃 |
| D04 | Request 构造观察 | 正常发送一条普通文本消息 | 只记录受限结构元数据；不记录 Prompt、消息正文、Token 或凭据 |
| D05 | 聊天原生流程 | 连续发送两条普通消息 | 宿主原生发送、接收和显示不受影响 |
| D06 | 历史清理路径 | 在可恢复测试数据下执行历史清理入口 | 目标功能行为正确；无越权删除或崩溃 |
| D07 | Hook 重复安装 | 返回前台、切换页面、重新进入设置 | 同一目标不重复安装；原方法调用次数不变 |
| D08 | Local API 服务 | 显式开启本机服务，访问 `GET /v1/models` | 服务可启停；本机路由正常返回模型目录 |
| D09 | API 认证 | 不带 Token / 错误 Token 请求 | 请求被拒绝；日志不打印 Token |
| D10 | LAN 默认安全 | 不启用 LAN 时从局域网访问 | 不可连接；服务不绑定 `0.0.0.0` |
| D11 | 原生 Completion 边界 | 请求一个 Completion | 当前预期为明确 `503 engine_missing`；不得伪造模型输出 |
| D12 | 稳定性 | 前后台切换、锁屏/解锁、连续使用 10 分钟 | 无持续崩溃、ANR、Hook 重复或明显资源泄漏 |

注意：D11 的 503 是当前 Open Native Completion Adapter 未完成时的 fail-closed 预期，不代表原生模型调用已验收通过。D04 的结构观察也不等于 Transport / Flow / Response 端到端链路已经证明。

## 4. 建议日志采集

优先使用 LSPosed 管理器提供的模块日志导出。必要时使用 ADB：

```bash
adb logcat -c
adb shell am force-stop com.deepseek.chat
adb shell monkey -p com.deepseek.chat 1
adb logcat -d -v threadtime > code289-device-log.txt
```

提交前只保留 ROI / LSPosed 相关诊断行，并脱敏设备标识、账号信息、网络地址及其他个人数据。严禁上传聊天正文、Prompt、Completion、Bearer Token、Cookie、Authorization、PoW/Challenge 内容或账号凭据。若日志中出现上述内容，先删除相关行再提交。

## 5. 当前旧 Hook 迁移基线

`module/src/ds/dsh/DeepSeek/JavaHookInventory.java` 在基线提交中记录：

- `SOURCE_HOOK_COUNT = 170`：Main.java 中当前被静态盘点的 `hook(...).intercept(...)` 注册点。
- 这 170 个是源码注册点数量，不等同于 170 个独立业务功能，也不等同于全部 Hook API 调用数量。
- 既有功能仍由 Legacy feature pipeline 注册；Universal Hook Runtime 已有运行时/生命周期实现，但当前没有经审核的生产 feature adapter 注册调用点。
- 因此生产功能迁移计数目前按 **0 个已授权迁移组** 统计。不能把 Universal Hook Runtime 自身 CI 通过写成 170 个 Hook 已迁移。
- 另有 ROI discovery / invocation observer 诊断 Hook，不计为用户功能迁移。

### 迁移分组与风险

| 分组 | 典型范围 | 风险 | 处理原则 |
|---|---|---|---|
| A. 纯 UI / 配置观察 | 设置导航、欢迎页、主题与配置 UI | 中 | 先确认完整方法描述符、调用时序、UI 生命周期；不改变宿主 UI 主路径 |
| B. 本地策略与轻量 UI 过滤 | 显示过滤、文案策略、只读状态 | 中 | 优先抽离纯策略；Hook 边界保留 Java/Xposed |
| C. 会话与历史 | Session、导航、历史加载/删除、刷新 | 高 | 需要状态生命周期、线程、数据库副作用和回滚证据 |
| D. Chat / Request / Response | Request 构造、发送、响应合并、消息重建 | 极高 | 先完成 code289 真实运行时链证据；不更换 Flow 身份、不改参数/返回值/异常 |
| E. Agent / Tool / Attachment | Agent 心跳、工具 UI、附件上传/图片 URI | 高 | 必须证明异步生命周期、权限和副作用边界 |
| F. Account / Security / Network | 账号路由、凭据、PoW、Transport、网络捕获 | 极高 | 不纳入首批迁移；不复制凭据、不改变认证/风控/网络行为 |
| G. Bootstrap / Persistence | 备份恢复、迁移、清理与调度 | 高 | 先建立幂等、事务、恢复和数据损坏测试 |

## 6. 迁移准入门槛

每个迁移组必须单独满足以下条件，缺一项就保持 Legacy：

1. 精确宿主构建身份：包名、真实 versionCode/versionName、渠道、base+split APK 内容摘要、签名证书摘要、ClassLoader scope。
2. 精确 Hook 目标：owner、method、参数、返回类型、静态/实例属性及继承解析结果。
3. 语义角色与调用图：真实调用点、调用顺序、线程/协程边界。
4. 运行时证据：目标方法确实执行；不以静态候选数量代替。
5. 返回与生命周期契约：返回值、异常、取消、Continuation/Flow 身份、所有权及回收行为。
6. 审核后的版本专属 Adapter record 与 allowlist。
7. Shadow 比对、单组启用、回滚路径和完整回归矩阵。
8. CI + Android 构建通过；涉及宿主 Hook 的组必须有对应 L5 真机结果。

禁止为了提高迁移计数而放宽 ActivationGate、伪造 fingerprint、自动批准 discovery candidate、删减 readiness 条件或将多个风险组一次性切换。

## 7. 首轮执行顺序

1. 先完成 D01–D12，特别是 D01–D05；收集 code289 真实设备证据。
2. 依据设备日志补齐 HostCompat、Hook 安装状态和运行时调用序列；发现异常先修复基础加载/安装问题。
3. 对 170 个静态注册点重新生成带功能组、目标符号、版本门禁、安装入口、可选/必需属性的清单；不得仅凭行号作为稳定 ID。
4. 选择一个经设备证据证明为低风险的 UI/配置 Hook 作为首个候选；当前不预先批准具体 Hook。
5. 先 shadow observe，再以 reviewed adapter + exact host identity 单组启用；验证后才扩展到下一个组。
6. D 类 Chat/Flow、F 类 Account/Security/Network 保持 Legacy，直到独立证据完整。

## 8. 验收记录模板

| ID | 结果 | 时间 | 证据/日志文件 | 问题 |
|---|---|---|---|---|
| D01–D12 | 待实测 | — | — | — |

最终报告必须明确区分 L1 JVM 回归、L2 Android 构建、L3 APK 检查、L4 模拟环境、L5 真机。没有设备日志的项标记 BLOCKED / NOT RUN，不得写 PASS。
