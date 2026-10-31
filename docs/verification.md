# 验收记录

验证日期：2026-10-28。首个实现版本：0.1.0。此记录区分已执行的自动验证和仍需真实设备覆盖的项目，不把构建成功等同于全部验收通过。下方真实 daemon 的历史结果基于 0.163.0；当前代码兼容基线已升级到 0.164.0，尚未重新执行同等回归。

## 环境

- Linux 远端（历史实测）：官方 Codex CLI / App Server daemon **0.163.0**。
- 当前最低兼容基线：官方 Codex CLI / App Server daemon **0.164.0**；后续 0.x 版本按兼容范围接入，真实 daemon 回归仍需重新执行。
- JDK 21、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.20；compile/target SDK 36，min SDK 29。
- Android Emulator：API 29 和 API 35，x86_64。当前 Fedora 主机的 SwiftShader 渲染在启动期间崩溃；使用 Emulator 37.2.12 的 `-gpu lavapipe` 正常启动。此问题发生在安装 App 前。
- SSH 测试使用临时 OpenSSH 服务和一次性密钥，不修改系统 sshd 或用户 `~/.ssh`。
- 会话测试使用本地确定性 Responses 测试服务；不调用付费模型，不接管、批准或中断用户已有任务。

## 已通过的验证

| 验证 | 证据与覆盖范围 |
|---|---|
| 协议单元测试 | 14 项：握手、并发响应匹配、发送不确定性、服务端拒绝、过期审批、跨客户端 resolved、未知请求失败关闭、断线释放、历史与增量合并、历史分页、未知条目、shell 参数引用 |
| JVM / OpenSSH 集成 | 6 项：两级跳板代理与重连、SFTP 上传下载和冲突、PTY 调整、拒绝错误指纹、加密 OpenSSH Ed25519 / PEM RSA、错误私钥口令拒绝 |
| 官方 daemon 真实审批 | 独立客户端观察同一持久化测试会话；收到相同审批 ID；全部断线后重新订阅；收到原审批并批准；服务端 resolved 和任务完成；测试会话归档。已验证直接代理及两级 SSH 跳板 |
| Android 完整设备测试 | **API 29 / API 35 均 3 项通过**：加密 Ed25519、两级跳板、官方 proxy 握手、空状态 / 主机配置、审批重放和手机批准、完成同步、中文 SFTP 编辑、UTF-8 终端、vim 全屏、tmux 跨 PTY 恢复、Activity 重建保留状态、图片上传及实际发送 |
| Android 构建 | 最终 Debug、签名 Release、instrumentation APK 和两种构建的 Lint 均通过；APK v2 签名校验通过 |
| Android Lint | 无错误；保留版本更新建议、KTX 风格提示及第三方 BC 包中的未使用 TLS 类警告；未添加忽略错误的 baseline |

设备流程的图片发送通过实际界面按钮触发，确认草稿清空及服务端用户消息中出现图片。终端输出断言使用拆分的 printf 参数，避免把命令回显误当成执行结果。Activity 重建覆盖配置变化生命周期，但不等同于系统杀进程。

已修复测试暴露的终端字体初始化顺序问题，以及底部提示遮挡发送 / 控制按钮的问题。操作反馈现位于顶部。

## 实现边界

- 代码兼容策略已改为 0.164.0 起的 0.x 版本范围；在重新完成真实 daemon 回归前，不应把旧版验证记录视为新版本的验证证据。客户端检查 CLI、daemon 和握手返回的语义版本，不自动更换远端版本。
- 实时接续要求同一用户、同一配置环境、同一个 daemon。独立 CLI 进程的历史可见不等于运行中任务可接管。
- 仅支持标准命令执行与文件改动审批；未知交互请求返回错误，不授予权限。持久化执行规则和网络规则编辑不在第一版 UI 内。
- SFTP 文本编辑限 1 MiB、UTF-8；图片预览限 20 MiB。保存采用内容校验和临时文件原子替换。SFTP 没有“比较并交换”操作，校验之后仍可能发生极短的并发修改窗口；也不保证保留文件 inode、硬链接关系或扩展属性。
- 服务器必须支持 SFTP 原子重命名。测试使用 OpenSSH。
- 前台断线后有限次数退避重连；认证、主机身份和版本问题需要处理。后台不保证长连接；重开 App 会恢复已连接主机和最近会话。未确认消息不会自动重发。
- 终端重连新建独立 PTY；使用远端 tmux 才能保留 shell 工作。终端初次布局前的输出缓存和等待 UI 渲染的数据均有限制。
- 设备语音识别优先本地服务；无法保证每台设备都有本地模型或识别服务。结果进入草稿，不自动发送。
- 0.1.0 首轮验收时尚未配置 GitHub remote，产物只在本地准备。当前公开发布入口见 [发布说明](releasing.md)。

