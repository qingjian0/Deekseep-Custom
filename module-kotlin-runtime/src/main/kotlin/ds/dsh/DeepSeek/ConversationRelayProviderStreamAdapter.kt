package ds.dsh.DeepSeek

/**
 * Per-response provider stream adapter shared by the Android JSON parser and JVM contract tests.
 *
 * The Android boundary decodes org.json values; this adapter owns provider field selection,
 * event routing, and per-response accumulation so JVM tests exercise the production decisions.
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

    fun appendOpenAiDelta(field: String?, value: String?) {
        accumulator.append(openAiChannel(field), value)
    }

    fun appendAnthropicStart(
        eventType: String?,
        blockType: String?,
        thinking: String?,
        text: String?
    ) {
        when (anthropicStartChannel(eventType, blockType)) {
            ConversationRelayProviderEventPolicy.OutputChannel.REASONING -> accumulator.append(
                ConversationRelayProviderEventPolicy.OutputChannel.REASONING, thinking
            )
            ConversationRelayProviderEventPolicy.OutputChannel.CONTENT -> accumulator.append(
                ConversationRelayProviderEventPolicy.OutputChannel.CONTENT, text
            )
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE -> Unit
        }
    }

    fun appendAnthropicDelta(
        eventType: String?,
        deltaType: String?,
        thinking: String?,
        text: String?
    ) {
        when (anthropicDeltaChannel(eventType, deltaType)) {
            ConversationRelayProviderEventPolicy.OutputChannel.REASONING -> accumulator.append(
                ConversationRelayProviderEventPolicy.OutputChannel.REASONING, thinking
            )
            ConversationRelayProviderEventPolicy.OutputChannel.CONTENT -> accumulator.append(
                ConversationRelayProviderEventPolicy.OutputChannel.CONTENT, text
            )
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE -> Unit
        }
    }

    fun append(channel: ConversationRelayProviderEventPolicy.OutputChannel, value: String?) {
        accumulator.append(channel, value)
    }

    fun reasoningText(): String = accumulator.reasoningText()

    fun contentText(): String = accumulator.contentText()
}
