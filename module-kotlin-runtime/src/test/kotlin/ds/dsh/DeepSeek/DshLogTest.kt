package ds.dsh.DeepSeek

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshLogTest {
    @Test
    fun redactsCompleteAuthorizationBearerCredential() {
        val safe = DshLog.sanitizeForStorage("Authorization: Bearer abcdef123456")
        assertTrue(safe.contains("Authorization: [redacted]"))
        assertFalse(safe.contains("abcdef123456"))
    }

    @Test
    fun redactsAuthorizationAssignmentAndStandaloneBearerVariants() {
        val inputs = listOf(
            "authorization=Bearer assignment-secret-123",
            "Authorization: Basic basic-secret-123",
            "Bearer standalone-secret-123",
        )
        inputs.forEach { input ->
            val safe = DshLog.sanitizeForStorage(input)
            assertFalse(safe.contains("assignment-secret-123"))
            assertFalse(safe.contains("basic-secret-123"))
            assertFalse(safe.contains("standalone-secret-123"))
            assertTrue(safe.contains("[redacted]"))
        }
    }

    @Test
    fun redactsApiKeyHeadersAndJsonSecrets() {
        val input = """x-api-key: key-123456 {"api_key":"json-secret-123"}"""
        val safe = DshLog.sanitizeForStorage(input)
        assertFalse(safe.contains("key-123456"))
        assertFalse(safe.contains("json-secret-123"))
        assertTrue(safe.contains("[redacted]"))
    }

    @Test
    fun redactsUrlTokensAndCookies() {
        val input = "https://example.test/?access_token=url-secret-123 Cookie: session-secret-123"
        val safe = DshLog.sanitizeForStorage(input)
        assertFalse(safe.contains("url-secret-123"))
        assertFalse(safe.contains("session-secret-123"))
    }

    @Test
    fun redactsEveryPairInMultiCookieHeader() {
        val safe = DshLog.sanitizeForStorage(
            "request Cookie: sessionid=session-secret-123; csrftoken=csrf-secret-456; auth=auth-secret-789"
        )
        assertFalse(safe.contains("session-secret-123"))
        assertFalse(safe.contains("csrf-secret-456"))
        assertFalse(safe.contains("auth-secret-789"))
        assertTrue(safe.contains("Cookie: [redacted]"))
    }

    @Test
    fun redactsPrivatePathsAndFlattensMultilineInput() {
        val safe = DshLog.sanitizeForStorage("failure\n/data/user/0/com.example/files/private.db")
        assertFalse(safe.contains("/data/user/0"))
        assertFalse(safe.contains("\n"))
        assertTrue(safe.contains("[private-path]"))
    }

    @Test
    fun redactsSensitiveThreadNameMetadataBeforeDispatch() {
        val originalName = Thread.currentThread().name
        val events = mutableListOf<DshLog.Event>()
        val sink = DshLog.addSink { event -> events += event }
        try {
            Thread.currentThread().name =
                "Bearer thread-secret-123 /data/user/0/private Cookie: sid=cookie-secret-456"
            DshLog.info("ThreadTest", "safe message")

            assertTrue(events.size == 1)
            val safeThreadName = events.single().threadName
            assertFalse(safeThreadName.contains("thread-secret-123"))
            assertFalse(safeThreadName.contains("cookie-secret-456"))
            assertFalse(safeThreadName.contains("/data/user/0"))
            assertTrue(safeThreadName.contains("[redacted]"))
            assertTrue(safeThreadName.contains("[private-path]"))
            assertTrue(safeThreadName.length <= 48)
        } finally {
            Thread.currentThread().name = originalName
            sink.close()
        }
    }

    @Test
    fun boundsSanitizedMessageLength() {
        val safe = DshLog.sanitizeForStorage("x".repeat(2048))
        assertTrue(safe.length <= 512)
    }
}