## 尚需补充的验收

- ARM64 真机、不同厂商系统、真实中文输入法组合输入、TalkBack 逐项操作、大字体与横竖屏的人工体验检查。
- 系统相机、相册提供方、麦克风及离线语音模型的真实设备验证。
- 真实密码 / 多因素键盘交互 SSH 服务器；自动测试已覆盖私钥认证，尚未搭建密码认证服务。
- Wi-Fi / 蜂窝切换、系统直接杀进程、远端 daemon 重启；目前已验证主动断开 / 重连和协议层响应丢失处理，不能替代这些故障场景。
- 长时间大输出、超长历史、上传中途网络故障、真实网络丢包压力测试。

## 重现

```sh
./gradlew :codex-client:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python scripts/ssh_fixture.py --jvm
python scripts/ssh_fixture.py
ADB="$ANDROID_HOME/platform-tools/adb" ANDROID_SERIAL=emulator-5556 python scripts/device_check.py
```

`device_check.py` 仅允许一次性模拟器，会清空其中 `dev.remotegpt.app` 的数据。完整设备流程需要远端测试机有 `sshd`、`tmux`、`vim`、兼容 Codex daemon；Python 的 `zstandard` 用于解码压缩请求。测试结束后清除一次性私钥并归档测试会话。

CI 的设备任务仅运行不依赖本机 daemon 的 UI 测试；真实 SSH / daemon 测试必须按上述命令单独运行。报告中跳过的测试不能算通过。

## 0.1.21 增量验证（2026-10-29）

- `:app:assembleDebug :app:lintDebug` 通过；Lint 为 0 errors、17 warnings，未增加忽略错误的 baseline。APK 为 `dev.remotegpt.preview`、0.1.21（22）。
- 395 条中英文资源键及格式参数一致，主源码字符串引用完整；应用语言包含中文、English、跟随系统。
- API 35 模拟器：实际 MainActivity 启动、设置切换英文到中文、重建后导航、服务器列表与新建连接编辑器正常；系统输入法显示时，靠下的私钥口令字段可见，保存操作位于键盘上方。未导入或保存凭据、未发起 SSH 连接。
- API 35 实际 Markdown 组件离线示例：中文/英文长单元格完整换行，宽表横向滑动可看到最右列，展开表格可返回；正文的强调、删除线、圆角行内代码、嵌套列表、引用、代码原始缩进及引用式链接显示正常。示例数据不等于真实会话端到端验证。
- 官方 0.165.0 daemon 只读握手与 model/list 返回 9 个模型及实际支持的思考级别；本次没有发送模型任务或改变已有远端会话。
- 尚未验证：ARM 真机、各厂商输入法、API 29 上语言持久化、实际 SSH 中选择模型/级别后发送、长时间流式布局和不同字体倍率。公式排版、任意 HTML 和复杂嵌入内容不在本轮支持承诺中。
- 0.1.21 将历史版本的顶部操作提示统一改到底部（输入/操作栏上方）；上文旧版“提示位于顶部”的描述仅代表历史状态。

产物：`dist/remote-gpt-0.1.21-preview.apk`，APK v2 签名校验通过。界面检查截图位于 `dist/ui-0.1.21/`。SHA-256：`dc9a5a18dab5b8b97dcf990e748bca79c2ee5f50319b5de97b89f1468b8c9a23`。

## 0.1.22 增量验证（2026-10-29）

- `:app:assembleDebug :app:lintDebug` 通过，Lint 0 errors、20 warnings；401 条中英文资源键一致。
- API 35 模拟器的实际状态组件离线预览覆盖运行、完成、失败、中断、审批、未知及命令条目；手动切换运行/完成/失败正常。不同帧的运行进度圈发生变化；关闭系统 animator duration scale 后显示静态图标与文字，页面无 AndroidRuntime 异常。
- 检查仅覆盖本轮组件与离线状态切换；未执行真实远端任务、多客户端状态竞争、ARM 真机或 TalkBack 人工验收。
- 产物 `dist/remote-gpt-0.1.22-preview.apk`（versionCode 23），v2 签名通过；截图在 `dist/ui-0.1.22/`。SHA-256：`d7f9be399708e07ff44ae400343f1d967f61d7efb9845798f01bfd2c76bd52bb`。

