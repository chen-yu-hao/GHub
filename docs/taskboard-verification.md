# 任务看板验证记录

记录日期：2026-11-02。验证对象为本次 `0.2.0-taskboard` 源码改造；不是 APK 发布合格报告。

## 已实际执行

| 检查 | 结果 | 证据与范围 |
|---|---|---|
| 前端测试 | 26 通过，0 失败，0 跳过 | `node scripts/taskboard/board.test.cjs`；执行实际生产 JS，使用 Node DOM / 事件测试环境 |
| 来源样式与 SVG | 通过 | 上述测试核对 632 × 840 布局、区块位置、字体尺寸、卡片边框、按钮与原 SVG 路径 |
| XML 资源 | 通过 | 默认和中文资源均解析成功，各有 491 个不重复字符串键 |
| 补丁空白检查 | 通过 | `git diff --check` |
| 原始源码溯源 | 通过 | 260 个上游文件逐一验证 Git blob SHA-1；基线树为 `98a5174e23a581eb0bec5aeab4055c72f0e29e2a` |
| 实现复核 | 完成静态阅读 | 原生动作、跨主机审批、过期列表响应、SSH 与 Codex 状态分离、生命周期、导航和 R8 规则 |

前端测试覆盖：状态分类、真实未知字段、服务器 / 项目过滤、2/3/7/2 分页、统计不完整提示、审批确认与防重复提交、断线或请求替换、原生返回键消息、不受支持审批、SSH-only 空态和终端入口、离线项目入口、真实计划、恶意远端文本、配色持久化和有限黑白重绘。

这些测试不启动 Chromium 或 Android WebView。样式来源断言不能替代截图差异检查，静态代码复核不能替代 Kotlin 编译或设备执行。

## 已编写，未执行

| 测试 | 本次新增或适配 |
|---|---|
| `TaskBoardActionTest` | 4 项：动作白名单、审批参数、路径与非法输入 |
| `TaskBoardSnapshotTest` | 15 项：任务分类、去重、审批、输出、计划、保密字段与分页元数据 |
| `PlanTimelineTest` | 10 项：计划通知、轮次切换、旧事件和无效结构 |
| `ResourceLimitsTest` | 新增计划内存计数覆盖 |
| `TaskBoardDeviceTest` | 4 项：实际打包 WebView、真实快照、资源限制和 Android Back |
| `SshAvailabilityDeviceTest` | 2 项：任务服务失败时的 SSH 断开与终端入口 |
| 既有设备流程 | 项目隐藏、会话状态、审批、终端与 Release 流程适配实际看板入口；不关闭看板来绕过集成 |

实际 Android WebView 测试通过主线程和 `evaluateJavascript` 回调操作 DOM，读取原生快照；Release 测试保留 R8、SFTP 与 PTY 覆盖。相关源码已经纳入交付，但不能计为已通过。

## 环境限制与未完成验证

当前工作环境没有 Android SDK、模拟器、可用 Gradle 分发包或浏览器可执行文件。提供的 Java 为 17，项目构建指南要求 JDK 21。Gradle Wrapper 的实际尝试在下载 Gradle 8.13 时失败，错误为 `java.net.SocketException: Operation not permitted`；因此未进入编译阶段。

本次没有执行 APK 构建、Kotlin / JVM 测试、Android Lint、设备测试、真实 OpenSSH / Codex 服务联调、Ocean 5C 字体与触控适配、截图差异比较或硬件全刷验证。没有 APK、真机截图或远端 CI 成功记录。

提供的 GitHub 仓库在本会话中只有读取权限，改动未推送至上游。完整源码、补丁和手动构建工作流随交付包提供。

## 复现检查

仅前端检查不需要 Android SDK：

```sh
node scripts/taskboard/board.test.cjs
```

完整构建与 JVM 检查：

```sh
./gradlew -PpreviewApplicationId=dev.remotegpt.taskboard \
  :codex-client:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

准备可丢弃的模拟器后运行普通设备测试：

```sh
./gradlew -PpreviewApplicationId=dev.remotegpt.taskboard :app:connectedDebugAndroidTest
```

完整 SSH / Codex / Release 测试按[项目测试指南](verification.md)配置隔离 fixture；其中部分旧脚本默认使用 `dev.remotegpt.preview`，应按该指南使用相同包名构建。最后在 Ocean 5C 真机确认字形、系统栏、审批触控、分页、网络切换和重绘效果。
