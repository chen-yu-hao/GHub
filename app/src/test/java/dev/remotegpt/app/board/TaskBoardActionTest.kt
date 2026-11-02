package dev.remotegpt.app.board

import org.junit.Assert.*
import org.junit.Test

class TaskBoardActionTest {
    @Test fun approvalsRequireHostThreadAndExactSingleRequestDecision() {
        val valid = """{"type":"approve","hostId":"server-a","threadId":"task-1","approvalKey":"epoch:5","decision":"accept"}"""
        assertEquals("accept", parseTaskBoardAction(valid)?.decision)
        assertNull(parseTaskBoardAction(valid.replace("accept", "acceptForSession")))
        assertNull(parseTaskBoardAction(valid.replace("\"hostId\":\"server-a\",", "")))
        assertNull(parseTaskBoardAction(valid.replace("\"threadId\":\"task-1\",", "")))
    }

    @Test fun arbitraryNativeCallsAndMalformedMessagesAreRejected() {
        assertNull(parseTaskBoardAction("""{"type":"exec","command":"rm -rf /"}"""))
        assertNull(parseTaskBoardAction("[]"))
        assertNull(parseTaskBoardAction("not-json"))
        assertNull(parseTaskBoardAction(" ".repeat(16_385)))
        assertNull(parseTaskBoardAction("""{"type":"terminal","hostId":"h","path":"relative/path"}"""))
    }

    @Test fun terminalAcceptsQuotedUnicodeAbsolutePathsAsData() {
        val action = parseTaskBoardAction("""{"type":"terminal","hostId":"h","path":"/home/dev/项目's folder"}""")
        assertEquals("/home/dev/项目's folder", action?.path)
        assertEquals("h", action?.hostId)
    }

    @Test fun invalidProvidedPathNeverFallsBackToADifferentDirectory() {
        assertNull(parseTaskBoardAction("""{"type":"terminal","hostId":"h","path":null}"""))
        assertNull(parseTaskBoardAction("""{"type":"terminal","hostId":"h","path":"/one\ntwo"}"""))
        assertNull(parseTaskBoardAction("""{"type":"terminal","hostId":"h","path":"/one\u0000two"}"""))
        assertNull(parseTaskBoardAction("{\"type\":\"terminal\",\"hostId\":\"h\",\"path\":\"/" + "x".repeat(4096) + "\"}"))
    }
}
