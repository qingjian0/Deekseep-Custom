package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostDiscoveryModelTest {
    private fun candidate(
        state: HookCandidateState = HookCandidateState.DISCOVERED,
        evidence: List<DiscoveryEvidence> = emptyList()
    ) = HookPointCandidate(
        id = "completion.transport",
        semanticRole = "completion_transport",
        ownerClassName = "jw3",
        memberName = "g",
        memberDescriptor = "(Lly1;Ljava/lang/Long;)Ly60;",
        confidence = 82,
        state = state,
        evidence = evidence,
        sourceVersionCode = 289L
    )

    @Test
    fun discoveredCandidateMustBeVerifiedBeforeActivation() {
        val item = candidate()
        assertFailsWith<IllegalArgumentException> {
            item.transitionTo(HookCandidateState.ACTIVE)
        }
        assertFailsWith<IllegalArgumentException> {
            item.transitionTo(HookCandidateState.VERIFIED)
        }
    }

    @Test
    fun candidateMovesThroughVerifiedAndActiveStates() {
        val evidence = listOf(DiscoveryEvidence(
            kind = "runtime_signature",
            description = "Observed compatible request parameter and response type",
            weight = 80
        ))
        val discovered = candidate(evidence = evidence)
        val verified = discovered.transitionTo(HookCandidateState.VERIFIED)
        val active = verified.transitionTo(HookCandidateState.ACTIVE)

        assertEquals(HookCandidateState.DISCOVERED, discovered.state)
        assertEquals(HookCandidateState.VERIFIED, verified.state)
        assertEquals(HookCandidateState.ACTIVE, active.state)
    }

    @Test
    fun rejectedCandidateCannotBeReactivated() {
        val rejected = candidate().transitionTo(HookCandidateState.REJECTED)
        assertFailsWith<IllegalArgumentException> {
            rejected.transitionTo(HookCandidateState.VERIFIED)
        }
    }

    @Test
    fun snapshotRejectsDuplicateCandidateIds() {
        val host = HostArtifactIdentity(
            packageName = "com.deepseek.chat",
            versionName = "2.7.2",
            versionCode = 289L,
            flavor = "mainland",
            dexInventoryFingerprint = null
        )
        assertFailsWith<IllegalArgumentException> {
            HostDiscoverySnapshot(
                host = host,
                classLoaders = emptyList(),
                dexUnits = emptyList(),
                candidates = listOf(candidate(), candidate()),
                completed = true,
                diagnostics = emptyList()
            )
        }
    }

    @Test
    fun artifactIdentityRequiresAHostPackageAndNonNegativeVersion() {
        assertFailsWith<IllegalArgumentException> {
            HostArtifactIdentity("", null, 289L, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            HostArtifactIdentity("com.deepseek.chat", null, -1L, null, null)
        }
    }

    @Test
    fun snapshotRejectsDuplicateDexReferencesWithinOneLoader() {
        val host = HostArtifactIdentity(
            packageName = "com.deepseek.chat",
            versionName = "2.7.2",
            versionCode = 289L,
            flavor = "mainland",
            dexInventoryFingerprint = null
        )
        assertFailsWith<IllegalArgumentException> {
            HostDiscoverySnapshot(
                host = host,
                classLoaders = listOf(
                    DiscoveredClassLoader("loader-1", "dalvik.system.PathClassLoader", null,
                        listOf("dex-1", "dex-1"), false)
                ),
                dexUnits = listOf(DiscoveredDexUnit("dex-1", "base.apk", 10, null)),
                candidates = emptyList(),
                completed = true,
                diagnostics = emptyList()
            )
        }
    }

    @Test
    fun snapshotRejectsDanglingDexReferences() {
        val host = HostArtifactIdentity(
            packageName = "com.deepseek.chat",
            versionName = "2.7.2",
            versionCode = 289L,
            flavor = "mainland",
            dexInventoryFingerprint = null
        )
        assertFailsWith<IllegalArgumentException> {
            HostDiscoverySnapshot(
                host = host,
                classLoaders = listOf(
                    DiscoveredClassLoader("loader-1", "dalvik.system.PathClassLoader", null,
                        listOf("missing-dex"), false)
                ),
                dexUnits = emptyList(),
                candidates = emptyList(),
                completed = true,
                diagnostics = emptyList()
            )
        }
    }

}
