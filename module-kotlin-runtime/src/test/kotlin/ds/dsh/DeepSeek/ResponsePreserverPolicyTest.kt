package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsePreserverPolicyTest {
    @Test
    fun detectsContentFilterMarkerWithLegacyCaseSensitiveSubstringSemantics() {
        assertTrue(ResponsePreserverPolicy.containsFilterStatus("CONTENT_FILTER"))
        assertTrue(ResponsePreserverPolicy.containsFilterStatus("prefix_CONTENT_FILTER_suffix"))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus("content_filter"))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus("FINISHED"))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus(null))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus(""))
    }

    @Test
    fun stateQualityPrefersPrimaryStatusAndFallsBackOnlyForNullOrEmpty() {
        assertEquals(3, ResponsePreserverPolicy.stateQuality("FINISHED", "STREAMING"))
        assertEquals(3, ResponsePreserverPolicy.stateQuality("", "FINISHED"))
        assertEquals(2, ResponsePreserverPolicy.stateQuality(null, "ERROR"))
        assertEquals(2, ResponsePreserverPolicy.stateQuality("INTERRUPTED", null))
        assertEquals(1, ResponsePreserverPolicy.stateQuality("STREAMING", null))
        assertEquals(0, ResponsePreserverPolicy.stateQuality("UNKNOWN", null))
        assertEquals(0, ResponsePreserverPolicy.stateQuality(null, null))
        // A whitespace-only primary value is not empty under the legacy Java String.isEmpty rule.
        assertEquals(0, ResponsePreserverPolicy.stateQuality(" ", "FINISHED"))
    }

    @Test
    fun stateQualityPreservesSubstringPriority() {
        assertEquals(3, ResponsePreserverPolicy.stateQuality("FINISHED_INTERRUPTED_ERROR", null))
        assertEquals(2, ResponsePreserverPolicy.stateQuality("INTERRUPTED_STREAMING", null))
        assertEquals(2, ResponsePreserverPolicy.stateQuality("ERROR_STREAMING", null))
    }

    @Test
    fun validSidPreservesTheLegacyThirtySixCharacterHexAndHyphenContract() {
        assertTrue(ResponsePreserverPolicy.validSid("12345678-1234-1234-1234-123456789abc"))
        assertTrue(ResponsePreserverPolicy.validSid("ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB"))
        // The historical expression validates the character set and length, not UUID hyphen positions.
        assertTrue(ResponsePreserverPolicy.validSid("------------------------------------"))
        assertTrue(ResponsePreserverPolicy.validSid("123456789012345678901234567890123456"))
        assertFalse(ResponsePreserverPolicy.validSid("1234567"))
        assertFalse(ResponsePreserverPolicy.validSid("g2345678-1234-1234-1234-123456789abc"))
        assertFalse(ResponsePreserverPolicy.validSid("12345678-1234-1234-1234-123456789abz"))
        assertFalse(ResponsePreserverPolicy.validSid(null))
    }
}
