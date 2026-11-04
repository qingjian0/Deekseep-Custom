# ROI 自动发现与跨版本 Hook 适配引擎

> 状态：架构决策草案（2026-11-04）。本文定义目标边界与实施顺序，不代表下述模块已经实现。

## 1. 目标

ROI 面向官方 DeepSeek Android 宿主 `com.deepseek.chat`，建立跨版本的运行时发现与适配能力。目标不是维护一份不断增长的混淆类名清单，而是把宿主代码中的类、字段、方法和调用关系识别为稳定的语义角色。

系统应能面对尚未维护的新版本：
- 识别宿主版本、发行渠道、APK/DEX 指纹和运行时 ClassLoader。
- 在不执行未知 Hook 的前提下发现可分析的 DEX 与类结构。
- 根据结构、签名、常量、调用关系等证据生成语义候选。
- 输出可复现的诊断报告，标记证据、置信度和失败原因。
- 只有通过明确验证的适配规则才能进入正式 Hook 安装流程。

## 2. 非目标与安全边界

- 未知版本不得回退到旧版本混淆映射，也不得自动安装猜测出的 Hook。
- 发现阶段只读取类元数据和 DEX 结构；不调用宿主业务方法、不构造请求、不读取用户消息或凭据。
- 不尝试绕过应用加固、DRM、登录验证或安全控制；只分析当前进程正常暴露、可合法读取的运行时代码与元数据。
- 不把 DEX 枚举成功等同于业务语义识别成功。
- 不把候选方法命中等同于真实请求链已验证。
- 不因扫描失败、动态 DEX 不可访问或 Native 分析库加载失败而阻断宿主正常启动。
- 既有模块包名、Xposed 入口、宿主包名和构建产物身份保持不变；ROI 是项目名称，不要求机械重命名既有代码命名空间。

## 3. 总体流水线

```text
HostLoadPackage
  ├─ Version / Channel / APK Fingerprint
  ├─ Known Adapter?
  │    ├─ yes -> Existing HostCompat + HostRemap -> verified Hook installers
  │    └─ no  -> Discovery-only mode
  │              ├─ ClassLoader / DEX inventory
  │              ├─ Structural & semantic scan
  │              ├─ Candidate Hook points + evidence
  │              └─ Report / cache; no automatic activation
  └─ Validation
       ├─ Static evidence
       ├─ Runtime metadata validation
       ├─ Call-chain validation
       └─ Explicitly approved adapter rule -> HookInstallRegistry
```

## 4. 模块边界

| 模块 | 职责 | 首期要求 |
|---|---|---|
| `HostVersionDetector` | 版本号、versionCode、渠道、宿主身份 | 不只依赖 versionName |
| `HostRuntimeFingerprint` | APK 路径、DEX CRC/大小、类加载器链指纹 | 生成稳定且不含用户数据的指纹 |
| `DexDiscoveryEngine` | 遍历 BaseDexClassLoader、父加载器、可见 Split/动态 DEX | 去重、限量、异常隔离 |
| `ClassStructureScanner` | 类、接口、字段、构造器、方法元数据 | 只读，不实例化对象 |
| `MethodFingerprintEngine` | 参数/返回类型、修饰符、常量、调用特征 | 结构指纹与版本差异比较 |
| `SemanticClassResolver` | 将混淆类映射到 request/transport/response/session 等语义角色 | 多证据评分，不以类名作为唯一依据 |
| `CallGraphAnalyzer` | 方法调用者、被调用者、字段读写和参数传播 | DexKit 或其他分析后端可插拔 |
| `HookCandidateRegistry` | 管理候选、证据、置信度与状态 | 与现有 Hook 安装注册表分离 |
| `RuntimeValidationEngine` | 验证候选签名、类加载、调用关系和生命周期 | 未验证候选不允许激活 |
| `AdaptiveMappingStore` | 按版本/DEX 指纹保存已验证映射 | 可回滚、可失效、带证据版本 |
| `ProbeReportExporter` | 导出可复现诊断报告 | 大小有界、脱敏、稳定字段 |

## 5. 候选状态机

| 状态 | 含义 | 是否允许安装 Hook |
|---|---|---|
| `DISCOVERED` | 扫描得到候选 | 否 |
| `CORRELATED` | 多个结构或调用证据相互印证 | 否 |
| `VERIFIED` | 静态与运行时验证通过，形成适配规则 | 仍需明确启用策略 |
| `ACTIVE` | 经授权进入现有 Hook 安装注册表 | 是 |
| `REJECTED` | 证据冲突、验证失败或过期 | 否 |

置信度必须附带可审计证据，不能只保存一个分数。映射规则需要记录宿主版本、发行渠道、DEX 指纹、逻辑角色、类/成员签名、验证时间、规则来源和回归结果。

## 6. 多版本与动态代码发现

