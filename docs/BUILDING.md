# 构建说明（当前工作分支）

> 适用分支：`work/roi-agent-core-kotlin`。本文描述当前 CI 与构建目录，不代表其他分支或历史 Release 使用相同流程。

## 环境

- JDK 21
- Gradle 9.8.2（Android CI 固定版本）
- Android SDK Platform 37 / Build Tools 37.0.0（CI）
- Android SDK 环境变量：`ANDROID_HOME` 或 `ANDROID_SDK_ROOT`

本地还需按操作系统配置 Android SDK 路径。不要提交本机绝对路径、代理凭据、签名密钥或签名密码。

## Android Open 构建

CI 在 `compose-launcher/` 工作目录执行：

```bash
gradle --no-daemon --stacktrace \
  -PprotectedBuild=false \
  -PlocalApiIncluded=false \
  -PandroidCompileSdk=37 \
  :app:assembleRelease :app:lintRelease
```

CI APK 路径：

```text
compose-launcher/app/build/outputs/apk/release/app-release.apk
```

CI 使用临时生成的测试签名密钥，仅用于验证 APK 结构、zipalign 与签名流程；它不是正式发布密钥，也不代表 Release 身份。

## 多模块 / Hook 回归构建

当前 `.github/workflows/ci.yml` 在 Regression job 中执行以下类型的检查：

- Kotlin Runtime JVM tests；
- 包名身份、Legacy adapter、Expert Relay、语言目录、进程准入、宿主映射、Hook 安装注册表与 Local API 网络回归；
- Legacy Xposed 构建；
- Universal Xposed 构建及 Google Play 变体；
- V271、thinking 与 Universal feature parity 回归；
- Open Android Release 构建、Lint、APK ZIP / Manifest / DEX / zipalign / CI 签名检查。

本地执行前先阅读 workflow 与各脚本，确保 SDK 版本、环境变量和构建变体一致。常用入口：

```bash
bash scripts/gradle-bootstrap.sh
bash module-legacy/build.sh
bash module-universal/build.sh
bash module/test-language-catalog.sh
bash module/test-local-api-network-regression.sh
```

仓库还保留 `scripts/build-all.sh` 和 `scripts/build-all-legacy.sh` 等历史/辅助脚本；运行前应核对其输出路径、版本号和签名行为，不要仅凭脚本名称判断产物用途。

## 工作流与发布边界

- `.github/workflows/ci.yml`：对 `dev` 与 `work/**` 推送，以及目标为 `dev` 的 PR 执行质量门禁；只使用 `contents: read` 权限和临时 CI 签名密钥。
- 正式签名与 Release 流程由独立 workflow 管理。不得为普通分支回归借用正式签名 Secrets。
- CI 上传的 APK 是构建验证产物，不应自动视为面向用户的正式发布包。

## 结果报告

每次交付至少记录提交 SHA、Workflow Run、Job 结果、JDK / Gradle / SDK、构建变体、APK 路径与校验结果。按 [TESTING.md](TESTING.md) 区分 L1 回归、L2 构建、L3 APK 检查、L4 模拟环境和 L5 真机验证。

**重要：** Open Local API 的 HTTP 服务层已实现，但 DeepSeek 原生 Completion Adapter 尚未完成。成功构建 APK 不代表本地 API 已能执行 DeepSeek 模型调用。