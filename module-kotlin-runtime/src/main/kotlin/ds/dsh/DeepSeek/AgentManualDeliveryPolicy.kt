package ds.dsh.DeepSeek

/**
 * Pure decision order for manual Agent delivery.
 *
 * One-shot approval/refusal tokens are consumed by the Java owner before calling this policy.
 * UI availability and persistence remain outside this class.
 */
object AgentManualDeliveryPolicy {
    const val ALLOW = 1
    const val WAIT = 0
    const val DECLINED = -1
    const val SHOW_CONFIRMATION = 2

    @JvmStatic
    fun decide(
        autoEnabled: Boolean,
        approved: Boolean,
        refused: Boolean,
        pending: Boolean
    ): Int = when {
        autoEnabled || approved -> ALLOW
        refused -> DECLINED
        pending -> WAIT
        else -> SHOW_CONFIRMATION
    }
}
