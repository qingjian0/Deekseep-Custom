package ds.dsh.DeepSeek

/**
 * Pure fallback-visibility policy for the native settings entry.
 *
 * A successfully installed hook is not sufficient evidence that the host rendered the native
 * row. Keep the floating entry available until a native row has actually been emitted.
 */
object NativeSettingsEntryPolicy {
    @JvmStatic
    fun showFloating(
        nativeModeEnabled: Boolean,
        nativeHookInstalled: Boolean,
        nativeRowEmitted: Boolean
    ): Boolean {
        return !nativeModeEnabled || !nativeHookInstalled || !nativeRowEmitted
    }
}
