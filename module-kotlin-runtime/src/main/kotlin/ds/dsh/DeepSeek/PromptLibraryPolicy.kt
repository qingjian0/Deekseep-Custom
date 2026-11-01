package ds.dsh.DeepSeek

import java.util.UUID

/** Pure prompt-library rules; persistence, JSON and UI remain Java-owned. */
object PromptLibraryPolicy {
    @JvmStatic
    fun normalizeCategory(category: String?, categories: Array<String>?): String {
        if (category == null || categories == null) return "通用"
        val candidate = category.trim()
        return categories.firstOrNull { it == candidate } ?: "通用"
    }

    /** Preserves the existing custom:<UUID> identifier format. */
    @JvmStatic
    fun newId(): String = "custom:" + UUID.randomUUID().toString()
}
