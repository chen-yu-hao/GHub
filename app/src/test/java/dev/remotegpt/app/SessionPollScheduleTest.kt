package dev.remotegpt.app

import org.junit.Assert.*
import org.junit.Test

class SessionPollScheduleTest {
    @Test fun notYetPersistedThreadRetriesPromptlyThenBacksOffIfPermanentlyEmpty() {
        val schedule = SessionPollSchedule()
        val loaded = setOf("new-cli")
        repeat(3) { tick ->
            val time = tick * 3_000L
            assertEquals(loaded, schedule.targets(loaded, emptySet(), time))
            schedule.attempted("new-cli", time)
            schedule.retry("new-cli", time)
        }
        assertEquals(loaded, schedule.targets(loaded, emptySet(), 9_000))
        schedule.attempted("new-cli", 9_000)
        schedule.retry("new-cli", 9_000)
        assertTrue(schedule.targets(loaded, emptySet(), 12_000).isEmpty())
        assertEquals(loaded, schedule.targets(loaded, emptySet(), 39_000))
        schedule.attempted("new-cli", 39_000)
        schedule.confirmed("new-cli")
        assertTrue(schedule.targets(loaded, emptySet(), 42_000).isEmpty())
    }

    @Test fun newCliSessionIsNotQueuedBehindInitialIdleBacklog() {
        val schedule = SessionPollSchedule()
        val old = (1..100).map { "old-$it" }.toSet()
        schedule.targets(old, emptySet(), 0).forEach { schedule.attempted(it, 0) }
        val targets = schedule.targets(old + "new-cli", emptySet(), 3_000)
        assertEquals("new-cli", targets.first())
        assertEquals(8, targets.size)
    }

    @Test fun idleChecksAreBoundedFairAndEventuallyRecheckOldCliSessions() {
        val schedule = SessionPollSchedule()
        val loaded = (1..100).map { "thread-$it" }.toSet()
        val seen = mutableSetOf<String>()
        // Active and currently viewed threads always receive a check.
        val urgent = setOf("running", "selected")
        repeat(13) { tick ->
            val targets = schedule.targets(loaded, urgent, tick * 1_000L)
            assertTrue(targets.containsAll(urgent))
            assertTrue((targets - urgent).size <= 8)
            targets.forEach { schedule.attempted(it, tick * 1_000L) }
            seen += targets
        }
        assertTrue(seen.containsAll(loaded))
        assertEquals(urgent, schedule.targets(loaded, urgent, 15_000))
        assertEquals(10, schedule.targets(loaded, urgent, 30_000).size)
        // Another CLI can turn a previously idle thread active at any time.
        assertTrue("thread-90" in schedule.targets(loaded, urgent + "thread-90", 15_000))
    }
}
