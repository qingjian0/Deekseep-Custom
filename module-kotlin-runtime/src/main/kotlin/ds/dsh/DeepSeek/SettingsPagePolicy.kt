package ds.dsh.DeepSeek

/** Pure navigation and configuration-change decisions for the standalone settings UI. */
object SettingsPagePolicy {
    const val HOME = 0
    const val SETTINGS = 1

    /** Preserve the existing two-page contract: every non-settings value resolves to Home. */
    @JvmStatic
    fun normalize(requestedPage: Int): Int =
        if (requestedPage == SETTINGS) SETTINGS else HOME

    /** Rebuild the native shell only before first render or when a rendered UI qualifier changes. */
    @JvmStatic
    fun shouldRecreateShell(
        rendered: Boolean,
        previousChinese: Boolean,
        currentChinese: Boolean,
        previousDark: Boolean,
        currentDark: Boolean,
    ): Boolean =
        !rendered || previousChinese != currentChinese || previousDark != currentDark
}
