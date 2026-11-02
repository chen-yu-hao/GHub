package dev.remotegpt.app.board

import dev.remotegpt.app.ConnectionStatus
import dev.remotegpt.app.HostWorkspace
import dev.remotegpt.app.PendingApproval
import dev.remotegpt.app.ResultReceipt
import dev.remotegpt.app.WorkbenchState
import dev.remotegpt.client.Conversation
import dev.remotegpt.client.FileEdit
import dev.remotegpt.client.HostProfile
import dev.remotegpt.client.ThreadSummary
import dev.remotegpt.client.string
import kotlinx.serialization.json.*

/**
 * A credential-free projection for the packaged task-board page.
 *
 * One row represents the latest known turn of a session, or one outstanding
 * approval. Transport state is separate from task outcome: disconnecting never
 * turns a running task into a failed task. Unknown/idle/draft sessions are not
 * counted as successful work. The original protocol/Room records remain the
 * source of truth; there is no second task database in the WebView.
 */
fun taskBoardSnapshot(state: WorkbenchState, nowMillis: Long = System.currentTimeMillis()): JsonObject {
    val hosts = state.profiles.map { profile ->
        val ws = state.workspaces[profile.id] ?: HostWorkspace()
        val summaries = boardSummaries(ws)
        buildJsonObject {
            put("id", profile.id)
            put("name", hostName(profile))
            put("connection", ws.connection.name)
            put("sshConnected", ws.sshConnected)
            ws.error?.let { put("error", it) }
            put("hasMore", hasMore(ws))
            put("lastSyncedAt", ws.lastSyncedAt)
            put("syncing", ws.loadingBoard || ws.connection.busy)
            putJsonArray("projects") {
                val paths = (ws.projects.map { it.path } + summaries.map { it.cwd } +
                    ws.approvals.mapNotNull { it.request.workingDirectory }).filter { it.isNotBlank() }.distinct()
                paths.forEach { path ->
                    add(buildJsonObject { put("path", path); put("name", projectName(ws, path)) })
                }
            }
        }
    }
    val tasks = state.profiles.flatMap { profile ->
        val ws = state.workspaces[profile.id] ?: return@flatMap emptyList()
        val summaries = boardSummaries(ws)
        val approvals = ws.approvals.distinctBy { it.request.key }
        val waitingThreads = approvals.map { it.request.threadId }.toSet()
        val rows = summaries.filter { it.id !in waitingThreads }.mapNotNull { summary ->
            val conversation = ws.conversations[summary.id]
            val receipt = ws.results[summary.id]
            val bucket = taskBucket(summary, receipt, conversation) ?: return@mapNotNull null
            taskRow(profile, ws, summary, bucket, receipt, conversation)
        }
        rows + approvals.map { pending ->
            val request = pending.request
            val summary = summaries.firstOrNull { it.id == request.threadId }
                ?: ws.selectedSummary?.takeIf { it.id == request.threadId }
                ?: ThreadSummary(request.threadId, "待确认任务", request.workingDirectory.orEmpty())
            taskRow(profile, ws, summary, "wait", ws.results[summary.id], ws.conversations[summary.id], pending)
        }
    }.sortedWith(compareByDescending<JsonObject> { it["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L }
        .thenBy { it["key"]?.jsonPrimitive?.content.orEmpty() })
    // A newest-host timestamp would incorrectly claim that older/never-synced
    // hosts are current. The filter UI may use an individual host's timestamp.
    val lastSync = state.profiles.minOfOrNull { state.workspaces[it.id]?.lastSyncedAt ?: 0L } ?: 0L
    return buildJsonObject {
        put("version", 1)
        put("generatedAt", nowMillis)
        put("hosts", JsonArray(hosts))
        put("tasks", JsonArray(tasks))
        put("lastSyncAt", lastSync)
        put("syncing", state.profiles.any { profile ->
            state.workspaces[profile.id]?.let { it.loadingBoard || it.connection.busy } == true
        })
        put("hasMore", state.profiles.any { state.workspaces[it.id]?.let(::hasMore) == true })
    }
}

private fun boardSummaries(ws: HostWorkspace): List<ThreadSummary> {
    // Once an independent board fetch exists, never fold archive/search UI rows
    // back into it. A cold start can still show verified cached recent rows.
    val rows = ws.boardThreads ?: ws.threads
    return rows.filterNot { it.archived }.distinctBy { it.id }
}

private fun hasMore(ws: HostWorkspace): Boolean =
    (if (ws.boardThreads != null) ws.boardNextThreads else ws.nextThreads) != null

/** Latest authoritative turn receipts win over stale loaded-conversation flags. */
internal fun taskBucket(summary: ThreadSummary, receipt: ResultReceipt?, conversation: Conversation?): String? {
    if (summary.archived) return null
    val receiptBucket = bucket(receipt?.status).takeIf { !receipt?.turnId.isNullOrBlank() }
    if (receiptBucket != null) return receiptBucket
    if (summary.awaitingFirstMessage) return null
    // An unknown newer receipt must not inherit success from older cached output.
    if (!receipt?.turnId.isNullOrBlank()) {
        return if (summary.status in setOf("active", "inProgress") || conversation?.activeTurn == receipt?.turnId) "run" else null
    }
    if (conversation?.activeTurn != null) return "run"
    bucket(conversation?.status)?.takeIf { conversation?.latestTurnId != null }?.let { return it }
    return if (summary.status in setOf("active", "inProgress")) "run" else null
}

private fun bucket(status: String?): String? = when (status) {
    "inProgress", "active" -> "run"
    "completed" -> "done"
    "failed", "declined", "interrupted" -> "failed"
    else -> null
}

private fun taskRow(
    profile: HostProfile,
    ws: HostWorkspace,
    summary: ThreadSummary,
    state: String,
    receipt: ResultReceipt?,
    conversation: Conversation?,
    pending: PendingApproval? = null,
): JsonObject {
    val request = pending?.request
    val turnId = request?.params?.string("turnId") ?: receipt?.turnId?.takeIf { it.isNotBlank() }
        ?: conversation?.activeTurn ?: conversation?.latestTurnId ?: conversation?.items?.lastOrNull()?.turnId
    val items = conversation?.items.orEmpty().filter { it.turnId == turnId }
    val approvalItem = request?.params?.string("itemId")?.let { itemId -> items.lastOrNull { it.id == itemId } }
    val commandItem = if (state == "failed") items.lastOrNull { it.kind == "commandExecution" && it.failed }
        ?: items.lastOrNull { it.kind == "commandExecution" }
        else items.lastOrNull { it.kind == "commandExecution" }
    val detailCommand = if (pending != null) approvalItem?.takeIf { it.kind == "commandExecution" } else commandItem
    val command = request?.command ?: detailCommand?.commandExecution?.command
    val currentItem = items.lastOrNull { !it.complete && it.kind != "userMessage" }
    val current = when {
        currentItem?.kind == "commandExecution" -> currentItem.commandExecution?.command ?: "执行命令中"
        currentItem?.kind == "agentMessage" -> "正在撰写回复"
        currentItem?.kind == "reasoning" -> "正在分析任务"
        currentItem?.kind == "fileChange" -> "正在修改文件"
        currentItem?.kind == "plan" -> "正在更新计划"
        state == "run" -> "等待远端状态更新"
        else -> ""
    }
    val edits = if (pending != null) approvalItem?.fileEdits.orEmpty() else items.flatMap { it.fileEdits }
    val plan = conversation?.takeIf { it.planTurnId == turnId }?.plan.orEmpty()
    val output = detailCommand?.text.orEmpty()
    val key = listOf(profile.id, summary.id, request?.key.orEmpty()).joinToString("") { "${it.length}:$it" }
    return buildJsonObject {
        put("key", key)
        put("hostId", profile.id); put("hostName", hostName(profile))
        put("threadId", summary.id)
        put("title", summary.title.ifBlank { "未命名任务" })
        put("project", projectName(ws, summary.cwd)); put("path", summary.cwd)
        put("state", state)
        put("updatedAt", if (pending != null) pending.receivedAt / 1_000L
            else maxOf(summary.updatedAt, receipt?.updatedAt ?: 0L))
        put("connected", ws.connection == ConnectionStatus.ONLINE)
        put("submitting", pending?.submitted == true)
        put("unread", state == "done" && receipt?.unread == true && receipt.completedTurnId == turnId)
        put("interrupted", (receipt?.status?.takeIf { bucket(it) != null } ?: conversation?.status) == "interrupted")
        put("detailLoaded", items.isNotEmpty())
        put("hasReply", items.any { it.kind == "agentMessage" && it.complete && it.text.isNotBlank() })
        put("imageCount", items.filter { it.kind == "imageView" }.sumOf { it.imagePaths.size })
        request?.let {
            put("approvalKey", it.key)
            put("approvalKind", when (it.method) {
                "item/commandExecution/requestApproval" -> "command"
                "item/fileChange/requestApproval" -> "file"
                else -> "unsupported"
            })
            it.reason?.let { reason -> put("reason", reason) }
            it.workingDirectory?.let { directory -> put("workingDirectory", directory) }
            it.writeRoot?.let { root -> put("writeRoot", root) }
            it.networkHost?.let { host -> put("networkHost", host) }
        }
        command?.let { put("command", it) }
        put("current", current)
        put("output", output.take(4_096))
        put("outputTruncated", output.length > 4_096 || detailCommand?.commandExecution?.outputTruncated == true)
        detailCommand?.commandExecution?.exitCode?.let { put("exitCode", it) }
        detailCommand?.commandExecution?.durationMs?.let { put("durationMs", it) }
        put("files", fileSummary(edits))
        putJsonArray("plan") {
            plan.forEach { step -> add(buildJsonObject { put("step", step.step); put("status", step.status) }) }
        }
    }
}

private fun fileSummary(edits: List<FileEdit>): JsonArray = JsonArray(edits.groupBy { it.path }.map { (path, changes) ->
    buildJsonObject {
        put("path", path)
        val counts = changes.map { it.lineCounts }
        // A missing/unsupported diff is unknown, not a zero-line change.
        if (counts.all { it != null }) {
            put("added", counts.sumOf { it!!.first }); put("removed", counts.sumOf { it!!.second })
        }
    }
})

private fun hostName(profile: HostProfile): String = profile.name.ifBlank { profile.endpoint.host.ifBlank { "未命名服务器" } }

private fun projectName(ws: HostWorkspace, path: String): String =
    ws.projects.firstOrNull { it.path.trimEnd('/') == path.trimEnd('/') }?.name
        ?: path.trimEnd('/').substringAfterLast('/').ifBlank { if (path == "/") "/" else "未分组" }
