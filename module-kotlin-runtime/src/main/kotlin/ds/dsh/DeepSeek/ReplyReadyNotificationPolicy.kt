package ds.dsh.DeepSeek

import java.util.Locale

/**
 * Pure state policy for background reply-ready notifications.
 *
 * The normalization deliberately matches java.lang.String.trim(): only UTF-16 code units
 * at or below U+0020 are removed. Do not replace this with Kotlin's default trim(), whose
 * whitespace set is broader and would change the existing Java behavior.
 */
object ReplyReadyNotificationPolicy {
    @JvmStatic
    fun shouldNotify(
        previousStatus: String?,
        nextStatus: String?,
        role: String?,
        hostForeground: Boolean
    ): Boolean {
        return !hostForeground &&
            normalize(role) == "ASSISTANT" &&
            isCompleted(nextStatus) &&
            isGenerating(previousStatus)
    }

    @JvmStatic
    fun isGenerating(status: String?): Boolean {
        return when (normalize(status)) {
            "WIP", "CHECKING", "INCOMPLETE", "STREAMING", "GENERATING", "RESPONDING" -> true
            else -> false
        }
    }

    @JvmStatic
    fun isCompleted(status: String?): Boolean {
        return when (normalize(status)) {
            "FINISHED", "COMPLETE", "COMPLETED", "SUCCESS", "SUCCEEDED", "DONE" -> true
            else -> false
        }
    }

    private fun normalize(value: String?): String {
        return value
            ?.trim { it <= '\u0020' }
            ?.uppercase(Locale.US)
            ?: ""
    }
}
