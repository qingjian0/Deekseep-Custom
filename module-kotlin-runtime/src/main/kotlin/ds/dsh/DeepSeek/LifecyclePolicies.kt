package ds.dsh.DeepSeek

import java.util.Locale

/** Pure state policies shared by the Android host integration. */
object AutoContinuePolicy {
    /**
     * Resume only an assistant response that transitioned from an active generation
     * state to INCOMPLETE while automatic continuation is enabled.
     */
    @JvmStatic
    fun shouldResume(
        enabled: Boolean,
        previousStatus: String?,
        nextStatus: String?,
        role: String?,
    ): Boolean =
        enabled &&
            role == "ASSISTANT" &&
            nextStatus == "INCOMPLETE" &&
            (previousStatus == "WIP" || previousStatus == "CHECKING")
}

/** Pure state policy for background reply-ready notifications. */
object ReplyReadyPolicy {
    @JvmStatic
    fun shouldNotify(
        previousStatus: String?,
        nextStatus: String?,
        role: String?,
        hostForeground: Boolean,
    ): Boolean =
        !hostForeground &&
            normalize(role) == "ASSISTANT" &&
            isCompleted(nextStatus) &&
            isGenerating(previousStatus)

    @JvmStatic
    fun isGenerating(status: String?): Boolean =
        when (normalize(status)) {
            "WIP", "CHECKING", "INCOMPLETE", "STREAMING", "GENERATING", "RESPONDING" -> true
            else -> false
        }

    @JvmStatic
    fun isCompleted(status: String?): Boolean =
        when (normalize(status)) {
            "FINISHED", "COMPLETE", "COMPLETED", "SUCCESS", "SUCCEEDED", "DONE" -> true
            else -> false
        }

    private fun normalize(value: String?): String = value?.trim()?.uppercase(Locale.US).orEmpty()
}
