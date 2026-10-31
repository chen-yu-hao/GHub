package dev.remotegpt.app

/** Aggregate local operation names only. No host, path, thread ID, arguments or message bodies. */
internal class SyncTimings {
    private data class Sample(var count: Long = 0, var total: Long = 0, var max: Long = 0)
    private val samples = sortedMapOf<String, Sample>()
    @Synchronized fun add(operation: String, milliseconds: Long) {
        val sample = samples.getOrPut(operation) { Sample() }
        sample.count++
        sample.total += milliseconds.coerceAtLeast(0)
        sample.max = maxOf(sample.max, milliseconds)
    }
    @Synchronized fun count(operation: String) = samples[operation]?.count ?: 0L
    @Synchronized fun report() = samples.entries.joinToString("\n") { (name, s) ->
        "$name count=${s.count} mean_ms=${s.total / s.count} max_ms=${s.max}"
    }
}
