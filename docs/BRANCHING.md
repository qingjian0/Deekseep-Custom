# 分支约定

## 稳定与开发分支

- `main`：稳定分支；未经明确要求不得合并、重置或强推。
- `dev`：唯一日常开发基线；ROI、Local API、通用 Hook 与文档均从最新 dev 开始。
- `work/*`：隔离任务分支；通过 PR 回到 `dev`，不得将旧工作分支整体覆盖 dev。
- `refactor/kotlin-hook-architecture`：历史 Kotlin Runtime 架构基线，不作为当前开发基线。

## 操作规则

1. 每轮工作前读取远端 dev HEAD，不能依赖旧快照。
2. 任务分支只提交本任务所需文件；ROI、Local API 与其他功能按模块和测试入口隔离。
3. 不自动合并 main，不 force push，不把历史分支 CI 结果移植为当前 HEAD 的验证结论。
4. PR 合并前审查 merge base、ahead/behind、文件范围、CI 与回滚路径。
5. 完整 CI、APK 构建、静态 APK 证据和 L5 真机验收分别记录，不互相替代。
6. 工作分支完成后通过 PR 交付；只有明确授权时才合并目标分支。

当前分支、提交与 CI 快照见 [PROJECT_STATUS.md](PROJECT_STATUS.md)；ROI 专项审计见 [ROI_DEV_CLOSEOUT_AUDIT.md](ROI_DEV_CLOSEOUT_AUDIT.md)。
