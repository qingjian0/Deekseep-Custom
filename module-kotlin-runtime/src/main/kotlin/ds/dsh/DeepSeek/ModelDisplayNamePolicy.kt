package ds.dsh.DeepSeek

/**
 * Pure display-only label policy. It never touches request payloads, model identifiers,
 * resources, or persisted configuration; Android owns loading and caching the map.
 */
object ModelDisplayNamePolicy {
    private const val MAX_LABEL_LENGTH = 40

    @JvmStatic
    fun clean(value: String?): String {
        if (value == null) return ""
        val normalized = value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim()
        return if (normalized.length > MAX_LABEL_LENGTH) {
            normalized.substring(0, MAX_LABEL_LENGTH)
        } else {
            normalized
        }
    }

    /** Returns null for disabled, unmatched, oversized, or already-identical labels. */
    @JvmStatic
    fun mapDisplay(value: String?, enabled: Boolean, mappings: Map<String, String>?): String? {
        if (!enabled || value.isNullOrEmpty() || value.length > MAX_LABEL_LENGTH) return null
        val mapped = mappings?.get(value.trim()) ?: return null
        return if (mapped == value) null else mapped
    }
}
