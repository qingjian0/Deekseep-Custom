package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRateLimiterTest {
    @Test
    fun enforcesMinimumGapAndRollingMinuteCap() {
        val state = AgentRateLimiterState()
        assertEquals(0L, state.waitMs(100_000L))
        state.recordSent(100_000L)
        assertEquals(8_000L, state.waitMs(100_000L + 0L))

        repeat(4) { index -> state.recordSent(108_000L + index * 8_000L) }
        assertEquals(20_000L, state.waitMs(140_000L))
        assertEquals(0L, state.waitMs(160_000L))
    }

    @Test
    fun enforcesRollingHourCapWithoutDroppingRecords() {
        val state = AgentRateLimiterState()
        repeat(AgentRateLimiter.PER_HOUR) { index ->
            state.recordSent(1_000_000L + index * 60_000L)
        }
        assertEquals(52_000L, state.waitMs(1_000_000L + 59 * 60_000L + 8_000L))
        assertEquals(0L, state.waitMs(1_000_000L + 3_600_000L))
    }

    @Test
    fun hourlyWarningIsThrottledToTenMinutes() {
        val state = AgentRateLimiterState()
        repeat(AgentRateLimiter.PER_HOUR) { index ->
            state.recordSent(1_000_000L + index * 1_000L)
        }
        assertTrue(state.shouldWarnHourCap(1_000_000L + 700_000L))
        assertFalse(state.shouldWarnHourCap(1_000_000L + 1_299_999L))
        assertTrue(state.shouldWarnHourCap(1_000_000L + 1_300_000L))
    }

    @Test
    fun publicFacadeExposesStableJavaFacingConstants() {
        assertEquals(8_000L, AgentRateLimiter.MIN_GAP_MS)
        assertEquals(5, AgentRateLimiter.PER_MINUTE)
        assertEquals(60, AgentRateLimiter.PER_HOUR)
    }
}
