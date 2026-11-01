package ds.dsh.DeepSeek

/**
 * Pure SSE line framing policy matching the legacy z17 BufferedReader consumers.
 *
 * The current adapter intentionally accepts only lines beginning with the exact prefix
 * "data: " and trims the payload with Java String.trim() semantics. It ignores comments,
 * event/id/retry fields, blank lines, and the valid-but-unspaced "data:" form. Do not broaden
 * this grammar during a language-only migration.
 */
object ConversationRelaySsePolicy {
    private const val DATA_PREFIX = "data: "
    const val OPENAI_DONE_SENTINEL = "[DONE]"

    @JvmStatic
    fun dataPayload(line: String?): String? {
        if (line == null || !line.startsWith(DATA_PREFIX)) return null
        return line.substring(DATA_PREFIX.length).trim { it <= ' ' }
    }

    @JvmStatic
    fun isOpenAiDone(payload: String?): Boolean = OPENAI_DONE_SENTINEL == payload
}
