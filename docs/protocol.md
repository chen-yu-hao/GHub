# App Server 兼容契约

官方参考：https://developers.openai.com/codex/app-server/

## 基线与传输

最低兼容基线为 0.164.0。客户端解析 `codex --version`、`codex app-server daemon version` 和 `initialize` 返回的语义版本，接受后续 0.x 版本，再由握手和协议行为确认可用性；低于基线、主版本不同或无法解析时停止连接，不自行修改远端安装。这样不需要为每个 CLI 版本单独发布 Android 包。

官方 `app-server proxy` 将 stdin/stdout 的原始字节连接到 daemon 控制 socket。正确顺序是 HTTP WebSocket Upgrade → WebSocket text frame 中的 JSON-RPC → `initialize` 响应 → `initialized` 通知。无需设置 PTY，stderr 必须单独排空。

每条协议请求携带 `id`、`method`、`params`，不发送 `jsonrpc` 字段。服务端请求也具有 `id`，与无 ID 通知严格区分。客户端请求、审批、重连分别维护状态，不能把一次 send 当成服务端已执行。

## 已接入接口

| 能力 | 接口 |
|---|---|
| 初始化 / 账户 / 模型 | `initialize`, `initialized`, `account/read`, `model/list` |
| 会话列表 / 元数据 | `thread/list`, `thread/read`, `thread/name/set` |
| 新建 / 接续 | `thread/start`, `thread/resume` |
| 历史 | `thread/turns/list`，`itemsView: full`，按游标分页 |
| 归档 / 恢复 | `thread/archive`, `thread/unarchive` |
| 对话 / 中断 | `turn/start`, `turn/interrupt` |
| 流式状态 | `thread/status/changed`, `turn/*`, `item/*` |
| 审批 | `item/commandExecution/requestApproval`, `item/fileChange/requestApproval` |
| 审批同步 | `serverRequest/resolved` |

沿用此前验证的 schema 约定：`approvalPolicy` 使用 `on-request`，`sandbox` 使用 `workspace-write`，与部分文档示例的 camelCase 写法不同。字段和枚举仍应以目标 CLI 导出的 schema 与实际往返为准；未知字段忽略，未知交互请求拒绝并提示兼容性问题。

默认不启用实验 API。未知字段被忽略，未知时间线类型显示占位信息。尚未支持的服务端交互请求返回 JSON-RPC 错误，不伪造批准响应；需要这些能力时使用 CLI。

## 会话接续

真实 daemon 验证包括：创建专用会话 → 客户端 A 开始任务 → A 收到审批 → B resume 同一会话并收到相同审批 → A/B 均断开 → C resume → 原审批重新投递 → C 批准 → 服务端 resolved → turn completed。

此验证使用持久化测试会话。临时 `ephemeral` 会话不能假定支持跨连接恢复；测试会话结束后归档。

手机与 CLI 必须共享同一个服务实例。独立 `--no-daemon` 进程、不同 SSH 用户、不同 Codex 配置目录不在实时接续承诺之内。

## 恢复规则

- 每次连接建立新 epoch；审批 ID 与 epoch 一起校验。
- `serverRequest/resolved` 使其他客户端的同一审批失效。
- 正常消息、历史和事件按 thread/turn/item ID 合并，最终 item 替换增量文本。
- 重连读取服务端历史；完成事件触发权威状态同步。
- `turn/start` 发送后超时或断开，持久化为“发送状态待确认”，不自动重试。`clientUserMessageId` 用于关联，不假定它提供幂等保证。
- 未实现离线事件队列或推送服务器，手机不在线时不承诺即时通知。

升级兼容基线前，重新导出官方 schema，核对枚举和必填字段，运行协议测试、真实 SSH / daemon 测试和 Android 设备测试。不要仅修改版本常量来宣称兼容。

## 兼容版本表

| CLI / daemon | 策略 | 实际验证 |
|---|---|---|
| 0.164.0 及更新的 0.x | 范围支持 | 版本范围门禁与握手检查；新协议能力仍需单独适配和回归 |
| 其他 / 不一致 | 拒绝连接 | 需新增明确适配和回归记录后支持 |

脱敏的示例消息见 [protocol-samples.json](protocol-samples.json)。真实请求样本和断线场景由 `ProtocolTest` 与 `scripts/probe.py` 维护。

## 0.1.17：新建空会话的生命周期

本机 CLI / daemon 0.165.0 通过真实 `app-server proxy` 复现：

- 应用现有的 `thread/start` 参数创建成功。
- 首条用户消息前立刻 `thread/resume` 返回 `-32600: no rollout found for thread id …`。
- 此时 `thread/turns/list` 返回 `-32600: thread … is not materialized yet; thread/turns/list is unavailable before first user message`；`thread/read` 元数据仍可读取。
- 新连接直接向该空会话 `turn/start` 可以成功，但没有隐含建立事件订阅。发送被确认后需要 `thread/resume` 订阅同一个 ID，并读取历史补齐订阅前的事件。

