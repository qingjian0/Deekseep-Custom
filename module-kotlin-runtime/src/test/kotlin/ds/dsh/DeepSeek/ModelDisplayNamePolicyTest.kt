package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDisplayNamePolicyTest {
    @Test
    fun cleanNormalizesWhitespaceAndCapsLength() {
        assertEquals("alpha  beta gamma", ModelDisplayNamePolicy.clean(" alpha\r\nbeta\tgamma "))
        assertEquals(40, ModelDisplayNamePolicy.clean("x".repeat(60)).length)
        assertEquals("", ModelDisplayNamePolicy.clean(null))
    }

    @Test
    fun mappingRequiresEnabledExactTrimmedKeyAndShortInput() {
        val labels = mapOf("快速" to "闪电", "深度思考" to "深思")
        assertEquals("闪电", ModelDisplayNamePolicy.mapDisplay(" 快速 ", true, labels))
        assertNull(ModelDisplayNamePolicy.mapDisplay("普通聊天里提到快速", true, labels))
        assertNull(ModelDisplayNamePolicy.mapDisplay("快速", false, labels))
        assertNull(ModelDisplayNamePolicy.mapDisplay("x".repeat(41), true, labels))
        assertNull(ModelDisplayNamePolicy.mapDisplay("快速", true, emptyMap()))
    }

    @Test
    fun identicalMappingDoesNotRequestReplacement() {
        assertNull(ModelDisplayNamePolicy.mapDisplay("快速", true, mapOf("快速" to "快速")))
    }
}
