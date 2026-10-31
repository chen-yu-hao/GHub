package dev.remotegpt.app

import dev.remotegpt.app.data.RemoteImageCache
import dev.remotegpt.client.*
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceLimitsTest {
    @Test fun historyEvictsLeastRecentlyViewedButKeepsPinnedAndActive() {
        val budget = ConversationMemoryBudget()
        val conversations = (1..12).associate { "$it" to Conversation("$it", activeTurn = if (it == 2) "turn" else null) }
        conversations.keys.forEach(budget::touch)
        budget.touch("3")
        assertEquals(listOf("4"), budget.evictions(conversations, setOf("1", "6")))
        assertEquals(setOf("3", "4", "5", "7", "8", "9", "10", "11", "12"), budget.evictions(conversations, setOf("1", "6"), true).toSet())
    }
    @Test fun oneLargeInactiveConversationIsEvictedWithoutTruncation() {
        val large = Conversation("big", listOf(TimelineItem("i", "t", "agentMessage", "x".repeat(4_000_001))))
        assertEquals(listOf("big"), ConversationMemoryBudget().evictions(mapOf("big" to large), emptySet()))
        assertTrue(ConversationMemoryBudget().evictions(mapOf("big" to large), setOf("big")).isEmpty())
        assertEquals(4_000_001, large.items.single().text.length)
    }
    @Test fun backgroundGraceCancelsForForegroundAndKeepalive() = runTest {
        var closes = 0
        val release = BackgroundRelease(this) { closes++ }
        release.update(false)
        advanceTimeBy(29_999)
        release.update(true)
        advanceTimeBy(60_000)
        assertEquals(0, closes)
        release.update(false)
        advanceTimeBy(20_000)
        release.update(false) // repeated stop must not extend the grace indefinitely
        advanceTimeBy(10_001)
        assertEquals(1, closes)
        release.update(true)
        release.update(false)
        advanceTimeBy(30_001)
        assertEquals(2, closes)
    }
    @Test fun imageCacheLimitsBytesAndCountAndProtectsVisibleImage() = runBlocking {
        val directory = Files.createTempDirectory("images").toFile()
        try {
            val cache = RemoteImageCache(directory, maxBytes = 10, maxFiles = 2)
            suspend fun load(key: String, protected: String? = null) = cache.get(key, { protected }) { it.write(ByteArray(4)) }
            val first = load("one")
            load("two")
            val third = load("three", first.file.absolutePath)
            assertEquals(setOf(first.file.absolutePath, third.file.absolutePath), third.retained)
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 10)
            cache.get("one", { null }) { error("Cached image must not download again") }
            cache.clear()
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun cancelledImageDownloadLeavesNoPartialFile() = runBlocking {
        val directory = Files.createTempDirectory("images").toFile()
        try {
            val cache = RemoteImageCache(directory)
            try { cache.get("broken", { null }) { it.write(byteArrayOf(1)); throw CancellationException() }; fail() }
            catch (_: CancellationException) { }
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
