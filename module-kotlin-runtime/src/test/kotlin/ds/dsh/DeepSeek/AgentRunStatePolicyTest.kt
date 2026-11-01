package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunStatePolicyTest {
    @Test
    fun normalizesKnownStatesAndFallsBackToExecuting() {
        assertEquals("waiting_user", AgentRunStatePolicy.cleanState(" WAITING_USER "))
        assertEquals("result_ready", AgentRunStatePolicy.cleanState("RESULT_READY"))
        assertEquals("delivering", AgentRunStatePolicy.cleanState("Delivering"))
        assertEquals("waiting_chat", AgentRunStatePolicy.cleanState("WAITING_CHAT"))
        assertEquals("completed", AgentRunStatePolicy.cleanState("COMPLETED"))
        assertEquals("failed", AgentRunStatePolicy.cleanState("FAILED"))
        assertEquals("cancelled", AgentRunStatePolicy.cleanState("CANCELLED"))
        assertEquals("executing", AgentRunStatePolicy.cleanState("executing"))
        assertEquals("executing", AgentRunStatePolicy.cleanState("unknown"))
        assertEquals("executing", AgentRunStatePolicy.cleanState(null))
    }

    @Test
    fun pendingResultRequiresOutboxAndNonterminalState() {
        assertTrue(AgentRunStatePolicy.hasPendingResult("outbox-1", "event", "result_ready"))
        assertFalse(AgentRunStatePolicy.hasPendingResult("", "event", "result_ready"))
        assertFalse(AgentRunStatePolicy.hasPendingResult("outbox-1", "", "result_ready"))
        assertFalse(AgentRunStatePolicy.hasPendingResult("outbox-1", "event", "completed"))
        assertFalse(AgentRunStatePolicy.hasPendingResult("outbox-1", "event", "failed"))
        assertFalse(AgentRunStatePolicy.hasPendingResult("outbox-1", "event", "cancelled"))
    }

    @Test
    fun terminalStatePredicateMatchesLedgerStates() {
        assertTrue(AgentRunStatePolicy.isFinished("completed"))
        assertTrue(AgentRunStatePolicy.isFinished("failed"))
        assertTrue(AgentRunStatePolicy.isFinished("cancelled"))
        assertFalse(AgentRunStatePolicy.isFinished("executing"))
        assertFalse(AgentRunStatePolicy.isFinished("waiting_user"))
        assertFalse(AgentRunStatePolicy.isFinished(null))
    }
}
