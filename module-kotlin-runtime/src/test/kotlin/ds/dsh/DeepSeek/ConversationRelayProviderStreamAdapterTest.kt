package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelayProviderStreamAdapterTest {
    @Test
    fun openAiDecodedDeltaFieldsUseProductionAdapterAndPreserveOrder() {
        val stream = ConversationRelayProviderStreamAdapter()

        stream.append(stream.openAiChannel("reasoning_content"), "think-1")
        stream.append(stream.openAiChannel("content"), "answer-1")
        stream.append(stream.openAiChannel("tool_calls"), "must-ignore")
        stream.append(stream.openAiChannel("reasoning_content"), "-think-2")
        stream.append(stream.openAiChannel("content"), "-answer-2")
        stream.append(stream.openAiChannel("content"), "")

        assertEquals("think-1-think-2", stream.reasoningText())
        assertEquals("answer-1-answer-2", stream.contentText())
    }

    @Test
    fun anthropicStartAndDeltaUseProductionAdapterAndKeepChannelsSeparate() {
        val stream = ConversationRelayProviderStreamAdapter()

        val thinkingStart = stream.anthropicStartChannel("content_block_start", "thinking")
        val textStart = stream.anthropicStartChannel("content_block_start", "text")
        val thinkingDelta = stream.anthropicDeltaChannel("content_block_delta", "thinking_delta")
        val textDelta = stream.anthropicDeltaChannel("content_block_delta", "text_delta")

        stream.append(thinkingStart, "thought-start")
        stream.append(textStart, "text-start")
        stream.append(thinkingDelta, "-thought")
        stream.append(textDelta, "-text")
        stream.append(stream.anthropicDeltaChannel("content_block_delta", "input_json_delta"), "ignored")
        stream.append(stream.anthropicStartChannel("message_start", "thinking"), "ignored")
        stream.append(stream.anthropicDeltaChannel("content_block_delta", "text_delta"), null)

        assertEquals("thought-start-thought", stream.reasoningText())
        assertEquals("text-start-text", stream.contentText())
    }

    @Test
    fun eachResponseGetsAnIndependentAdapter() {
        val first = ConversationRelayProviderStreamAdapter()
        val second = ConversationRelayProviderStreamAdapter()

        first.append(first.openAiChannel("content"), "first")
        second.append(second.openAiChannel("content"), "second")

        assertEquals("first", first.contentText())
        assertEquals("second", second.contentText())
        assertEquals("", first.reasoningText())
        assertEquals("", second.reasoningText())
    }
}
