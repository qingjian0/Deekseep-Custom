# Java → Kotlin 全量迁移矩阵（执行基线）

- 仓库：`qingjian0/Deekseep-Custom`
- 分支：`work/kotlin-logging-architecture`
- 基线 HEAD（生成清单时）：`e8051e933841c745868703f7de0b58bf4c8d4cbe`
- 清点结果：145 个生产 Java 文件、52 个测试/测试桩 Java 文件、5 个兼容/其他 Java 文件。
- 本清单按源码树自动分类，是任务分配初稿；文件风险和依赖须由认领者打开源码逐项确认，不能把规则分类当作已完成审计。
- 目标：10–15 个工作日完成主体迁移；完整兼容验收仍保留 18–30 个工作日风险预算。不得为追求速度牺牲行为等价、宿主兼容或测试。

## 迁移门禁

1. 每个生产 Java 实现必须有 Kotlin 对应实现、明确豁免理由，或经审计确认属于第三方/宿主 API 桩。
2. 迁移前记录调用方、被调用方、反射字符串、静态字段、线程/生命周期、持久化格式及外部可见 ABI。
3. 新 Kotlin 代码先进入既有 Kotlin runtime/合适模块；禁止为了迁移而引入无必要抽象层或改变包根。内部包根保持 `ds.dsh.DeepSeek`，宿主包名 `com.deepseek.chat` 不得改动。
4. 不允许同一批同时删除旧实现和引入未验证的新实现；按小批次提交，保持可回滚。
5. 每批必须通过对应单测/回归、模块构建、Java↔Kotlin ABI/反射检查；Hook 关键路径还需设备端验证。
6. 测试、Xposed API shim、旧版兼容代码不自动算作生产迁移对象；逐项判断是否必须保留，并记录豁免。

## 并行职责

| 角色 | 责任 | 输出 |
|---|---|---|
| F | 总协调、依赖排序、领域/业务迁移、超大类拆分、清零验收 | 每批 Kotlin 实现与迁移状态更新 |
| F2 | Java/Kotlin ABI、反射、包名、架构边界与迁移审查 | 互操作审计清单及阻断项 |
| F3 | 日志、安全脱敏、并发、存储可靠性相关迁移 | 安全/存储回归结果 |
| D | Gradle/Android 构建、CI、设备兼容验证 | 可复现构建与设备验证记录 |
| C | 测试迁移、行为对照、边界与回归测试 | 测试映射及回归报告 |

并行规则：每个文件只能有一个主修改者；其他角色通过 review 提供意见。跨组依赖先登记接口契约，不得同时改同一文件。

## 生产 Java 文件清单（逐项认领）

