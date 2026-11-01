package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelayStreamAccumulatorTest {
    @Test
    fun accumulatesChunksIndependentlyAndPreservesOrder() {
        val accumulator = ConversationRelayStreamAccumulator()
        val policy = ConversationRelayProviderEventPolicy
        accumulator.append(policy.openAiChannel("reasoning_content"), "think-1")
        accumulator.append(policy.openAiChannel("content"), "answer-1")
        accumulator.append(policy.openAiChannel("reasoning_content"), "-think-2")
        accumulator.append(policy.openAiChannel("content"), "-answer-2")

        assertEquals("think-1-think-2", accumulator.reasoningText())
        assertEquals("answer-1-answer-2", accumulator.contentText())
    }

    @Test
    fun ignoresUnknownAndEmptyValuesWithoutTrimmingAcceptedText() {
        val accumulator = ConversationRelayStreamAccumulator()
        val policy = ConversationRelayProviderEventPolicy
        accumulator.append(policy.openAiChannel("tool_calls"), "ignored")
        accumulator.append(policy.openAiChannel("content"), "")
        accumulator.append(policy.openAiChannel("content"), " leading ")
        accumulator.append(policy.openAiChannel("reasoning_content"), " ")

        assertEquals(" ", accumulator.reasoningText())
        assertEquals(" leading ", accumulator.contentText())
    }

    @Test
    fun eachAccumulatorHasIsolatedResponseState() {
        val first = ConversationRelayStreamAccumulator()
        val second = ConversationRelayStreamAccumulator()
        first.append(ConversationRelayProviderEventPolicy.openAiChannel("content"), "first")
        second.append(ConversationRelayProviderEventPolicy.openAiChannel("content"), "second")

        assertEquals("first", first.contentText())
        assertEquals("second", second.contentText())
        assertEquals("", first.reasoningText())
        assertEquals("", second.reasoningText())
    }
}
