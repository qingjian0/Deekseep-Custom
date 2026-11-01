package ds.dsh.DeepSeek

/**
 * Pure display-only transformation policy for user-configured model labels.
 *
 * This policy is intentionally disconnected from model identifiers, request payloads,
 * endpoint selection, and all network-facing code. Android file/config/cache handling remains
 * in the Java compatibility layer.
 */
object ModelDisplayNamePolicy {
    @JvmStatic
    fun clean(value: String?): String {
        if (value == null) return ""
        val normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
            .trim { it <= '\u0020' }
        return if (normalized.length > 40) normalized.substring(0, 40) else normalized
    }

    /**
     * Returns a replacement only for an exact display label match; null means unchanged.
     * Java String.trim() semantics are preserved rather than Kotlin's broader whitespace set.
     */
    @JvmStatic
    fun mapDisplay(value: String?, enabled: Boolean, mappings: Map<String, String>?): String? {
        if (value == null || value.isEmpty() || value.length > 40 || !enabled) return null
        if (mappings == null || mappings.isEmpty()) return null
        val mapped = mappings[value.trim { it <= '\u0020' }]
        return if (mapped == null || mapped == value) null else mapped
    }
}
