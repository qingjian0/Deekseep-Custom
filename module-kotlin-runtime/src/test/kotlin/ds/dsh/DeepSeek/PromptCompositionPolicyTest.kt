package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptCompositionPolicyTest {
    @Test
    fun combinesNonEmptyFragmentsWithTheLegacySeparator() {
        assertEquals("first\n\nsecond", PromptCompositionPolicy.combine(" first ", " second "))
    }

    @Test
    fun nullAndEmptyFragmentsReturnTheOtherTrimmedFragment() {
        assertEquals("second", PromptCompositionPolicy.combine(null, " second "))
        assertEquals("first", PromptCompositionPolicy.combine(" first ", null))
        assertEquals("", PromptCompositionPolicy.combine(null, null))
        assertEquals("", PromptCompositionPolicy.combine("", " \t\r\n "))
    }

    @Test
    fun trimsOnlyCharactersRemovedByJavaStringTrim() {
        assertEquals(
            "\u2003left\u2003\n\nright",
            PromptCompositionPolicy.combine("\u2003left\u2003", "right")
        )
        assertEquals("left\n\nright", PromptCompositionPolicy.combine("\tleft\r", "\nright\n"))
    }

    @Test
    fun preservesInternalWhitespaceAndLineBreaks() {
        assertEquals("first\nsecond\n\nthird\nfourth",
            PromptCompositionPolicy.combine(" first\nsecond ", " third\nfourth "))
    }
}