| # | Java 文件 | 大小 | 优先级 | 风险 | 迁移要求 | 状态 | Owner |
|---:|---|---:|---|---|---|---|---|
| 1 | `module/src/ds/dsh/DeepSeek/AccountCredentialCodec.java` | 12.3 KiB | P1 | 中 | JSON 结构仍由 Android-facing Java 层处理；纯字段与文件名策略抽至 Kotlin runtime，保持 Java ABI 和格式行为 | 迁移中 | F/A |
| 2 | `module/src/ds/dsh/DeepSeek/AccountImportArchive.java` | 4.4 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 3 | `module/src/ds/dsh/DeepSeek/AccountManager.java` | 42.3 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 4 | `module/src/ds/dsh/DeepSeek/AccountRegistrationBridge.java` | 29.0 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 5 | `module/src/ds/dsh/DeepSeek/AccountSafetyPrank.java` | 26.5 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 6 | `module/src/ds/dsh/DeepSeek/AccountUi.java` | 94.3 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 7 | `module/src/ds/dsh/DeepSeek/AccountUiLegacy.java` | 38.0 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 8 | `module/src/ds/dsh/DeepSeek/AgentBingSearch.java` | 29.1 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 9 | `module/src/ds/dsh/DeepSeek/AgentDelayActivity.java` | 3.5 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 10 | `module/src/ds/dsh/DeepSeek/AgentDelayReceiver.java` | 1.8 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 11 | `module/src/ds/dsh/DeepSeek/AgentDeviceBridge.java` | 162.2 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 12 | `module/src/ds/dsh/DeepSeek/AgentManualGate.java` | 11.2 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 13 | `module/src/ds/dsh/DeepSeek/AgentMcpManager.java` | 35.8 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 14 | `module/src/ds/dsh/DeepSeek/AgentMcpUi.java` | 16.3 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 15 | `module/src/ds/dsh/DeepSeek/AgentQuestionUi.java` | 23.6 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 16 | `module/src/ds/dsh/DeepSeek/AgentRunStore.java` | 22.7 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 17 | `module/src/ds/dsh/DeepSeek/AgentRunUi.java` | 20.1 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 18 | `module/src/ds/dsh/DeepSeek/AgentSettingsUi.java` | 103.6 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 19 | `module/src/ds/dsh/DeepSeek/AgentToolConfig.java` | 33.2 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 20 | `module/src/ds/dsh/DeepSeek/AgentToolTraceStore.java` | 15.8 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 21 | `module/src/ds/dsh/DeepSeek/ChatAppearance.java` | 141.6 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 22 | `module/src/ds/dsh/DeepSeek/ChatAppearanceUi.java` | 144.6 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 23 | `module/src/ds/dsh/DeepSeek/ChatBackupStore.java` | 6.0 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 24 | `module/src/ds/dsh/DeepSeek/ChatEditorUi.java` | 207.0 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 25 | `module/src/ds/dsh/DeepSeek/ChatSearchUi.java` | 14.5 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 26 | `module/src/ds/dsh/DeepSeek/ClosedFullDataBackupBridge.java` | 29.4 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 27 | `module/src/ds/dsh/DeepSeek/CloudPromptClient.java` | 4.8 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 28 | `module/src/ds/dsh/DeepSeek/CommunityUi.java` | 0.1 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 29 | `module/src/ds/dsh/DeepSeek/ConversationTools.java` | 37.8 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 30 | `module/src/ds/dsh/DeepSeek/CrashDiagnosticsUi.java` | 20.7 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 31 | `module/src/ds/dsh/DeepSeek/DeekseepFileClient.java` | 9.1 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 32 | `module/src/ds/dsh/DeepSeek/DeekseepLetter.java` | 5.5 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 33 | `module/src/ds/dsh/DeepSeek/DeekseepTools.java` | 40.1 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 34 | `module/src/ds/dsh/DeepSeek/DeekseepUi.java` | 378.2 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 35 | `module/src/ds/dsh/DeepSeek/DeepSeekCacheCleaner.java` | 5.9 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 36 | `module/src/ds/dsh/DeepSeek/DeepSeekRequestDebugUi.java` | 14.8 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 37 | `module/src/ds/dsh/DeepSeek/DeveloperDiagnostics.java` | 6.3 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 38 | `module/src/ds/dsh/DeepSeek/DualChatUi.java` | 0.4 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 39 | `module/src/ds/dsh/DeepSeek/FontOverride.java` | 3.8 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 40 | `module/src/ds/dsh/DeepSeek/FontScaleFeature.java` | 12.0 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 41 | `module/src/ds/dsh/DeepSeek/GoogleLoginUnlock.java` | 4.0 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 42 | `module/src/ds/dsh/DeepSeek/HeartbeatToolProtocol.java` | 129.6 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 43 | `module/src/ds/dsh/DeepSeek/HistoryBridge.java` | 25.2 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 44 | `module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java` | 204.1 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 45 | `module/src/ds/dsh/DeepSeek/HookAgentPipeline.java` | 191.3 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 46 | `module/src/ds/dsh/DeepSeek/HookAttachmentPipeline.java` | 116.3 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 47 | `module/src/ds/dsh/DeepSeek/HookChatPipeline.java` | 122.2 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 48 | `module/src/ds/dsh/DeepSeek/HookConfigRollout.java` | 10.7 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 49 | `module/src/ds/dsh/DeepSeek/HookLogOverlay.java` | 14.4 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 50 | `module/src/ds/dsh/DeepSeek/HookSessionManagement.java` | 174.9 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 51 | `module/src/ds/dsh/DeepSeek/HookUiSurface.java` | 181.1 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 52 | `module/src/ds/dsh/DeepSeek/HostCompat.java` | 55.9 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 53 | `module/src/ds/dsh/DeepSeek/HostNavigationBridge.java` | 6.4 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 54 | `module/src/ds/dsh/DeepSeek/HostNavigationUi.java` | 23.7 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 55 | `module/src/ds/dsh/DeepSeek/HostRemap.java` | 17.3 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 56 | `module/src/ds/dsh/DeepSeek/HostV271.java` | 9.4 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 57 | `module/src/ds/dsh/DeepSeek/HubInsetSwitch.java` | 8.0 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 58 | `module/src/ds/dsh/DeepSeek/HubMaterialGlyphView.java` | 11.0 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 59 | `module/src/ds/dsh/DeepSeek/ImageCutoutUi.java` | 59.3 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 60 | `module/src/ds/dsh/DeepSeek/InputGlassStyle.java` | 12.7 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 61 | `module/src/ds/dsh/DeepSeek/JavaHookGuide.java` | 15.6 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 62 | `module/src/ds/dsh/DeepSeek/JavaHookInventory.java` | 4.5 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 63 | `module/src/ds/dsh/DeepSeek/JavaPluginApi.java` | 4.7 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 64 | `module/src/ds/dsh/DeepSeek/JavaPluginHookCatalog.java` | 14.9 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 65 | `module/src/ds/dsh/DeepSeek/JavaPluginManager.java` | 63.9 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 66 | `module/src/ds/dsh/DeepSeek/JavaPluginPageUi.java` | 21.9 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 67 | `module/src/ds/dsh/DeepSeek/JavaPluginPlatform.java` | 39.2 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 68 | `module/src/ds/dsh/DeepSeek/JavaPluginRuntimeBridge.java` | 10.9 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 69 | `module/src/ds/dsh/DeepSeek/JavaPluginUi.java` | 26.7 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 70 | `module/src/ds/dsh/DeepSeek/LiquidGlassEngine.java` | 77.9 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 71 | `module/src/ds/dsh/DeepSeek/LoaderSecurityGuard.java` | 1.2 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 72 | `module/src/ds/dsh/DeepSeek/LocalApiFloatingOverlay.java` | 37.1 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 73 | `module/src/ds/dsh/DeepSeek/LocalAudioControlActivity.java` | 3.4 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 74 | `module/src/ds/dsh/DeepSeek/LocalAudioPlaybackService.java` | 8.5 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 75 | `module/src/ds/dsh/DeepSeek/LogExportUi.java` | 19.0 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 76 | `module/src/ds/dsh/DeepSeek/M3Theme.java` | 13.4 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 77 | `module/src/ds/dsh/DeepSeek/Main.java` | 539.4 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 78 | `module/src/ds/dsh/DeepSeek/MainReflectionSupport.java` | 8.0 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 79 | `module/src/ds/dsh/DeepSeek/ModelDisplayNames.java` | 5.2 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 80 | `module/src/ds/dsh/DeepSeek/ModelFileOutput.java` | 14.0 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 81 | `module/src/ds/dsh/DeepSeek/ModelFileViewerUi.java` | 14.3 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 82 | `module/src/ds/dsh/DeepSeek/ModuleConfigBridge.java` | 11.0 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 83 | `module/src/ds/dsh/DeepSeek/ModuleGlyphView.java` | 4.2 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 84 | `module/src/ds/dsh/DeepSeek/ModuleUpdateChecker.java` | 10.3 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 85 | `module/src/ds/dsh/DeepSeek/NativeApiPatchDecoder.java` | 2.4 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 86 | `module/src/ds/dsh/DeepSeek/NativeDualChatBridge.java` | 43.0 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 87 | `module/src/ds/dsh/DeepSeek/NativeModelFileCardRenderer.java` | 9.7 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 88 | `module/src/ds/dsh/DeepSeek/NotificationIcons.java` | 1.1 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 89 | `module/src/ds/dsh/DeepSeek/PassiveInjectionEntry.java` | 4.6 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 90 | `module/src/ds/dsh/DeepSeek/PinggyTunnelManager.java` | 0.7 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 91 | `module/src/ds/dsh/DeepSeek/PrivacyGuard.java` | 20.7 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 92 | `module/src/ds/dsh/DeepSeek/ProactiveHeartbeatReceiver.java` | 31.1 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 93 | `module/src/ds/dsh/DeepSeek/ProcessManagerActivity.java` | 26.8 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 94 | `module/src/ds/dsh/DeepSeek/PromptLibrary.java` | 12.9 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 95 | `module/src/ds/dsh/DeepSeek/PromptLibraryUi.java` | 28.6 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 96 | `module/src/ds/dsh/DeepSeek/PromptPickerActivity.java` | 2.2 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 97 | `module/src/ds/dsh/DeepSeek/PublicTunnelBinderBridge.java` | 4.7 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 98 | `module/src/ds/dsh/DeepSeek/PublicTunnelManager.java` | 53.7 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 99 | `module/src/ds/dsh/DeepSeek/QqMusicPlaybackService.java` | 6.7 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 100 | `module/src/ds/dsh/DeepSeek/QqMusicSessionAccessService.java` | 0.2 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 101 | `module/src/ds/dsh/DeepSeek/RawHookTrace.java` | 10.7 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 102 | `module/src/ds/dsh/DeepSeek/RemoteFeatureFlags.java` | 39.8 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 103 | `module/src/ds/dsh/DeepSeek/RemoteFeatureFlagsUi.java` | 35.7 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 104 | `module/src/ds/dsh/DeepSeek/ResponsePreserver.java` | 31.8 KiB | P2-P3 | 高 | 服务/协议：按调用链迁移，保持线程、超时、重试、IPC 与协议兼容 | 待认领 | — |
| 105 | `module/src/ds/dsh/DeepSeek/RichPanelRenderer.java` | 77.4 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 106 | `module/src/ds/dsh/DeepSeek/RuntimeEmbeddingMode.java` | 3.2 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 107 | `module/src/ds/dsh/DeepSeek/RuntimeProofPartA.java` | 0.1 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 108 | `module/src/ds/dsh/DeepSeek/RuntimeProofPartB.java` | 0.1 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 109 | `module/src/ds/dsh/DeepSeek/RuntimeProtection.java` | 0.4 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 110 | `module/src/ds/dsh/DeepSeek/SettingsActivity.java` | 23.1 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 111 | `module/src/ds/dsh/DeepSeek/SettingsSwipeGesture.java` | 4.0 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 112 | `module/src/ds/dsh/DeepSeek/ShakeParallaxUi.java` | 11.0 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 113 | `module/src/ds/dsh/DeepSeek/SpatialLayerCache.java` | 13.7 KiB | P2 | 高 | 存储/数据：保持序列化格式、路径、权限、原子性、并发和异常语义 | 待认领 | — |
| 114 | `module/src/ds/dsh/DeepSeek/SpatialMotionController.java` | 29.5 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 115 | `module/src/ds/dsh/DeepSeek/SpatialMotionUi.java` | 20.4 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 116 | `module/src/ds/dsh/DeepSeek/SystemBarCompat.java` | 2.8 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 117 | `module/src/ds/dsh/DeepSeek/TaskExecutionUi.java` | 13.5 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 118 | `module/src/ds/dsh/DeepSeek/TextWaveEngine.java` | 15.0 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 119 | `module/src/ds/dsh/DeepSeek/ThemeColorConfig.java` | 3.9 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 120 | `module/src/ds/dsh/DeepSeek/ThemeColorUi.java` | 15.1 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 121 | `module/src/ds/dsh/DeepSeek/UiLanguage.java` | 13.5 KiB | P4 | 高 | UI：先拆职责与状态边界；保持资源 ID、主线程、生命周期和交互行为 | 待认领 | — |
| 122 | `module/src/ds/dsh/DeepSeek/UiLanguageCatalog.java` | 185.8 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 123 | `module/src/ds/dsh/DeepSeek/WorkspaceVtTerminalV241.java` | 6.0 KiB | P1 | 中 | 纯逻辑/领域/工具：优先迁移；补充行为等价测试和 Java 调用兼容检查 | 待认领 | — |
| 124 | `module/src/ds/dsh/DeepSeek/XposedActivationProvider.java` | 21.6 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 125 | `module/src/ds/dsh/DeepSeek/XposedActivationReceiver.java` | 2.3 KiB | P3 | 极高 | Hook/反射边界：逐项核对目标类/方法签名、ClassLoader、生命周期、异常与 Java ABI | 待认领 | — |
| 126 | `module/src/ds/dsh/DeepSeek/z12.java` | 14.2 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 127 | `module/src/ds/dsh/DeepSeek/z13.java` | 8.9 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 128 | `module/src/ds/dsh/DeepSeek/z14.java` | 0.4 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 129 | `module/src/ds/dsh/DeepSeek/z15.java` | 0.5 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 130 | `module/src/ds/dsh/DeepSeek/z16.java` | 0.3 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 131 | `module/src/ds/dsh/DeepSeek/z16Decision.java` | 0.5 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 132 | `module/src/ds/dsh/DeepSeek/z17.java` | 13.3 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 133 | `module/src/ds/dsh/DeepSeek/z18.java` | 9.0 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 134 | `module/src/ds/dsh/DeepSeek/z19.java` | 121.5 KiB | P4 | 高 | 超大/复杂文件：先提取职责、梳理调用图与静态状态，再分片迁移；禁止整体机械转换 | 待认领 | — |
| 135 | `module/src/ds/dsh/DeepSeek/z2.java` | 17.1 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 136 | `module/src/ds/dsh/DeepSeek/z20.java` | 3.5 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 137 | `module/src/ds/dsh/DeepSeek/z21.java` | 34.1 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 138 | `module/src/ds/dsh/DeepSeek/z22.java` | 3.8 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 139 | `module/src/ds/dsh/DeepSeek/z3.java` | 4.5 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 140 | `module/src/ds/dsh/DeepSeek/z4.java` | 9.3 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 141 | `module/src/ds/dsh/DeepSeek/z5.java` | 26.8 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 142 | `module/src/ds/dsh/DeepSeek/z6.java` | 11.3 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 143 | `module/src/ds/dsh/DeepSeek/z7.java` | 20.6 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 144 | `module/src/ds/dsh/DeepSeek/z8.java` | 3.7 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |
| 145 | `module/src/ds/dsh/DeepSeek/z9.java` | 7.0 KiB | P4 | 高 | 混淆/遗留类：先追踪调用点、反射字符串、宿主映射和测试覆盖，再决定迁移或保留 | 待认领 | — |

