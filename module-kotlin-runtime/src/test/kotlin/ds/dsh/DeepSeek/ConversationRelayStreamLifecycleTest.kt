package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationRelayStreamLifecycleTest {
    @Test
    fun closesReaderAfterNormalEof() {
        val source = TrackingReader("data: one\ndata: two\n")
        val lines = mutableListOf<String>()

        ConversationRelayStreamLifecycle.readLinesAndClose(BufferedReader(source)) { line ->
            lines += line
            true
        }

        assertEquals(listOf("data: one", "data: two"), lines)
        assertEquals(1, source.closeCalls)
    }

    @Test
    fun closesReaderAfterHandlerStopsEarly() {
        val source = TrackingReader("first\n[DONE]\nnever-read")
        val lines = mutableListOf<String>()

        ConversationRelayStreamLifecycle.readLinesAndClose(BufferedReader(source)) { line ->
            lines += line
            line != "[DONE]"
        }

        assertEquals(listOf("first", "[DONE]"), lines)
        assertEquals(1, source.closeCalls)
    }

    @Test
    fun closesReaderAfterReadFailure() {
        val source = TrackingReader(failOnRead = true)

        assertFailsWith<IOException> {
            ConversationRelayStreamLifecycle.readLinesAndClose(BufferedReader(source)) { true }
        }

        assertEquals(1, source.closeCalls)
    }

    @Test
    fun closesReaderAfterHandlerFailure() {
        val source = TrackingReader("one\ntwo")

        assertFailsWith<IllegalStateException> {
            ConversationRelayStreamLifecycle.readLinesAndClose(BufferedReader(source)) {
                throw IllegalStateException("handler failed")
            }
        }

        assertEquals(1, source.closeCalls)
    }

    @Test
    fun readerCloseFailureDoesNotReplaceSuccessfulRead() {
        val source = TrackingReader("one", failOnClose = true)

        ConversationRelayStreamLifecycle.readLinesAndClose(BufferedReader(source)) { true }

        assertEquals(1, source.closeCalls)
    }

    private class TrackingReader(
        value: String = "",
        private val failOnRead: Boolean = false,
        private val failOnClose: Boolean = false,
    ) : Reader() {
        private val delegate = StringReader(value)
        var closeCalls = 0
            private set

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (failOnRead) throw IOException("read failed")
            return delegate.read(buffer, offset, length)
        }

        override fun close() {
            closeCalls++
            delegate.close()
            if (failOnClose) throw IOException("close failed")
        }
    }
}
