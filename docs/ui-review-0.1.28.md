# 0.1.28 选择弹层布局与文案检查

范围：模型、思考强度、服务器、项目四类选择弹层；使用现有 Android 字体、颜色与 Material 手势。服务器返回的任意长名称未穷举，不代表全应用无障碍审计。

| Severity | Location | Before | After | Why |
| --- | --- | --- | --- | --- |
| LOW | app/src/main/java/dev/remotegpt/app/ui/MessageComposer.kt | 规则说明常驻，刷新与选项混排 | 正常状态直接显示选项，运行时保留短提示，刷新位于标题右侧 | 高频选择优先显示决策所需内容 |
| LOW | app/src/main/java/dev/remotegpt/app/ui/SelectionSheet.kt; ManagementScreens.kt; ProjectsScreen.kt | 行高、边距、图标与状态样式分散 | 共享最小 52dp 单选行、24dp 边距及固定勾选位置；服务器状态为轻量文字 | 对齐一致，降低重复装饰带来的视觉负担 |
| MEDIUM | app/src/main/java/dev/remotegpt/app/ui/ManagementScreens.kt; ProjectsScreen.kt | 服务器名单行受状态胶囊挤压；每个项目都显示路径 | 名称可换行，窄屏/大字体状态换到第二行；仅同名项目显示路径 | 保留必要识别信息，同时减少无关信息 |

验证：既有设备回归 3 项通过；离线截图覆盖中英文、浅深色、320dp/2.0 字体和选中项靠后的长列表。仅代码接入轻触反馈，未在真机检验触感；单选语义通过 UIAutomator 核对，TalkBack 人工朗读 Not verified。

Approve（限上述已检查范围）。
