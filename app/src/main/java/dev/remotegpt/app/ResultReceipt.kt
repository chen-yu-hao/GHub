package dev.remotegpt.app

import kotlinx.serialization.Serializable

/** Per-host, per-thread observations. Completion and viewing are separate facts. */
@Serializable data class ResultReceipt(
    val turnId: String = "", val status: String = "", val updatedAt: Long = 0,
    val completedTurnId: String? = null, val seenTurnId: String? = null,
) {
    val unread: Boolean get() = completedTurnId != null && completedTurnId != seenTurnId
}

fun observeResult(previous: ResultReceipt?, turnId: String, status: String, updatedAt: Long, liveEvent: Boolean = false): ResultReceipt {
    val completed = turnId.takeIf { it.isNotBlank() && status == "completed" }
    return ResultReceipt(turnId, status, updatedAt,
        completedTurnId = completed ?: previous?.completedTurnId,
        // First synchronization establishes a baseline, not a backlog of unread history.
        seenTurnId = if (previous == null && !liveEvent) completed else previous?.seenTurnId)
}
