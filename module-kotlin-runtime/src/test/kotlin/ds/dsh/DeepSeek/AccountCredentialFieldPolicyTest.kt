package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCredentialFieldPolicyTest {
    @Test
    fun idLengthPreservesInclusiveBounds() {
        assertFalse(AccountCredentialFieldPolicy.isValidIdLength(7))
        assertTrue(AccountCredentialFieldPolicy.isValidIdLength(8))
        assertTrue(AccountCredentialFieldPolicy.isValidIdLength(128))
        assertFalse(AccountCredentialFieldPolicy.isValidIdLength(129))
    }

    @Test
    fun tokenLengthPreservesInclusiveBounds() {
        assertFalse(AccountCredentialFieldPolicy.isValidTokenLength(15))
        assertTrue(AccountCredentialFieldPolicy.isValidTokenLength(16))
        assertTrue(AccountCredentialFieldPolicy.isValidTokenLength(16_384))
        assertFalse(AccountCredentialFieldPolicy.isValidTokenLength(16_385))
    }

    @Test
    fun trimMatchesJavaStringTrimRatherThanUnicodeWhitespaceTrim() {
        assertEquals("value", AccountCredentialFieldPolicy.trim("  value  "))
        assertEquals("\u2003value\u2003", AccountCredentialFieldPolicy.trim("\u2003value\u2003"))
        assertEquals("", AccountCredentialFieldPolicy.trim(null))
    }

    @Test
    fun integralNumberRejectsFractionsAndNonFiniteValues() {
        assertTrue(AccountCredentialFieldPolicy.isIntegralNumber(0.0))
        assertTrue(AccountCredentialFieldPolicy.isIntegralNumber(-2.0))
        assertFalse(AccountCredentialFieldPolicy.isIntegralNumber(1.5))
        assertFalse(AccountCredentialFieldPolicy.isIntegralNumber(Double.NaN))
        assertFalse(AccountCredentialFieldPolicy.isIntegralNumber(Double.POSITIVE_INFINITY))
        assertFalse(AccountCredentialFieldPolicy.isIntegralNumber(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun filenameSanitizationMatchesJavaRules() {
        assertEquals("a_b_name", AccountCredentialFieldPolicy.safeFileName(" a/b:name. "))
        assertEquals(40, AccountCredentialFieldPolicy.safeFileName("x".repeat(45)).length)
        assertEquals("", AccountCredentialFieldPolicy.safeFileName("  "))
        assertEquals("", AccountCredentialFieldPolicy.safeFileName(null))
    }

    @Test
    fun suggestedFilenameSkipsEmptyNamesAndKeepsOriginalEntryCount() {
        assertEquals(
            "Alice_a_b_trailing_等5个账号_DeepSeek账号.txt",
            AccountCredentialFieldPolicy.suggestFileName(
                listOf(" Alice ", "a/b", null, " trailing. ", "ignored"),
                5
            )
        )
        assertEquals(
            "DeepSeek账号_DeepSeek账号.txt",
            AccountCredentialFieldPolicy.suggestFileName(null, 0)
        )
        assertEquals(
            "x".repeat(40) + "_Bob_DeepSeek账号.txt",
            AccountCredentialFieldPolicy.suggestFileName(listOf("x".repeat(45), " ", "Bob"), 2)
        )
    }

    @Test
    fun shortIdPreservesNullFallbackAndUsLowercase() {
        assertEquals("未知账号", AccountCredentialFieldPolicy.shortId(null))
        assertEquals("abcdef12", AccountCredentialFieldPolicy.shortId(" ABCDEF123456 "))
        assertEquals("abc", AccountCredentialFieldPolicy.shortId(" ABC "))
        assertEquals("i", AccountCredentialFieldPolicy.shortId(" I "))
    }
}
