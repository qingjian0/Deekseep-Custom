package ds.dsh.DeepSeek.agent

/**
 * Conservative effect classification for Agent tool calls.
 *
 * UNKNOWN is never executable, including after an approval attempt.
 */
enum class AgentToolEffect {
    READ_ONLY,
    FILE_WRITE,
    DEVICE_CONTROL,
    EXTERNAL_NETWORK,
    UNKNOWN,
}

enum class AgentApprovalMode {
    /** Every known tool call requires an explicit one-call approval. */
    MANUAL,

    /** Read-only calls may run automatically; side effects require approval. */
    READ_ONLY_AUTO,

    /** Same safe baseline as READ_ONLY_AUTO; future policy expansion stays explicit. */
    SEMI_AUTO,
}

enum class AgentPolicyDecision {
    ALLOW,
    REQUIRE_CONFIRMATION,
    DENY,
}

/** Identity is scoped to one request, one tool and one concrete invocation. */
data class AgentToolInvocation(
    val requestId: String,
    val toolId: String,
    val callId: String,
) {
    init {
        require(requestId.isNotBlank() && requestId.length <= MAX_ID_CHARS)
        require(toolId.isNotBlank() && toolId.length <= MAX_ID_CHARS)
        require(callId.isNotBlank() && callId.length <= MAX_ID_CHARS)
    }

    companion object {
        private const val MAX_ID_CHARS = 128
    }
}

/**
 * In-memory policy gate. Approvals are exact-match, single-use capabilities;
 * they are neither persisted nor reusable across requests or tool calls.
 */
class AgentToolPolicy(
    private val mode: AgentApprovalMode = AgentApprovalMode.MANUAL,
) {
    private data class Approval(
        val invocation: AgentToolInvocation,
        val effect: AgentToolEffect,
    )

    private val approvals = LinkedHashSet<Approval>()

    @Synchronized
    fun approveOnce(invocation: AgentToolInvocation, effect: AgentToolEffect): Boolean {
        if (effect == AgentToolEffect.UNKNOWN) return false
        return approvals.add(Approval(invocation, effect))
    }

    @Synchronized
    fun evaluate(invocation: AgentToolInvocation, effect: AgentToolEffect): AgentPolicyDecision {
        if (effect == AgentToolEffect.UNKNOWN) return AgentPolicyDecision.DENY

        val approval = Approval(invocation, effect)
        if (approvals.remove(approval)) return AgentPolicyDecision.ALLOW

        if (mode != AgentApprovalMode.MANUAL && effect == AgentToolEffect.READ_ONLY) {
            return AgentPolicyDecision.ALLOW
        }
        return AgentPolicyDecision.REQUIRE_CONFIRMATION
    }

    @Synchronized
    fun revoke(invocation: AgentToolInvocation): Boolean =
        approvals.removeAll { it.invocation == invocation }

    @Synchronized
    fun clearApprovals() {
        approvals.clear()
    }
}
