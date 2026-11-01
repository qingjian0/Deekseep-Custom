package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRelaySsePolicyTest {
    @Test
    fun acceptsOnlyTheLegacyDataPrefix() {
        assertEquals("{\"choices\":[]}", ConversationRelaySsePolicy.dataPayload("data: {\"choices\":[] }").let {
            it?.replace(" }", "}")
        })
        assertEquals("", ConversationRelaySsePolicy.dataPayload("data: "))
        assertNull(ConversationRelaySsePolicy.dataPayload("data:"))
        assertNull(ConversationRelaySsePolicy.dataPayload("event: message"))
        assertNull(ConversationRelaySsePolicy.dataPayload("id: 12"))
        assertNull(ConversationRelaySsePolicy.dataPayload("retry: 1000"))
        assertNull(ConversationRelaySsePolicy.dataPayload(": keep-alive"))
        assertNull(ConversationRelaySsePolicy.dataPayload(""))
        assertNull(ConversationRelaySsePolicy.dataPayload(null))
    }

    @Test
    fun retainsJavaTrimSemanticsAndDoneSentinel() {
        assertEquals("\u00a0payload\u00a0", ConversationRelaySsePolicy.dataPayload("data: \u00a0payload\u00a0"))
        assertTrue(ConversationRelaySsePolicy.isOpenAiDone("[DONE]"))
        assertFalse(ConversationRelaySsePolicy.isOpenAiDone("[done]"))
        assertFalse(ConversationRelaySsePolicy.isOpenAiDone(null))
    }
}
