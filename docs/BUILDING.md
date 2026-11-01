# 从源码构建（YunSeek 1.8.1）

本说明对应当前 `dev` 分支的 `compose-launcher` 构建工程。默认构建为 Open 版，不包含 Closed 专属载荷、Local API 或受保护构建内容。上游历史构建说明存档于 [upstream/BUILDING_upstream.md](upstream/BUILDING_upstream.md)。

## 环境要求

- JDK 21（AGP 9.3.0 / Kotlin Compose Compiler 2.4.10）
- Gradle 9.8.2；一键脚本会在本机没有匹配版本时下载并校验官方 SHA-256
- Android SDK；默认 compile SDK 由 `app/build.gradle.kts` 中的 `androidCompileSdk` 属性决定（默认 37）
- Android Build Tools，需包含 `zipalign` 和 `apksigner`
- `ANDROID_SDK_ROOT` 或 `ANDROID_HOME` 指向 SDK
- Open 一键脚本还需要 `curl`、`unzip`、`zip` 和 `sha256sum`

## 推荐构建方式

在仓库根目录执行：

```bash
bash scripts/build-all.sh
```

脚本行为：

1. 使用已安装的 Gradle 9.8.2，或下载固定版本的官方 binary distribution。
2. 校验 Gradle 9.8.2 官方 SHA-256：`21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`。
3. 执行 Open Debug 构建（`protectedBuild=false`、`localApiIncluded=false`）。
4. 移除临时 Debug APK 签名并重新 zipalign。
5. 检查 APK 存在、对齐且未签名，并生成 SHA-256。

输出：

- `out/YunSeek-1.8.1-unsigned-aligned.apk`
- `out/SHA256.txt`

这是**未签名 APK**，不能直接作为正式 Release 发布。请使用自己的 JKS 在本地签名，并通过 `apksigner verify --verbose --print-certs` 检查签名。签名密钥、密码和签名产物不得提交到仓库。

## 手动构建

```bash
cd compose-launcher
gradle --version
gradle --no-daemon --stacktrace \
  -PprotectedBuild=false \
  -PlocalApiIncluded=false \
  :app:assembleDebug
```

原始 APK 输出在 `compose-launcher/app/build/outputs/apk/debug/app-debug.apk`。手动构建不自动移除 Debug 签名，也不自动执行 zipalign。

## CI 与正式签名构建

- `.github/workflows/ci.yml`：目标为 `dev` 的质量门禁，不读取正式签名 Secrets，只使用临时 CI 签名。
- `.github/workflows/build.yml`：仅用于 `main` 的正式签名构建和预发布 Release。不得把此工作流用于普通 PR 验证。
- CI 显式选择已安装的 Android SDK Platform 36；本地可使用默认 compile SDK 37，或通过 `-PandroidCompileSdk=<已安装平台版本>` 覆盖。

## 环境与可移植性说明

- `compose-launcher/gradle.properties` 不再包含 Termux 设备绝对路径。Termux、Linux、macOS 和 Windows 应分别使用自己的 Android SDK 配置。
- `scripts/gradle-open-debug.sh` 不依赖开发机的 `/workspace/work` 路径，也不强制加载私有 `tools/env.sh`。
- 代理由当前 shell / Gradle 用户配置提供；不要把代理账号或密码写入仓库。
- 旧版多模块流程仍保留在 `scripts/build-all-legacy.sh`，其产物版本和适用范围应以脚本自身校验为准。
