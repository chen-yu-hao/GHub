package dev.remotegpt.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owned by one live connection; call from its single state dispatcher.
 * Persisted history alone never proves that this connection is subscribed. */
class SessionSync {
    private class Entry {
        val subscriptionLock = Mutex()
        val historyLock = Mutex()
        var generation = 0L
        var subscribed = false
        var revision = 0L
        var appliedRevision = -1L
    }
    private val entries = mutableMapOf<String, Entry>()
    private fun entry(id: String) = entries.getOrPut(id) { Entry() }
    fun isSubscribed(id: String) = entries[id]?.subscribed == true
    fun isCurrent(id: String) = entries[id]?.let { it.subscribed && it.appliedRevision == it.revision } == true
    fun created(id: String) { entry(id).apply { subscribed = true; appliedRevision = revision } }
    fun dirty(id: String) { entry(id).revision++ }
    fun clearHistory() { entries.values.forEach { it.revision++ } }
    fun closed(id: String) { entry(id).apply { generation++; subscribed = false; revision++ } }

    /** Concurrent opens share the subscription. Failure/empty fallback is never cached as a subscription. */
    suspend fun subscribe(id: String, attach: suspend () -> Boolean) {
        val e = entry(id)
        e.subscriptionLock.withLock {
            if (e.subscribed) return
            val generation = e.generation
            if (attach() && generation == e.generation) e.subscribed = true
        }
    }

    /** Coalesce identical repairs. An invalidation arriving during a read remains dirty.
     * Older pages use the same lock but never claim that the latest history is current. */
    suspend fun history(id: String, more: Boolean = false, read: suspend () -> Boolean) {
        val e = entry(id)
        e.historyLock.withLock {
            if (more) { read(); return }
            while (e.appliedRevision != e.revision) {
                val revision = e.revision
                if (!read()) return
                e.appliedRevision = revision
            }
        }
    }
}
