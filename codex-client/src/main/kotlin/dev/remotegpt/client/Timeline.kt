package dev.remotegpt.client

import kotlinx.serialization.json.*

object Timeline {
    /** Collapse repeated records of the same turn/item, preserving their first position. */
    fun normalized(conversation: Conversation): Conversation {
        val items = linkedMapOf<String, TimelineItem>()
        conversation.items.forEach { item ->
            val previous = items[item.identityKey]
            if (previous == null || !previous.complete || item.complete) items[item.identityKey] = item
        }
        return conversation.copy(items = items.values.toList())
    }

    fun parseItem(raw: JsonObject, turn: String, complete: Boolean): TimelineItem {
        val type = raw.string("type") ?: "unknown"
        val text = when (type) {
            "agentMessage" -> raw.string("text").orEmpty()
            "userMessage" -> raw.array("content").mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("\n")
            "commandExecution" -> raw.string("aggregatedOutput").orEmpty()
            "fileChange" -> raw.array("changes").joinToString("\n\n") { c -> c.jsonObject.string("path").orEmpty() + "\n" + c.jsonObject.string("diff").orEmpty() }
            "imageView" -> ""
            "plan" -> raw.string("text").orEmpty()
            "reasoning" -> raw.array("summary").joinToString("\n") { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
            else -> "此类型暂未完整显示：$type"
        }
        val images = buildList {
            raw.array("content").mapNotNull { (it as? JsonObject)?.takeIf { v -> v.string("type") == "localImage" }?.string("path") }
                .forEach(::add)
            raw.string("path")?.takeIf { type == "imageView" }?.let(::add)
        }
        val files = if (type == "fileChange") raw.array("changes").mapNotNull { change ->
            val entry = change as? JsonObject ?: return@mapNotNull null
            entry.string("path")?.let { FileEdit(it, entry.string("diff").orEmpty()) }
        } else emptyList()
        val failed = raw.string("status") in setOf("failed", "declined") ||
            (raw["exitCode"] as? JsonPrimitive)?.intOrNull?.let { it != 0 } == true
        return TimelineItem(raw.string("id") ?: error("Item without ID"), turn, type, text, complete, images, files, failed,
            executionStatus = raw.string("status"),
            commandExecution = if (type == "commandExecution") CommandExecutionDetails(
                command = raw.string("command"),
                workingDirectory = raw.string("cwd"),
                exitCode = (raw["exitCode"] as? JsonPrimitive)?.intOrNull,
                durationMs = (raw["durationMs"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 },
            ) else null)
    }

    fun history(threadId: String, page: JsonObject): Conversation {
        val turns = page.array("data").map { it.jsonObject }.reversed()
        val active = turns.lastOrNull { it.string("status") == "inProgress" }
        return normalized(Conversation(threadId, turns.flatMap { turn ->
            turn.array("items").map { parseItem(it.jsonObject, turn.string("id").orEmpty(), turn.string("status") != "inProgress") }
        }, active?.string("id"), page.string("nextCursor"), if (active != null) "active" else "idle"))
    }

    fun notification(state: Conversation, method: String, params: JsonObject): Conversation {
        if (params.string("threadId") != state.threadId) return state
        return when (method) {
            "turn/started" -> state.copy(activeTurn = params.obj("turn").string("id"), status = "active")
            "turn/completed" -> {
                val turn = params.obj("turn")
                val id = turn.string("id")
                var items = state.items.map { if (it.turnId == id) it.copy(complete = true) else it }
                turn.array("items").forEach { raw -> items = upsert(items, parseItem(raw.jsonObject, id.orEmpty(), true)) }
                state.copy(items = items, activeTurn = null, status = turn.string("status") ?: "completed")
            }
            "thread/status/changed" -> state.copy(status = params.obj("status").string("type") ?: state.status)
            "item/started", "item/completed" -> state.copy(items = upsert(state.items,
                parseItem(params.obj("item"), params.string("turnId").orEmpty(), method == "item/completed")))
            "item/agentMessage/delta", "item/commandExecution/outputDelta", "item/plan/delta", "item/reasoning/summaryTextDelta" -> {
                val id = params.string("itemId") ?: return state
                val turn = params.string("turnId").orEmpty()
                val current = state.items.firstOrNull { it.id == id && it.turnId == turn }
                    ?: TimelineItem(id, turn, if (method.contains("commandExecution")) "commandExecution" else "agentMessage", "",
                        commandExecution = if (method.contains("commandExecution")) CommandExecutionDetails() else null)
                if (current.complete) state else state.copy(items = upsert(state.items, current.copy(text = current.text + params.string("delta").orEmpty())))
            }
            else -> state
        }
    }

    fun older(current: Conversation, page: Conversation): Conversation {
        val ids = current.items.map { it.identityKey }.toSet()
        return normalized(current.copy(items = page.items.filterNot { it.identityKey in ids } + current.items, nextCursor = page.nextCursor))
    }

    /** Keep older pages and events received while the history request was in flight. */
    fun refresh(before: Conversation, live: Conversation, page: Conversation): Conversation {
        val pageTurns = page.items.map { it.turnId }.toSet()
        val overlap = live.items.indexOfFirst { it.turnId in pageTurns }
        val older = if (page.nextCursor != null && overlap > 0) live.items.take(overlap) else emptyList()
        val beforeItems = before.items.associateBy { it.identityKey }
        val liveItems = live.items.associateBy { it.identityKey }
        val snapshotIds = page.items.map { it.identityKey }.toSet()
        val items = page.items.map { snapshot ->
            val current = liveItems[snapshot.identityKey]
            when {
                snapshot.complete -> snapshot
                current != null && current != beforeItems[current.identityKey] -> current
                current != null && current.text.startsWith(snapshot.text) -> current
                else -> snapshot
            }
        }
        val arrived = live.items.filter { it.identityKey !in snapshotIds && it.identityKey !in beforeItems }
        return normalized(page.copy(
            items = older + items + arrived,
            nextCursor = if (older.isNotEmpty()) live.nextCursor else page.nextCursor,
            activeTurn = if (live.activeTurn != before.activeTurn) live.activeTurn else page.activeTurn,
            status = if (live.status != before.status) live.status else page.status,
        ))
    }

    private fun upsert(items: List<TimelineItem>, item: TimelineItem): List<TimelineItem> {
        val index = items.indexOfFirst { it.identityKey == item.identityKey }
        if (index < 0) return items + item
        // A delayed item/started must not erase completed output.
        if (items[index].complete && !item.complete) return items
        return items.toMutableList().also { it[index] = item }
    }
}
