package dev.remotegpt.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CommandTimelineTest {
    private val command = "printf '%s\\n' \"\$HOME\"\n\tgit status --short"
    private fun event(output: String, status: String = "inProgress") = buildJsonObject {
        put("threadId", "thread"); put("turnId", "turn")
        putJsonObject("item") {
            put("id", "command"); put("type", "commandExecution"); put("command", command)
            put("aggregatedOutput", output); put("status", status); put("cwd", "/tmp/a b")
            if (status == "completed") { put("exitCode", 0); put("durationMs", 1234) }
        }
    }
    private fun delta(text: String) = buildJsonObject {
        put("threadId", "thread"); put("turnId", "turn"); put("itemId", "command"); put("delta", text)
    }

    @Test fun `streaming output stays separate from the exact command and final metadata`() {
        val started = Timeline.notification(Conversation("thread"), "item/started", event(""))
        val partial = Timeline.notification(started, "item/commandExecution/outputDelta", delta("hello\n"))
        assertEquals("hello\n", partial.items.single().text)
        assertEquals(command, partial.items.single().commandExecution?.command)
        assertNull(partial.items.single().commandExecution?.exitCode)
        val finished = Timeline.notification(partial, "item/completed", event("hello\nworld\n", "completed"))
        val item = finished.items.single()
        assertEquals("hello\nworld\n", item.text)
        assertEquals(CommandExecutionDetails(command, "/tmp/a b", 0, 1234), item.commandExecution)
        assertEquals(finished, Timeline.notification(finished, "item/commandExecution/outputDelta", delta("late")))
        assertEquals(finished, Timeline.notification(finished, "item/started", event("")))
        assertEquals(finished, wireJson.decodeFromString<Conversation>(wireJson.encodeToString(finished)))
    }

    @Test fun `missing start and legacy cache never infer a command from output`() {
        val partial = Timeline.notification(Conversation("thread"), "item/commandExecution/outputDelta", delta("not a command\n"))
        assertNotNull(partial.items.single().commandExecution)
        assertNull(partial.items.single().commandExecution?.command)
        val old = wireJson.decodeFromString<TimelineItem>("""{"id":"i","turnId":"t","kind":"commandExecution","text":"echo x\nx\ncompleted","complete":true}""")
        assertNull(old.commandExecution)
        assertEquals("echo x\nx\ncompleted", old.text)
    }

    @Test fun `history failure exit code remains visible and does not pollute output`() {
        val item = Timeline.parseItem(buildJsonObject {
            put("id", "command"); put("type", "commandExecution"); put("command", command)
            put("aggregatedOutput", "error\n"); put("status", "completed"); put("exitCode", 2)
        }, "turn", true)
        assertTrue(item.failed)
        assertEquals(2, item.commandExecution?.exitCode)
        assertEquals("error\n", item.text)
    }

    @Test fun `history refresh preserves output deltas received while loading`() {
        val before = Timeline.notification(Conversation("thread"), "item/started", event("one\n"))
        val live = Timeline.notification(before, "item/commandExecution/outputDelta", delta("two\n"))
        val refreshed = Timeline.refresh(before, live, before)
        assertEquals("one\ntwo\n", refreshed.items.single().text)
        assertEquals(command, refreshed.items.single().commandExecution?.command)
    }
}
