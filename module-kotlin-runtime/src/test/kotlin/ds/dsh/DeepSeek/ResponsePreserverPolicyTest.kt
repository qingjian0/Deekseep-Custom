package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsePreserverPolicyTest {
    @Test
    fun detectsContentFilterMarkerInEitherStatusWithoutChangingCaseSensitivity() {
        assertTrue(ResponsePreserverPolicy.containsFilterStatus("CONTENT_FILTER"))
        assertTrue(ResponsePreserverPolicy.containsFilterStatus("prefix_CONTENT_FILTER_suffix"))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus("content_filter"))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus("FINISHED"))
    }

    @Test
    fun treatsNullAndEmptyStatusAsNotFiltered() {
        assertFalse(ResponsePreserverPolicy.containsFilterStatus(null))
        assertFalse(ResponsePreserverPolicy.containsFilterStatus(""))
    }
}
