package ds.dsh.DeepSeek.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertRelayGateTest {
    @Test
    fun explicitModelTakesPrecedenceOverCapturedModel() {
        assertEquals("expert", ExpertRelayGate.resolveModel("expert", null))
        assertEquals("vision", ExpertRelayGate.resolveModel("vision", "expert"))
        assertEquals("expert", ExpertRelayGate.resolveModel(null, "expert"))
    }

    @Test
    fun relayRequiresFilesAndReliableExpertModel() {
        assertTrue(ExpertRelayGate.matches("expert", null, true))
        assertTrue(ExpertRelayGate.matches(null, "expert", true))
        assertFalse(ExpertRelayGate.matches(null, "default", true))
        assertFalse(ExpertRelayGate.matches("vision", "expert", true))
        assertFalse(ExpertRelayGate.matches(null, null, true))
        assertFalse(ExpertRelayGate.matches("expert", null, false))
    }
}
