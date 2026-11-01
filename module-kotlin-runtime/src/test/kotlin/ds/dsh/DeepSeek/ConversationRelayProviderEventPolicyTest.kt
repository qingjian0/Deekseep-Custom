package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelayProviderEventPolicyTest {
    @Test
    fun routesOpenAiDeltaFieldsToIndependentChannels() {
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.REASONING,
            ConversationRelayProviderEventPolicy.openAiChannel("reasoning_content"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.CONTENT,
            ConversationRelayProviderEventPolicy.openAiChannel("content"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE,
            ConversationRelayProviderEventPolicy.openAiChannel("tool_calls"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE,
            ConversationRelayProviderEventPolicy.openAiChannel(null),
        )
    }

    @Test
    fun routesAnthropicBlockStartWithoutAcceptingUnknownEvents() {
        val policy = ConversationRelayProviderEventPolicy
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.REASONING,
            policy.anthropicStartChannel("content_block_start", "thinking"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.CONTENT,
            policy.anthropicStartChannel("content_block_start", "text"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE,
            policy.anthropicStartChannel("message_start", "thinking"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE,
            policy.anthropicStartChannel("content_block_start", "tool_use"),
        )
    }

    @Test
    fun routesAnthropicDeltaWithoutCrossingChannels() {
        val policy = ConversationRelayProviderEventPolicy
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.REASONING,
            policy.anthropicDeltaChannel("content_block_delta", "thinking_delta"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.CONTENT,
            policy.anthropicDeltaChannel("content_block_delta", "text_delta"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE,
            policy.anthropicDeltaChannel("content_block_delta", "input_json_delta"),
        )
        assertEquals(
            ConversationRelayProviderEventPolicy.OutputChannel.IGNORE,
            policy.anthropicDeltaChannel("content_block_start", "text_delta"),
        )
    }
}
