package ds.dsh.DeepSeek

/**
 * Version-neutral data contract for runtime host discovery.
 *
 * This module deliberately contains no Android, Xposed, reflection, or DEX parser dependency.
 * Discovery adapters collect facts; semantic resolvers score them; the hook layer consumes only
 * candidates that have passed explicit validation.
 */
data class HostArtifactIdentity(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val flavor: String?,
    val dexInventoryFingerprint: String?
) {
    init {
        require(packageName.isNotBlank()) { "host package name must not be blank" }
        require(versionCode >= 0L) { "host version code must not be negative" }
    }
}

/** Runtime class-loader inventory entry. Names are diagnostic identifiers, not stable identity. */
data class DiscoveredClassLoader(
    val id: String,
    val implementationName: String,
    val parentId: String?,
    val dexUnitIds: List<String>,
    val isDynamic: Boolean
) {
    init {
        require(id.isNotBlank()) { "class-loader ID must not be blank" }
        require(implementationName.isNotBlank()) { "class-loader implementation must not be blank" }
    }
}

/** A DEX/APK code unit visible to one discovered class loader. */
data class DiscoveredDexUnit(
    val id: String,
    val source: String?,
    val classCount: Int,
    val fingerprint: String?
) {
    init {
        require(id.isNotBlank()) { "DEX unit ID must not be blank" }
        require(classCount >= 0) { "DEX class count must not be negative" }
    }
}

enum class HookCandidateState {
    DISCOVERED,
    VERIFIED,
    ACTIVE,
    REJECTED
}

/** Evidence is intentionally typed so a score can be explained and audited. */
data class DiscoveryEvidence(
    val kind: String,
    val description: String,
    val weight: Int
) {
    init {
        require(kind.isNotBlank()) { "evidence kind must not be blank" }
        require(description.isNotBlank()) { "evidence description must not be blank" }
        require(weight in 0..100) { "evidence weight must be between 0 and 100" }
    }
}

/**
 * A semantic Hook point candidate. owner/member names are retained for runtime invocation, but
 * semanticRole and evidence are the cross-version identity used for comparison and migration.
 */
data class HookPointCandidate(
    val id: String,
    val semanticRole: String,
    val ownerClassName: String,
    val memberName: String,
    val memberDescriptor: String,
    val confidence: Int,
    val state: HookCandidateState,
    val evidence: List<DiscoveryEvidence>,
    val sourceVersionCode: Long
) {
    init {
        require(id.isNotBlank()) { "candidate ID must not be blank" }
        require(semanticRole.isNotBlank()) { "semantic role must not be blank" }
        require(ownerClassName.isNotBlank()) { "owner class must not be blank" }
        require(memberName.isNotBlank()) { "member name must not be blank" }
        require(memberDescriptor.isNotBlank()) { "member descriptor must not be blank" }
        require(confidence in 0..100) { "confidence must be between 0 and 100" }
        require(sourceVersionCode >= 0L) { "source version code must not be negative" }
        if (state == HookCandidateState.VERIFIED || state == HookCandidateState.ACTIVE) {
            require(evidence.isNotEmpty()) { "verified candidates require evidence" }
        }
    }

    fun transitionTo(next: HookCandidateState): HookPointCandidate {
        val allowed = when (state) {
            HookCandidateState.DISCOVERED ->
                next == HookCandidateState.VERIFIED || next == HookCandidateState.REJECTED
            HookCandidateState.VERIFIED ->
                next == HookCandidateState.ACTIVE || next == HookCandidateState.REJECTED
            HookCandidateState.ACTIVE ->
                next == HookCandidateState.VERIFIED || next == HookCandidateState.REJECTED
            HookCandidateState.REJECTED -> false
        }
        require(allowed) { "invalid Hook candidate transition: $state -> $next" }
        if (next == HookCandidateState.VERIFIED || next == HookCandidateState.ACTIVE) {
            require(evidence.isNotEmpty()) { "activation requires auditable evidence" }
        }
        return copy(state = next)
    }
}

/** Immutable result of one bounded discovery pass. */
data class HostDiscoverySnapshot(
    val host: HostArtifactIdentity,
    val classLoaders: List<DiscoveredClassLoader>,
    val dexUnits: List<DiscoveredDexUnit>,
    val candidates: List<HookPointCandidate>,
    val completed: Boolean,
    val diagnostics: List<String>
) {
    init {
        require(classLoaders.map { it.id }.distinct().size == classLoaders.size) {
            "class-loader IDs must be unique"
        }
        require(dexUnits.map { it.id }.distinct().size == dexUnits.size) {
            "DEX unit IDs must be unique"
        }
        require(candidates.map { it.id }.distinct().size == candidates.size) {
            "candidate IDs must be unique"
        }
    }
}
