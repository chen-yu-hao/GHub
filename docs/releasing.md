# 发布指南

正式版本通过 [`release.yml`](../.github/workflows/release.yml) 发布。推送 `v*` 标签后，工作流先运行完整检查，再构建签名 APK 并创建 GitHub Release。

## 准备版本

1. 在 `app/build.gradle.kts` 更新 `versionName`，并递增 `versionCode`。
2. 在 `docs/releases/v<版本>.md` 编写更新说明，包括主要变化和安装信息。
3. 按[测试指南](verification.md)检查本次改动；协议变更同时更新[兼容说明](protocol.md)。
4. 提交修改，创建与 `versionName` 一致的标签并推送。

例如，`versionName` 为 `0.1.41` 时：

```sh
git tag -a v0.1.41 -m "GHub v0.1.41"
git push --atomic origin main v0.1.41
```

发布流程检查标签与应用版本是否一致。JVM、Lint、API 29/35/36 设备回归和 API 35 Release 运行测试通过后，才会构建公开附件。

## GitHub 签名配置

仓库需要以下 Actions secrets：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 编码的发布 keystore |
| `ANDROID_STORE_PASSWORD` | keystore 口令 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥口令 |

发布密钥应独立备份并保留，用于后续覆盖升级。密钥和口令保存在仓库 secrets 或本机私有目录中，不纳入版本控制。

GHub 当前发布证书的 SHA-256 指纹：

```text
EC:A8:41:A3:86:BC:9C:82:79:23:AC:A0:72:9B:D2:78:B2:B7:E7:9C:09:BB:8E:2F:B3:03:73:F1:C7:92:F6:20
```

## 本地签名构建

配置以下环境变量后运行 Release 构建：

```sh
export RELEASE_KEYSTORE=/private/path/release.jks
export RELEASE_STORE_PASSWORD='<store-password>'
export RELEASE_KEY_ALIAS='<key-alias>'
export RELEASE_KEY_PASSWORD='<key-password>'
./gradlew :codex-client:test :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。普通 Release 构建在未配置密钥时生成未签名 APK；`-PtestBuildType=release` 的设备测试构建使用测试签名作为后备。

核对签名：

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## 发布附件

| 文件 | 用途 |
|---|---|
| `ghub.apk` | 正式签名的 Android 安装包 |
| `ghub-source.tar.gz` | 对应提交的源码，包含 Termux 修改与许可证 |
| `SHA256SUMS` | APK 与源码包的 SHA-256 校验和 |

工作流先创建草稿 Release，上传附件后公开，并设置为最新版本。R8 mapping 和测试报告保存在对应 Actions 运行的 artifacts 中。

发布完成后，从 Release 下载附件，运行 `sha256sum -c SHA256SUMS`，核对 APK 版本、签名，并在测试设备上检查覆盖升级与启动。公开下载入口为 [GitHub Releases](https://github.com/iawnix/GHub/releases/latest)。
