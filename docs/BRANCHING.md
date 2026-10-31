# 分支说明

- `main`：正式稳定分支，只接受通过审核的 release PR。
- `dev`：集成分支，只接受通过审核的 feature/fix/docs/test/build PR。
- 所有工作必须在从最新 `dev` 创建的独立工作分支完成。
- 分支前缀：`feat/`、`fix/`、`refactor/`、`docs/`、`test/`、`build/`、`release/`。
- 一个 PR 只处理一个主要目标；有依赖关系的 PR 必须标注合并顺序。
- 禁止直接推送 `main` 或 `dev`，禁止未经仓库所有者批准合并，禁止 force-push 共享分支。
- 完整流程见 [开发与发布规范](DEVELOPMENT_AND_RELEASE_WORKFLOW.md)。
