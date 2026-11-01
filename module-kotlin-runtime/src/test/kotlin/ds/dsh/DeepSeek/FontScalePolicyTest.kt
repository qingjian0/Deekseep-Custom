package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontScalePolicyTest {
    @Test fun parseTrimsAndClampsValidValues() {
        assertEquals(1.25f, FontScalePolicy.parse(" 1.25 "), 0.0001f)
        assertEquals(0.80f, FontScalePolicy.parse("0.1"), 0.0001f)
        assertEquals(1.60f, FontScalePolicy.parse("9"), 0.0001f)
    }

    @Test fun parseDefaultsMalformedAndNonFiniteValues() {
        listOf(null, "", "bad", "NaN", "Infinity", "-Infinity").forEach {
            assertEquals(1.0f, FontScalePolicy.parse(it), 0.0001f)
        }
    }

    @Test fun clampRetainsBoundaryAndNaNSemantics() {
        assertEquals(0.80f, FontScalePolicy.clamp(0.5f, 0.80f, 1.60f), 0.0001f)
        assertEquals(1.60f, FontScalePolicy.clamp(2f, 0.80f, 1.60f), 0.0001f)
        assertEquals(1.2f, FontScalePolicy.clamp(1.2f, 0.80f, 1.60f), 0.0001f)
        assertTrue(FontScalePolicy.clamp(Float.NaN, 0.80f, 1.60f).isNaN())
    }
}
