package ds.dsh.DeepSeek

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalApiKeepAliveDecisionTest {
    @Test
    fun heartbeatRequiresEnablementFreshnessAndRequestedKeepalive() {
        assertTrue(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 0L, true))
        assertTrue(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 15_000L, true))

        assertFalse(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(false, 1_000L, true))
        assertFalse(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, -1L, true))
        assertFalse(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 15_001L, true))
        assertFalse(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 1_000L, false))
    }

    @Test
    fun throttleOnlySameDirectionLaunchesInsideThreeSecondWindow() {
        assertTrue(LocalApiKeepAliveDecision.throttleSameDirection(10_001L, 10_000L, true, true))
        assertTrue(LocalApiKeepAliveDecision.throttleSameDirection(12_999L, 10_000L, false, false))

        assertFalse(LocalApiKeepAliveDecision.throttleSameDirection(13_000L, 10_000L, true, true))
        assertFalse(LocalApiKeepAliveDecision.throttleSameDirection(10_001L, 10_000L, true, false))
        assertFalse(LocalApiKeepAliveDecision.throttleSameDirection(9_999L, 10_000L, true, true))
        assertFalse(LocalApiKeepAliveDecision.throttleSameDirection(10_001L, 0L, true, true))
    }
}
