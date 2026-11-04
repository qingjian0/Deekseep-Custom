package ds.dsh.DeepSeek

/**
 * Conservative, version-neutral resolver for semantic Hook points.
 *
 * The resolver may identify a candidate as VERIFIED, but it never installs or authorizes a
 * Hook. Runtime invocation, argument shape, return shape, and observed behavior are mandatory:
 * static DEX/string evidence alone can only produce INSUFFICIENT_EVIDENCE.
 */
class HookResolutionPolicy(
    private val minimumScore: Int = 85,
    private val minimumDistinctEvidenceKinds: Int = 6,
    private val minimumWinnerMargin: Int = 12,
    private val requiredEvidenceKinds: Set<String> = DEFAULT_REQUIRED_EVIDENCE_KINDS
) {
    enum class Status {
        NO_CANDIDATE,
        INSUFFICIENT_EVIDENCE,
        AMBIGUOUS,
        VERIFIED
    }

    data class Assessment(
        val candidate: HookPointCandidate,
        val score: Int,
        val distinctEvidenceKinds: Set<String>,
        val missingEvidenceKinds: Set<String>,
        val blockingEvidenceKinds: Set<String>,
        val eligible: Boolean
    )

    data class Decision(
        val semanticRole: String,
        val status: Status,
        val selected: HookPointCandidate?,
        val selectedAssessment: Assessment?,
        val alternatives: List<Assessment>,
        val reasons: List<String>,
        /** Resolution is evidence only; activation remains a separate, explicit operation. */
        val activationPermitted: Boolean = false
    )

    init {
        require(minimumScore in 1..100) { "minimumScore must be between 1 and 100" }
        require(minimumDistinctEvidenceKinds > 0) {
            "minimumDistinctEvidenceKinds must be positive"
        }
        require(minimumWinnerMargin in 0..100) {
            "minimumWinnerMargin must be between 0 and 100"
        }
        require(requiredEvidenceKinds.isNotEmpty()) { "requiredEvidenceKinds must not be empty" }
        require(requiredEvidenceKinds.all { it.isNotBlank() }) {
            "required evidence kinds must not be blank"
        }
        require(minimumDistinctEvidenceKinds >= requiredEvidenceKinds.size) {
            "minimumDistinctEvidenceKinds cannot be smaller than requiredEvidenceKinds"
        }
    }

    fun resolve(
        semanticRole: String,
        candidates: List<HookPointCandidate>
    ): Decision {
        require(semanticRole.isNotBlank()) { "semanticRole must not be blank" }
        require(candidates.map { it.id }.distinct().size == candidates.size) {
            "candidate IDs must be unique"
        }

        val matching = candidates.filter {
            it.semanticRole == semanticRole &&
                (it.state == HookCandidateState.DISCOVERED ||
                    it.state == HookCandidateState.VERIFIED)
        }
        if (matching.isEmpty()) {
            return Decision(
                semanticRole = semanticRole,
                status = Status.NO_CANDIDATE,
                selected = null,
                selectedAssessment = null,
                alternatives = emptyList(),
                reasons = listOf("no non-rejected candidate matches semantic role")
            )
        }

        val assessments = matching.map(::assess).sortedWith(
            compareByDescending<Assessment> { it.score }
                .thenBy { it.candidate.id }
        )
        val viable = assessments.filter { it.eligible }
        if (viable.isEmpty()) {
            val best = assessments.first()
            return Decision(
                semanticRole = semanticRole,
                status = Status.INSUFFICIENT_EVIDENCE,
                selected = null,
                selectedAssessment = best,
                alternatives = assessments,
                reasons = buildList {
                    if (best.missingEvidenceKinds.isNotEmpty()) {
                        add("missing required evidence: " + best.missingEvidenceKinds.sorted().joinToString(","))
                    }
                    if (best.blockingEvidenceKinds.isNotEmpty()) {
                        add("candidate has blocking evidence: " +
                            best.blockingEvidenceKinds.sorted().joinToString(","))
                    }
                    if (best.distinctEvidenceKinds.size < minimumDistinctEvidenceKinds) {
                        add("insufficient independent evidence categories")
                    }
                    if (best.score < minimumScore) {
                        add("evidence score below threshold")
                    }
                }
            )
        }

        val winner = viable.first()
        val runnerUp = viable.getOrNull(1)
        if (runnerUp != null && winner.score - runnerUp.score < minimumWinnerMargin) {
            return Decision(
                semanticRole = semanticRole,
                status = Status.AMBIGUOUS,
                selected = null,
                selectedAssessment = winner,
                alternatives = viable,
                reasons = listOf(
                    "multiple candidates satisfy evidence gates",
                    "winner margin ${winner.score - runnerUp.score} is below $minimumWinnerMargin"
                )
            )
        }

        return Decision(
            semanticRole = semanticRole,
            status = Status.VERIFIED,
            selected = winner.candidate,
            selectedAssessment = winner,
            alternatives = viable.drop(1),
            reasons = listOf(
                "required static and runtime evidence is present",
                "candidate is the unique evidence-qualified result"
            )
        )
    }

    private fun assess(candidate: HookPointCandidate): Assessment {
        // A category contributes at most once; repeated copies of one string anchor cannot inflate
        // confidence. The strongest observation within each category is retained.
        val weightsByKind = candidate.evidence
            .groupBy { normalizeKind(it.kind) }
            .mapValues { (_, evidence) -> evidence.maxOf { it.weight } }
        val kinds = weightsByKind.keys
        val missing = requiredEvidenceKinds.map(::normalizeKind).toSet() - kinds
        val blocking = kinds intersect BLOCKING_EVIDENCE_KINDS
        val positiveScore = weightsByKind
            .filterKeys { it !in BLOCKING_EVIDENCE_KINDS }
            .values
            .sum()
            .coerceAtMost(100)
        val penalty = blocking.maxOfOrNull { weightsByKind[it] ?: 0 } ?: 0
        val score = (positiveScore - penalty).coerceAtLeast(0)
        val eligible = missing.isEmpty() &&
            blocking.isEmpty() &&
            kinds.size >= minimumDistinctEvidenceKinds &&
            score >= minimumScore

        return Assessment(
            candidate = candidate,
            score = score,
            distinctEvidenceKinds = kinds,
            missingEvidenceKinds = missing,
            blockingEvidenceKinds = blocking,
            eligible = eligible
        )
    }

    private fun normalizeKind(value: String): String = value.trim().lowercase()

    companion object {
        val DEFAULT_REQUIRED_EVIDENCE_KINDS: Set<String> = setOf(
            "semantic_anchor",
            "descriptor_shape",
            "runtime_invocation",
            "runtime_argument_shape",
            "runtime_return_shape",
            "behavior_contract"
        )

        private val BLOCKING_EVIDENCE_KINDS = setOf(
            "negative_conflict",
            "runtime_mismatch",
            "descriptor_mismatch"
        )
    }
}
