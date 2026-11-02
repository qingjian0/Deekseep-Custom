package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelayProviderStreamAdapterTest {
    @Test
    fun openAiDecodedDeltaFieldsUseProductionAdapterAndPreserveOrder() {
        val stream = ConversationRelayProviderStreamAdapter()

        stream.appendOpenAiDelta("reasoning_content", "think-1")
        stream.appendOpenAiDelta("content", "answer-1")
        stream.appendOpenAiDelta("tool_calls", "must-ignore")
        stream.appendOpenAiDelta("reasoning_content", "-think-2")
        stream.appendOpenAiDelta("content", "-answer-2")
        stream.appendOpenAiDelta("content", "")

        assertEquals("think-1-think-2", stream.reasoningText())
        assertEquals("answer-1-answer-2", stream.contentText())
    }

    @Test
    fun anthropicStartAndDeltaUseProductionAdapterAndKeepChannelsSeparate() {
        val stream = ConversationRelayProviderStreamAdapter()

        stream.appendAnthropicStart("content_block_start", "thinking", "thought-start", "unused")
        stream.appendAnthropicStart("content_block_start", "text", "unused", "text-start")
        stream.appendAnthropicDelta("content_block_delta", "thinking_delta", "-thought", "unused")
        stream.appendAnthropicDelta("content_block_delta", "text_delta", "unused", "-text")
        stream.appendAnthropicDelta("content_block_delta", "input_json_delta", "ignored", "ignored")
        stream.appendAnthropicStart("message_start", "thinking", "ignored", "ignored")
        stream.appendAnthropicDelta("content_block_delta", "text_delta", null, null)

        assertEquals("thought-start-thought", stream.reasoningText())
        assertEquals("text-start-text", stream.contentText())
    }

    @Test
    fun eachResponseGetsAnIndependentAdapter() {
        val first = ConversationRelayProviderStreamAdapter()
        val second = ConversationRelayProviderStreamAdapter()

        first.appendOpenAiDelta("content", "first")
        second.appendOpenAiDelta("content", "second")

        assertEquals("first", first.contentText())
        assertEquals("second", second.contentText())
        assertEquals("", first.reasoningText())
        assertEquals("", second.reasoningText())
    }
}
