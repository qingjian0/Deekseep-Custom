package ds.dsh.DeepSeek

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentManualGateStateTest {
    @After
    fun reset() { AgentManualGateState.clearForTest() }

    @Test
    fun pendingStateIsAddedAndCleared() {
        assertFalse(AgentManualGateState.isPending("call-1"))
        assertTrue(AgentManualGateState.markPending("call-1"))
        assertTrue(AgentManualGateState.isPending("call-1"))
        assertTrue(AgentManualGateState.clearPending("call-1"))
        assertFalse(AgentManualGateState.isPending("call-1"))
    }

    @Test
    fun approvalIsConsumedExactlyOnce() {
        AgentManualGateState.markPending("call-2")
        AgentManualGateState.approve("call-2")
        assertFalse(AgentManualGateState.isPending("call-2"))
        assertTrue(AgentManualGateState.consumeApproval("call-2"))
        assertFalse(AgentManualGateState.consumeApproval("call-2"))
    }

    @Test
    fun refusalIsConsumedExactlyOnce() {
        AgentManualGateState.markPending("call-3")
        AgentManualGateState.refuse("call-3")
        assertFalse(AgentManualGateState.isPending("call-3"))
        assertTrue(AgentManualGateState.consumeRefusal("call-3"))
        assertFalse(AgentManualGateState.consumeRefusal("call-3"))
    }

    @Test
    fun approvalAndRefusalRemainIndependentQueues() {
        AgentManualGateState.approve("same-key")
        AgentManualGateState.refuse("same-key")
        assertTrue(AgentManualGateState.consumeApproval("same-key"))
        assertTrue(AgentManualGateState.consumeRefusal("same-key"))
    }
}
