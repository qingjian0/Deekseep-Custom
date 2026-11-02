package ds.dsh.DeepSeek

/**
 * Per-response provider stream adapter shared by the Android JSON parser and JVM contract tests.
 *
 * The Android boundary remains responsible for decoding org.json values and selecting the
 * provider-specific text field. This adapter centralizes the exact production routing path:
 * decoded field/type -> provider policy -> per-response channel accumulator.
 */
class ConversationRelayProviderStreamAdapter {
    private val accumulator = ConversationRelayStreamAccumulator()

    fun openAiChannel(field: String?): ConversationRelayProviderEventPolicy.OutputChannel =
        ConversationRelayProviderEventPolicy.openAiChannel(field)

    fun anthropicStartChannel(
        eventType: String?,
        blockType: String?
    ): ConversationRelayProviderEventPolicy.OutputChannel =
        ConversationRelayProviderEventPolicy.anthropicStartChannel(eventType, blockType)

    fun anthropicDeltaChannel(
        eventType: String?,
        deltaType: String?
    ): ConversationRelayProviderEventPolicy.OutputChannel =
        ConversationRelayProviderEventPolicy.anthropicDeltaChannel(eventType, deltaType)

    fun append(channel: ConversationRelayProviderEventPolicy.OutputChannel, value: String?) {
        accumulator.append(channel, value)
    }

    fun reasoningText(): String = accumulator.reasoningText()

    fun contentText(): String = accumulator.contentText()
}
