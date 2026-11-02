package dev.remotegpt.app.board

import dev.remotegpt.app.*
import dev.remotegpt.client.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TaskBoardSnapshotTest {
    private val host = HostProfile(id = "host", name = "开发机", endpoint = SshEndpoint(host = "example.test", username = "dev"))
    private fun summary(id: String = "thread", status: String = "idle") = ThreadSummary(id, "实现任务", "/work/project", status, updatedAt = 100)
    private fun snapshot(ws: HostWorkspace) = taskBoardSnapshot(WorkbenchState(profiles = listOf(host), workspaces = mapOf(host.id to ws)), 200_000)
    private fun tasks(snapshot: JsonObject) = snapshot.getValue("tasks").jsonArray.map { it.jsonObject }
    private fun request(id: Int, method: String = "item/commandExecution/requestApproval", thread: String = "thread", command: String = "git status") =
        ServerRequest("epoch", JsonPrimitive(id), method, buildJsonObject {
            put("threadId", thread); put("turnId", "turn"); put("itemId", "command"); put("command", command)
        })

    @Test fun idleUnloadedAndEmptySessionsAreNotSuccessfulTasks() {
        val ws = HostWorkspace(threads = listOf(summary("idle"), summary("unknown", "notLoaded"),
            summary("draft").copy(awaitingFirstMessage = true)),
            conversations = mapOf("idle" to Conversation("idle", items = listOf(TimelineItem("text", "old", "agentMessage", "旧消息", true)))))
        assertTrue(tasks(snapshot(ws)).isEmpty())
    }

    @Test fun latestReceiptWinsOverStaleRunningFlagsAndRecoveredCommandFailures() {
        val conversation = Conversation("thread", listOf(TimelineItem("cmd", "turn", "commandExecution", "first attempt failed", true, failed = true)),
            activeTurn = "turn", status = "active")
        val ws = HostWorkspace(threads = listOf(summary(status = "active")), conversations = mapOf("thread" to conversation),
            results = mapOf("thread" to ResultReceipt("turn", "completed", 100, "turn", null)))
        val row = tasks(snapshot(ws)).single()
        assertEquals("done", row.string("state")); assertTrue(row.getValue("unread").jsonPrimitive.boolean)
    }

    @Test fun completedAndInterruptedAreDifferentEvenWithAnOlderUnreadResult() {
        val ws = HostWorkspace(threads = listOf(summary()), results = mapOf("thread" to ResultReceipt("new", "interrupted", 100, "old", null)))
        val row = tasks(snapshot(ws)).single()
        assertEquals("failed", row.string("state")); assertTrue(row.getValue("interrupted").jsonPrimitive.boolean)
        assertFalse(row.getValue("unread").jsonPrimitive.boolean)
    }

    @Test fun unknownNewerTurnCannotReuseOlderCompletion() {
        val ws = HostWorkspace(threads = listOf(summary()), results = mapOf("thread" to ResultReceipt("new", "unknown")),
            conversations = mapOf("thread" to Conversation("thread", status = "completed", latestTurnId = "old")))
        assertTrue(tasks(snapshot(ws)).isEmpty())
    }

    @Test fun latestRunningReceiptDoesNotReuseOldCompletedPlanOrCommand() {
        val ws = HostWorkspace(threads = listOf(summary()), results = mapOf("thread" to ResultReceipt("new", "inProgress")),
            conversations = mapOf("thread" to Conversation("thread",
                items = listOf(TimelineItem("oldcmd", "old", "commandExecution", "done", true,
                    commandExecution = CommandExecutionDetails("rm obsolete-file", exitCode = 0, durationMs = 20))),
                latestTurnId = "old", planTurnId = "old", plan = listOf(PlanStep("旧任务", "completed")))))
        val row = tasks(snapshot(ws)).single()
        assertEquals("run", row.string("state")); assertTrue(row.getValue("plan").jsonArray.isEmpty())
        assertNull(row["command"]); assertNull(row["exitCode"]); assertNull(row["durationMs"])
    }

    @Test fun eachApprovalGetsOneWaitingRowAndHidesRunningDuplicate() {
        val ws = HostWorkspace(connection = ConnectionStatus.ONLINE, threads = listOf(summary(status = "active")),
            results = mapOf("thread" to ResultReceipt("turn", "inProgress")),
            approvals = listOf(PendingApproval(request(1), receivedAt = 150_000), PendingApproval(request(2), receivedAt = 160_000)))
        val rows = tasks(snapshot(ws))
        assertEquals(2, rows.size); assertEquals(setOf("wait"), rows.map { it.string("state") }.toSet())
        assertEquals(2, rows.map { it.string("key") }.toSet().size)
        assertEquals(160L, rows.first().getValue("updatedAt").jsonPrimitive.long)
    }

    @Test fun disconnectedTasksKeepTheirOutcomeAndCannotApprove() {
        val ws = HostWorkspace(connection = ConnectionStatus.OFFLINE, threads = listOf(summary()),
            approvals = listOf(PendingApproval(request(1), submitted = true)))
        val row = tasks(snapshot(ws)).single()
        assertEquals("wait", row.string("state")); assertFalse(row.getValue("connected").jsonPrimitive.boolean)
        assertTrue(row.getValue("submitting").jsonPrimitive.boolean)
    }

    @Test fun boardListingIsIndependentOfArchiveAndSearchScreens() {
        val ws = HostWorkspace(threads = listOf(summary("archived").copy(archived = true), summary("search")), nextThreads = "wrong-cursor",
            boardThreads = listOf(summary("board")), boardNextThreads = null,
            results = listOf("archived", "search", "board").associateWith { ResultReceipt("turn", "completed") })
        val result = snapshot(ws)
        assertEquals(listOf("board"), tasks(result).map { it.string("threadId") })
        assertFalse(result.getValue("hasMore").jsonPrimitive.boolean)
    }

    @Test fun sameThreadIdsAcrossHostsStayIndependentAndNewHostPreventsFalseGlobalSyncClaim() {
        val other = host.copy(id = "other", name = "另一台服务器")
        val completed = HostWorkspace(threads = listOf(summary()), results = mapOf("thread" to ResultReceipt("turn", "completed")), lastSyncedAt = 200_000)
        val running = HostWorkspace(threads = listOf(summary(status = "active")), lastSyncedAt = 0)
        val result = taskBoardSnapshot(WorkbenchState(profiles = listOf(host, other), workspaces = mapOf(host.id to completed, other.id to running)), 200_000)
        val rows = tasks(result)
        assertEquals(2, rows.size); assertEquals(2, rows.map { it.string("key") }.toSet().size)
        assertEquals(setOf("done", "run"), rows.map { it.string("state") }.toSet())
        assertEquals(0L, result.getValue("lastSyncAt").jsonPrimitive.long)
    }

    @Test fun rawRemoteStringsRoundTripAsDataAndCredentialsNeverReachPage() {
        val payload = "</script><img src=x onerror=alert(1)>\n\"quoted\""
        val ws = HostWorkspace(threads = listOf(summary().copy(title = payload)),
            approvals = listOf(PendingApproval(request(1, command = payload))))
        val result = snapshot(ws)
        val decoded = Json.parseToJsonElement(result.toString()).jsonObject
        val row = tasks(decoded).single()
        assertEquals(payload, row.string("title")); assertEquals(payload, row.string("command"))
        assertFalse(result.toString().contains(host.endpoint.credentialId))
        assertFalse(result.toString().contains("privateKey")); assertFalse(result.toString().contains("passphrase"))
    }

    @Test fun approvalCannotBorrowAnUnrelatedCommandOrFileDiffFromSameTurn() {
        val current = TimelineItem("unrelated", "turn", "commandExecution", "", true,
            commandExecution = CommandExecutionDetails("unrelated destructive operation", exitCode = 0))
        val fileApproval = request(1, "item/fileChange/requestApproval").let { it.copy(params = JsonObject(it.params - "command")) }
        val ws = HostWorkspace(threads = listOf(summary()), conversations = mapOf("thread" to Conversation("thread", listOf(current))),
            approvals = listOf(PendingApproval(fileApproval)))
        val row = tasks(snapshot(ws)).single()
        assertNull(row["command"]); assertNull(row["exitCode"]); assertTrue(row.getValue("files").jsonArray.isEmpty())
    }

    @Test fun actualDiffCountsAreReportedButMissingDiffStaysUnknown() {
        val item = TimelineItem("files", "turn", "fileChange", "", true, fileEdits = listOf(
            FileEdit("known.kt", "@@ -1 +1 @@\n-old\n+new\n+another"), FileEdit("unknown.kt", "server omitted diff")))
        val ws = HostWorkspace(threads = listOf(summary()), conversations = mapOf("thread" to Conversation("thread", listOf(item))),
            results = mapOf("thread" to ResultReceipt("turn", "completed")))
        val files = tasks(snapshot(ws)).single().getValue("files").jsonArray.map { it.jsonObject }
        assertEquals(2, files.first().getValue("added").jsonPrimitive.int)
        assertEquals(1, files.first().getValue("removed").jsonPrimitive.int)
        assertNull(files.last()["added"]); assertNull(files.last()["removed"])
    }

    @Test fun knownCompletionWithoutLoadedHistoryDoesNotInventDetailsOrPlans() {
        val ws = HostWorkspace(threads = listOf(summary()), results = mapOf("thread" to ResultReceipt("turn", "completed")))
        val row = tasks(snapshot(ws)).single()
        assertEquals("done", row.string("state")); assertFalse(row.getValue("detailLoaded").jsonPrimitive.boolean)
        assertTrue(row.getValue("plan").jsonArray.isEmpty()); assertNull(row["durationMs"])
    }

    @Test fun sshOnlyConnectionDoesNotMasqueradeAsSyncedCodexHost() {
        val result = snapshot(HostWorkspace(connection = ConnectionStatus.ERROR, sshConnected = true, error = "Codex unavailable"))
        val projected = result.getValue("hosts").jsonArray.single().jsonObject
        assertTrue(projected.getValue("sshConnected").jsonPrimitive.boolean)
        assertEquals("ERROR", projected.string("connection")); assertEquals(0L, result.getValue("lastSyncAt").jsonPrimitive.long)
    }

    @Test fun unsupportedApprovalRemainsVisibleWithoutPretendingItIsACommandApproval() {
        val ws = HostWorkspace(approvals = listOf(PendingApproval(request(1, "unsupported/request"))))
        val row = tasks(snapshot(ws)).single()
        assertEquals("wait", row.string("state")); assertEquals("unsupported", row.string("approvalKind"))
    }
}
