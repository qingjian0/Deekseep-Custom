package ds.dsh.DeepSeek

/**
 * Immutable configuration snapshot for the legacy conversation-relay adapter.
 *
 * The API key is intentionally excluded from toString() to prevent accidental diagnostic leaks.
 * Values are trimmed in the same way as the existing z17.save()/get() boundary.
 */
class ConversationRelayConfig(
    apiKey: String?,
    format: String?,
    baseUrl: String?,
    model: String?,
) {
    val apiKey: String = apiKey?.trim { it <= ' ' }.orEmpty()
    val format: String = ConversationRelayEndpointPolicy.normalizeFormat(format)
    val baseUrl: String = baseUrl?.trim { it <= ' ' }.orEmpty()
    val model: String = model?.trim { it <= ' ' }.orEmpty()

    override fun toString(): String =
        "ConversationRelayConfig(apiKey=<redacted>, format=$format, baseUrl=$baseUrl, model=$model)"
}
