package ds.dsh.DeepSeek

/** Pure state normalization and delivery predicates for the durable Agent run ledger. */
object AgentRunStatePolicy {
    private const val EXECUTING = "executing"
    private const val WAITING_USER = "waiting_user"
    private const val RESULT_READY = "result_ready"
    private const val DELIVERING = "delivering"
    private const val WAITING_CHAT = "waiting_chat"
    private const val COMPLETED = "completed"
    private const val FAILED = "failed"
    private const val CANCELLED = "cancelled"

    @JvmStatic
    fun cleanState(value: String?): String {
        val state = value?.trim()?.lowercase(java.util.Locale.US).orEmpty()
        return when (state) {
            WAITING_USER, RESULT_READY, DELIVERING, WAITING_CHAT, COMPLETED, FAILED, CANCELLED -> state
            else -> EXECUTING
        }
    }

    @JvmStatic
    fun hasPendingResult(outboxId: String?, event: String?, state: String?): Boolean =
        !outboxId.isNullOrEmpty() &&
            !event.isNullOrEmpty() &&
            state != CANCELLED &&
            state != COMPLETED &&
            state != FAILED

    @JvmStatic
    fun isFinished(state: String?): Boolean =
        state == COMPLETED || state == FAILED || state == CANCELLED
}
