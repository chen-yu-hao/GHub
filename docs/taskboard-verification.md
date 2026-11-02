# 任务看板验证记录

记录日期：2026-11-02。仓库为 [chen-yu-hao/GHub](https://github.com/chen-yu-hao/GHub)，开发分支为 `main`，版本为 `0.2.0-taskboard`。以下记录对应已核对的 GitHub Actions 结论、JUnit XML 与实际下载 APK；安装包为独立 Debug 预览版。

## 当前结论

**同一源码的 APK 构建与完整 CI 均已成功；Debug APK 已下载并检查打包内容。**

| 运行 | 当前结果 | 范围 |
|---|---|---|
| [APK 构建 41418505659](https://github.com/chen-yu-hao/GHub/actions/runs/41418505659) | 成功 | 源码 [`96c07a2`](https://github.com/chen-yu-hao/GHub/commit/96c07a207a505e20c6d7cf50cc2f33d4a5218cdc)；前端、JVM、Lint、Debug APK 与测试 APK 编译；APK 已下载校验 |
| [完整 CI 41418462024](https://github.com/chen-yu-hao/GHub/actions/runs/41418462024) | `completed / success`，5 个 Job 全部成功 | 同一源码 `96c07a2`；build、API 29 / 35 / 36 设备测试、release-device |

两项运行的完整源码提交均为 `96c07a207a505e20c6d7cf50cc2f33d4a5218cdc`。下表保留 fixture 跳过数量；工作流成功不将跳过项目计为通过。`main` 上后续提交应依据对应运行记录判断。

完整 CI 的 devices / release 流程使用默认包名构建，独立 APK 工作流使用 `-PpreviewApplicationId=dev.remotegpt.taskboard`。两者源码相同、构建配置不同：设备测试结果对应完整 CI 生成的 APK，下载包的编译和 6 项资源校验对应独立 APK 工作流。

## 已成功的构建与检查

以下数量来自成功的 APK 构建运行 `41418505659`、源码 `96c07a2` 的实际报告：

| 检查 | 结果 | 证据与范围 |
|---|---|---|
| 生产前端测试 | **27 通过，0 失败** | `node scripts/taskboard/board.test.cjs`，执行实际生产 JS 的 Node DOM / 事件测试环境 |
| client JVM 测试 | **43 项：36 通过、0 失败、7 跳过** | 7 项需专门 fixture 的检查未在该构建中执行；跳过不代表通过 |
| app JVM 测试 | **40 通过，0 失败、0 跳过** | 包括看板动作、快照与相关原生逻辑测试 |
| Android Lint | **通过** | `:app:lintDebug` |
| Debug APK 编译 | **通过** | `:app:assembleDebug`；已生成并下载实际 APK |
| Android 测试 APK 编译 | **通过** | `:app:assembleDebugAndroidTest`；编译成功不等同于设备测试通过 |
| APK 内看板资源 | **6 项字节一致** | 解包下载 APK，对照该源码中的 HTML、CSS、JavaScript、SVG 数据、字体和许可 |
| 来源样式与 SVG | **通过静态来源断言** | 核对 632 × 840 几何、区块位置、字体尺寸、卡片、按钮与原 SVG 路径 |

6 项实际打包资源为 `index.html`、`board.css`、`board.js`、`icons.js`、`jetbrains-mono.woff2`、`FONT-LICENSE.txt`，均位于 APK 的 `assets/taskboard/`。该检查确认构建包含预期资源；导航另由下面的设备测试验证，Ocean 5C 视觉效果仍待真机对照。

前端测试覆盖状态分类、未知字段、服务器 / 项目过滤、2/3/7/2 分页、部分统计、审批确认与防重复提交、断线或请求替换、不支持审批、SSH-only 状态、终端与离线项目入口、结构化计划、恶意远端文本、配色持久化、有限黑白重绘，以及旧 WebView Flexbox gap 的实际布局检测分支。样式和事件测试使用 Node 环境，不能替代 Android WebView 和真机测试。

## 已通过的设备测试

以下数据来自完整 CI `41418462024` 的实际 JUnit XML：

| 设备流程 | XML 结果 | 实际范围 |
|---|---|---|
| API 29 / 35 / 36（每档） | **38 项：34 通过、4 fixture 跳过、0 失败、0 错误** | 三档 Job 均成功；含实际 HTML 按钮到原生页面的导航、直接桥接、返回与快照等检查 |
| Release 设备综合用例 | **1 通过、0 跳过、0 失败、0 错误** | `optimizedReleaseStartsAndUsesEncryptedKeysSftpAndPty`，耗时 24.277 秒 |

三档设备 XML 中，下列导航回归用例均已通过：

- `ProjectVisibilityDeviceTest.hiddenProjectSurvivesReloadWithoutDeletingSessionsOrDrafts`
- `TaskBoardDeviceTest.packagedPageLoadsAndActualDomButtonOpensNativeHostManagement`
- `TaskBoardDeviceTest.directNativeBridgeHostsActionOpensHostManagementAndReturnsToBoard`
- `WorkbenchDeviceTest.emptyWorkspaceOffersHostSetupAndAccessibleEditor`
- `WorkbenchDeviceTest.serverSettingsPreserveTheActiveWorkspaceAcrossEditAndDelete`

Release 综合用例完成了实际 DOM 到原生历史页面与剪贴板的操作，随后分别使用加密 Ed25519 和 RSA 密钥建立 SSH 连接、执行命令，并验证 SFTP 上传、读取和保存后的内容，以及 PTY resize / write / pwd 断言。[Release 报告附件](https://github.com/chen-yu-hao/GHub/actions/runs/41418462024/artifacts/13128604393) 中该用例为通过，未跳过。这些是针对隔离测试服务的集成验证，不代表已联调用户的真实服务器。

普通 API 设备流程每档跳过的 4 项需要专门 fixture；独立 APK 构建中的 client JVM 另有 7 项 fixture 测试跳过。上述跳过项不以其他流程成功替代计数。

## APK 下载与签名

从 [成功运行 41418505659](https://github.com/chen-yu-hao/GHub/actions/runs/41418505659) 的 **Artifacts** 下载 `ghub-taskboard-debug-apk`。JVM 与 Lint 报告位于同一运行的 `taskboard-verification-reports`。

已下载并核验的 APK 信息：

| 项目 | 实际值 |
|---|---|
| 下载后保存的文件名 | `GHub-taskboard-0.2.0-96c07a2-debug.apk` |
| 源码提交 | `96c07a207a505e20c6d7cf50cc2f33d4a5218cdc` |
| 文件大小 | 78,981,807 字节 |
| SHA-256 | `c113c47a290a1a99b25d5aad90769fbc3b413baa78e1d5b767834ca970614399` |
| 包名 | `dev.remotegpt.taskboard` |
| 看板资源检查 | APK 中 6 项资源与该提交源码字节一致 |

APK 使用独立包名 `dev.remotegpt.taskboard`，应用名称为“GHub 看板 Preview”，可与原版共存。它采用 **Debug 调试签名**，不是正式发行签名；Release 设备流程通过也不会改变下载包的签名类型。上游 `iawnix/GHub` 的 Releases 不包含本次任务看板改造。

## 尚未验证的真机范围

- Ocean 5C 实机字体、触控、系统栏、分页及与原 HTML 的像素对照。
- 网络切换、长时间运行及墨水屏实际残影表现。
- 厂商硬件全刷 / 波形刷新 API。本版提供通用 WebView 黑白重绘降级，没有把它表述为已验证的硬件全刷。

原附件没有完整中文字体文件，中文按原字体栈使用系统回退；字体字形差异仍需目标设备确认。模拟器与静态样式检查无法替代上述 Ocean 5C 验证。

## 已修复问题历史

早期[完整 CI 41415847753](https://github.com/chen-yu-hao/GHub/actions/runs/41415847753) 在 API 29 / 35 / 36 各出现 4 项导航失败，Release 流程也在导航后等待 `release-code` 失败。[诊断运行 41417876103](https://github.com/chen-yu-hao/GHub/actions/runs/41417876103) 增加了桥接与组合生命周期证据；实际 APK 的 DEX 检查定位到 `MainActivity` 条件调用 `isSystemInDarkTheme()` 改变了 Compose 组合结构，使导航状态重置。

提交 `96c07a2` 将系统深色状态读取改为无条件调用。同提交的完整 CI `41418462024` 已成功：上述 4 项导航与新增直接桥接用例均通过，Release 也完成后续密钥、命令、SFTP 与 PTY 验证。这是已修复的历史问题，不是当前验收阻塞。

## 复现检查

克隆本次改造的仓库：

```sh
git clone --branch main https://github.com/chen-yu-hao/GHub.git
cd GHub
```

如需复现上表已成功的构建，先检出确切源码；不要将后续 main 的状态混入该记录：

```sh
git switch --detach 96c07a207a505e20c6d7cf50cc2f33d4a5218cdc
```

仅前端检查不需要 Android SDK：

```sh
node scripts/taskboard/board.test.cjs
```

完整构建与 JVM 检查需要 JDK 21、Android SDK Platform 36、Build Tools 35.0.0 与 Node.js 22：

```sh
./gradlew -PpreviewApplicationId=dev.remotegpt.taskboard \
  :codex-client:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

准备可丢弃的模拟器后，按完整 CI 的默认包名运行普通设备测试：

```sh
./gradlew :app:connectedDebugAndroidTest
```

完整 SSH / Codex / Release 测试按[项目测试指南](verification.md)配置隔离 fixture；其中部分脚本默认使用 `dev.remotegpt.preview`，应使用与脚本一致的构建包名。复现 CI 流程后，Ocean 5C 真机仍需完成上述视觉、中文字体和硬件刷新检查。