## 非生产 Java 文件清点

### 测试与测试桩（不纳入生产 Java 清零数字）
- `module-legacy/tests/ds/dsh/DeepSeek/LegacyXposedModuleRegressionTest.java` (17.7 KiB)
- `module-legacy/tests/support/ds/dsh/DeepSeek/LegacyInstrumentationStubs.java` (1.0 KiB)
- `module/test/ds/dsh/DeepSeek/DshLogRegression.java` (3.2 KiB)
- `module/test/ds/dsh/DeepSeek/FeatureHookInstallRegistryRegression.java` (8.7 KiB)
- `module/test/ds/dsh/DeepSeek/KotlinPolicyJavaInteropRegression.java` (8.3 KiB)
- `module/tests/c74.java` (0.8 KiB)
- `module/tests/ds/dsh/DeepSeek/AccountCredentialCodecRegressionTest.java` (4.8 KiB)
- `module/tests/ds/dsh/DeepSeek/AccountServerValidationRegressionTest.java` (6.1 KiB)
- `module/tests/ds/dsh/DeepSeek/AgentRunStoreRegressionTest.java` (4.3 KiB)
- `module/tests/ds/dsh/DeepSeek/AutoContinuePolicyRegressionTest.java` (1.6 KiB)
- `module/tests/ds/dsh/DeepSeek/ChatAppearanceConfigRegressionTest.java` (37.1 KiB)
- `module/tests/ds/dsh/DeepSeek/ChatBackupDeviceSmoke.java` (3.5 KiB)
- `module/tests/ds/dsh/DeepSeek/ChatBackupFormatRegressionTest.java` (1.2 KiB)
- `module/tests/ds/dsh/DeepSeek/ChatBackupInstrumentation.java` (1.4 KiB)
- `module/tests/ds/dsh/DeepSeek/ChatEditorHistoryImageRegressionTest.java` (5.6 KiB)
- `module/tests/ds/dsh/DeepSeek/ChatEditorThinkingRegressionTest.java` (4.8 KiB)
- `module/tests/ds/dsh/DeepSeek/DeepSeekCacheCleanerRegressionTest.java` (2.6 KiB)
- `module/tests/ds/dsh/DeepSeek/GoogleLoginUnlockRegressionTest.java` (5.2 KiB)
- `module/tests/ds/dsh/DeepSeek/HeartbeatToolProtocolRegressionTest.java` (56.1 KiB)
- `module/tests/ds/dsh/DeepSeek/HistoryBridgeRegressionTest.java` (14.6 KiB)
- `module/tests/ds/dsh/DeepSeek/HostCompatRegressionTest.java` (13.0 KiB)
- `module/tests/ds/dsh/DeepSeek/ImageCutoutRegressionTest.java` (6.3 KiB)
- `module/tests/ds/dsh/DeepSeek/NativeApiPatchDecoderRegressionTest.java` (3.0 KiB)
- `module/tests/ds/dsh/DeepSeek/NativeSessionDeleteRegressionTest.java` (2.1 KiB)
- `module/tests/ds/dsh/DeepSeek/NativeSessionRefreshRegressionTest.java` (2.8 KiB)
- `module/tests/ds/dsh/DeepSeek/PortV271RegressionTest.java` (4.1 KiB)
- `module/tests/ds/dsh/DeepSeek/ProcessManagerRegressionTest.java` (2.8 KiB)
- `module/tests/ds/dsh/DeepSeek/RemoteFeatureFlagsRegressionTest.java` (3.0 KiB)
- `module/tests/ds/dsh/DeepSeek/ReplyReadyPolicyRegressionTest.java` (1.8 KiB)
- `module/tests/ds/dsh/DeepSeek/ResponsePreserverRegressionTest.java` (6.2 KiB)
- `module/tests/ds/dsh/DeepSeek/RichPanelRendererRegressionTest.java` (15.8 KiB)
- `module/tests/ds/dsh/DeepSeek/UiLanguagePolicyRegressionTest.java` (3.5 KiB)
- `module/tests/ds/dsh/DeepSeek/relay/ExpertRelayGateRegressionTest.java` (1.2 KiB)
- `module/tests/h61.java` (0.2 KiB)
- `module/tests/hostmap/ds/dsh/DeepSeek/HostRemapRegressionTest.java` (1.3 KiB)
- `module/tests/hostmap/support/ds/dsh/DeepSeek/HostCompat.java` (0.1 KiB)
- `module/tests/hostmap/support/ds/dsh/DeepSeek/Main.java` (0.1 KiB)
- `module/tests/hv.java` (0.1 KiB)
- `module/tests/iu0.java` (0.0 KiB)
- `module/tests/kv.java` (1.7 KiB)
- `module/tests/kv0.java` (0.0 KiB)
- `module/tests/lq.java` (0.0 KiB)
- `module/tests/n02.java` (0.1 KiB)
- `module/tests/p64.java` (0.2 KiB)
- `module/tests/process/TargetProcessGuardRegressionTest.java` (1.5 KiB)
- `module/tests/sl8.java` (0.4 KiB)
- `module/tests/td1.java` (0.1 KiB)
- `module/tests/tp.java` (0.5 KiB)
- `module/tests/v271stubs/ey1.java` (0.2 KiB)
- `module/tests/v271stubs/gy.java` (0.1 KiB)
- `module/tests/v271stubs/v9a.java` (0.3 KiB)
- `module/tests/x94.java` (0.4 KiB)

