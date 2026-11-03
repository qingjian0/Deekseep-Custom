package ds.dsh.DeepSeek.agent

/** Deterministic transition rules; no Android, Hook, or persistence dependencies. */
enum class AgentRunState {
    CREATED,
    QUEUED,
    RUNNING,
    AWAITING_CONFIRMATION,
    STREAMING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

object AgentRunStateMachine {
    fun isTerminal(state: AgentRunState?): Boolean =
        state == AgentRunState.COMPLETED ||
            state == AgentRunState.FAILED ||
            state == AgentRunState.CANCELLED

    fun canTransition(from: AgentRunState?, to: AgentRunState?): Boolean {
        if (from == null || to == null || from == to || isTerminal(from)) return false
        return when (from) {
            AgentRunState.CREATED ->
                to == AgentRunState.QUEUED ||
                    to == AgentRunState.CANCELLED ||
                    to == AgentRunState.FAILED
            AgentRunState.QUEUED ->
                to == AgentRunState.RUNNING ||
                    to == AgentRunState.CANCELLED ||
                    to == AgentRunState.FAILED
            AgentRunState.RUNNING ->
                to == AgentRunState.AWAITING_CONFIRMATION ||
                    to == AgentRunState.STREAMING ||
                    to == AgentRunState.COMPLETED ||
                    to == AgentRunState.FAILED ||
                    to == AgentRunState.CANCELLED
            AgentRunState.AWAITING_CONFIRMATION ->
                to == AgentRunState.RUNNING ||
                    to == AgentRunState.CANCELLED ||
                    to == AgentRunState.FAILED
            AgentRunState.STREAMING ->
                to == AgentRunState.AWAITING_CONFIRMATION ||
                    to == AgentRunState.COMPLETED ||
                    to == AgentRunState.FAILED ||
                    to == AgentRunState.CANCELLED
            AgentRunState.COMPLETED,
            AgentRunState.FAILED,
            AgentRunState.CANCELLED -> false
        }
    }

    fun requireTransition(from: AgentRunState, to: AgentRunState): AgentRunState {
        check(canTransition(from, to)) { "Invalid Agent run transition: $from -> $to" }
        return to
    }
}
