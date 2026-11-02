package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationRelaySseLineReaderTest {
    @Test
    fun preservesBufferedReaderLineBoundariesAndBlankLines() {
        val lines = mutableListOf<String>()
        ConversationRelaySseLineReader.readLines(
            BufferedReader(StringReader("data: one\r\ndata: two\n\n:comment\rdata: three"))
        ) { line ->
            lines += line
            true
        }
        assertEquals(listOf("data: one", "data: two", "", ":comment", "data: three"), lines)
    }

    @Test
    fun stopsImmediatelyWhenHandlerReturnsFalse() {
        val lines = mutableListOf<String>()
        ConversationRelaySseLineReader.readLines(
            BufferedReader(StringReader("first\n[DONE]\nnever-read"))
        ) { line ->
            lines += line
            line != "[DONE]"
        }
        assertEquals(listOf("first", "[DONE]"), lines)
    }

    @Test
    fun emptyInputAndTrailingLineTerminatorMatchReadLineSemantics() {
        val empty = mutableListOf<String>()
        ConversationRelaySseLineReader.readLines(BufferedReader(StringReader(""))) {
            empty += it
            true
        }
        assertTrue(empty.isEmpty())

        val terminated = mutableListOf<String>()
        ConversationRelaySseLineReader.readLines(BufferedReader(StringReader("one\n"))) {
            terminated += it
            true
        }
        assertEquals(listOf("one"), terminated)
    }

    @Test
    fun propagatesReadFailureWithoutTakingReaderOwnership() {
        val source = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int =
                throw IOException("read failed")
            override fun close() {}
        }
        val reader = BufferedReader(source)
        val failure = assertFailsWith<IOException> {
            ConversationRelaySseLineReader.readLines(reader) { true }
        }
        assertEquals("read failed", failure.message)
        assertFalse(reader.ready())
    }

    @Test
    fun propagatesHandlerFailure() {
        val failure = assertFailsWith<IllegalStateException> {
            ConversationRelaySseLineReader.readLines(BufferedReader(StringReader("one\ntwo"))) {
                throw IllegalStateException("handler failed")
            }
        }
        assertEquals("handler failed", failure.message)
    }
}
