package dev.remotegpt.app

/** Connection-scoped, monotonic polling schedule. Push events still apply immediately.
 * Idle loaded threads need occasional checks: another CLI can start a new turn on
 * an old loaded thread without notifying a client that never resumed it. */
internal class SessionPollSchedule {
    private val attempted = mutableMapOf<String, Long>()
    private val retryAt = mutableMapOf<String, Long>()
    private val failures = mutableMapOf<String, Int>()
    private var previouslyLoaded: Set<String> = emptySet()

    fun targets(loaded: Set<String>, urgent: Set<String>, now: Long): Set<String> {
        attempted.keys.retainAll(loaded + urgent)
        retryAt.keys.retainAll(loaded + urgent)
        failures.keys.retainAll(loaded + urgent)
        val newlyLoaded = loaded - previouslyLoaded
        previouslyLoaded = loaded
        val idle = (loaded - urgent).filter { id ->
            retryAt[id]?.let { now >= it } ?: (attempted[id]?.let { now - it >= 30_000 } ?: true)
        }.sortedWith(compareBy<String> { if (it in newlyLoaded) 0 else 1 }
            .thenBy { attempted[it] ?: Long.MIN_VALUE }).take(8)
        return urgent + idle
    }

    fun attempted(id: String, now: Long) { attempted[id] = now }
    fun confirmed(id: String) { retryAt.remove(id); failures.remove(id) }
    fun retry(id: String, now: Long) {
        val count = (failures[id] ?: 0) + 1
        failures[id] = count
        // A newly loaded thread can precede its persisted first turn. Retry that
        // window promptly, but do not poll permanently empty/broken threads forever.
        retryAt[id] = now + if (count <= 3) 3_000 else 30_000
    }
}
