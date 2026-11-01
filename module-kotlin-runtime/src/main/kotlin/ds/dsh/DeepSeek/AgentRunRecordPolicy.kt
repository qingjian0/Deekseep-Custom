package ds.dsh.DeepSeek

/**
 * Pure record-key and bounded-string rules for AgentRunStore.
 *
 * The trim predicate intentionally matches java.lang.String.trim(): only UTF-16 characters
 * at or below U+0020 are removed. Do not replace it with Kotlin's broader default trim().
 */
object AgentRunRecordPolicy {
    @JvmStatic
    fun clean(value: String?, max: Int): String {
        var result = value?.trim { it <= ' ' }.orEmpty()
        if (result.length > max) result = result.substring(0, max)
        return result
    }

    @JvmStatic
    fun cleanEvent(value: String?): String {
        val event = value?.trim { it <= ' ' }.orEmpty()
        return if (event.length > 512 * 1024) event.substring(0, 512 * 1024) else event
    }

    @JvmStatic
    fun callKey(scope: String?, callId: String?): String =
        clean(scope, 2048) + "|" + clean(callId, 128)
}
