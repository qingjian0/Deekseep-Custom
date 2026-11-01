package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SpatialLayerPixelPolicyTest {
    @Test
    fun midgroundScalesAlpha() {
        val result = SpatialLayerPixelPolicy.buildMidgroundPixels(
            intArrayOf(0x80402010.toInt()), byteArrayOf(0x80.toByte())
        )
        assertEquals(0x40402010, result[0])
    }

    @Test
    fun backgroundFieldUsesObservedBackgroundColor() {
        val color = 0xFF345678.toInt()
        assertContentEquals(
            intArrayOf(color),
            SpatialLayerPixelPolicy.buildBackgroundField(
                intArrayOf(color), byteArrayOf(0), 1, 1
            )
        )
    }

    @Test
    fun backplateUsesFallbackAndPreservesAlpha() {
        assertContentEquals(
            intArrayOf(0x7F202020),
            SpatialLayerPixelPolicy.buildBackplatePixels(
                intArrayOf(0x7F123456), byteArrayOf(0xFF.toByte()), 1, 1
            )
        )
    }

    @Test
    fun backplateLeavesUnmaskedPixelsUnchanged() {
        val pixel = 0x80123456.toInt()
        assertContentEquals(
            intArrayOf(pixel),
            SpatialLayerPixelPolicy.buildBackplatePixels(
                intArrayOf(pixel), byteArrayOf(0), 1, 1
            )
        )
    }
}
