# GHub 文档

## 使用 GHub

- [任务看板](taskboard.md)：本次 HTML 看板改造、OpenSSH、使用方法、构建与边界。
- [任务看板验证记录](taskboard-verification.md)：已通过的 APK、JVM、Lint、前端、API 29 / 35 / 36 与 Release 设备检查，跳过范围及已修复问题历史。
- [看板 APK 构建与下载](https://github.com/chen-yu-hao/GHub/actions/runs/41418505659)：源码 `96c07a2` 的成功 Debug 构建；同提交的[完整 CI](https://github.com/chen-yu-hao/GHub/actions/runs/41418462024) 也已通过，文件校验值见[验证记录](taskboard-verification.md)。
- [快速开始与常见问题](getting-started.md)：安装、服务器配置、项目与会话、文件和终端。
- [连接与同步](connection-recovery.md)：重连、后台保持、草稿和缓存的工作方式。
- [上游版本历史](https://github.com/iawnix/GHub/releases)：原项目的更新说明与发行包，不包含本 fork 的任务看板改造。

## 参与开发

- [贡献指南](../CONTRIBUTING.md)：问题反馈、Pull Request 和文档约定。
- [开发指南](development.md)：构建环境、模块结构和离线界面预览。
- [测试指南](verification.md)：本地检查、SSH 集成测试和 CI。
- [界面设计](ui-design.md)：导航、排版、交互和适配约定。
- [协议实现](protocol.md)：App Server 接口、会话生命周期和兼容策略。
- [发布指南](releasing.md)：版本号、签名和 GitHub Release 流程。

## 技术记录

- [崩溃诊断与列表标识](crash-investigation.md)
- [选择面板设计记录](ui-review-0.1.28.md)
- [Termux 组件与本地修改](../terminal/UPSTREAM.md)
- [第三方组件许可](../THIRD_PARTY_NOTICES.md)
