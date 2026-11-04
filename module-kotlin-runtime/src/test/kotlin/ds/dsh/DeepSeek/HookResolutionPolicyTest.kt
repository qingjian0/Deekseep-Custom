package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HookResolutionPolicyTest {
    private val policy = HookResolutionPolicy()

    private fun candidate(
        id: String = "candidate-a",
        role: String = "completion_transport",
        state: HookCandidateState = HookCandidateState.DISCOVERED,
        evidence: List<DiscoveryEvidence> = completeEvidence(20)
    ) = HookPointCandidate(
        id = id,
        semanticRole = role,
        ownerClassName = "obfuscated.Owner",
        memberName = "a",
        memberDescriptor = "(Ljava/lang/Object;)Ljava/lang/Object;",
        confidence = 99,
        state = state,
        evidence = evidence,
        sourceVersionCode = 289L
    )

    private fun completeEvidence(weight: Int) = listOf(
        evidence("semantic_anchor", weight),
        evidence("descriptor_shape", weight),
        evidence("runtime_invocation", weight),
        evidence("runtime_argument_shape", weight),
        evidence("runtime_return_shape", weight),
        evidence("behavior_contract", weight)
    )

    private fun evidence(kind: String, weight: Int) =
        DiscoveryEvidence(kind, "test observation: $kind", weight)

    @Test
    fun staticOnlyEvidenceCannotVerifyOrActivateCandidate() {
        val staticOnly = candidate(evidence = listOf(
            evidence("semantic_anchor", 100),
            evidence("descriptor_shape", 100),
            evidence("call_graph", 100),
            evidence("owner_shape", 100)
        ))

        val decision = policy.resolve("completion_transport", listOf(staticOnly))

        assertEquals(HookResolutionPolicy.Status.INSUFFICIENT_EVIDENCE, decision.status)
        assertNull(decision.selected)
        assertFalse(decision.activationPermitted)
        assertTrue(decision.selectedAssessment!!.missingEvidenceKinds.contains("runtime_invocation"))
    }

    @Test
    fun completeIndependentStaticAndRuntimeEvidenceVerifiesUniqueCandidate() {
        val decision = policy.resolve(
            "completion_transport",
            listOf(candidate())
        )

        assertEquals(HookResolutionPolicy.Status.VERIFIED, decision.status)
        assertEquals("candidate-a", decision.selected!!.id)
        assertEquals(100, decision.selectedAssessment!!.score)
        assertFalse(decision.activationPermitted)
    }

    @Test
    fun duplicateEvidenceWithinOneCategoryCannotInflateScore() {
        val observations = completeEvidence(10) +
            listOf(evidence("semantic_anchor", 10), evidence("semantic_anchor", 10))
        val decision = policy.resolve(
            "completion_transport",
            listOf(candidate(evidence = observations))
        )

        assertEquals(HookResolutionPolicy.Status.INSUFFICIENT_EVIDENCE, decision.status)
        assertEquals(60, decision.selectedAssessment!!.score)
        assertEquals(6, decision.selectedAssessment!!.distinctEvidenceKinds.size)
    }

    @Test
    fun runtimeMismatchBlocksCandidateEvenWhenScoreIsHigh() {
        val observations = completeEvidence(30) + evidence("runtime_mismatch", 100)
        val decision = policy.resolve(
            "completion_transport",
            listOf(candidate(evidence = observations))
        )

        assertEquals(HookResolutionPolicy.Status.INSUFFICIENT_EVIDENCE, decision.status)
        assertTrue(decision.selectedAssessment!!.blockingEvidenceKinds.contains("runtime_mismatch"))
        assertNull(decision.selected)
    }

    @Test
    fun closeQualifiedCandidatesAreReportedAsAmbiguous() {
        val decision = policy.resolve(
            "completion_transport",
            listOf(
                candidate(id = "candidate-strong", evidence = completeEvidence(25)),
                candidate(id = "candidate-close", evidence = completeEvidence(15))
            )
        )

        assertEquals(HookResolutionPolicy.Status.AMBIGUOUS, decision.status)
        assertNull(decision.selected)
        assertEquals(2, decision.alternatives.size)
        assertFalse(decision.activationPermitted)
    }

    @Test
    fun clearlyInferiorCandidateDoesNotPreventUniqueResolution() {
        val decision = policy.resolve(
            "completion_transport",
            listOf(
                candidate(id = "candidate-strong", evidence = completeEvidence(25)),
                candidate(id = "candidate-weak", evidence = completeEvidence(10))
            )
        )

        assertEquals(HookResolutionPolicy.Status.VERIFIED, decision.status)
        assertEquals("candidate-strong", decision.selected!!.id)
        assertFalse(decision.activationPermitted)
    }

    @Test
    fun candidatesForOtherRolesAndRejectedCandidatesAreIgnored() {
        val decision = policy.resolve(
            "completion_transport",
            listOf(
                candidate(id = "other-role", role = "message_renderer"),
                candidate(id = "rejected", state = HookCandidateState.REJECTED)
            )
        )

        assertEquals(HookResolutionPolicy.Status.NO_CANDIDATE, decision.status)
        assertNull(decision.selected)
    }

    @Test
    fun duplicateCandidateIdsAreRejected() {
        val first = candidate(id = "duplicate")
        val second = candidate(id = "duplicate", evidence = completeEvidence(15))

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            policy.resolve("completion_transport", listOf(first, second))
        }
    }

    @Test
    fun verifiedResolutionNeverClaimsHookActivation() {
        val decision = policy.resolve("completion_transport", listOf(candidate()))

        assertEquals(HookResolutionPolicy.Status.VERIFIED, decision.status)
        assertNotNull(decision.selected)
        assertFalse(decision.activationPermitted)
    }
}
