package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class SpatialLayerPixelPolicyTest {
    @Test
    fun midgroundScalesAlpha() {
        val result = SpatialLayerPixelPolicy.buildMidgroundPixels(
            intArrayOf(0x80402010.toInt()), byteArrayOf(0x80.toByte())
        )
        assertEquals(0x40402010, result[0])
    }
}
