package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConversationRelayStreamLifecycleTest {
    @Test
    fun closesReaderAndDisconnectsConnectionAfterNormalEof() {
        val source = TrackingReader("data: one\ndata: two\n")
        val connection = FakeConnection()
        val lines = mutableListOf<String>()

        ConversationRelayStreamLifecycle.readLinesAndClose(
            BufferedReader(source), connection
        ) { line ->
            lines += line
            true
        }

        assertEquals(listOf("data: one", "data: two"), lines)
        assertTrue(source.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun closesReaderAndDisconnectsAfterHandlerStopsEarly() {
        val source = TrackingReader("first\n[DONE]\nnever-read")
        val connection = FakeConnection()
        val lines = mutableListOf<String>()

        ConversationRelayStreamLifecycle.readLinesAndClose(
            BufferedReader(source), connection
        ) { line ->
            lines += line
            line != "[DONE]"
        }

        assertEquals(listOf("first", "[DONE]"), lines)
        assertTrue(source.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun closesReaderAndDisconnectsAfterReadFailure() {
        val source = TrackingReader(failOnRead = true)
        val connection = FakeConnection()

        assertFailsWith<IOException> {
            ConversationRelayStreamLifecycle.readLinesAndClose(
                BufferedReader(source), connection
            ) { true }
        }

        assertTrue(source.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun closesReaderAndDisconnectsAfterHandlerFailure() {
        val source = TrackingReader("one\ntwo")
        val connection = FakeConnection()

        assertFailsWith<IllegalStateException> {
            ConversationRelayStreamLifecycle.readLinesAndClose(
                BufferedReader(source), connection
            ) { throw IllegalStateException("handler failed") }
        }

        assertTrue(source.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun readerCloseFailureDoesNotPreventConnectionDisconnect() {
        val source = TrackingReader("one", failOnClose = true)
        val connection = FakeConnection()

        ConversationRelayStreamLifecycle.readLinesAndClose(
            BufferedReader(source), connection
        ) { true }

        assertTrue(source.closed)
        assertTrue(connection.disconnected)
    }

    private class TrackingReader(
        value: String = "",
        private val failOnRead: Boolean = false,
        private val failOnClose: Boolean = false,
    ) : Reader() {
        private val delegate = StringReader(value)
        var closed = false
            private set

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (failOnRead) throw IOException("read failed")
            return delegate.read(buffer, offset, length)
        }

        override fun close() {
            closed = true
            delegate.close()
            if (failOnClose) throw IOException("close failed")
        }
    }

    private class FakeConnection : HttpURLConnection(URL("https://relay.example/stream")) {
        var disconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false
    }
}
