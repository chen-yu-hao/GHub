# 任务看板验证记录

记录日期：2026-11-02。仓库为 [chen-yu-hao/GHub](https://github.com/chen-yu-hao/GHub)，开发分支为 `main`。当前交付版本为 **`0.2.2-taskboard`，versionCode 44**。

## 当前版本与实际结果

APK 与完整 CI 对应同一源码提交 [`5bd7f89f0040932d5aed039e563b3faadd303dcd`](https://github.com/chen-yu-hao/GHub/commit/5bd7f89f0040932d5aed039e563b3faadd303dcd)。[APK 工作流 41437823444](https://github.com/chen-yu-hao/GHub/actions/runs/41437823444) 与 [完整 CI 41437406135](https://github.com/chen-yu-hao/GHub/actions/runs/41437406135) 均已完成，结论为 **success**。以下计数读取本轮报告与日志，未沿用旧提交的结果。

| 检查 | 本轮实际结果 | 证据范围 |
|---|---|---|
| 生产前端 Node 检查 | **32 通过，0 失败、0 跳过** | JS 事件、样式来源、短视口分支、主动导航与后台快照行为 |
| client JVM | **43 项：36 通过、7 fixture 跳过、0 失败** | `codex-client` 测试报告 |
| app JVM | **40 通过，0 失败、0 跳过** | `app:testDebugUnitTest` 测试报告 |
| Android Lint | **0 errors、38 warnings** | `lintDebug` 成功；没有把成功等同于零警告 |
| Debug APK 与测试 APK | **构建成功** | 独立包名 APK 已下载核对 Manifest、文件哈希、证书和打包资源 |
| API 29 / 35 / 36 设备测试 | **每档 39 项：35 通过、4 fixture 跳过、0 失败、0 错误** | 三份实际 JUnit XML；布局综合用例均执行且未跳过 |
| Release 设备综合用例 | **1 通过，0 失败、0 错误、0 跳过** | `optimizedReleaseStartsAndUsesEncryptedKeysSftpAndPty`，**25.195 秒** |
| 独立布局审计 | **36 份 JSON、36 张 PNG 全部检查通过** | 三档逐样本核对请求尺寸、原生 / CSS / PNG 尺寸、系统栏、Insets、溢出与滚动；选定原图另作目视检查 |

JVM 共 83 项，其中 **76 通过、7 跳过**；7 项均为需要独立 SSH fixture 的 `SshIntegrationTest`。设备每档的 4 项跳过为 `failedStreamingCommandSurvivesSshReconnectAndDiskCache`、`unopenedSessionUpdatesWhileArchivedAndInBackground`、`androidSshAndProxyReadExistingDaemon` 和 `workbenchResumesPendingApprovalAndRunsRemoteTerminal`。**跳过项不计为通过，也不由另一个通过的 Release 用例替代。**

完整 CI 的 devices / release 流程使用默认包名构建；独立 APK 工作流使用 `-PpreviewApplicationId=dev.remotegpt.taskboard`。两种构建使用相同源码，设备测试执行的是各自流程生成的 APK，未把下载的独立 APK 原字节在全部模拟器上运行。

## 0.2.2 的布局与交互修复

| 已定位的问题 | 当前处理 |
|---|---|
| WebView 高度按内容测量，页面可能只占半截 | 显式设置原生 `LayoutParams(MATCH_PARENT, MATCH_PARENT)`，同时保留 Compose 填满可用空间。WebView 自身的测量参数与外层尺寸约束一起核对。 |
| 固定宽高限制宽屏和矮横屏 | 取消全局 `max-width: 632px` 和 `min-height: 460px`；632 × 840 下保留原稿字号、间距及固定区块位置。 |
| 短屏固定栏挤压任务区 | 高度 ≤600px，或宽度 ≤460px 且高度 ≤700px 时整页自然滚动；其余视口保留任务区内部滚动。旧 WebView 使用百分比高度和实际能力检测后的 Flexbox gap margin 回退。 |
| 整页换页后仍停在页脚，弹窗关闭可能跳顶 | 用户主动换页、切换页签、应用 / 清除筛选后回到任务列表起点；后台快照不复位。恢复弹窗前焦点时使用 `preventScroll`。 |
| API 29 旋转后系统清除全屏标志 | 在窗口恢复焦点及旧系统实际 visibility 标志变化后，按当前看板状态恢复隐藏系统栏；只恢复丢失的标志，保留用户边缘手势的临时系统栏覆盖。 |

前端 32 项测试中的模拟 DOM 检查只证明相应事件与滚动调用行为；真实几何结论来自下面的设备捕获。

## 三档设备的独立布局证据

[`TaskBoardLayoutDeviceTest`](../app/src/androidTest/java/dev/remotegpt/app/TaskBoardLayoutDeviceTest.kt) 使用生产 WebView、系统显示配置、实际旋转、真实滑动和分页。两组配置各经历竖屏 → 横屏 → 返回竖屏，共 **6 个阶段**；每阶段保存顶部和滚动后的原始 PNG / JSON。每档 12 对，三档共 36 对。

| Android API | 实际 WebView | 布局用例用时 | 独立审计 |
|---|---|---:|---|
| 29 | `74.0.3729.185` | 77.230 秒 | 12 份 JSON、12 张 PNG 通过；实际执行 `dvh: false`、`flexGap: unsupported` 兼容分支 |
| 35 | `124.0.6367.219` | 57.668 秒 | 12 份 JSON、12 张 PNG 通过 |
| 36 | `133.0.6943.137` | 54.650 秒 | 12 份 JSON、12 张 PNG 通过 |

三档在以下各阶段的实际数值一致，设备密度均为 2：

| 显示阶段 | CSS 视口 | 原生 WebView / PNG | 实际滚动位置 | 滚动后的页脚 y 范围 |
|---|---|---|---|---|
| reference 竖屏及返回竖屏 | 632 × 840 | 1264 × 1680 | 任务区 0 → 1716px | 761–824 |
| reference 横屏 | 840 × 632 | 1680 × 1264 | 任务区 0 → 1044px | 553–616 |
| short 竖屏及返回竖屏 | 360 × 640 | 720 × 1280 | 文档 0 → 2919px | 531–628 |
| short 横屏 | 640 × 360 | 1280 × 720 | 文档 0 → 2196px | 281–344 |

独立审计没有只读取 JUnit 的绿色结果：36 份 JSON 均按独立的预期尺寸矩阵检查，原生 LayoutParams 全为 `MATCH_PARENT`，WebView 填满窗口，CSS 视口 ×2 与原生尺寸一致，页面缩放为 1，view-aware Insets 均为零，状态栏和导航栏均隐藏。所有测量区块、页签和卡片均无水平溢出；滚动到底后，末张卡片与页脚完整进入视口。

特别核对 API 29 的参考返回竖屏：**CSS 632 × 840、原生 1264 × 1680、Insets 全 0、系统标志 `0x3716`、状态栏 / 导航栏均不可见**。旧报告曾出现的 632 × 768 没有在本轮 36 份捕获中重现。每档 4 张返回竖屏 PNG 与对应初始竖屏 PNG 字节完全相同。

PNG 从报告 ZIP 按原始字节提取，逐一检查尺寸与完整性，没有重编码、裁切或拼接。API 29 的参考返回竖屏、参考横屏及短横屏滚动后三张原图已直接查看：页面占满可用宽高，没有半屏空白；短屏末卡操作按钮、页码和分页按钮可达。长命令来自用于触发滚动的 fixture，首屏无法显示整张长卡片是正常滚动内容。

632 × 840 下，按[原 HTML](../app/design/taskboard/supplied-board-markup.html) 推导并核对的页头、筛选、四状态页签、任务区、页脚起点为 **16 / 91 / 147 / 231 / 761px**，外框尺寸和四个页签尺寸均一致。这是外层几何检查，未声称完成中文字体或逐像素相等验证。

### 测试遗漏与修正记录

- **0.2.0 历史边界：** `96c07a2` 的功能 CI 曾通过，但当时未测量真实 WebView 尺寸及横竖屏边界。之后用户报告显示不完整，旧成功结果不能代表布局已验收。
- **早期设备环境：** 默认模拟器可能限制请求分辨率；改用 `pixel_2` profile，并检查实际生效的 `wm` 尺寸和密度。旋转等待改为限时轮询当前 Activity / WebView，避免一直等待已经替换的 Compose 根。API 29 的 Insets 转换使用 View 上下文，避免把已隐藏系统栏的尺寸误当作占用空间。
- **`d8495cc` 的实际反例：** 旧布局用例虽通过，API 29 返回参考竖屏的两份 JSON / PNG 却显示系统栏恢复、CSS 变为 632 × 768。只按带 Insets 的可用空间断言会放过这个错误。本版增加旧系统 visibility 事件处理，并把测试改为严格核对请求的完整尺寸、系统栏状态与零 Insets；本轮独立审计也逐张执行这些检查。
- **服务器设置测试同步：** 对 IME 与设置页滚动的等待作测试侧修正。本轮相关用例实际通过，没有以扩大超时替代布局证据。

## Release 与 OpenSSH 验证

本轮 [CI 41437406135](https://github.com/chen-yu-hao/GHub/actions/runs/41437406135) 的 API 35 Release 用例在 **25.195 秒**内通过，XML 为 **1 项、1 通过、0 跳过、0 失败、0 错误**。该用例检查优化后的非 debuggable Release 构建，执行 HTML 到原生历史页的导航和剪贴板内容核对。

加密 Ed25519 与 RSA 两套 fixture 凭据分别执行主机指纹确认、SSH 命令输出校验、Unicode 内容的 SFTP 上传 / 读取 / 保存后回读，以及 PTY 创建、resize / write / pwd 输出断言。覆盖依据是通过用例中顺序执行的断言，未声称日志为每步单独输出成功标记。

这些检查使用隔离测试服务，不等同于已经联调用户的真实服务器，也不覆盖上面明确跳过的全部 SSH / Codex 场景。

## APK 下载、签名与升级

从 [成功的 APK 运行 41437823444](https://github.com/chen-yu-hao/GHub/actions/runs/41437823444) 的 **Artifacts → ghub-taskboard-debug-apk** 下载；JVM / Lint 报告为 `taskboard-verification-reports`。

| 项目 | 已下载 APK 的实际值 |
|---|---|
| 保存文件名 | `GHub-taskboard-0.2.2-5bd7f89-debug.apk` |
| 源码 | `5bd7f89f0040932d5aed039e563b3faadd303dcd` |
| 版本 / versionCode | `0.2.2-taskboard` / `44` |
| 包名 / SDK | `dev.remotegpt.taskboard`；min SDK 29，target SDK 36 |
| 文件大小 | 78,982,267 字节 |
| APK SHA-256 | `e4c27cbef0461a9775f74db2fc52f171e3964a09d89b0440fdd51814592f6d5a` |
| 公开签名证书 SHA-256 | `9835E03C96CA3625CC3FE6C234B5310DEA348908B6D8FFDC32B533B2EC0685E2` |
| 证书主题 / debuggable | `C=US,O=Android,CN=Android Debug` / `true` |
| 打包看板资源 | 6 项与本轮生产源码逐字节一致 |

6 项资源为 `assets/taskboard/` 下的 `index.html`、`board.css`、`board.js`、`icons.js`、`jetbrains-mono.woff2`、`FONT-LICENSE.txt`。

核验范围为 APK 文件哈希、二进制 Manifest 字段、公开 v2 签名证书提取及结构 / 公钥一致性检查，**不是完整的 `apksigner verify` 校验，也没有执行旧版到新版的实际覆盖安装实验**。下载包使用 Debug 调试签名，应用名称为“GHub 看板 Preview”；另一个构建配置的 Release 测试成功不会改变下载包的签名类型。

### 已安装旧预览版时

已核验的 0.2.0（`96c07a2`）证书 SHA-256 为 `B07D5C8FAA04794A45D275AD25C2D27CED034E40D666FB627F0F6F60BE2DE167`；0.2.1（`8b4b8b2`）为 `B81B3584DE8252E62CBE824F7D93EC12F7B1BD4E8B27DC8470EEDCE41CFD20EB`。它们与本次 0.2.2 的证书不同，且未提供签名密钥轮换链。根据 [Android 应用签名要求](https://developer.android.com/studio/publish/app-signing#considerations)，这些同包名预览版**不能常规覆盖升级**。

请先保存服务器配置、认证材料和需要的本地数据，再卸载旧预览版并安装新版。**卸载会清除旧预览版的本地配置和数据。** 独立包名仍可与原版 GHub 共存；上游 Releases 的 APK 不包含本次任务看板改造。

## 仍需真机验证

- Ocean 5C 的实际触控、旋转与系统栏行为、中文字体及与原 HTML 的像素对照。
- 网络切换、长时间运行和墨水屏残影表现。
- 厂商硬件全刷 / 波形刷新 API。本版提供通用 WebView 黑白重绘，未验证硬件全刷。

原附件未提供完整中文字体文件，中文按原字体栈使用系统回退。模拟器的几何与滚动检查不能替代 Ocean 5C 的字形、像素和硬件刷新验证。

## 复现构建与设备测试

准备 JDK 21、Android SDK Platform 36、Build Tools 35.0.0 与 Node.js 22。复现交付 APK 使用相同源码和独立包名配置：

```sh
git clone --branch main https://github.com/chen-yu-hao/GHub.git
cd GHub
git switch --detach 5bd7f89f0040932d5aed039e563b3faadd303dcd
node scripts/taskboard/board.test.cjs
./gradlew -PpreviewApplicationId=dev.remotegpt.taskboard \
  :codex-client:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

设备测试使用同一提交，创建 `pixel_2` 硬件 profile 的可丢弃模拟器（API 29 / 35 / 36，Google APIs、x86_64），按默认应用包名运行：

```sh
./gradlew :app:connectedDebugAndroidTest
```

布局用例会调整模拟器分辨率、密度和旋转，并在退出时恢复。完整 SSH / Codex / Release 测试按[测试指南](verification.md)配置隔离 fixture，构建包名与对应脚本保持一致。复现构建并不保证获得与 CI 相同的临时 Debug 证书或完全相同的 APK 字节。
