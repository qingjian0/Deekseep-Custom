package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLibraryPolicyTest {
    private val categories = arrayOf("通用", "代码审查", "写作", "翻译")

    @Test
    fun normalizeCategoryTrimsAndPreservesKnownCategories() {
        assertEquals("通用", PromptLibraryPolicy.normalizeCategory(" 通用 ", categories))
        assertEquals("代码审查", PromptLibraryPolicy.normalizeCategory("代码审查", categories))
        assertEquals("写作", PromptLibraryPolicy.normalizeCategory("写作", categories))
        assertEquals("翻译", PromptLibraryPolicy.normalizeCategory("翻译", categories))
    }

    @Test
    fun normalizeCategoryFallsBackForNullUnknownAndMissingCatalog() {
        assertEquals("通用", PromptLibraryPolicy.normalizeCategory(null, categories))
        assertEquals("通用", PromptLibraryPolicy.normalizeCategory("未知", categories))
        assertEquals("通用", PromptLibraryPolicy.normalizeCategory("写作", null))
    }

    @Test
    fun newIdKeepsCustomUuidFormatAndCreatesDistinctIds() {
        val first = PromptLibraryPolicy.newId()
        val second = PromptLibraryPolicy.newId()
        assertTrue(first.startsWith("custom:"))
        val uuid = first.removePrefix("custom:")
        assertEquals(4, java.util.UUID.fromString(uuid).version())
        assertNotEquals(first, second)
    }
}
