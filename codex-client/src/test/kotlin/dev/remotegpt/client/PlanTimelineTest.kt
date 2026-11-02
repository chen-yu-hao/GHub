package dev.remotegpt.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class PlanTimelineTest {
    private fun start(turn: String) = buildJsonObject {
        put("threadId", "thread"); putJsonObject("turn") { put("id", turn) }
    }

    private fun plan(turn: String, vararg steps: Pair<String, String>) = buildJsonObject {
        put("threadId", "thread"); put("turnId", turn); put("explanation", "真实计划")
        putJsonArray("plan") {
            steps.forEach { (text, status) -> add(buildJsonObject { put("step", text); put("status", status) }) }
        }
    }

    private fun history(turn: String) = buildJsonObject {
        putJsonArray("data") {
            add(buildJsonObject { put("id", turn); put("status", "completed"); put("items", JsonArray(emptyList())) })
        }
    }

    @Test fun `structured plan retains server steps and unknown status without fabricated progress`() {
        val started = Timeline.notification(Conversation("thread"), "turn/started", start("one"))
        val updated = Timeline.notification(started, "turn/plan/updated",
            plan("one", "检查" to "completed", "实现" to "inProgress", "验证" to "pending", "扩展状态" to "paused"))
        assertEquals("one", updated.planTurnId)
        assertEquals(listOf("completed", "inProgress", "pending", "paused"), updated.plan.map { it.status })
        assertEquals("真实计划", updated.planExplanation)
        assertEquals(updated, wireJson.decodeFromString<Conversation>(wireJson.encodeToString(updated)))
    }

    @Test fun `a new turn clears old progress and late old plan cannot return`() {
        val one = Timeline.notification(Conversation("thread", activeTurn = "one", latestTurnId = "one"),
            "turn/plan/updated", plan("one", "旧任务" to "completed"))
        val two = Timeline.notification(one, "turn/started", start("two"))
        assertTrue(two.plan.isEmpty()); assertNull(two.planTurnId); assertNull(two.planExplanation)
        assertEquals(two, Timeline.notification(two, "turn/plan/updated", plan("one", "旧任务" to "pending")))
    }

    @Test fun `duplicate turn started preserves current plan and empty snapshot clears it`() {
        val current = Conversation("thread", activeTurn = "one", latestTurnId = "one", planTurnId = "one",
            plan = listOf(PlanStep("实现", "inProgress")))
        assertEquals(current, Timeline.notification(current, "turn/started", start("one")).copy(status = current.status))
        val cleared = Timeline.notification(current, "turn/plan/updated", plan("one"))
        assertTrue(cleared.plan.isEmpty()); assertEquals("one", cleared.planTurnId)
    }

    @Test fun `history reconciliation keeps an update received while the request was in flight`() {
        val before = Conversation("thread", activeTurn = "one", status = "active", latestTurnId = "one", planTurnId = "one",
            plan = listOf(PlanStep("实现", "inProgress")))
        val live = Timeline.notification(before, "turn/plan/updated", plan("one", "实现" to "completed"))
        val page = Timeline.history("thread", history("one"))
        val result = Timeline.refresh(before, live, page)
        assertEquals(live.plan, result.plan); assertEquals("one", result.planTurnId)
    }

    @Test fun `history confirming a different latest turn discards stale progress`() {
        val before = Conversation("thread", latestTurnId = "one", planTurnId = "one",
            plan = listOf(PlanStep("旧步骤", "completed")))
        val result = Timeline.refresh(before, before, Timeline.history("thread", history("two")))
        assertEquals("two", result.latestTurnId); assertTrue(result.plan.isEmpty()); assertNull(result.planTurnId)
    }

    @Test fun `new live turn is not replaced by a stale history response`() {
        val before = Conversation("thread", latestTurnId = "one")
        val started = Timeline.notification(before, "turn/started", start("two"))
        val live = Timeline.notification(started, "turn/plan/updated", plan("two", "新步骤" to "inProgress"))
        val result = Timeline.refresh(before, live, Timeline.history("thread", history("one")))
        assertEquals("two", result.latestTurnId); assertEquals("two", result.activeTurn)
        assertEquals(live.plan, result.plan)
    }

    @Test fun `late older completion cannot stop a different active turn or clear its plan`() {
        val current = Conversation("thread", activeTurn = "two", status = "active", latestTurnId = "two", planTurnId = "two",
            plan = listOf(PlanStep("新步骤", "inProgress")))
        val event = buildJsonObject {
            put("threadId", "thread"); putJsonObject("turn") {
                put("id", "one"); put("status", "completed"); put("items", JsonArray(emptyList()))
            }
        }
        assertEquals(current, Timeline.notification(current, "turn/completed", event))
    }

    @Test fun `other threads and malformed plan events cannot change current progress`() {
        val current = Conversation("thread", activeTurn = "one", latestTurnId = "one")
        val foreign = JsonObject(plan("one", "其他任务" to "completed") + ("threadId" to JsonPrimitive("other")))
        assertEquals(current, Timeline.notification(current, "turn/plan/updated", foreign))
        val missingPlan = buildJsonObject { put("threadId", "thread"); put("turnId", "one") }
        assertEquals(current, Timeline.notification(current, "turn/plan/updated", missingPlan))
        val partialPlan = buildJsonObject {
            put("threadId", "thread"); put("turnId", "one")
            putJsonArray("plan") {
                add(buildJsonObject { put("step", "valid"); put("status", "completed") })
                add(buildJsonObject { put("step", "missing status") })
            }
        }
        assertEquals(current, Timeline.notification(current, "turn/plan/updated", partialPlan))
    }

    @Test fun `completion notification without status does not claim success`() {
        val current = Conversation("thread", activeTurn = "one", status = "active", latestTurnId = "one")
        val result = Timeline.notification(current, "turn/completed", start("one"))
        assertEquals("unknown", result.status); assertNull(result.activeTurn)
    }

    @Test fun `older pages and legacy caches retain backward compatibility`() {
        val legacy = wireJson.decodeFromString<Conversation>("""{"threadId":"thread","items":[],"status":"idle"}""")
        assertNull(legacy.latestTurnId); assertTrue(legacy.plan.isEmpty())
        val current = legacy.copy(latestTurnId = "two", planTurnId = "two", plan = listOf(PlanStep("新步骤", "completed")))
        val merged = Timeline.older(current, Timeline.history("thread", history("one")))
        assertEquals(current.plan, merged.plan); assertEquals("two", merged.latestTurnId)
    }
}