## 0.1.23 增量验证（2026-10-29）

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :codex-client:test --tests dev.remotegpt.client.ProtocolTest` 通过；协议回归 14 项通过。
- API 35 模拟器通过一次性加密 Ed25519 密钥和独立 OpenSSH 服务连接官方 0.165.0 daemon。独立 SSH 客户端创建并启动隔离会话，使用本地延迟 Responses fixture（无模型费用、无工具执行）。手机始终不选择该会话、停留归档列表，观察到 inProgress 和 completed；归档列表未混入该会话，手机没有下载其消息正文，完成结果保留未读。返回正常列表后结果仍存在。
- 同一个设备测试还覆盖 ViewModel 的后台保持开关与 onBackground/onForeground 同步路径；不等同于实际系统长时间后台保活或被杀进程恢复验收。
- 只读真实协议检查确认 `itemsView=notLoaded` 返回有效状态和空 items。所有新建验证会话在验证后归档；未操作已有用户会话。
- 重现：使用一次性模拟器，设置 `ADB` 与 `ANDROID_SERIAL` 后运行 `python scripts/status_device_check.py`。脚本明确限制 emulator 设备并清空其 `dev.remotegpt.preview` 测试数据。
- 尚需真机覆盖：网络切换、系统回收、大量已加载会话的耗时及多级跳板下的状态延迟。

产物：`dist/remote-gpt-0.1.23-preview.apk`（versionCode 24），APK v2 签名验证通过；Lint 0 errors、20 warnings。SHA-256：`f708e32f66bab55c686fb8e10b6c8cfa1c5f19d9fa1fe0a08c096b40677b07c9`。

## 0.1.24 增量验证（2026-10-29）

- `MarkdownStreamingDeviceTest` 在 API 35 模拟器使用真实 Compose Markdown 渲染器，对比相同 40 个固定段落、10 次尾部文本更新：原全量节点路径累计重绘 410 个段落，块复用与稳定渲染环境后为 10 个。该结果是段落组合次数，不是帧率、触摸延迟或整机功耗数据。
- JVM 回归覆盖：80 个固定段落的 40 次流式更新保持块身份；表格、代码围栏、嵌套列表、引用与中英文/emoji 的每个输入前缀维持正确 AST 局部偏移；晚到的引用定义及全文替换不会使用旧链接；长 Markdown 经 UTF-8 缓存分块完整往返、旧缓存兼容、缺失尾块被拒绝。
- 调度测试覆盖：大量空闲会话分批检查、运行/当前会话优先、新 CLI 会话优先于旧积压、空闲会话仍定期复查，以及未持久化会话的短重试与连续失败退避。
- API 35 真 SSH + 官方 0.165.0 daemon + 本地延迟 Responses fixture 的 `SessionStatusDeviceTest` 通过：不打开目标会话，在归档视图中观察运行→完成、未读结果与空正文缓存，返回普通列表后结果保留。包含 ViewModel 的后台保持分支，不代表 Android 系统长期后台生存测试。
- 新增 8 项 JVM 测试及既有 14 项 ProtocolTest 通过。静态示例截图见 `dist/ui-0.1.24/markdown.png`；表格、圆角行内代码、嵌套列表、代码缩进及引用链接可见。静态截图不替代真实网络流式性能测试。
- 尚未测量实体手机的掉帧率、超长单段/超大表格的滚动延迟、弱网功耗或长时间锁屏存活；不能据此声称达到 Telegram 的同步与帧率水平。

产物：`dist/remote-gpt-0.1.24-preview.apk`（versionCode 25）。最终构建与 Lint 通过（0 errors、20 warnings），APK v2 签名验证通过。SHA-256：`210a73e6d1a19d80f45dacab25572eec48ba5af3957ee470e5131262cca43ad8`。

## 0.1.25：测试数据隔离与历史残留清理（2026-10-29）

- 已通过官方 `thread/delete` 清理 30 条确认由本项目生成的归档测试记录：24 条使用明确的 fixture provider，6 条为限定名称前缀、零 token、无用户消息的空会话探针。清理不按 `/tmp` 路径单独判定，不直接修改运行中 daemon 的数据库。
- 删除前备份元数据及已有 rollout；执行前重新核对记录、文件哈希和 loaded 状态。备份路径为 `/home/iaw/.local/share/remote-gpt/test-cleanup/20261029T192342Z/`，不随 APK 或仓库发布。
- `scripts/cleanup_test_threads.py` 默认仅审计，`--apply` 才会执行限定范围清理；默认用户环境复查匹配记录为零。
- `scripts/codex_fixture.py` 提供独立的 Codex 数据目录、配置、控制 socket/daemon 与绝对路径 CLI 包装器。测试不复制用户认证，默认模型指向不可用的本地地址，测试请求显式选择 localhost 模型 fixture；未限定到独立环境的 `probe.py --exercise` 会拒绝运行。
- 官方删除接口已在独立空环境中验证；独立环境的双客户端审批广播、断线后审批接续与完成探针通过，退出后临时状态目录已移除，日常 daemon 无新增 `/tmp` 会话。

- API 35 在新隔离环境中的真实 SSH / 官方 daemon 状态同步测试通过；测试后日常环境 `/tmp` 会话数仍为 0，临时 fixture 包装器无残留。另验证测试主体抛异常时仍能停止并移除私有 daemon 状态目录；stop 失败则保留私有目录供诊断。
- API 35 实际点击项目菜单“从首页移除”，重建 ViewModel 后仍隐藏，Room 会话与草稿保留，其他服务器的隐藏集合不受影响，再次添加项目可恢复。该测试不连接真实服务器，也不创建远端会话。
- 4 项 Python 清理选择器/隔离防护测试通过。Debug 构建与 Lint 通过（0 errors、20 warnings），APK v2 签名验证通过。

产物：`dist/remote-gpt-0.1.25-preview.apk`（versionCode 26）。SHA-256：`a069b77f826f07c5049b7ce50ee9b3798bca7829205e9fbf6a59bcd2ca243fe7`。
## 0.1.26：聊天阅读布局（2026-10-29）

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 通过。Lint 为 0 errors、25 warnings；未通过忽略错误或新增 baseline 绕过检查。
- API 35 模拟器通过 4 项设备测试：文件摘要默认前三项、展开文件及完整路径、展开剩余文件、仅复制回答；输入框模型与思考级别、发送、上传限制和停止；长回复通过文件分享完整保留中文、Markdown 与 emoji；流式段落复用回归。
- 流式测试中，40 个固定段落、10 次尾部更新的段落组合次数由旧路径 410 次降为 10 次。这是组合计数，不是帧率或真机延迟测量。
- 使用实际组件的离线预览检查中文浅色、深色、键盘展开；输入框、发送、模型和思考级别可见。320dp 宽度、字体倍率 2.0 时正文换行、文件增删量分行显示，截断文件名可通过展开读取完整路径。截图位于 `dist/ui-0.1.26/`。
- 视觉检查使用离线样本，未创建远端会话；未重新验证真实 SSH、多客户端状态、ARM 真机、TalkBack 或厂商输入法。离线预览的键盘检查不替代真实会话的滚动与网络端到端验收。
- 证据保存在 `dist/verification/0.1.26/`，包括构建、设备测试、Lint 和签名校验结果。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.26-preview.apk`，包名 `dev.remotegpt.preview`，版本 0.1.26（27），APK v2 签名校验通过。SHA-256：`e618e19b78629f3d63c73e04fd9edd0e43b604f37603c83e202c022f13503610`。


