package ds.dsh.DeepSeek.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentToolPolicyTest {
    private fun invocation(
        requestId: String = "request-1",
        toolId: String = "file.read",
        callId: String = "call-1",
    ) = AgentToolInvocation(requestId, toolId, callId)

    @Test
    fun unknownEffectsAreAlwaysDeniedAndCannotBeApproved() {
        val policy = AgentToolPolicy(AgentApprovalMode.READ_ONLY_AUTO)
        val call = invocation()
        assertFalse(policy.approveOnce(call, AgentToolEffect.UNKNOWN))
        assertEquals(AgentPolicyDecision.DENY, policy.evaluate(call, AgentToolEffect.UNKNOWN))
    }

    @Test
    fun readOnlyAutoAllowsOnlyReadOnlyCalls() {
        val policy = AgentToolPolicy(AgentApprovalMode.READ_ONLY_AUTO)
        assertEquals(
            AgentPolicyDecision.ALLOW,
            policy.evaluate(invocation(), AgentToolEffect.READ_ONLY),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(callId = "call-2"), AgentToolEffect.FILE_WRITE),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(callId = "call-3"), AgentToolEffect.DEVICE_CONTROL),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(callId = "call-4"), AgentToolEffect.EXTERNAL_NETWORK),
        )
    }

    @Test
    fun manualModeRequiresApprovalForEveryKnownEffect() {
        val policy = AgentToolPolicy(AgentApprovalMode.MANUAL)
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(), AgentToolEffect.READ_ONLY),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(callId = "call-2"), AgentToolEffect.FILE_WRITE),
        )
    }

    @Test
    fun approvalIsExactMatchAndConsumedOnce() {
        val policy = AgentToolPolicy(AgentApprovalMode.MANUAL)
        val call = invocation()
        assertTrue(policy.approveOnce(call, AgentToolEffect.FILE_WRITE))
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(call, AgentToolEffect.DEVICE_CONTROL),
        )
        assertEquals(
            AgentPolicyDecision.ALLOW,
            policy.evaluate(call, AgentToolEffect.FILE_WRITE),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(call, AgentToolEffect.FILE_WRITE),
        )
    }

    @Test
    fun approvalsDoNotCrossRequestToolOrCallIdentity() {
        val policy = AgentToolPolicy(AgentApprovalMode.MANUAL)
        val original = invocation()
        assertTrue(policy.approveOnce(original, AgentToolEffect.EXTERNAL_NETWORK))

        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(requestId = "request-2"), AgentToolEffect.EXTERNAL_NETWORK),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(toolId = "file.write"), AgentToolEffect.EXTERNAL_NETWORK),
        )
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(invocation(callId = "call-2"), AgentToolEffect.EXTERNAL_NETWORK),
        )
        assertEquals(
            AgentPolicyDecision.ALLOW,
            policy.evaluate(original, AgentToolEffect.EXTERNAL_NETWORK),
        )
    }

    @Test
    fun approvalsCanBeRevokedOrCleared() {
        val policy = AgentToolPolicy()
        val first = invocation()
        val second = invocation(callId = "call-2")
        assertTrue(policy.approveOnce(first, AgentToolEffect.FILE_WRITE))
        assertTrue(policy.approveOnce(second, AgentToolEffect.DEVICE_CONTROL))
        assertTrue(policy.revoke(first))
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(first, AgentToolEffect.FILE_WRITE),
        )
        policy.clearApprovals()
        assertEquals(
            AgentPolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(second, AgentToolEffect.DEVICE_CONTROL),
        )
    }
}
