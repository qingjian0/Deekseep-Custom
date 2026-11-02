package ds.dsh.DeepSeek

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ConversationRelayHttpTransportTest {
    @Test
    fun configuresGetRequestWithoutWritingBody() {
        val fake = FakeConnection(URL("https://relay.example/v1/models"))
        val opened = ConversationRelayHttpTransport.openRequest(
            "https://relay.example/v1/models",
            "GET",
            8_000,
            12_000,
            false,
            mapOf("Accept" to "application/json", "Authorization" to "Bearer token"),
            null,
            ConversationRelayHttpTransport.ConnectionFactory { fake },
        )

        assertSame(fake, opened)
        assertEquals("GET", fake.requestMethod)
        assertEquals(8_000, fake.connectTimeout)
        assertEquals(12_000, fake.readTimeout)
        assertFalse(fake.doOutput)
        assertEquals("application/json", fake.getRequestProperty("Accept"))
        assertEquals("Bearer token", fake.getRequestProperty("Authorization"))
        assertTrue(fake.outputBytes.toByteArray().isEmpty())
        assertFalse(fake.disconnected)
    }

    @Test
    fun configuresPostHeadersAndWritesExactUtf8Body() {
        val fake = FakeConnection(URL("https://relay.example/v1/chat/completions"))
        val body = """{"model":"m","stream":true,"messages":[{"role":"user","content":"你好"}]}"""
            .toByteArray(Charsets.UTF_8)
        val opened = ConversationRelayHttpTransport.openRequest(
            "https://relay.example/v1/chat/completions",
            "POST",
            10_000,
            180_000,
            true,
            linkedMapOf("Content-Type" to "application/json", "Accept" to "text/event-stream"),
            body,
            ConversationRelayHttpTransport.ConnectionFactory { fake },
        )

        assertSame(fake, opened)
        assertEquals("POST", fake.requestMethod)
        assertEquals(10_000, fake.connectTimeout)
        assertEquals(180_000, fake.readTimeout)
        assertTrue(fake.doOutput)
        assertEquals("application/json", fake.getRequestProperty("Content-Type"))
        assertEquals("text/event-stream", fake.getRequestProperty("Accept"))
        assertContentEquals(body, fake.outputBytes.toByteArray())
        assertTrue(fake.outputClosed)
        assertFalse(fake.disconnected)
    }

    @Test
    fun disconnectsConnectionWhenRequestBodyWriteFails() {
        val fake = FakeConnection(URL("https://relay.example/chat"), failOnWrite = true)
        assertFailsWith<IOException> {
            ConversationRelayHttpTransport.openRequest(
                "https://relay.example/chat",
                "POST",
                10_000,
                180_000,
                true,
                emptyMap(),
                byteArrayOf(1, 2, 3),
                ConversationRelayHttpTransport.ConnectionFactory { fake },
            )
        }
        assertTrue(fake.disconnected)
    }

    private class FakeConnection(
        url: URL,
        private val failOnWrite: Boolean = false,
    ) : HttpURLConnection(url) {
        val outputBytes = ByteArrayOutputStream()
        var outputClosed = false
        var disconnected = false

        override fun connect() {
            connected = true
        }

        override fun disconnect() {
            disconnected = true
            connected = false
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream {
            return object : OutputStream() {
                override fun write(value: Int) {
                    if (failOnWrite) throw IOException("write failed")
                    outputBytes.write(value)
                }

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    if (failOnWrite) throw IOException("write failed")
                    outputBytes.write(bytes, offset, length)
                }

                override fun close() {
                    outputClosed = true
                }
            }
        }
    }
}
