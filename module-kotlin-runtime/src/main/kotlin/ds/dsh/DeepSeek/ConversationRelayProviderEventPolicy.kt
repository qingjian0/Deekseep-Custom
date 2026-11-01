package ds.dsh.DeepSeek

/**
 * Provider event classification for the legacy conversation relay.
 *
 * JSON decoding deliberately remains in the Android adapter (org.json is supplied by Android).
 * This object owns only provider semantics so it can be verified by plain JVM tests.
 */
object ConversationRelayProviderEventPolicy {
    const val OPENAI_REASONING_FIELD = "reasoning_content"
    const val OPENAI_CONTENT_FIELD = "content"

    const val ANTHROPIC_CONTENT_BLOCK_START = "content_block_start"
    const val ANTHROPIC_CONTENT_BLOCK_DELTA = "content_block_delta"
    const val ANTHROPIC_THINKING_BLOCK = "thinking"
    const val ANTHROPIC_TEXT_BLOCK = "text"
    const val ANTHROPIC_THINKING_DELTA = "thinking_delta"
    const val ANTHROPIC_TEXT_DELTA = "text_delta"

    enum class OutputChannel { REASONING, CONTENT, IGNORE }

    @JvmStatic
    fun openAiChannel(deltaField: String?): OutputChannel = when (deltaField) {
        OPENAI_REASONING_FIELD -> OutputChannel.REASONING
        OPENAI_CONTENT_FIELD -> OutputChannel.CONTENT
        else -> OutputChannel.IGNORE
    }

    @JvmStatic
    fun anthropicStartChannel(eventType: String?, blockType: String?): OutputChannel {
        if (eventType != ANTHROPIC_CONTENT_BLOCK_START) return OutputChannel.IGNORE
        return when (blockType) {
            ANTHROPIC_THINKING_BLOCK -> OutputChannel.REASONING
            ANTHROPIC_TEXT_BLOCK -> OutputChannel.CONTENT
            else -> OutputChannel.IGNORE
        }
    }

    @JvmStatic
    fun anthropicDeltaChannel(eventType: String?, deltaType: String?): OutputChannel {
        if (eventType != ANTHROPIC_CONTENT_BLOCK_DELTA) return OutputChannel.IGNORE
        return when (deltaType) {
            ANTHROPIC_THINKING_DELTA -> OutputChannel.REASONING
            ANTHROPIC_TEXT_DELTA -> OutputChannel.CONTENT
            else -> OutputChannel.IGNORE
        }
    }
}
