package dev.remotegpt.client

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class SessionSyncTest {
    @Test fun `concurrent opens and repeated navigation reuse a subscription and snapshot`() = runTest {
        val sync = SessionSync()
        var subscriptions = 0
        var reads = 0
        suspend fun open() {
            sync.subscribe("a") { subscriptions++; delay(10); true }
            sync.history("a") { reads++; delay(10); true }
        }
        coroutineScope { repeat(5) { launch { open() } } }
        repeat(5) { open() }
        assertEquals(1, subscriptions)
        assertEquals(1, reads)
        assertTrue(sync.isCurrent("a"))
        // A different connection cannot reuse a persisted/local subscription claim.
        assertFalse(SessionSync().isCurrent("a"))
    }

    @Test fun `completion during history load is reconciled without losing the invalidation`() = runTest {
        val sync = SessionSync()
        sync.created("a")
        sync.dirty("a")
        var reads = 0
        sync.history("a") {
            reads++
            if (reads == 1) sync.dirty("a")
            true
        }
        assertEquals(2, reads)
        assertTrue(sync.isCurrent("a"))
    }

    @Test fun `failure cancellation and empty fallback remain retryable`() = runTest {
        val sync = SessionSync()
        sync.subscribe("a") { false }
        assertFalse(sync.isSubscribed("a"))
        assertFailsWith<IllegalStateException> { sync.subscribe("a") { error("fixture") } }
        sync.subscribe("a") { true }
        assertFailsWith<CancellationException> { sync.history("a") { throw CancellationException() } }
        assertFalse(sync.isCurrent("a"))
        sync.history("a") { false }
        assertFalse(sync.isCurrent("a"))
        sync.history("a") { true }
        assertTrue(sync.isCurrent("a"))
    }

    @Test fun `close while subscribing cannot resurrect the old subscription`() = runTest {
        val sync = SessionSync()
        sync.subscribe("a") { sync.closed("a"); true }
        assertFalse(sync.isSubscribed("a"))
        sync.subscribe("a") { true }
        sync.history("a") { true }
        sync.closed("a")
        assertFalse(sync.isCurrent("a"))
        assertFalse(sync.isSubscribed("a"))
    }

    @Test fun `clearing local history retains subscriptions but requires fresh snapshots`() = runTest {
        val sync = SessionSync()
        sync.created("a")
        sync.created("b")
        sync.clearHistory()
        assertTrue(sync.isSubscribed("a"))
        assertFalse(sync.isCurrent("a"))
        assertFalse(sync.isCurrent("b"))
        sync.history("a") { true }
        assertTrue(sync.isCurrent("a"))
        assertFalse(sync.isCurrent("b"))
    }

    @Test fun `older pages do not clear latest history repair and other sessions do not wait`() = runTest {
        val sync = SessionSync()
        sync.created("a")
        sync.dirty("a")
        val blocked = CompletableDeferred<Unit>()
        val older = launch { sync.history("a", more = true) { blocked.await(); true } }
        yield()
        sync.subscribe("b") { true }
        sync.history("b") { true }
        assertTrue(sync.isCurrent("b"))
        blocked.complete(Unit)
        older.join()
        assertFalse(sync.isCurrent("a"))
        sync.history("a") { true }
        assertTrue(sync.isCurrent("a"))
    }
}
