package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalApiResponseStateStoreTest {
    @Test
    fun storesTranscriptAndDefensivelyCopiesPendingToolIds() {
        LocalApiResponseStateStore.clear()
        val pending = linkedSetOf("call-1")
        assertTrue(LocalApiResponseStateStore.putAt("resp-1", "prompt", pending, 1_000L))
        pending += "call-2"

        val state = LocalApiResponseStateStore.getAt("resp-1", 1_001L)
        assertNotNull(state)
        assertEquals("prompt", state.transcript)
        assertEquals(setOf("call-1"), state.pendingToolCallIds)
    }

    @Test
    fun expiresEntriesOnlyAfterThirtyMinuteTtl() {
        LocalApiResponseStateStore.clear()
        assertTrue(LocalApiResponseStateStore.putAt("resp-ttl", "prompt", emptySet(), 10_000L))

        assertNotNull(LocalApiResponseStateStore.getAt("resp-ttl", 10_000L + 30L * 60L * 1000L))
        assertNull(LocalApiResponseStateStore.getAt("resp-ttl", 10_001L + 30L * 60L * 1000L))
    }

    @Test
    fun evictsLeastRecentlyUsedEntryWhenCapacityIsExceeded() {
        LocalApiResponseStateStore.clear()
        repeat(64) { index ->
            assertTrue(LocalApiResponseStateStore.putAt("resp-$index", "p$index", emptySet(), 1_000L))
        }
        assertNotNull(LocalApiResponseStateStore.getAt("resp-0", 1_001L))
        assertTrue(LocalApiResponseStateStore.putAt("resp-64", "p64", emptySet(), 1_002L))

        assertNotNull(LocalApiResponseStateStore.getAt("resp-0", 1_003L))
        assertNull(LocalApiResponseStateStore.getAt("resp-1", 1_003L))
        assertNotNull(LocalApiResponseStateStore.getAt("resp-64", 1_003L))
        assertEquals(64, LocalApiResponseStateStore.sizeForTest())
    }

    @Test
    fun rejectsOversizedTranscriptWithoutReplacingExistingEntry() {
        LocalApiResponseStateStore.clear()
        assertTrue(LocalApiResponseStateStore.putAt("resp-limit", "safe", emptySet(), 1_000L))
        assertFalse(LocalApiResponseStateStore.putAt(
            "resp-limit", "x".repeat(256 * 1024 + 1), emptySet(), 1_001L
        ))

        assertEquals("safe", LocalApiResponseStateStore.getAt("resp-limit", 1_002L)?.transcript)
    }

    @Test
    fun clearRemovesAllContinuationState() {
        LocalApiResponseStateStore.clear()
        assertTrue(LocalApiResponseStateStore.putAt("resp-clear", "prompt", emptySet(), 1_000L))
        LocalApiResponseStateStore.clear()

        assertNull(LocalApiResponseStateStore.getAt("resp-clear", 1_001L))
        assertEquals(0, LocalApiResponseStateStore.sizeForTest())
    }
}
