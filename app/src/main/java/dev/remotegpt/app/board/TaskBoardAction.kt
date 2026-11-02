package dev.remotegpt.app.board

import dev.remotegpt.client.string
import dev.remotegpt.client.wireJson
import kotlinx.serialization.json.JsonObject

/** Only these commands can cross the local HTML/native boundary. Never accept SSH credentials here. */
internal data class TaskBoardAction(
    val type: String,
    val hostId: String? = null,
    val threadId: String? = null,
    val approvalKey: String? = null,
    val decision: String? = null,
    val path: String = "",
)

internal fun parseTaskBoardAction(message: String): TaskBoardAction? {
    if (message.length > 16_384) return null
    val data = runCatching { wireJson.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return null
    val type = data.string("type") ?: return null
    if (type !in setOf("ready", "sync", "refresh", "hosts", "settings", "openTask", "approve", "newTask", "terminal", "files", "projects", "connect", "loadMore")) return null
    fun bounded(key: String, limit: Int) = data.string(key)?.takeIf { it.length <= limit && '\u0000' !in it }
    val host = bounded("hostId", 256)
    val thread = bounded("threadId", 512)
    val approval = bounded("approvalKey", 2048)
    val decision = bounded("decision", 16)
    val path = if ("path" in data) bounded("path", 4096) ?: return null else ""
    if (type in setOf("openTask", "approve", "newTask", "terminal", "files", "projects", "connect", "loadMore") && host.isNullOrBlank()) return null
    if (type in setOf("openTask", "approve") && thread.isNullOrBlank()) return null
    if (type == "approve" && (approval.isNullOrBlank() || decision !in setOf("accept", "decline"))) return null
    if ((path.isNotEmpty() && !path.startsWith('/')) || '\n' in path || '\r' in path) return null
    return TaskBoardAction(type, host, thread, approval, decision, path)
}
