# ROI dev 专项收尾审计

> 审计日期：2026-11-08  
> 仓库：`qingjian0/Deekseep-Custom`  
> 审计基线：`dev` @ `4ca8a84548bd3fd2286935eeb4ffb1e6aa6ec03f`  
> 目标宿主：官方 DeepSeek Android `com.deepseek.chat`  
> 范围：ROI 自动发现、调用链证据、Hook 授权、Universal Hook、旧 Hook 迁移、ROI CI  
> 稳定分支：`main` 未修改

## 1. 执行结论

ROI 已经具备一套可运行的只读 DEX 发现、静态候选关联、运行时类加载观察、受控诊断探针、Hook 证据门禁和 Universal Hook 生命周期框架。不能再把当前状态描述成“只有 R0 纯 Java Resolver”或“自动发现完全没有接入”。

但项目仍未完成任意宿主版本的自动适配，也未完成现有生产功能 Hook 向 Universal Hook 的批量迁移。当前最重要的安全事实是：发现候选不等于授权，静态调用边不等于真实执行链，Universal Hook 核心可用不等于生产功能已迁移。

**本次收尾目标：修正过期状态文档、把 ROI 专项回归直接接入 dev 触发的独立工作流，并保持所有未知宿主及未审核候选 fail-closed。**

## 2. 当前分支与 CI 证据

