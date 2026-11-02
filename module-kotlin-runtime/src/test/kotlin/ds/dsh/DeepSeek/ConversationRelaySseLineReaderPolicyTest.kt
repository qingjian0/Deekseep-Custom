package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelaySseLineReaderPolicyTest {
    @Test
    fun composesLegacyDataPrefixFilteringWithDoneTermination() {
        val payloads = mutableListOf<String>()
        ConversationRelaySseLineReader.readLines(
            BufferedReader(
                StringReader(
                    ": keep-alive\nevent: message\ndata: first\ndata:\ndata: [DONE]\ndata: must-not-be-read"
                )
            )
        ) { line ->
            val payload = ConversationRelaySsePolicy.dataPayload(line)
            if (payload != null && ConversationRelaySsePolicy.isOpenAiDone(payload)) {
                return@readLines false
            }
            if (payload != null) payloads += payload
            true
        }

        assertEquals(listOf("first"), payloads)
    }

    @Test
    fun keepsAnthropicDataEventsAndIgnoresNonDataLinesWithoutDoneSpecialCase() {
        val payloads = mutableListOf<String>()
        ConversationRelaySseLineReader.readLines(
            BufferedReader(StringReader("event: content_block_delta\ndata: {\"type\":\"content_block_delta\"}\n: ping\ndata: tail"))
        ) { line ->
            ConversationRelaySsePolicy.dataPayload(line)?.let(payloads::add)
            true
        }

        assertEquals(listOf("{\"type\":\"content_block_delta\"}", "tail"), payloads)
    }
}
