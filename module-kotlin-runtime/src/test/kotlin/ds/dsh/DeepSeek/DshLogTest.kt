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
    fun redactsPrivatePathsAndFlattensMultilineInput() {
        val safe = DshLog.sanitizeForStorage("failure\n/data/user/0/com.example/files/private.db")
        assertFalse(safe.contains("/data/user/0"))
        assertFalse(safe.contains("\n"))
        assertTrue(safe.contains("[private-path]"))
    }

    @Test
    fun boundsSanitizedMessageLength() {
        val safe = DshLog.sanitizeForStorage("x".repeat(2048))
        assertTrue(safe.length <= 512)
    }
}
