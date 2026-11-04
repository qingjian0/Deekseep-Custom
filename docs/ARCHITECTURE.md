# ROI 架构说明

**适用范围：** 当前工作分支 `work/roi-agent-core-kotlin`。本文描述渐进式架构约束；具体实现进度以 [PROJECT_STATUS.md](PROJECT_STATUS.md) 为准。

## 1. 项目定位

ROI 是作用于 DeepSeek Android 客户端（`com.deepseek.chat`）的 LSPosed / Xposed 模块。它不是独立聊天客户端，也不是通用 Android SDK。

当前工程包含三类主要代码：

1. **Hook 核心**：以 Java 为主，运行在目标宿主进程，负责宿主识别、类/方法定位、Hook 安装和功能逻辑。
2. **管理界面**：`compose-launcher` 下的 Kotlin + Jetpack Compose Android 应用，用于展示状态和管理设置。
3. **兼容与构建层**：旧版模块/资源、Universal/Legacy 兼容目录、开发输入准备脚本及 Gradle 构建工程。

## 2. 主要目录职责

| 路径 | 当前职责 | 修改注意事项 |
|---|---|---|
| `module/src/` | Java Hook 核心、宿主适配与 Local API Gateway | 修改前追踪入口、调用链和宿主版本条件 |
| `module/hostmap/` | 宿主版本符号映射 | 不可猜测映射；记录来源并验证 |
| `module-kotlin-runtime/` | Kotlin Runtime、Agent Core、确定性策略与 JVM 测试 | 新增核心逻辑优先放在此处，避免依赖 Android/Xposed 类型 |
| `module-legacy/` | Legacy Xposed 构建与兼容代码/清单 | 不可假设与当前主构建完全等价 |
| `module-universal/` | 通用/旧构建资源及载荷路径 | 先确认目标构建是否实际引用 |
| `compose-launcher/` | Kotlin Compose 管理应用与 Gradle 工程 | 不要把管理 UI 与宿主内 Hook 生命周期混为一谈 |
| `scripts/` | 构建、检查及辅助脚本 | 检查路径假设、退出码和 CI 可移植性 |
| `docs/upstream/` | 上游原始文档存档 | 保留来源，不把上游说明当作当前实现事实 |
| `.github/workflows/` | GitHub Actions 工作流 | 区分 PR 验证、构建、签名和发布权限 |

## 3. 运行边界

- Hook 代码运行在 DeepSeek 宿主进程，通常拥有宿主进程可访问的数据和权限；必须按高敏感边界设计。
- Compose launcher 是独立 Android 应用进程/组件入口，其与宿主进程之间的状态通信必须明确认证和授权。
- 宿主版本映射、Xposed API 和 Android 系统 API 属于不同兼容层；不要把它们耦合成一个未经验证的版本判断。
- 当前仓库同时保留新旧构建路径。实际发布路径必须以对应 workflow、Gradle task 和最终产物为准，不能只看脚本名称或旧文档。

## 4. 已有设计应优先复用

- 宿主识别与兼容性逻辑（例如 HostCompat / HostV271 等现有机制）。
- 按功能拆分的 Hook 类和现有功能边界。
- 现有 Xposed 入口及 Legacy 兼容层。
- 已有设置、持久化和诊断机制。

新增公共抽象前，必须说明至少两个真实调用场景、当前实现的具体痛点和迁移成本。不得为了架构整洁引入大型 SPI、DI、插件系统或跨平台抽象层。

## 5. 关键不变量

- 目标宿主包名保持 `com.deepseek.chat`，除非单独任务明确调整支持范围。
- 未知宿主版本不执行未经验证的 Hook。
- 可选 Hook 不应破坏宿主基本功能；关键 Hook 的失败必须可见。
- 用户设置、偏好键、文件路径和组件身份属于兼容性接口，变更需要迁移策略。
- 导出组件、Provider、Receiver、Binder 和跨进程通信均属于安全敏感面。
- Open/Protected 等构建变体必须保持源代码、载荷、签名与发布边界，不得意外混装。

## 6. Main.java 渐进式拆分

`Main.java` 仍承担较多入口、生命周期和功能协调职责。拆分必须以可独立验证的职责边界为单位，不允许仅按行数或方法数量机械拆分。当前已将目标包名准入和同进程重复注入抑制提取到 `TargetProcessGuard`，并提供纯 JVM 回归测试。后续拆分边界、顺序和验收条件见 [MAIN_DECOMPOSITION.md](MAIN_DECOMPOSITION.md)。

## 7. 自动发现与跨版本适配

ROI 自动发现引擎的目标架构、模块边界、候选状态机与实施阶段见 [ROI 自动发现与跨版本 Hook 适配引擎](architecture/ROI_AUTO_DISCOVERY_ENGINE.md)。当前文档是架构决策草案，不代表自动发现或自动 Hook 已经实现。

## 8. 架构变更要求

任何跨模块重构必须先提供：
1. 现状调用图或关键调用链；
2. 新旧职责映射；
3. 对 Hook 时序、进程、线程、权限、存储和宿主兼容性的影响；
4. 可回滚的分阶段方案；
5. 回归测试和验证环境。

不允许以“清理代码”为理由一次性重写大文件或批量移动 Hook。大文件拆分应保持行为不变，并以可验证的小步骤进行。
