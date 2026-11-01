package ds.dsh.DeepSeek

/**
 * Pure HTTP response policy for the legacy conversation-relay adapter.
 *
 * Preserve the historical contract: only HTTP 200 succeeds, and error bodies
 * are truncated to the first 200 Java UTF-16 code units before formatting.
 */
object ConversationRelayHttpPolicy {
    const val SUCCESS_STATUS_CODE = 200
    const val MAX_ERROR_BODY_CHARS = 200

    @JvmStatic
    fun isSuccessfulStatus(statusCode: Int): Boolean =
        statusCode == SUCCESS_STATUS_CODE

    @JvmStatic
    fun truncateErrorBody(body: String?): String {
        val value = body.orEmpty()
        return value.substring(0, minOf(MAX_ERROR_BODY_CHARS, value.length))
    }

    @JvmStatic
    fun formatHttpError(statusCode: Int, body: String?): String {
        val snippet = truncateErrorBody(body)
        return "HTTP $statusCode" + if (snippet.isEmpty()) "" else ": $snippet"
    }
}