## 0.1.27：订阅复用与同步优先级（2026-10-29）

- 最终 `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 通过，Lint 0 errors、25 warnings。JVM 回归共 29 项通过（ProtocolTest 15、SessionSyncTest 6、应用测试 8），无失败或跳过。
- 新增协调器回归覆盖并发打开合并、页面切换复用、历史读取期间收到完成失效、失败/取消/空会话 fallback 可重试、订阅过程中收到关闭事件、旧分页不清除最新校验、不同会话不互相等待、清除缓存保留订阅但要求重读。诊断回调失败不改变 RPC 请求结果，回调不接触请求参数与响应正文。
- API 35 模拟器经真实 SSH 连接独立官方 0.165.0 daemon，与第二个客户端及本地延迟 Responses fixture 联测：未打开会话在归档视图中发现运行/完成和未读；后台保持分支继续观察；首次打开后连续三次重新打开，不增加 resume 和完整历史读取次数；手动同步仍读历史但不重复订阅；另一客户端开启第二轮时手机在项目列表继续接收事件；重新建立 SSH/RPC 后重新订阅原会话，两条用户消息和两条回答均保留且无重复。
- 最终同步设备测试用时 50.535 秒。该次本地 fixture 中，另一客户端启动到手机状态更新观测约 60ms；这是单次本地模拟器结果，不是公网、真机或 iOS 对比基准。两个连接只累计 2 次 `thread/resume`、4 次最新完整历史修复；6 次打开流程包括首次打开、三次重复打开、手动同步及第二轮结束后打开。计时均为应用端本地阶段耗时。
- 审批设备回归通过：断线后旧审批清除，重连收到同一请求的新 epoch，通过 UI 允许后任务完成。该现有测试同时通过 SFTP 文本编辑、vim/tmux、Activity 重建、保留草稿及图片上传发送流程。所有变更会话位于独立 daemon 数据目录，未操作日常 daemon；测试结束停止并清理独立环境。
- `scripts/device_check.py` 从构建元数据读取实际包名，支持通过 `DEVICE_TEST_CLASS` 选择回归；设备测试仍仅允许一次性 emulator 并明确重置其应用数据。
- 未完成：ARM 真机、公网/弱网/多主机大量会话负载、系统杀进程与长期锁屏、iOS 同条件性能对比。状态到 Compose 提交诊断不代表实际呈现或 Markdown 解析完成；不据此声称达到某一帧率。未订阅会话仍保留定时兜底检查，没有增加全局实时订阅承诺。

复现同步回归：设置 `ADB` 和 `ANDROID_SERIAL` 后执行 `python3 scripts/status_device_check.py`。审批回归执行 `DEVICE_TEST_CLASS='dev.remotegpt.app.WorkbenchDeviceTest#workbenchResumesPendingApprovalAndRunsRemoteTerminal' python3 scripts/device_check.py`。证据见 `dist/verification/0.1.27/`。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.27-preview.apk`，版本 0.1.27（28），包名 `dev.remotegpt.preview`，APK v2 签名验证通过。SHA-256：`485dd5753294cfbb2e915af9f783880d88257042d958fff0bf542396ba44dfae`。

