package ds.dsh.DeepSeek

/** Pure response-status and session-key decisions for ResponsePreserver. */
object ResponsePreserverPolicy {
    @JvmStatic
    fun containsFilterStatus(value: String?): Boolean =
        value != null && value.contains("CONTENT_FILTER")

    @JvmStatic
    fun stateQuality(status: String?, quasiStatus: String?): Int {
        val value = if (status == null || status.isEmpty()) quasiStatus else status
        return when {
            value == null -> 0
            value.contains("FINISHED") -> 3
            value.contains("INTERRUPTED") || value.contains("ERROR") -> 2
            value.contains("STREAMING") -> 1
            else -> 0
        }
    }

    @JvmStatic
    fun validSid(sid: String?): Boolean =
        sid != null && Regex("[0-9a-fA-F-]{36}").matches(sid)
}
