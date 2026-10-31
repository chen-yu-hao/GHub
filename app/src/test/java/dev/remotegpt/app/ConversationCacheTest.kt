package dev.remotegpt.app

import dev.remotegpt.app.data.*
import dev.remotegpt.client.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ConversationCacheTest {
    @Test fun longMarkdownSurvivesDiskChunksWithFencesAndUnicodeIntact() {
        val text = "# Long reply\n\n```text\n" + "中文 😀 `code`\n".repeat(20_000) + "```\n\n[link][ref]\n\n[ref]: https://example.com"
        val original = Conversation("thread", listOf(TimelineItem("reply", "turn", "agentMessage", text, true)))
        val rows = encodeConversationCache(original)
        assertTrue(rows.size > 2)
        // Exercise the UTF-8 round-trip performed by SQLite, not only Kotlin strings.
        val stored = rows.map { it.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) }
        assertEquals(original, decodeConversationCache(stored))
        assertTrue(rows.all { it.toByteArray(Charsets.UTF_8).size < 131_072 })
        assertNull(decodeConversationCache(rows.dropLast(1)))
    }

    @Test fun existingInstalledCachesStillLoad() {
        val one = Conversation("t", listOf(TimelineItem("one", "turn", "userMessage", "Hello", true)))
        val two = Conversation("t", listOf(TimelineItem("two", "turn", "agentMessage", "**Answer**", true)))
        assertEquals(one.items + two.items, decodeConversationCache(listOf(wireJson.encodeToString(one), wireJson.encodeToString(two)))?.items)
    }
}