| 项目 | 结果 |
|---|---|
| dev HEAD | `4ca8a84548bd3fd2286935eeb4ffb1e6aa6ec03f` |
| 最新完整 CI | [Run 42454218917](https://github.com/qingjian0/Deekseep-Custom/actions/runs/42454218917) |
| Run 对应提交 | 与当前 dev HEAD 完全一致 |
| ROI fast audit (0.5) | PASS |
| Regression tests | PASS |
| Android Open build and lint | PASS |
| APK 结构、对齐、签名检查 | PASS |
| main | 未修改 |
| 真机验收 | 未由该 CI 证明 |

该 Run 的三个 Job 均成功。它证明当前提交通过仓库 CI 中已启用的脚本/JVM 回归和 Open Android 构建，不证明任何特定 DeepSeek 版本在 LSPosed 真机上全部功能正常。

## 3. 当前实际调用链

### 3.1 已维护宿主

```text
Xposed / LSPosed load-package callback
  -> package identity gate (com.deepseek.chat)
  -> HostCompat generation / channel classification
  -> host ClassLoader capture
  -> optional maintained-host class-load observation
  -> existing Main.installAllFeatureHooks(...)
  -> installHookStep / installBestEffortHook
  -> FeatureHookInstallRegistry
  -> feature pipelines (Chat / UI / Session / Agent / Attachment / Account ...)
  -> LegacyXposedModule / HookTarget
  -> one shared native Xposed callback per Member
  -> ordered logical Hooker chain
  -> original method exactly once
```

已维护版本仍主要通过既有 Java feature pipelines 安装生产功能 Hook。ROI class-load observer 是独立 opt-in 观察能力，不代表这些生产 Hook 已迁移至 Universal HookSpec。

### 3.2 未维护 / 未知宿主

```text
Xposed load-package callback
  -> HostCompat maintained-runtime gate
  -> unknown-host activation gates
       ├─ metadata observation switch
       ├─ diagnostic invocation-probe switch
       └─ feature-hook authorization switch
  -> optional BaseDexClassLoader.findClass observer
  -> RoiHostDiscovery.schedule(...)
  -> RoiDexKitDiscovery.schedule(...)
       ├─ bounded ClassLoader snapshot
       ├─ in-memory DexKit scan
       ├─ installed base / split APK scan
       ├─ semantic string anchors
       ├─ descriptor / method-shape extraction
       ├─ invoke + caller relation extraction
       ├─ CFG invoke reachability / dead-code filtering
       ├─ semantic role hints
       ├─ exact loader-scoped candidate identity
       └─ bounded diagnostic report
  -> discovery finalization
  -> optional exact runtime-class correlation
  -> diagnostic pass-through probe OR narrowly gated prompt adapter
  -> no fall-through to ordinary legacy feature-hook installation
```

未知宿主分支在调度只读发现后提前返回，不会继续进入常规已维护宿主的功能 Hook 安装流程。未知版本的开关互相隔离；诊断探针、元数据观察和功能 Hook 不是同一个授权开关。

## 4. 自动发现引擎现状

### 已实现并有源码/回归入口

- Host package / version / channel 识别路径与已安装 APK 身份采集。
- BaseDexClassLoader、父加载器及线程上下文加载器快照；动态加载器通过受信任的类加载观察点登记。
- DexKit 内存 DEX 查询与已安装 base/split APK 独立扫描。
- 类结构、方法描述符、字段/接口信息和已加载类元数据提取。
- 字符串锚点、方法形状、incoming/outgoing invoke/caller 关系。
- CFG 基本块可达性筛选；不完整解码按不完整证据处理。
- 语义角色提示和候选图；跨 ClassLoader 的边不合并。
- 扫描预算：每次扫描最多 12 个 Loader、48 次查询、128 个候选，软截止 45 秒；最多 3 轮发现，后续轮次不重复扫描 APK。
- APK base/split 内容指纹和宿主构建身份采集。
- 未知宿主候选输出、诊断报告和类加载事件观察。
- 对已加载类进行精确 owner / method descriptor / ClassLoader 关联的只读诊断探针。

### 仍未完成或不能声称完成

- 任意动态 DEX / 自定义 ClassLoader 的全面覆盖；当前为有界快照与受信任事件登记，不是对所有加载器的全局追踪。
- 所有业务语义角色的完整跨版本 Profile 库。
- request → transport → response 的完整、可关联运行时调用链。
- Flow suspend collector / response parser 的安全相关性证明；当前明确记录 unsupported coverage，不伪造 RESPONSE_RECEIVED / RESPONSE_PARSED。
- 自动生成并持久化经审核的跨版本 Adapter。
- 任意新版本自动 Hook。候选仍然不是可安装规则。

## 5. 调用链证据与授权边界

静态调用图来自 DEX invoke/caller 关系，且可经过 CFG 可达性过滤；它只说明静态引用关系。运行时 recorder 当前可记录请求构造、transport 等无正文观察，但 Flow collect 与响应 parser 的关联覆盖仍不完整。

`RoiDexKitDiscovery` 输出明确标记：

- runtime evidence 按完整 descriptor、adapter identity 和 ClassLoader scope 关联；
- adapter identity 标签不是 APK 密码学指纹；
- recorder 对 Resolver 的贡献为 diagnostic-only；
- 发现候选使用 unresolved role、`adapterApproved=false`、空 fingerprint allowlist 和 `Policy.discoveryOnly()`。

Universal Hook 激活还必须同时满足：

1. 精确宿主包、真实 versionCode/versionName、发行渠道；
2. base + split APK 内容摘要和签名证书摘要；
3. 当前进程精确 ClassLoader scope；
4. 候选 owner、method、完整 descriptor 和稳定 candidate identity；
5. 已审核证据目录中存在精确匹配记录；
6. 语义角色 allowlist、显式策略和 Resolver 硬门槛；
7. 安装后健康检查、可追踪句柄、失败回滚和卸载重试。

当前没有可供一般生产功能使用的已审核 HookSpec 目录记录。因此不能为了显示“自动 Hook 已启用”而放宽门禁。

## 6. Universal Hook 与旧 Hook 迁移

PR #18 已合并至 dev，提交为 `3342c3e521411417c8b23a0f4ff511b3cd80e07f`。其核心包含：

- Universal Hook Spec / Resolver / Installer；
- 共享 Legacy Xposed per-Member callback backend；
- FeatureHookInstallRegistry 生命周期接入；
- exact ClassLoader / host identity / candidate identity 约束；
- reviewed evidence registry 与 fail-closed activation gate；
- 依赖、冲突、幂等、rollback、uninstall/reinstall 和 retained-handle retry。

**迁移结论：** 生产功能 Hook 仍由既有 feature pipelines 安装；当前没有证据完整的通用生产 HookSpec 适配记录，故不执行机械批量迁移。迁移应按功能组逐个进行，先建立精确版本证据和完整行为契约，再做 shadow comparison、回滚与真机验证。

## 7. ROI 回归脚本覆盖

仓库现有 ROI runner 包括：

- DEX file/data/code reader、instruction decoder/scanner、register flow、invoke reachability；
- DexKit discovery、APK CLI budget、discovery budget；
- candidate graph / identity、ClassLoader profile、loaded-class inspector、class-load observer；
- semantic role hints、functional role classifier、class candidate matcher；
- hook resolver、unknown-host policy、invocation probe、auto-probe、chat-request wiring；
- runtime recorder、Universal Hook runtime；
- 生命周期、reviewed evidence、host identity、Legacy Xposed adapter 的独立回归。

本次新增/调整的 ROI 专项工作流会在 `dev` push 和目标为 `dev` 的 PR 上触发，并把 `module/test-roi-*.sh` 纳入专用 JVM 回归。原 `ci.yml` 的完整 Regression 与 Android 构建保持不变；专用工作流不替代完整 CI。

## 8. 真机验收与发布门槛

CI 通过后仍需对明确的设备/宿主组合执行 L5：

- 官方 DeepSeek 包名 `com.deepseek.chat`；
- 国内 2.7.1 / 2.7.2（内部 adapter code 287/289）与 Google Play 2.7.2（290）分别记录真实 PackageManager 版本信息；
- 设备型号、Android/API、ABI、LSPosed 版本、模块版本和作用域；
- Hook 安装状态、原方法 exactly-once、异常/取消、卸载/重装；
- 未知宿主发现模式不影响宿主启动，默认不安装功能 Hook；
- 收集不含 Prompt、Completion、Token、账号凭据和用户内容的脱敏日志。

不得把内部 adapter code 当成 Android PackageManager versionCode；不得把 CI APK 或静态 APK 报告说成真机通过。

## 9. 收尾验收标准

- [x] 当前 dev HEAD 与最新完整 CI 对齐并通过。
- [x] Universal Hook 核心已合并 dev。
- [x] 未知宿主只读发现已接入 load-package 分支。
- [x] 静态候选与 Hook 激活权限分离。
- [x] 文档明确记录真实调用链与未完成证据边界。
- [ ] 专项 ROI JVM workflow 在本次变更提交上通过。
- [ ] 对 287/289/290 的逐版本 L5 设备验收。
- [ ] 为任一生产功能建立完整 reviewed adapter evidence 后，逐组迁移 Universal Hook。

因此本轮可以完成工程文档和 CI 接线收尾；不能把尚缺少运行时 response/lifecycle 证据和 L5 设备验证的跨版本自动适配宣布为完成。
