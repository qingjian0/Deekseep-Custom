package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRelayModelListPolicyTest {
    @Test
    fun trimsFiltersAndDeduplicatesPreservingFirstSeenOrder() {
        assertEquals(
            listOf("alpha", "Beta", "beta"),
            ConversationRelayModelListPolicy.normalizeAndDeduplicate(
                listOf(" alpha ", "", "alpha", "Beta", "beta", "  ")
            ),
        )
    }

    @Test
    fun nullValuesAreIgnoredAndJavaTrimSemanticsArePreserved() {
        assertEquals(
            listOf("\u00a0id\u00a0", "id"),
            ConversationRelayModelListPolicy.normalizeAndDeduplicate(
                listOf(null, "\u00a0id\u00a0", "id", "\u0001id\u0001")
            ),
        )
    }

    @Test
    fun emptyInputProducesAnEmptyList() {
        assertEquals(emptyList(), ConversationRelayModelListPolicy.normalizeAndDeduplicate(emptyList()))
    }
}
