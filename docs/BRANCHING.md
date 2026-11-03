# 分支约定

## 稳定与历史分支

- `main`：默认稳定分支；不因本任务自动接收工作分支改动。
- `dev` / `develop`：历史开发分支，具体状态以 GitHub 远端分支为准。
- `refactor/kotlin-hook-architecture`：Kotlin Runtime 架构基线。

## 当前任务分支

- `work/roi-agent-core-kotlin`：当前主工作分支，承载 Agent Core、Local API Gateway、Native Adapter 诊断与相关文档。
- `work/roi-agent-core-batch`、`work/kotlin-logging-architecture` 等：历史或并行工作分支；合并前必须比较提交与文件差异。

## 操作规则

1. 每轮工作开始时读取远端分支 HEAD，不依赖过期的本地快照。
2. 在当前任务分支直接提交小步、可验证改动。
3. 不自动合并 `main`、不重置工作分支、不使用 force push 覆盖历史。
4. 合并前先审查 merge base、ahead/behind、冲突文件、CI 与回滚路径。
5. 不把某个分支的 CI 结果、APK 或真机验证结果转移宣称为其他分支的结果。
6. 工作分支完成后由用户决定是否创建 PR 或合并。

当前提交与 CI 快照见 [PROJECT_STATUS.md](PROJECT_STATUS.md)。
