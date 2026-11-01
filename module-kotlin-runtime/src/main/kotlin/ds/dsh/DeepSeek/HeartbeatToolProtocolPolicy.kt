package ds.dsh.DeepSeek

/** Pure token normalization and validation shared by the heartbeat tool protocol. */
object HeartbeatToolProtocolPolicy {
    private val TOKEN_PATTERN = Regex("[A-Za-z0-9_.:-]{4,80}")
    private val SCOPE_PATTERN = Regex("[A-Za-z0-9_.:-]{4,160}")

    @JvmStatic
    fun cleanToken(value: String?, max: Int): String {
        if (value == null) return ""
        // Match String.trim() exactly: Java removes UTF-16 code units <= U+0020.
        val trimmed = value.trim { it <= ' ' }
        return if (trimmed.length > max) trimmed.substring(0, max) else trimmed
    }

    @JvmStatic
    fun cleanId(value: String?): String {
        val token = cleanToken(value, 80)
        return if (TOKEN_PATTERN.matches(token)) token else ""
    }

    @JvmStatic
    fun cleanScope(value: String?): String {
        val token = cleanToken(value, 160)
        return if (SCOPE_PATTERN.matches(token)) token else ""
    }
}
