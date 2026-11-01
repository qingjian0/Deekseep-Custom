# Main.java 渐进式拆分记录

## 当前基线

- 文件：`module/src/com/dsmod/probe/Main.java`
- 当前规模：约 10.7k 行（以本分支提交为准，后续可能变化）
- 角色：传统 Xposed 入口、宿主进程启动、生命周期接入、功能 Hook 安装协调、管理 UI/运行状态的兼容转发，以及仍未迁出的历史功能实现。
- 已有拆分：`MainReflectionSupport`、`HostCompat`、`HostRemap`、`HostV271`、多个 `Hook*` 功能类和 UI/服务类。

Main 仍然过大，但它不是一个可以通过机械拆分解决的单纯文件长度问题。其静态状态、跨类兼容转发、Hook 安装顺序、宿主 ClassLoader、生命周期回调和文件路径均构成隐式接口。未经调用关系和回归测试确认，不得批量移动方法或改变访问级别。

## 本次 P3 已完成的第一步

将目标包名准入和同进程重复注入抑制从 `Main.handleLoadPackage` 提取到：

- `module/src/com/dsmod/probe/TargetProcessGuard.java`
- `module/tests/process/TargetProcessGuardRegressionTest.java`
- `module/test-target-process-guard.sh`

该边界不依赖 Android Context、Xposed API 或宿主类加载器，可用普通 JVM 独立测试。Main 保留 Xposed 回调入口，并将准入结果映射回原有行为。生产包名 `com.deepseek.chat` 和现有嵌入式共存包名 `com.deepseek.chat.embedded` 均保持兼容。

## 后续拆分顺序

每个切片必须先具备行为测试，再单独迁移：

1. **进程准入与启动门禁**：已完成首个切片；后续只在有明确测试时再抽离 BuildInfo/Closed admission。
2. **Hook 安装协调器**：先建立每个 Hook 安装组的必需/可选分类、重复安装和部分失败测试，再考虑从 Main 移出安装流程。
3. **Activity 生命周期协调**：梳理 onActivityResumed / onActivityPaused / onActivityDestroyed 的调用顺序、弱引用和 Handler 所有权后再迁移。
4. **诊断与日志出口**：先统一脱敏、日志级别和线程约束；不能改变现有诊断文件路径或日志格式。
5. **遗留功能实现**：按功能域逐个迁移，保持 Hook 目标、参数、返回值、副作用和版本门禁不变。

## 拆分验收条件

- 每次只迁移一个职责边界。
- 不改变 Xposed 入口类名、Manifest、applicationId、偏好键、持久化路径或公开组件身份。
- 不引入 DI、SPI、Service Locator 或新的大型框架。
- 迁移前后执行同一组 JVM 回归、宿主映射回归、Universal 构建和 Open Android 构建。
- Hook 目标或时序发生变化时，必须补充对应宿主版本的真机验证；没有设备时标记 NOT RUN。
- 如果无法证明行为等价，保留原实现并停止该切片，不以“代码更整洁”为由强行合并。

本文件是渐进式重构记录，不代表 Main 已完成拆分。
