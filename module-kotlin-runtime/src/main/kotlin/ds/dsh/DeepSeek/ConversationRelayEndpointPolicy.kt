package ds.dsh.DeepSeek

/**
 * Pure endpoint and header policy shared by the Java/Xposed adapter and JVM tests.
 *
 * Keep paths and wire headers aligned with the established z17 implementation:
 * OpenAI-compatible chat uses /v1/chat/completions; Anthropic uses /v1/messages;
 * model discovery uses /v1/models for both formats.
 */
object ConversationRelayEndpointPolicy {
    const val FORMAT_OPENAI = "openai"
    const val FORMAT_ANTHROPIC = "anthropic"
    const val DEFAULT_MODEL = "default"
    const val CONNECT_TIMEOUT_MILLIS = 10_000
    const val MODEL_CONNECT_TIMEOUT_MILLIS = 8_000
    const val MODEL_READ_TIMEOUT_MILLIS = 12_000
    const val STREAM_READ_TIMEOUT_MILLIS = 180_000

    @JvmStatic
    fun normalizeFormat(format: String?): String =
        if (FORMAT_ANTHROPIC == format?.trim()) FORMAT_ANTHROPIC else FORMAT_OPENAI

    /** Trims whitespace and removes trailing slashes, preserving any configured path prefix. */
    @JvmStatic
    fun normalizeBaseUrl(baseUrl: String?): String {
        var value = baseUrl?.trim().orEmpty()
        while (value.endsWith("/")) value = value.dropLast(1)
        return value
    }

    @JvmStatic
    fun chatEndpoint(baseUrl: String?, format: String?): String {
        val suffix = if (normalizeFormat(format) == FORMAT_ANTHROPIC) {
            "/v1/messages"
        } else {
            "/v1/chat/completions"
        }
        return normalizeBaseUrl(baseUrl) + suffix
    }

    @JvmStatic
    fun modelsEndpoint(baseUrl: String?): String =
        normalizeBaseUrl(baseUrl) + "/v1/models"

    /** Ordered immutable-by-contract header map; callers should not mutate it. */
    @JvmStatic
    fun requestHeaders(format: String?, apiKey: String?): Map<String, String> {
        val key = apiKey.orEmpty()
        return if (normalizeFormat(format) == FORMAT_ANTHROPIC) {
            linkedMapOf(
                "Content-Type" to "application/json",
                "Accept" to "text/event-stream",
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
            )
        } else {
            linkedMapOf(
                "Content-Type" to "application/json",
                "Accept" to "text/event-stream",
                "Authorization" to "Bearer $key",
            )
        }
    }

    @JvmStatic
    fun modelDiscoveryHeaders(format: String?, apiKey: String?): Map<String, String> {
        val key = apiKey.orEmpty()
        return if (normalizeFormat(format) == FORMAT_ANTHROPIC) {
            linkedMapOf(
                "Accept" to "application/json",
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
            )
        } else {
            linkedMapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $key",
            )
        }
    }

    @JvmStatic
    fun effectiveModel(configuredModel: String?): String =
        configuredModel?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL
}
