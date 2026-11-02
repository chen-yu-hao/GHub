# GHub 任务看板：使用与实现

本版将 GHub 的默认首页改为所提供的「墨水屏任务看板 · Ocean 5C 竖屏」HTML 界面，连接远端 OpenSSH 和 Codex App Server。Android 最低版本保持为 10（API 29）。源码基于上游提交 [`2686cf23d8c6d6d47086e38477cb27411cc768f4`](https://github.com/iawnix/GHub/commit/2686cf23d8c6d6d47086e38477cb27411cc768f4)，版本号为 `0.2.0-taskboard`。

本次交付包含完整源码、可应用于该上游提交的补丁、离线示例预览和构建工作流。当前环境未完成 Android 编译，交付包中没有 APK；真实执行结果见[验证记录](taskboard-verification.md)。

## 界面与 HTML 对应

首页直接使用 APK 内的 HTML / CSS / SVG，原生层负责连接、状态、审批与导航。保留原参考的高对比边框、状态色、卡片排版、按钮与图标，并使实际视口在 632 × 840 时符合参考尺寸。

| 区域 | 参考尺寸或行为 |
|---|---|
| 整体 | 632 × 840；上下内边距 16、左右 20；区块间距 12 |
| 页头 | y=16，高 63；任务看板、同步、全刷 |
| 筛选行 | y=91，高 44；服务器 / 项目与同步时间 |
| 状态栏 | y=147，高 72；等待确认、执行中、已完成、已失败 |
| 任务区域 | y=231 开始；等待卡、运行计划、完成条目、失败卡 |
| 页脚 | y=761，高 63；页码、统计、上一页 / 下一页 |
| 分页容量 | 等待确认 2、执行中 3、已完成 7、已失败 2 |
| 审批卡 | 圆角 14、边框 2、内边距 12/14；参考内容自然高 204 |

较窄视口调整间距、字号和页脚换行，必要时仅任务区域滚动。看板显示期间隐藏系统栏，边缘滑动可呼出；进入原生管理或详情页面时恢复系统栏。

原始附件中封装的 JetBrains Mono 字体已本地打包并保留 OFL 许可。中文继续按原字体栈使用 Noto Sans SC、PingFang SC、Microsoft YaHei 及系统回退。附件未提供完整中文字体文件，因此不同 Android 系统的字形可能存在差异。

原附件没有提供它引用的独立审批详情页面。会话详情、服务器编辑、设置、文件和终端采用原有原生页面；看板新增筛选与审批确认弹窗沿用参考的边框和按钮样式。浏览器截图差异与 Ocean 5C 真机像素对照尚未执行，不能把 CSS / SVG 检查当成真机一致性结论。

## 第一次连接

1. 打开顶部筛选按钮，进入服务器管理，添加服务器名称、SSH 地址、端口和用户名。
2. 选择私钥、密码或键盘交互认证。私钥支持填写口令，跳板机按连接顺序添加。
3. 填写默认项目的绝对目录；需要远程任务时填写服务器上的 Codex 可执行文件路径。
4. 发起连接，核对显示的 SHA-256 主机指纹。
5. 返回看板，选择服务器和项目。已连接任务服务的任务会进入四种状态页；仅 SSH 已连接时仍可打开终端，以及通过「项目与会话」进入文件管理。

认证沿用原项目的 SSHJ 实现，可连接 OpenSSH 服务端，保留 OpenSSH Ed25519 私钥（包括加密私钥）、PEM RSA、密码、键盘交互、多级跳板和主机密钥确认流程。凭据在 Android 原生层通过 Keystore 加密保存，不传给看板 JavaScript。当前交付没有用真实服务器重新运行这些集成测试。

### OpenSSH 与任务服务

| 连接状态 | 可用功能 |
|---|---|
| 未连接 | 查看已有本地状态、项目与会话入口、管理服务器；审批与远端操作停用 |
| SSH 已认证，Codex 未就绪 | PTY 交互终端、SFTP 文件；显示任务服务尚未就绪 |
| SSH 与 Codex 均可用 | 任务同步、真实计划、会话、新建任务、审批，以及终端和文件 |

终端和 SFTP 现在判断实际 SSH 认证与连接状态，不依赖 Codex 的 `ONLINE` 状态。远端需要启用 SFTP 才能使用文件操作。

要使用远程任务，按上游兼容策略准备 Codex CLI 0.164.0 或更新的 0.x 版本，以同一个 SSH 用户完成模型认证或提供方配置，再运行：

```sh
codex app-server daemon bootstrap
codex app-server daemon version
```

本版保留原有安装引导；不会由看板自动审批或自动安装远端服务。若仅使用 SSH，连接后即使显示 Codex 错误，也可使用现有 SSH 连接。

已知连接边界：当 Codex 不存在触发原有重试阻止标记后，纯 SSH 连接若又丢失或被后台释放，需要点击连接手动重连。现有 Codex 正常连接的恢复策略保留。普通 shell 进程如需跨断线保持，可使用服务器上的 tmux。

## 日常操作

### 真实任务状态

等待确认来自当前有效的原生审批请求；执行中来自活动任务；完成与失败依据真实最新执行状态及结果。草稿、空闲会话和未知状态不会被填成已完成任务。没有提供的耗时、退出码、文件增删数或计划保持未知。

任务与审批按服务器和会话定位，避免不同服务器上相同会话 ID 混在一起。运行计划读取 `turn/plan/updated` 的结构化步骤；没有计划时显示“计划未提供”。旧轮次事件不覆盖当前轮次计划。

选择顶部筛选面板可切换服务器、项目、配色，并进入新建任务、终端、项目与会话、服务器管理或设置。新建任务创建原生会话后进入编辑界面，用户提交内容才开始远端任务。创建失败时保留看板并显示原生错误。

### 同步与分页

点击“同步”刷新在线服务器的未归档任务，看板在可见且 Activity 已启动时每 5 分钟同步一次。流式更新合并至约每秒一次，以减少墨水屏重复绘制。页面 JavaScript 不自行进行网络轮询。

看板列表与原生项目页的搜索、归档筛选分开维护，原生页面的筛选不会改变看板数据源。尚有服务器分页未加载时，统计显示 `+` 和“已载入”，末页可继续加载，避免把部分记录说成全量统计。手动刷新会重新载入第一页；较早记录可再次继续加载。

同步时间只在成功收到列表后更新；跨服务器视图采用各服务器中较早的同步时间。断线时保留已知内容，但停用审批和需要在线服务的操作。

### 审批

卡片上的允许 / 拒绝先打开确认框，显示真实命令或文件以及协议实际提供的工作目录、写入范围、网络目标和理由。确认绑定具体 `hostId + threadId + approvalKey`。提交前原生层再次核对当前请求、连接与提交状态；请求已经替换、断线或提交中时不重复发送。

当前看板只提供本次请求的允许和拒绝，不扩大为会话级授权。不支持的审批类型进入原生详情处理。远端标题、输出和命令以文本节点显示；网页不接收任意执行命令的接口。

### 全刷与配色

“全刷”执行有限的黑白页面重绘并恢复内容；这是通用 WebView 降级实现。代码没有调用未提供的 Ocean 5C 厂商硬件波形 API，清除残影的实际效果需要真机验证。彩色 / 黑白模式以及服务器、项目、页签偏好保存在本机。

## 构建

使用 JDK 21、Android SDK Platform 36、Build Tools 35.0.0、Node.js 22。Gradle Wrapper 随完整源码提供，首次构建需要能访问 Gradle、Google 和 Maven 依赖源。

在交付源码的 `GHub` 目录执行：

```sh
node scripts/taskboard/board.test.cjs
./gradlew -PpreviewApplicationId=dev.remotegpt.taskboard \
  :codex-client:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

输出为 `app/build/outputs/apk/debug/app-debug.apk`，包名 `dev.remotegpt.taskboard`，可与原版共存，应用名称显示“GHub 看板 Preview”。不传 `previewApplicationId` 时沿用原包名，覆盖安装还受签名是否一致影响。

也可以将交付源码提交至自己的 GitHub 仓库，进入 Actions，选择 **Build taskboard APK → Run workflow**。工作流先执行前端检查、JVM 测试、Lint 和编译，成功后生成 `ghub-taskboard-debug-apk` 构建附件。工作流不会自动发布 Release；本次未在远端执行。

若使用补丁而不是完整源码，应从确切的上游提交开始：

```sh
git clone https://github.com/iawnix/GHub.git
cd GHub
git switch -c taskboard-ocean5c 2686cf23d8c6d6d47086e38477cb27411cc768f4
git apply --check ../GHub-taskboard.patch
git apply --index ../GHub-taskboard.patch
```

## 实现入口

| 文件或目录 | 职责 |
|---|---|
| `app/src/main/assets/taskboard/` | 实际打包的看板 HTML、CSS、JavaScript、SVG 和字体 |
| `app/src/main/java/dev/remotegpt/app/ui/TaskBoardScreen.kt` | 本地资源 WebView、生命周期、更新节流和原生桥接 |
| `app/src/main/java/dev/remotegpt/app/board/TaskBoardSnapshot.kt` | 原生任务、审批和计划转换为界面快照 |
| `app/src/main/java/dev/remotegpt/app/board/TaskBoardAction.kt` | 桥接动作白名单与参数限制 |
| `app/src/main/java/dev/remotegpt/app/ui/Workbench.kt` | 看板与原生项目、会话、终端、文件的导航 |
| `app/src/main/java/dev/remotegpt/app/WorkbenchViewModel.kt` | SSH 可用性、看板查询、分页和审批校验 |
| `codex-client/src/main/kotlin/dev/remotegpt/client/Timeline.kt` | 结构化计划事件和历史状态合并 |
| `app/design/taskboard/` | 示例预览、原 HTML 提取内容与设计说明 |
| `scripts/taskboard/board.test.cjs` | 可单独执行的生产前端行为与来源样式检查 |
| `.github/workflows/taskboard-apk.yml` | 手动 APK 构建工作流 |

WebView 只读取 APK 中固定 HTTPS 虚拟源的 `taskboard` 资源，禁止文件 / 内容访问、外部导航、网络回退和任意原生动作。审批从当前原生对象中取请求，页面提供的显示文本不能成为新的 shell 命令。

源码继续采用 GPL-3.0-only；原有终端组件和依赖许可保留，JetBrains Mono 许可已加入应用内许可列表及第三方声明。
