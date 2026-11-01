package ds.dsh.DeepSeek

/**
 * Pure state policy for DeepSeek's native automatic continue-generation action.
 *
 * Kept free of Android and Xposed dependencies so the same implementation is shared by
 * Legacy and Universal builds through the Kotlin runtime artifact.
 */
object AutoContinueGenerationPolicy {
    @JvmStatic
    fun shouldResume(
        enabled: Boolean,
        previousStatus: String?,
        nextStatus: String?,
        role: String?
    ): Boolean {
        return enabled &&
            role == "ASSISTANT" &&
            nextStatus == "INCOMPLETE" &&
            (previousStatus == "WIP" || previousStatus == "CHECKING")
    }
}
