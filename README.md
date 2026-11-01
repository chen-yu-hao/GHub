<img src="app/design/brand/ghub-icon-512.png" alt="GHub 图标" width="96" height="96" />

# GHub

GHub 是开源 Android 远程 Codex 工作台。通过 SSH 管理 Linux 上的官方 Codex CLI 会话，支持跳板机、审批、文件、图片、终端和语音转文字。

主要代码使用 Kotlin 与 Jetpack Compose。终端复用固定版本的 Termux 组件。没有自研云端，也不需要在手机安装 Linux、Termux 或 Codex。

## 下载

从 [GitHub Releases](https://github.com/iawnix/GHub/releases/latest) 下载 `ghub.apk`，支持 Android 10 及更新版本。每个版本附带 SHA-256 校验和及对应源码。当前版本 `0.1.38`。正式包名为 `dev.remotegpt.app`，与开发预览版 `dev.remotegpt.preview` 可同时安装。

## 连接原理

```text
Android → SSH（可经多级跳板机）→ codex app-server proxy
                                     ↓ WebSocket / Unix socket
                              官方 App Server daemon ← Codex CLI
```

`proxy` 转发的是 WebSocket 字节流，**不能直接向它写 JSONL**。客户端通过 SSH 流适配标准 WebSocket 库，完成 `initialize` / `initialized` 握手。

最低兼容基线是 **Codex CLI / App Server 0.164.0**。客户端不再精确锁死某一个 CLI 版本：0.164.0 及更新的 0.x 版本会先通过版本范围检查，再以 App Server 握手和协议行为为准。客户端不会自动升级、降级、重启或停止远端 Codex。官方 App Server 仍是实验性接口，兼容性记录见 [协议说明](docs/protocol.md)。

## 使用

1. 在 Linux 主机安装兼容版本的官方 Codex，并完成自己的 `codex login` 或模型提供方配置。
2. 运行 `codex app-server daemon bootstrap` 配置官方常驻服务，使用 `codex app-server daemon version` 检查状态。也可在 App 的连接引导中明确选择配置服务。
3. 让电脑端 CLI 使用同一个 daemon。`codex --remote unix://` 可显式连接默认控制 socket；使用其他配置目录或独立 `--no-daemon` 会话时，不能假定可以实时接管。
4. 在 App 添加 SSH 主机：填写用户名、认证方式、可执行文件路径和默认工作目录。如有跳板机，按连接顺序逐级添加。
5. 首次连接核对服务器的 SHA256 主机指纹。配置完成后选择项目，再进入项目的会话列表或新建会话。

SSH 非交互环境可能不包含你的 Node/npm 路径。此时填写 Codex 的绝对路径，例如 `/home/user/.local/bin/codex`。认证错误和服务版本错误会分别显示。

手机只管理连接与界面，任务生命周期由远端服务决定。断网后可重连恢复会话，发送结果不确定时需要核对历史，App 不自动重发。离线期间不提供即时推送。

0.1.7 增加网络切换与回前台连接检查、持续退避重连、流式事件合并及分类诊断。没有可用网络时暂停尝试，恢复后同步原会话；未开启后台保持时，退到后台 30 秒后释放连接和终端，回前台自动恢复连接；需要保留 shell 任务请使用 tmux。即使开启后台保持，也不承诺后台长期保活。实现和验证边界见 [连接恢复](docs/connection-recovery.md)。

0.1.9 将会话缓存迁移为分片记录，并在顶部提供直接的服务器切换面板。旧版本缓存会在迁移后逐步转换，不删除远端会话。

### 功能说明

- 项目按“主机 + 远端工作目录”组织，可手动添加名称和目录，也会从已载入的会话中发现。项目列表和最近打开的项目会保存到本地；这是客户端的目录分组，不需要服务端提供项目 API。
- 首页项目直接展开会话，点击会话进入聊天，不显示常驻导航标签或首页底栏。聊天顶部更多菜单提供项目文件、终端和会话操作，输入区提供模型、思考级别、附件、语音及发送。首页右上角齿轮进入设置，服务器切换由左上角入口负责；从文件或终端返回时回到原会话。
- 使用 Material 3 可折叠大标题、原生列表、浮动操作按钮和底部面板。新建、项目切换、输入选项、终端控制与编辑保存靠近屏幕下方；任务运行时可从输入区停止。主要触控目标至少 48dp，并为系统手势和键盘保留边距。
- 同一次 App 运行期间，各项目分别保留文件浏览位置和终端标签，切换不会主动关闭终端。草稿仍按主机和会话保存。新终端从项目根目录启动；项目目录采用明确的路径匹配，不自动合并 Git 子目录或符号链接。
- 会话历史按页加载；其他项目可能出现在更早的分页中，使用“加载更多项目与会话”继续发现。项目内的空结果也保留继续加载入口。
- 密钥、密码和键盘交互认证；每级跳板机单独配置；私钥及口令使用 Android Keystore 包装密钥加密。
- 会话分页、新建、重命名、归档、恢复、模型及思考级别选择、停止任务。
- 设置支持中文、English 和跟随系统；切换界面语言不翻译会话正文、远端输出或用户填写的名称。
- Markdown 表格完整换行、横向滚动、展开及复制；长代码按需展开并保留原始缩进。
- 会话状态独立于当前页面同步：前台状态核对每轮间隔约 3 秒，后台保持模式约 10 秒；归档视图不会暂停运行状态观察。消息正文按需加载。
- 同一连接内重复打开会话复用订阅与已同步正文；当前会话恢复不等待账号、模型或列表。当前/运行/空闲会话分开调度，断线后重新订阅并校验原会话。诊断可导出各同步阶段的聚合耗时，详见 [连接恢复](docs/connection-recovery.md)。
- 流式消息、命令输出、文件 diff、审批。未知权限请求失败关闭，不能静默授予权限。
- SFTP 浏览、下载和文本编辑；保存前检查内容变化，使用临时文件加原子重命名写入。
- 图片、拍照、附件上传到工作目录的 `.remote-gpt/attachments/`；图片通过远端 `localImage` 发送。不会自动修改 `.gitignore`。
- ANSI / UTF-8 交互终端、控制键、窗口调整；tmux 持久化由远端提供。
- 语音识别结果进入草稿，由用户编辑并发送。识别依赖 Android 系统服务，是否联网由该服务决定。
- 本地缓存与草稿；无遥测；诊断记录不包含凭据和聊天正文。

## 构建

需要 JDK 21、Android SDK platform 36 与 Build Tools 35.0.0。最低支持 Android 10（API 29）。

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

或者在未提交的 `local.properties` 中设置 `sdk.dir`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，可直接安装。

项目模块：

- `app`：Compose 工作台、Room、凭据、系统文件选择和语音。
- `codex-client`：纯 JVM 的 SSH、官方协议、状态合并与 SFTP，便于独立验证。
- `terminal`：Termux 组件及 SSH 会话适配；见 [上游记录](terminal/UPSTREAM.md)。

### 界面预览

Debug 构建包含一个离线的 Compose 组件预览入口，使用示例内容，不连接 SSH，也不发送消息。可在 Android Studio 打开 `DesignPreviewActivity.kt` 查看浅色和深色 Preview。

需要在设备查看时，使用独立包名安装，避免覆盖日常使用的版本：

```sh
./gradlew :app:assembleDebug -PpreviewApplicationId=dev.remotegpt.preview
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.remotegpt.preview/dev.remotegpt.app.DesignPreviewActivity --es theme light --es screen projects
# screen 可选 projects、sessions、chat、workspace、picker；theme 可选 light、dark。
# 切换入口前先关闭预览 Activity。
```

预览复用项目列表、项目会话列表、顶部栏、工作区菜单、消息卡片和输入区；预览 Activity 仅存在于 Debug 构建。省略 `previewApplicationId` 时，构建的包名仍为 `dev.remotegpt.app`。

## 验证

```sh
./gradlew :codex-client:test :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

完整本机协议验证需要已有 **0.164.0 或更新的 0.x daemon**，Python 3 与 OpenSSH。测试创建自己的临时主机密钥、私钥和高端口 SSH 服务，不改写 `~/.ssh` 或系统 sshd：

```sh
python scripts/probe.py                     # 只读检查代理握手
python scripts/probe.py --exercise          # 共享会话、审批重放、断线恢复
python scripts/ssh_fixture.py               # 同样的协议验证，经两级 SSH 跳板
python scripts/ssh_fixture.py --jvm         # Kotlin SSH / SFTP / PTY 集成测试
```

`--exercise` 使用临时本地 Responses 服务作为确定性模型，创建一个专用测试会话，仅批准该会话中的无害 `printf`，完成后归档。不会调用付费模型。若服务启用请求压缩，需要 Python `zstandard` 包。

在一次性 API 29 或 API 35 Android 模拟器验证真实 SSH、审批、文件、图片和终端。此脚本会清空测试 App 数据，需要远端装有 `vim` 和 `tmux`：

```sh
export ADB="$ANDROID_HOME/platform-tools/adb"
export ANDROID_SERIAL=emulator-5556
python scripts/device_check.py
```

集成测试的凭据只存在于临时目录，测试结束后清理。测试运行情况和边界见 [验收记录](docs/verification.md)。

## 发布与签名

```sh
export RELEASE_KEYSTORE=/private/path/release.jks
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=...
export RELEASE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

没有配置发布密钥时 Release 为未签名产物。不要提交密钥或口令。仓库提供 GitHub Actions 验证与基于版本标签的签名发布工作流。详见 [发布说明](docs/releasing.md)。

本项目与 OpenAI 无隶属关系。许可证：GPL-3.0-only；第三方声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