## 0.1.28：选择弹层密度（2026-10-30）

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 通过，Lint 0 errors、25 warnings。中英文 412 条资源键一致，删除不再使用的常驻说明文案。
- API 35 模拟器的既有 ConversationLayoutDeviceTest 三项全部通过（11.229 秒），覆盖模型切换、思考强度切换、发送、附件未完成禁止发送、运行时停止，以及文件摘要、复制和长回复分享回归。
- 实际组件离线预览检查中文模型/思考强度/服务器/项目、英文模型、浅色/深色、320dp 窄屏和双倍字体。服务器状态在大字体下分行，无名称与状态重叠；同名项目显示可区分的路径；运行时显示简短“用于下一条消息”，普通选择状态不显示说明。
- 25 项模型示例中，打开后选中第 22 项直接可见；UIAutomator 节点为 checkable=true、checked=true，勾选位置固定。选择控件保留系统单选语义，未进行 TalkBack 人工朗读或实体手机振动验收。
- 截图 `dist/ui-0.1.28/`，构建、设备测试、Lint、单选节点与签名证据 `dist/verification/0.1.28/`。预览使用离线示例，没有创建远端会话；本次未重跑 SSH/同步协议端到端测试。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.28-preview.apk`，版本 0.1.28（29），包名 `dev.remotegpt.preview`，APK v2 签名通过。SHA-256：`144dfbf375d3d1d1e99e7bdc3ff5bae41da87945389bcc7f3cd5ea4977597907`。

## 0.1.29：图片预览（2026-10-30）

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 通过，Lint 0 errors、25 warnings。
- 图片组件编译覆盖 Coil 3 `AsyncImage` 的加载、失败与成功状态；远程查看器包含安全区关闭按钮、按比例显示、双指缩放/拖动和双击重置，附件缩略图包含裁切、圆角和失败状态。
- 本轮未在真机或真实远端图片上做视觉回归；未宣称不同厂商图片解码器、超大图片内存占用和 TalkBack 操作已覆盖。现有图片 SSH 上传/发送回归仍由上一版设备测试覆盖。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.29-preview.apk`，版本 0.1.29（30），包名 `dev.remotegpt.preview`，APK v2 签名通过。SHA-256：见同目录 `.sha256` 文件。

