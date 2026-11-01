package ds.dsh.DeepSeek

/**
 * Per-response stream accumulator. Each relay invocation owns one instance; channels never
 * share storage. Values are appended verbatim and are not trimmed here because the legacy
 * RelayResult boundary performs the final trim.
 */
class ConversationRelayStreamAccumulator {
    private val reasoning = StringBuilder()
    private val content = StringBuilder()

    fun append(channel: ConversationRelayProviderEventPolicy.OutputChannel, value: String?) {
        if (value.isNullOrEmpty()) return
        when (channel) {
            ConversationRelayProviderEventPolicy.OutputChannel.REASONING -> reasoning.append(value)
            ConversationRelayProviderEventPolicy.OutputChannel.CONTENT -> content.append(value)
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE -> Unit
        }
    }

    fun reasoningText(): String = reasoning.toString()

    fun contentText(): String = content.toString()
}
