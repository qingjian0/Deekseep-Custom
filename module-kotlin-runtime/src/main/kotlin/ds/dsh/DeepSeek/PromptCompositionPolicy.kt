package ds.dsh.DeepSeek

/**
 * Pure composition rule for the ordered system-prompt fragments assembled by HookChatPipeline.
 *
 * The trim predicate intentionally matches java.lang.String.trim() (characters <= U+0020),
 * rather than Kotlin's broader default whitespace predicate, to preserve legacy behavior.
 */
object PromptCompositionPolicy {
    @JvmStatic
    fun combine(first: String?, second: String?): String {
        val left = first?.trim { it <= ' ' } ?: ""
        val right = second?.trim { it <= ' ' } ?: ""
        return when {
            left.isEmpty() -> right
            right.isEmpty() -> left
            else -> "$left\n\n$right"
        }
    }
}