## 0.1.30：中性表面层级（2026-10-30）

- 主题重新分开页面画布、普通内容、浮起内容和次级内容的灰阶，保持单一蓝灰强调色，不增加渐变或彩色卡片。
- 输入区使用主表面与轻微 tonal elevation，附件缩略卡使用次级表面；用户消息气泡使用更明确的 raised surface。
- 文件摘要外层使用主表面，标题栏使用次级表面；选择弹层只给当前选项淡蓝灰背景并固定勾选位置，普通行保持干净。
- 公共工作区卡片阴影收窄，避免大面积厚重投影。未改动 SSH、协议和同步逻辑。
- 在同一套中性层级上加入受控的玻璃质感：顶部栏、工作区卡片、输入区、选择弹层和图片查看器按钮使用低透明度、细边界与轻微阴影；聊天正文、终端和文件列表保持清晰的实体表面。
- 本轮 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 通过；Lint 结果为 0 errors。API 35 模拟器未能启动，原因是环境缺少 `libxkbfile.so.1`，因此未执行设备截图和设备回归。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.30-preview.apk`，版本 0.1.30（31），包名 `dev.remotegpt.preview`，APK v2 签名通过。SHA-256：`a5ca87ad51f1b989c453d03736a9ee2cd7351df0bab478d6f17266f55d305edf`。

## 0.1.31：首页与会话状态密度（2026-10-30）

- 首页移除“项目 / 已归档”的重复文字标题，只保留紧凑的归档筛选图标，并保留无障碍描述和原有切换行为。
- 会话行删除左侧状态图标，状态统一放在右侧；运行中的圆形进度、完成、失败和审批图标都与状态文字共享同一尾部区域，避免同一行重复表达。
- 会话正文缩进调整以保持与项目名称对齐，未改变归档、恢复和打开会话行为。
- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` 通过；设备截图仍受本机模拟器缺少 `libxkbfile.so.1` 影响。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.31-preview.apk`，版本 0.1.31（32），包名 `dev.remotegpt.preview`，APK v2 签名通过。SHA-256：见同目录 `.sha256` 文件。

## 0.1.32：首页归档入口位置（2026-10-30）

- 移除首页首行的独立归档图标和占位空间，项目列表从顶部内容区直接开始。
- 归档/最近会话切换移动到顶部栏操作组，与新建项目和设置保持同一层级；操作仍保留无障碍描述和原有刷新逻辑。
- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` 通过。

产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.32-preview.apk`，版本 0.1.32（33），包名 `dev.remotegpt.preview`，APK v2 签名通过。SHA-256：见同目录 `.sha256` 文件。


## 0.1.33：项目动效、真实背景模糊、底部输入工具栏（2026-10-30）

- APK 包名 `dev.remotegpt.preview`，版本 0.1.33（34），最低 API 29。背景模糊由固定版本 Haze 1.6.10 提供；API 31+ 启用背景 RenderEffect，API 29–30 使用明确的高不透明度降级。许可证已同步到应用内 notices。
- 新增 `WorkbenchInteractionDeviceTest`：展开屏幕下方项目后露出标题/首条会话、快速反向展开、320dp 双倍字体长文本下的底部操作位置、背景条纹像素的模糊/降级检查。既有 `ConversationLayoutDeviceTest` 更新为窄屏合并入口，再验证模型、思考、上传限制、发送和停止。设备测试源码已编译，**这些设备用例本轮未执行成功，不能视为通过**。
- 运行验证阻塞：宿主机 emulator 36/37 在 Android 系统启动阶段以 SIGSEGV 退出。已尝试 API 29/35、不同渲染与 CPU 加速路径、无窗口与 Qt offscreen、独立 userdata。失败发生在 App 安装/启动之前；不归因于 App，也不据此宣称 App 无崩溃。没有生成本轮真实设备截图/录屏。
- 尚未验收：真实背景模糊的像素检查、项目滚动及连续反向动画、键盘动画/历史阅读锚点、实际设备的帧率/功耗、大字体和浅深色截图、TalkBack 人工操作。本轮没有访问远端 SSH 或创建远端会话。
- 编译、单元测试、Lint、APK 元数据与签名结果保存在 `dist/verification/0.1.33/`。旧版本设备通过记录不作为本轮新布局的验收证据。

在可用的一次性模拟器上安装 `app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 后，可运行：

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.remotegpt.app.WorkbenchInteractionDeviceTest,dev.remotegpt.app.ConversationLayoutDeviceTest,dev.remotegpt.app.MarkdownStreamingDeviceTest \
  dev.remotegpt.preview.test/androidx.test.runner.AndroidJUnitRunner
