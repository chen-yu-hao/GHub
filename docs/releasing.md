# 发布

1. 运行 JVM、Lint、Android 设备测试并更新兼容说明。
2. 更新 `app/build.gradle.kts` 的版本号。
3. 准备长期保存的 Android 发布密钥，安全备份；后续更新必须使用相同签名。
4. 使用 README 中的四个环境变量构建签名 Release APK。
5. 用 SDK 的 `apksigner verify --verbose --print-certs` 核对 APK。

GitHub 发布工作流由 `v*` 标签触发，需要这些仓库 secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

工作流核对标签与 `versionName`、运行 JVM 单元测试和 Release Lint、构建并验证 APK 签名，然后发布 `ghub.apk`、`SHA256SUMS` 校验和及 `ghub-source.tar.gz` 源码归档。校验和覆盖 APK 和源码包。源码包含 Termux 适配与许可证。不要将私钥、签名口令或用户的主机配置放入附件。

公开仓库：[iawnix/GHub](https://github.com/iawnix/GHub)。下载入口：[GitHub Releases](https://github.com/iawnix/GHub/releases/latest)。发布说明放在 `docs/releases/v<版本>.md`；没有对应文件时使用 GitHub 自动生成的说明。工作流先上传草稿附件，上传完成后公开发布。

## 本次本地构建

交付目录为 `dist/`，包含签名 APK、SHA256、签名验证结果、源码归档和验收日志。APK 使用本机生成并保留的发布密钥，后续更新应继续使用该密钥。

本机密钥目录：`~/.local/share/remote-gpt/signing/`。目录和文件只允许当前用户访问，不在 Git 仓库或源码包内。GitHub Actions 使用同一密钥的仓库 secrets，后续发布必须保留此签名，维护者应另行安全备份。

发布证书 SHA-256 指纹：`EC:A8:41:A3:86:BC:9C:82:79:23:AC:A0:72:9B:D2:78:B2:B7:E7:9C:09:BB:8E:2F:B3:03:73:F1:C7:92:F6:20`。

Debug 与 Release 使用不同签名，不能互相覆盖安装同一包名；测试模拟器使用 Debug，首次安装 Release 时需卸载 Debug。卸载会清除本机配置和草稿，远端任务不受影响。

从 0.1.35 起，发布先复用完整检查工作流（JVM、Lint、API 29/35/36 常规设备测试、API 35 精简 Release 的真实 SSH 测试），全部通过才构建并公开附件。R8 mapping 随 CI 验证报告保留，用于还原混淆堆栈。

本地精简 Release 冒烟测试：在可丢弃的模拟器上设置 `ANDROID_SERIAL` 后运行 `python3 scripts/release_device_check.py`。脚本创建独立 OpenSSH 服务及临时密钥；使用 `-PtestBuildType=release` 选择专用测试源码。如果未设置正式签名环境变量，此测试构建使用 Debug 密钥但保持 `debuggable=false`、R8 与资源裁剪开启。不要将测试签名包当作正式发布附件。