客户端在新建成功后直接显示空会话，持久化本地 `awaitingFirstMessage` 标记，列表刷新保留这些空会话，不提前读取其历史。重新打开或重连时，仅对明确标记为空会话且匹配上述缺失 rollout 错误的情况使用 `thread/read` 确认远端仍存在；其他错误继续上报。首条消息确认成功后清除标记、恢复订阅并同步历史；订阅失败不把已发送消息恢复为可重发草稿。不以新建另一个会话替代恢复。

验证：本机 0.165.0 的真实 proxy / daemon，使用临时目录与本地 Responses 文本 fixture（无模型费用、无工具命令），验证直接发送首条消息、断线重连后发送首条消息，两条流程均收到 `turn/completed`，并确认同一会话只有一轮历史；验证用会话随后归档。尚未完成 Android 真机 SSH 回归。尚未持久化的空会话不承诺在 daemon 重启后继续存在。

## 0.1.18：识别旧版创建的空会话

截图确认的错误为 `no rollout found for thread id …`。0.1.17 的处理依赖本地 `awaitingFirstMessage` 标记，旧版创建的空会话没有该字段，仍会进入错误分支。

`CodexApi.resumeOrReadEmpty` 现集中处理恢复：匹配缺失 rollout 错误后，必须先成功读取同一 ID 的元数据，再收到明确的 `not materialized yet` 历史错误，才认定为空会话并补存标记。不能仅凭“新会话”标题、空缓存或缺失 rollout 判断。若验证期间另一客户端发送了首条消息、历史已可读取，则仅重试一次原会话订阅；不会新建会话或重发消息。元数据读取失败、其他协议错误照常上报。

本机 0.165.0 proxy / daemon 验证覆盖：不依赖本地标记识别空会话、重连后识别空会话、首条消息后的同 ID 订阅与完成事件。使用临时目录与本地文本 fixture，无模型费用或工具执行，验证用会话已归档。Android 真机 SSH 仍待验收。

## 0.1.20：失效记录与可用空会话分开处理

重新打开旧记录时，如果 `thread/resume` 明确返回该 ID 缺失 rollout，并且随后 `thread/read` 返回该 ID `thread not loaded` 或同一缺失 rollout 错误，客户端返回 `ThreadUnavailable`。它与元数据仍可读取、历史明确尚未 materialize 的可用空会话不同。

会话恢复的 RPC 拒绝与 `ThreadUnavailable` 保存为当前主机/会话的问题状态，不再向上转为整个服务器连接失败。真实传输错误仍进入连接恢复流程。恢复成功才清除问题状态；发送和上传入口在恢复失败或加载期间禁用，草稿仍可编辑。请求异常携带接口名，诊断记录恢复失败的接口、错误码和类别，不记录会话正文或凭据。

本机 0.165.0 daemon 验证：不存在 ID 的恢复与元数据读取分别返回预期错误；真实新建空会话仍可读取元数据并返回明确的空历史响应；失效 ID 的错误不影响同一连接随后调用 `thread/loaded/list`。验证新建的空会话已归档，无模型调用。未实测用户远端服务器与 Android 真机。


## 0.1.21：模型与思考级别

- `model/list` 按 `nextCursor` 分页，使用 `model`、`displayName`、`isDefault`、`hidden`、`supportedReasoningEfforts[].reasoningEffort` 和 `defaultReasoningEffort`。
- `thread/start` / `thread/resume` 的顶层 `model`、`reasoningEffort` 与 thread 元数据集中映射为客户端会话配置。
- `turn/start` 使用 `model` 与 **`effort`**；不发送虚构的 `reasoningEffort` 请求字段。不把客户端 hardcode 的级别当成模型能力。
- 未选择覆盖时省略参数，保留会话设置。显式切换模型时清除草稿旧级别，使用新模型的默认级别；选择在下一次发送时生效，不中断当前轮次。
- 2026-10-29 本机官方 daemon 的只读 `initialize` / `model/list` 验证返回 9 个模型与对应思考级别。本次未发送模型调用；这不等于 Android SSH 上切换模型的完整端到端验收。

## 0.1.23：未打开会话的状态同步

只读发现使用分页的 `thread/loaded/list`，新发现目标使用 `thread/read` 取元数据；最新轮次读取为 `thread/turns/list(limit=1, sortDirection=desc, itemsView=notLoaded)`。观察过程不调用 `thread/resume`、`thread/start` 或 `turn/start`。普通最近列表仅负责发现和标题/目录元数据，独立于页面的归档筛选。详情仍按需加载。
