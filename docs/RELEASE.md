# 构建与发布规范

本文描述发布必须遵守的边界，并记录本规范建立时观察到的仓库工作流现状。实际执行前仍需检查当前分支、workflow 和产物。

## 1. 发布原则

- `main` 代表正式稳定源码；日常开发在 `dev`。
- Release 必须对应明确的源码 commit、版本号和宿主支持范围。
- 不得从未审查的工作分支直接发布。
- 未完成构建、APK 检查和签名验证，不得宣称发布成功。
- 不得将 JKS、密码、私钥或签名材料提交到 Git。
- 正式发布必须由明确授权的发布流程触发；AI 不得擅自创建正式 Release 或覆盖现有标签。

## 2. 现有工作流观察

建立本文时，`.github/workflows/build.yml` 的触发条件为：
- 推送到 `main`；
- 手动 `workflow_dispatch`。

该 workflow 使用 JDK 21、Gradle 9.8.2 和 Android SDK 36，执行 Release 构建，读取仓库 Secrets 中的签名配置，检查 APK ZIP 结构、Manifest、DEX、zipalign 与签名，并创建带有 run number 的预发布 Release。

这只是当前文件内容的静态审查结果，不代表本次提交已运行该工作流，也不代表其 Secrets 已配置或最近一次运行成功。该 workflow 目前不是 PR 验证门禁。

## 3. 发布前检查清单

- [ ] 发布来源 commit 已审查，变更范围符合预期。
- [ ] 版本名、versionCode、包名与发布说明一致。
- [ ] 支持的 DeepSeek 版本、渠道和限制已准确列出。
- [ ] 对应构建变体构建成功。
- [ ] APK 可解压，包含 Manifest 与 DEX。
- [ ] ABI、资源、Manifest 组件与权限符合预期。
- [ ] zipalign 检查通过。
- [ ] 使用预期证书签名，证书 SHA-256 指纹已核对。
- [ ] SHA-256 校验文件与最终 APK 匹配。
- [ ] 安装及必要的宿主/LSPosed 验证完成；未完成项明确披露。
- [ ] Release notes、CHANGELOG、安装和升级说明已更新。
- [ ] 不包含调试密钥、个人数据、宿主 APK 或临时文件。

## 4. 签名与 Secrets

- 签名密钥只存放于受控 Secrets 或安全的本地密钥库。
- PR 验证工作流不得获得发布签名 Secrets。
- 日志不得输出密码、Base64 keystore 内容或私钥。
- 证书指纹应作为发布身份核对依据；不能只依赖文件名或别名。
- 签名身份变更会影响覆盖安装与升级，必须单独说明迁移影响。

## 5. 产物与发布记录

发布记录至少包括：
- Git commit SHA、版本名和 versionCode；
- 构建变体、JDK/Gradle/SDK 版本；
- APK 文件名、大小和 SHA-256；
- 签名证书 SHA-256 指纹；
- 构建、APK 检查、安装和真机验证结果；
- 已知问题、宿主兼容范围和回滚方式。

不得把“workflow 创建 Release”当作“真机验证通过”。
