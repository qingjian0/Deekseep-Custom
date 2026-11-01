package ds.dsh.DeepSeek

/**
 * Deterministic model-id normalization for the legacy model-discovery adapter.
 *
 * Trimming deliberately matches Java String.trim(): only characters <= U+0020
 * are removed. Results preserve first-seen order and remain case-sensitive.
 */
object ConversationRelayModelListPolicy {
    @JvmStatic
    fun normalizeAndDeduplicate(modelIds: Iterable<String?>): List<String> {
        val result = ArrayList<String>()
        val seen = HashSet<String>()
        for (rawId in modelIds) {
            val id = rawId?.trim { it <= ' ' }.orEmpty()
            if (id.isNotEmpty() && seen.add(id)) result.add(id)
        }
        return result
    }
}