```

离线视觉预览：启动 `dev.remotegpt.preview/dev.remotegpt.app.DesignPreviewActivity`，传入 `--es screen reference --es theme light`（或 `dark`）。`reference` 使用与真实聊天相同的背景采样、输入框和 ConversationViewport；`projects` 使用实际项目列表及顶部背景采样。预览不连接 SSH。

最终构建 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PpreviewApplicationId=dev.remotegpt.preview` 通过；8 项 JVM 单测通过，Lint 0 errors、29 warnings。APK v2 签名校验通过。产物：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.33-preview.apk`。SHA-256：`85394b96d7a697608595261fffeadf1c9c584fb525cadfc9220bec7e5081518d`。

## 0.1.34：输出过程与同步状态可视化（2026-10-31）

- 输出摘要把任务状态与远端同步状态分开显示：实时接收、与服务端对齐、已同步、等待同步。执行过程显示事件、命令和文件计数，并以最近四个事件标签形成紧凑轨道；原始命令/思考/计划仍可展开。
- 远端 `localImage` 现在支持会话内嵌缩略图：首次点击加载并缓存，后续可直接点击缩略图进入全屏缩放查看；本地附件缩略图和远端文件查看器保持原有行为。
- 根据官方 App Server 文档补齐 `imageView {id, path}` 条目解析；新增协议单测确认路径进入图片预览模型，图片查看事件不再以未知条目展示。
- 助手正文增加 Codex 标识和固定状态占位，继续使用局部 Markdown AST 复用，不引入逐字布局动画。流式状态与最终回复共享同一消息结构，减少页面闪动和跳跃。
- 新增 `OutputVisualizationDeviceTest` 两个设备用例，覆盖实时接收及完成后对齐状态；测试源码已通过 AndroidTest 编译。设备测试仍需在可启动模拟器或真机运行，本轮未把编译结果当作设备验收。
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 通过；Lint 0 errors。由于本机 Android 模拟器此前在系统启动阶段 SIGSEGV，尚未生成本轮真实设备截图或性能数据。
- 预览 APK：`/home/iaw/project/remote_gpt/dist/remote-gpt-0.1.34-preview.apk`，版本 0.1.34（35），包名 `dev.remotegpt.preview`，APK v2 签名校验通过。SHA-256：`b7444ff45bbb2f8e1161758a3b0c0982dae45356399d8ae5aa97ca996e3218d5`。

## 0.1.34 首次公开发布验证（2026-10-31）

- 正式包名 `dev.remotegpt.app`，versionName `0.1.34`、versionCode 35，最低 API 29、目标 API 36。沿用本机长期发布密钥，APK v2 签名校验通过，证书指纹见 [发布说明](releasing.md)。
- 本地 `:codex-client:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest` 全部完成。协议与会话同步 22 项、App JVM 测试 8 项通过；需要 SSH fixture 的 6 项集成测试跳过，不计入通过数量。
- Debug / Release Lint 均为 0 errors，分别有 36 / 37 warnings；未新增忽略错误的 baseline。4 项 Python fixture 防护测试通过。
- 本轮本地没有连接 Android 设备，也未重跑真实 SSH / daemon 集成验证。GitHub Actions 的 API 29 / 35 设备测试结果可在 [CI 运行记录](https://github.com/iawnix/GHub/actions/workflows/check.yml) 查看；这些任务仍不包含需要私有 SSH fixture 的测试。
- 公开源码排除 `.remote-gpt/` 本地附件、构建目录、签名密钥和本机 SDK 配置。GitHub 发布任务独立构建签名 APK，附件的实际哈希以 Release 中的 `SHA256SUMS` 为准。
