package dev.remotegpt.client

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*

/** Bounded staging off the socket reader. Responses never wait for the UI consumer. */
internal class OrderedRpcEvents(private val overflow: () -> Unit) {
    val output = Channel<RpcEvent>(1)
    private data class Entry(val event: RpcEvent, val size: Int)
    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var characters = 0
    private var ended = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            for (ignored in wake) {
                // A short batch window reduces rendering and cache writes during streaming.
                // All non-text events remain in their original position, including approvals.
                if (synchronized(lock) { queue.firstOrNull()?.event.isTextDelta() }) delay(75)
                val batch = synchronized(lock) {
                    buildList {
                        var size = 0
                        while (queue.isNotEmpty() && this.size < 128 && size < 262_144) {
                            val entry = queue.removeFirst()
                            characters -= entry.size
                            size += entry.size
                            add(entry.event)
                        }
                        if (queue.isNotEmpty()) wake.trySend(Unit)
                    }
                }
                for (event in batch) output.send(event)
            }
        }
    }

    fun offer(event: RpcEvent, size: Int) {
        val full = synchronized(lock) {
            if (ended) return
            if (characters + size > 2_097_152) true else {
                val last = queue.lastOrNull()
                val merged = last?.let { merge(it.event, event) }
                if (merged != null && last.size + size <= 262_144) {
                    queue.removeLast()
                    queue.addLast(Entry(merged, last.size + size))
                } else {
                    if (queue.size >= 1024) return@synchronized true
                    queue.addLast(Entry(event, size))
                }
                characters += size
                wake.trySend(Unit)
                false
            }
        }
        if (full) overflow()
    }

    fun close(reason: Throwable?) {
        synchronized(lock) { ended = true; queue.clear(); characters = 0 }
        wake.close()
        scope.cancel()
        // RpcClient retains the reason even if the output channel has no room.
        output.trySend(RpcEvent.Disconnected(reason))
        output.close()
    }

    private fun RpcEvent?.isTextDelta() = this is RpcEvent.Notification && method in textMethods
    private fun merge(a: RpcEvent, b: RpcEvent): RpcEvent.Notification? {
        if (!a.isTextDelta() || b !is RpcEvent.Notification) return null
        a as RpcEvent.Notification
        if (a.method != b.method || a.params.string("delta") == null || b.params.string("delta") == null) return null
        if (listOf("threadId", "turnId", "itemId").any { a.params.string(it).isNullOrBlank() }) return null
        // Includes summary index and any future metadata; never merge across an event barrier.
        if (a.params - "delta" != b.params - "delta") return null
        return a.copy(params = JsonObject(a.params + ("delta" to JsonPrimitive(a.params.string("delta")!! + b.params.string("delta")!!))))
    }

    private companion object {
        val textMethods = setOf("item/agentMessage/delta", "item/commandExecution/outputDelta", "item/plan/delta", "item/reasoning/summaryTextDelta")
    }
}
