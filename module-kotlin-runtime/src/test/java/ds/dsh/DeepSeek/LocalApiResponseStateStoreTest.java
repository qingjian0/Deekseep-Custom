package ds.dsh.DeepSeek;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LocalApiResponseStateStoreTest {
    @BeforeEach
    void clearState() {
        LocalApiResponseStateStore.clear();
    }

    @Test
    void storesTranscriptAndDefensivelyCopiesPendingToolIds() {
        Set<String> pending = new LinkedHashSet<String>();
        pending.add("call-1");
        assertTrue(LocalApiResponseStateStore.putAt("resp-1", "prompt", pending, 1_000L));
        pending.add("call-2");

        LocalApiResponseStateStore.State state =
                LocalApiResponseStateStore.getAt("resp-1", 1_001L);
        assertNotNull(state);
        assertEquals("prompt", state.transcript);
        assertEquals(Collections.singleton("call-1"), state.pendingToolCallIds);
        assertThrows(UnsupportedOperationException.class,
                () -> state.pendingToolCallIds.add("call-3"));
    }

    @Test
    void expiresEntriesOnlyAfterThirtyMinuteTtl() {
        assertTrue(LocalApiResponseStateStore.putAt("resp-ttl", "prompt",
                Collections.<String>emptySet(), 10_000L));

        assertNotNull(LocalApiResponseStateStore.getAt("resp-ttl",
                10_000L + 30L * 60L * 1000L));
        assertNull(LocalApiResponseStateStore.getAt("resp-ttl",
                10_001L + 30L * 60L * 1000L));
    }

    @Test
    void evictsLeastRecentlyUsedEntryWhenCapacityIsExceeded() {
        for (int index = 0; index < 64; index++) {
            assertTrue(LocalApiResponseStateStore.putAt("resp-" + index, "p" + index,
                    Collections.<String>emptySet(), 1_000L));
        }
        assertNotNull(LocalApiResponseStateStore.getAt("resp-0", 1_001L));
        assertTrue(LocalApiResponseStateStore.putAt("resp-64", "p64",
                Collections.<String>emptySet(), 1_002L));

        assertNotNull(LocalApiResponseStateStore.getAt("resp-0", 1_003L));
        assertNull(LocalApiResponseStateStore.getAt("resp-1", 1_003L));
        assertNotNull(LocalApiResponseStateStore.getAt("resp-64", 1_003L));
        assertEquals(64, LocalApiResponseStateStore.sizeForTest());
    }

    @Test
    void rejectsOversizedTranscriptWithoutReplacingExistingEntry() {
        assertTrue(LocalApiResponseStateStore.putAt("resp-limit", "safe",
                Collections.<String>emptySet(), 1_000L));
        assertFalse(LocalApiResponseStateStore.putAt("resp-limit",
                repeat('x', 256 * 1024 + 1), Collections.<String>emptySet(), 1_001L));

        assertEquals("safe", LocalApiResponseStateStore.getAt("resp-limit", 1_002L).transcript);
    }

    @Test
    void clearRemovesAllContinuationState() {
        assertTrue(LocalApiResponseStateStore.putAt("resp-clear", "prompt",
                Collections.<String>emptySet(), 1_000L));
        LocalApiResponseStateStore.clear();

        assertNull(LocalApiResponseStateStore.getAt("resp-clear", 1_001L));
        assertEquals(0, LocalApiResponseStateStore.sizeForTest());
    }

    @Test
    void rejectsMissingIdentifiersAndTranscript() {
        assertFalse(LocalApiResponseStateStore.putAt(null, "prompt",
                Collections.<String>emptySet(), 1_000L));
        assertFalse(LocalApiResponseStateStore.putAt("resp-null", null,
                Collections.<String>emptySet(), 1_000L));
        assertEquals(0, LocalApiResponseStateStore.sizeForTest());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
