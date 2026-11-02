package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelayProviderStreamContractTest {
    @Test
    fun openAiReasoningAndContentRemainIndependentAndOrdered() {
        val accumulator = ConversationRelayStreamAccumulator()
        val policy = ConversationRelayProviderEventPolicy

        accumulator.append(policy.openAiChannel("reasoning_content"), "think-1")
        accumulator.append(policy.openAiChannel("content"), "answer-1")
        accumulator.append(policy.openAiChannel("tool_calls"), "must-ignore")
        accumulator.append(policy.openAiChannel("reasoning_content"), "-think-2")
        accumulator.append(policy.openAiChannel("content"), "-answer-2")

        assertEquals("think-1-think-2", accumulator.reasoningText())
        assertEquals("answer-1-answer-2", accumulator.contentText())
    }

    @Test
    fun anthropicStartAndDeltaEventsPreserveSeparateChannels() {
        val accumulator = ConversationRelayStreamAccumulator()
        val policy = ConversationRelayProviderEventPolicy

        accumulator.append(policy.anthropicStartChannel("content_block_start", "thinking"), "thought-start")
        accumulator.append(policy.anthropicStartChannel("content_block_start", "text"), "text-start")
        accumulator.append(policy.anthropicDeltaChannel("content_block_delta", "thinking_delta"), "-thought")
        accumulator.append(policy.anthropicDeltaChannel("content_block_delta", "text_delta"), "-text")
        accumulator.append(policy.anthropicDeltaChannel("content_block_delta", "input_json_delta"), "must-ignore")
        accumulator.append(policy.anthropicStartChannel("message_start", "thinking"), "must-also-ignore")

        assertEquals("thought-start-thought", accumulator.reasoningText())
        assertEquals("text-start-text", accumulator.contentText())
    }
}
