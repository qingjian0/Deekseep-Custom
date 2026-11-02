package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelaySseChunkBoundaryTest {
    @Test
    fun decodesUtf8AndSseFramingAcrossSingleByteReads() {
        val json = """{"text":"你好😀"}"""
        val wire = (
            ": keep-alive\r\n" +
                "event: message\r\n" +
                "data: $json\r\n" +
                "data: [DONE]\n" +
                "data: must-not-be-read"
            ).toByteArray(StandardCharsets.UTF_8)
        val input = OneByteAtATimeInputStream(wire)
        val payloads = mutableListOf<String>()

        ConversationRelaySseLineReader.readLines(
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        ) { line ->
            val payload = ConversationRelaySsePolicy.dataPayload(line)
            if (ConversationRelaySsePolicy.isOpenAiDone(payload)) {
                return@readLines false
            }
            if (payload != null) payloads += payload
            true
        }

        assertEquals(listOf(json), payloads)
    }

    @Test
    fun emitsFinalUnterminatedUtf8DataLineAtEof() {
        val json = """{"text":"尾部😀"}"""
        val input = OneByteAtATimeInputStream(
            "data: $json".toByteArray(StandardCharsets.UTF_8)
        )
        val payloads = mutableListOf<String>()

        ConversationRelaySseLineReader.readLines(
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        ) { line ->
            ConversationRelaySsePolicy.dataPayload(line)?.let(payloads::add)
            true
        }

        assertEquals(listOf(json), payloads)
    }

    private class OneByteAtATimeInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        override fun read(target: ByteArray, offset: Int, length: Int): Int =
            super.read(target, offset, minOf(length, 1))
    }
}