### 兼容接口、旧版兼容及其他（逐项判定是否保留）
- `module-legacy/compat/ds/dsh/DeepSeek/LegacyXposedModule.java` (14.9 KiB)
- `module-legacy/src/de/robv/android/xposed/IXposedHookLoadPackage.java` (0.3 KiB)
- `module-legacy/src/de/robv/android/xposed/XC_MethodHook.java` (1.5 KiB)
- `module-legacy/src/de/robv/android/xposed/XposedBridge.java` (1.3 KiB)
- `module-legacy/src/de/robv/android/xposed/callbacks/XC_LoadPackage.java` (0.4 KiB)

## 每批提交格式

- `refactor(kotlin): migrate <area> from Java`
- Commit 描述必须列出迁移文件、行为变化（应为无）、测试命令与结果、尚未验证的设备场景。
- 状态只能使用：`待认领`、`分析中`、`迁移中`、`待验证`、`已验证`、`豁免（附理由）`。

## 进度汇报

每个角色在本 Issue 下回复：认领范围、当前阻塞、预计完成批次；完成后附 commit SHA、构建/测试结果。不得仅报告“已迁移”。

> 清单统计：生产 Java 145 个；优先级分布：P2 16、P4 71、P2-P3 17、P1 28、P3 13。生产源码合计 5295.1 KiB。