package ds.dsh.DeepSeek

/** Pure age-based retention decisions used by the verified cache-directory cleaner. */
object DeepSeekCacheCleanerPolicy {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    @JvmStatic
    fun cutoff(now: Long, ageDays: Int): Long =
        now - maxOf(1, ageDays).toLong() * DAY_MS

    /** Only regular files with a known timestamp strictly older than the cutoff may be deleted. */
    @JvmStatic
    fun shouldDelete(isFile: Boolean, lastModified: Long, cutoff: Long): Boolean =
        isFile && lastModified > 0L && lastModified < cutoff
}
