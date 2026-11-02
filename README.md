<img src="app/design/brand/ghub-icon-512.png" alt="GHub" width="80" height="80" />

# GHub 任务看板

面向墨水屏的 Android 远程任务看板，基于 GHub 和提供的「Ocean 5C 竖屏」HTML 改造。

首页采用随 APK 打包的 HTML / CSS / SVG，通过原生桥接读取真实远端任务，提供等待确认、执行中、已完成和已失败四个状态页。OpenSSH 连接、凭据管理、SFTP、PTY 终端以及会话详情沿用 Kotlin / Compose 实现。仅连接 OpenSSH 时可使用文件和终端；远程任务、计划和审批由 Codex App Server 提供。

[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green)](docs/getting-started.md)

[看板使用与实现说明](docs/taskboard.md) · [APK 构建与下载](https://github.com/chen-yu-hao/GHub/actions/workflows/taskboard-apk.yml) · [离线 UI 对照](app/design/taskboard/preview.html) · [测试记录](docs/taskboard-verification.md) · [原项目](https://github.com/iawnix/GHub)

本次改造维护在 [chen-yu-hao/GHub 的 main 分支](https://github.com/chen-yu-hao/GHub/tree/main)，基于上游提交 `2686cf23d8c6d6d47086e38477cb27411cc768f4`，版本为 `0.2.0-taskboard`。[源码 `96c07a2`](https://github.com/chen-yu-hao/GHub/commit/96c07a207a505e20c6d7cf50cc2f33d4a5218cdc) 的 [APK 构建 41418505659](https://github.com/chen-yu-hao/GHub/actions/runs/41418505659) 和 [完整 CI 41418462024](https://github.com/chen-yu-hao/GHub/actions/runs/41418462024) 均已成功：前端、JVM、Lint、APK 编译、API 29 / 35 / 36 设备测试及 Release 设备流程全部完成。设备测试每档 34 项通过、4 项 fixture 检查跳过，Release 综合用例 1 项通过、0 跳过；跳过项目与完整范围见[验证记录](docs/taskboard-verification.md)。

可下载安装独立包名 `dev.remotegpt.taskboard` 的 **Debug 预览 APK**，与原版共存。下载包采用调试签名；已核对其中 6 项看板资源与源码字节一致。文件校验值见[验证记录](docs/taskboard-verification.md)。Ocean 5C 真机像素、中文字体和硬件全刷仍需实机确认；上游 Releases 的 APK 不包含本次改造。

## 功能

- **HTML 看板首页**：以 632 × 840 为对照画布，保留附件的布局、卡片、图形和状态色；四个状态页每页分别为 2 / 3 / 7 / 2 项。
- **真实任务状态**：跨服务器和项目筛选、分页、继续加载、手动同步、可见期间每 5 分钟同步；真实审批、结构化计划、结果和失败信息。
- **墨水屏交互**：高对比边框、彩色 / 黑白切换、按秒合并流式界面更新、有限黑白页面重绘；硬件波形刷新待 Ocean 5C 真机接入验证。
- **独立 SSH 可用性**：任务服务未就绪时，已经建立的 OpenSSH 连接仍可用于文件与交互终端。
- **会话工作台**：按服务器和项目组织会话，支持新建、归档、历史记录、模型与思考级别选择。
- **任务与审批**：接收流式回复，查看执行过程、命令输出和文件改动，在手机上处理命令与文件审批。
- **代码阅读**：Markdown、语法高亮、Diff、自动换行和全屏阅读，命令与输出可分别复制。
- **远程文件**：通过 SFTP 浏览、上传、下载、预览图片和编辑文本，保存时检查远端修改。
- **交互终端**：支持 Vim、tmux、多个终端、组合键、双指缩放和横屏输入。
- **连接与输入**：多级 SSH 跳板、密钥与密码认证、断线重连、本地草稿、图片附件和系统语音输入。
- **原生管理界面**：Kotlin、Jetpack Compose、Material 3；服务器、设置与会话详情保留中文、English 和深浅色主题，看板按提供的中文浅色设计实现。

## 界面预览

用浏览器打开 [app/design/taskboard/preview.html](app/design/taskboard/preview.html)。预览引用生产 CSS、SVG 与 JavaScript，显式使用示例数据，不连接服务器、不执行命令。原 HTML 提取内容和尺寸对照见[设计说明](app/design/taskboard/README.md)。尚未完成 Ocean 5C 真机与参考 HTML 的像素、字形和硬件全刷验证。

## 快速开始

需要 Android 10 或更新版本和可通过 OpenSSH 访问的 Linux 主机。远程任务功能按上游兼容策略要求 Codex CLI **0.164.0 或更新的 0.x 版本**；SFTP 文件和 PTY 终端不要求任务服务成功启动。

1. 打开 [成功的 APK 构建运行 41418505659](https://github.com/chen-yu-hao/GHub/actions/runs/41418505659)，在 **Artifacts** 下载 `ghub-taskboard-debug-apk`，解压并安装 `app-debug.apk`。这是源码 `96c07a2`、独立包名 `dev.remotegpt.taskboard` 的 Debug 预览版，可与原版共存。
2. 要使用远程任务，在开发机完成 Codex 登录或模型提供方配置，启动 App Server：

   ```sh
   codex app-server daemon bootstrap
   codex app-server daemon version
   ```

3. 打开看板顶部筛选面板中的服务器管理，添加 SSH 地址、端口、用户和认证信息，核对主机指纹后连接。
4. 从看板筛选服务器和项目，查看任务、确认审批或进入「项目与会话」创建任务。纯 SSH 场景可选择服务器后打开终端；文件操作从原生项目菜单进入。

电脑端使用 `codex --remote unix://` 连接同一用户的默认 daemon，即可与手机接续同一会话。多级跳板、Codex 路径和连接排障见[使用指南](docs/getting-started.md)。

## 工作方式

```mermaid
flowchart LR
    app["GHub · Android"] --> ssh["SSH"]
    ssh --> proxy["codex app-server proxy"]
    proxy --> daemon["App Server daemon"]
    cli["Codex CLI"] --> daemon
    ssh --> files["SFTP 文件操作"]
    ssh --> terminal["PTY 交互终端"]
```

任务在开发机上运行，手机负责交互和状态同步。SSH 凭据在本机通过 Android Keystore 加密保存；诊断由用户主动导出。原有连接恢复与后台行为见[连接与同步](docs/connection-recovery.md)。纯 SSH 模式在 Codex 不存在且连接随后丢失或被后台释放时需要手动重连，详见[看板说明](docs/taskboard.md)。

## 从源码构建

准备 JDK 21、Android SDK Platform 36、Build Tools 35.0.0 和 Node.js 22。克隆本次改造的仓库，在 Android Studio 打开项目，或运行：

```sh
git clone --branch main https://github.com/chen-yu-hao/GHub.git
cd GHub
node scripts/taskboard/board.test.cjs
./gradlew -PpreviewApplicationId=dev.remotegpt.taskboard \
  :codex-client:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。仓库已提供并成功运行 [Build taskboard APK](https://github.com/chen-yu-hao/GHub/actions/workflows/taskboard-apk.yml) 工作流；有权限时可在 Actions 中选择 **Run workflow** 构建选定分支。下载前核对运行的提交与结论，不要把构建成功等同于设备验收通过。更多环境配置见[开发指南](docs/development.md)。

## 参与贡献

欢迎通过 [Issues](https://github.com/iawnix/GHub/issues) 反馈问题或讨论功能，也欢迎改进代码、文档和翻译。提交前请阅读[贡献指南](CONTRIBUTING.md)。

## 许可证

GHub 使用 [GPL-3.0-only](LICENSE) 许可证。终端组件基于 [Termux](https://github.com/termux/termux-app)，完整依赖声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

GHub 是社区项目，与 OpenAI 无隶属关系。
