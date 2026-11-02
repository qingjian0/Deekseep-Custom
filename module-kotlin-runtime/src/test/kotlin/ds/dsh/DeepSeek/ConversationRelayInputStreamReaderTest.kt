package ds.dsh.DeepSeek

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConversationRelayInputStreamReaderTest {
    @Test
    fun readsUtf8AndClosesInputAfterSuccess() {
        val input = TrackingInputStream("响应-body".toByteArray(Charsets.UTF_8))
        assertEquals("响应-body", ConversationRelayInputStreamReader.readUtf8(input))
        assertTrue(input.closed)
    }

    @Test
    fun closesInputWhenReadFails() {
        val input = FailingInputStream()
        assertFailsWith<IOException> {
            ConversationRelayInputStreamReader.readUtf8(input)
        }
        assertTrue(input.closed)
    }

    @Test
    fun propagatesCloseFailureAfterSuccessfulRead() {
        val input = CloseFailingInputStream("body".toByteArray())
        assertFailsWith<IOException> {
            ConversationRelayInputStreamReader.readUtf8(input)
        }
    }

    @Test
    fun nullInputProducesEmptyBody() {
        assertEquals("", ConversationRelayInputStreamReader.readUtf8(null))
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FailingInputStream : InputStream() {
        var closed = false
        override fun read(): Int = throw IOException("read failed")
        override fun close() {
            closed = true
        }
    }

    private class CloseFailingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        override fun close() {
            throw IOException("close failed")
        }
    }
}