首期扫描范围：
1. 当前 `LoadPackageParam.classLoader`。
2. 可访问的父 ClassLoader 链。
3. 各 `BaseDexClassLoader.pathList.dexElements` 中的 DexFile。
4. 后续在真实证据支持时扩展到运行时新增的 DexClassLoader、Split APK 和插件加载器。

每个 Loader/DEX 独立记录来源、枚举状态、类数量、重复数量、失败数量和耗时。禁止把不同 ClassLoader 中同名类视为同一个 Class 对象。

动态代码发现采用低频、事件驱动、限额扫描；不持续轮询，不在主线程执行，不对宿主启动路径增加不可控延迟。

## 7. 分析后端选择

### 第一层：平台反射与现有 DEX 枚举

作为必备后备路径，负责 ClassLoader、DexFile、类元数据与基础指纹。即使高级分析库不可用也能生成基础报告。

### 第二层：DexKit（候选方案，尚未集成）

DexKit 可用于字符串/字段/方法特征查询及调用关系分析，但需要先验证：
- 当前手动 `javac + D8` 构建管线的依赖接入方式。
- Native ABI 覆盖、Android API 兼容性与 LSPosed 进程内加载。
- LGPL-3.0 / Apache-2.0 双许可证的合规要求。
- 初始化耗时、扫描耗时、内存峰值和崩溃隔离。

DexKit 不应成为核心接口的唯一实现，也不应因 Native 库加载失败而禁用基础发现能力。

## 8. 与当前代码的衔接

- `HostCompat`：继续负责已知宿主代际识别和已验证适配能力；未知版本识别应转交发现模式，而不是猜测旧映射。
- `HostRemap`：继续承载经过审查的版本映射表，不自动写入未验证结果。
- `OpenNativeCompletionProbe`：保留为 Open 原生 Completion 的专项只读探针；逐步抽取通用 DEX/结构发现能力，避免其对固定请求类的依赖成为未知版本入口。
- `Main.handleLoadPackage`：未知版本的只读发现入口必须位于“拒绝安装未维护功能 Hook”的分支之前；发现入口不得调用现有业务 Hook 安装器。
- `FeatureHookInstallRegistry`：只接收已验证且被允许启用的 Hook 安装步骤。候选发现注册表与 Hook 安装注册表不得混用。

## 9. 实施阶段与验收

### Phase 0 — 入口与边界
- 在未知版本提前退出之前触发只读发现任务。
- 输出版本、渠道、APK/DEX 指纹和 ClassLoader 链。
- 验收：未知版本不安装功能 Hook，宿主启动不受扫描失败影响。

### Phase 1 — 通用 DEX 清单
- 支持当前 Loader 与父链中的可见 DEX，逐个隔离异常。
- 输出去重类名清单摘要、DEX 来源与完整性统计。
- 验收：扫描结果有界、可重复、不会因单个损坏 DEX 中断全局扫描。

### Phase 2 — 结构指纹
- 对候选类提取接口、字段类型、构造器、方法签名和必要常量。
- 建立已知 2.7.1/2.7.2 样本基准，验证指纹跨版本稳定性。
- 验收：已知样本能识别逻辑角色；未知样本只给候选，不自动启用。

### Phase 3 — 调用链分析
- 接入可选 DexKit 后端，追踪 request -> transport -> response。
- 对每条链记录证据和不确定性。
- 验收：真实调用链必须由静态与运行时证据共同验证。

### Phase 4 — 适配规则沉淀
- 保存经过验证的映射、版本指纹和回归证据。
- 支持规则过期、回滚和禁用。
- 验收：新版本规则不会污染旧版本映射。

### Phase 5 — 受控激活
- 将已验证适配器接入现有 Hook 安装注册表。
- 按功能粒度启用；任一关键契约不满足则 fail-closed。
- 验收：CI、静态审计、真机验证分开报告，不以 CI 代替真机结论。

## 10. 当前已知缺口

- 当前 `OpenNativeCompletionProbe` 以固定请求类作为专项分析入口，不能独立完成未知版本语义发现。
- 当前扫描只覆盖传入 Loader 的 `pathList.dexElements`；虽输出父 Loader 链信息，但没有递归枚举父链与独立动态 Loader。
- 当前 `HostCompat` 的已知版本识别和 `HostRemap` 映射表是已维护版本的快速路径，不是自动学习系统。
- 当前没有可复用的跨版本方法指纹库、调用图分析器或自动映射持久化机制。
- 当前未知版本保护逻辑会提前抑制功能 Hook；发现模式尚未接入该分支之前。

## 11. 设计决策

1. 先做只读发现和诊断，再做语义解析；不先做自动 Hook。
2. 基础扫描必须无 Native 依赖；DexKit 作为可选分析后端。
3. 版本名只是元数据，DEX/结构指纹才是跨版本匹配的重要证据之一。
4. 映射规则必须可审计、可回滚、可失效。
5. 未知版本始终 fail-closed；发现能力与功能启用能力彻底分离。
