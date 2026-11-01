package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationRelayHttpPolicyTest {
    @Test
    fun onlyExactHttp200IsSuccessful() {
        assertTrue(ConversationRelayHttpPolicy.isSuccessfulStatus(200))
        for (code in listOf(-1, 0, 199, 201, 204, 301, 302, 400, 404, 500, 599, 600)) {
            assertFalse(ConversationRelayHttpPolicy.isSuccessfulStatus(code), "status=$code")
        }
    }

    @Test
    fun truncatesErrorBodyAtExactlyTwoHundredCharacters() {
        assertEquals("", ConversationRelayHttpPolicy.truncateErrorBody(""))
        assertEquals("x", ConversationRelayHttpPolicy.truncateErrorBody("x"))
        assertEquals("x".repeat(199), ConversationRelayHttpPolicy.truncateErrorBody("x".repeat(199)))
        assertEquals("x".repeat(200), ConversationRelayHttpPolicy.truncateErrorBody("x".repeat(200)))
        assertEquals("x".repeat(200), ConversationRelayHttpPolicy.truncateErrorBody("x".repeat(201)))
        assertEquals("", ConversationRelayHttpPolicy.truncateErrorBody(null))
    }

    @Test
    fun truncationPreservesJavaUtf16AndWhitespaceSemantics() {
        val body = "x".repeat(199) + "😀"
        assertEquals("x".repeat(199) + "\uD83D", ConversationRelayHttpPolicy.truncateErrorBody(body))
        assertEquals("  leading and trailing  ",
            ConversationRelayHttpPolicy.truncateErrorBody("  leading and trailing  "))
    }

    @Test
    fun preservesLegacyErrorMessageFormat() {
        assertEquals("HTTP 401", ConversationRelayHttpPolicy.formatHttpError(401, ""))
        assertEquals("HTTP 401: denied", ConversationRelayHttpPolicy.formatHttpError(401, "denied"))
        assertEquals("HTTP 503: " + "x".repeat(200),
            ConversationRelayHttpPolicy.formatHttpError(503, "x".repeat(201)))
    }
}
