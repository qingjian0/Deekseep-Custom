package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuntimeEmbeddingPolicyTest {
    @Test
    fun acceptsOnlyTheExactV236Identity() {
        assertNull(RuntimeEmbeddingPolicy.failureReason(
            "2.3.6", 249L, isV236 = true, isV241 = false,
            isGooglePlay = false, isV250 = false
        ))
        assertEquals(
            "v236_mismatch:2.3.6/250",
            RuntimeEmbeddingPolicy.failureReason(
                "2.3.6", 250L, true, false, false, false
            )
        )
        assertEquals(
            "v236_mismatch:2.3.7/249",
            RuntimeEmbeddingPolicy.failureReason(
                "2.3.7", 249L, true, false, false, false
            )
        )
    }

    @Test
    fun acceptsOnlyTheExactV241Identity() {
        assertNull(RuntimeEmbeddingPolicy.failureReason(
            "2.4.1", 257L, isV236 = false, isV241 = true,
            isGooglePlay = false, isV250 = false
        ))
        assertEquals(
            "v241_mismatch:2.4.1/258",
            RuntimeEmbeddingPolicy.failureReason(
                "2.4.1", 258L, false, true, false, false
            )
        )
    }

    @Test
    fun preservesV236PriorityWhenBothCompatibilityFlagsAreSet() {
        assertEquals(
            "v236_mismatch:2.4.1/257",
            RuntimeEmbeddingPolicy.failureReason(
                "2.4.1", 257L, true, true, false, false
            )
        )
    }

    @Test
    fun reportsUnsupportedHostCompatibilityWithoutEnablingEmbedding() {
        assertEquals(
            "hostcompat_not_v236_nor_v241(isV241=false,isV236=false,isGP=true,isV250=true)",
            RuntimeEmbeddingPolicy.failureReason(
                "2.5.0", 270L, false, false, true, true
            )
        )
    }

    @Test
    fun exposesTheSameHostPackageIdentity() {
        assertEquals("com.deepseek.chat", RuntimeEmbeddingPolicy.HOST_PACKAGE)
    }
}
