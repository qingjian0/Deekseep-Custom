package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentRunRecordPolicyTest {
    @Test
    fun cleanMatchesJavaTrimAndNullFallback() {
        assertEquals("", AgentRunRecordPolicy.clean(null, 10))
        assertEquals("", AgentRunRecordPolicy.clean(" \t\n ", 10))
        assertEquals("value", AgentRunRecordPolicy.clean(" \u0000value\u001f ", 10))
        assertEquals("\u00a0value\u00a0", AgentRunRecordPolicy.clean("\u00a0value\u00a0", 20))
    }

    @Test
    fun cleanTruncatesAfterTrimming() {
        assertEquals("abc", AgentRunRecordPolicy.clean("  abcdef  ", 3))
        assertEquals("", AgentRunRecordPolicy.clean("value", 0))
    }

    @Test
    fun cleanEventPreservesTheExistingBound() {
        assertEquals("event", AgentRunRecordPolicy.cleanEvent("  event  "))
        assertEquals("", AgentRunRecordPolicy.cleanEvent(null))
        val bounded = AgentRunRecordPolicy.cleanEvent("x".repeat(512 * 1024 + 1))
        assertEquals(512 * 1024, bounded.length)
    }

    @Test
    fun callKeyUsesTheSameIndependentFieldLimits() {
        assertEquals("scope|call", AgentRunRecordPolicy.callKey(" scope ", " call "))
        assertEquals("s".repeat(2048) + "|" + "c".repeat(128),
            AgentRunRecordPolicy.callKey("s".repeat(2049), "c".repeat(129)))
    }
}
