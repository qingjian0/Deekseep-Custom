package ds.dsh.DeepSeek

/** Decides whether the top-right module entry must remain available. */
class NativeSettingsEntryPolicy private constructor() {
    companion object {
        /**
         * Installing a Compose hook does not prove that the native row was rendered. Keep the
         * floating entry until one native row has completed successfully, so a host update can
         * never leave the user with neither entry.
         */
        @JvmStatic
        fun showFloating(
            nativeModeEnabled: Boolean,
            nativeHookInstalled: Boolean,
            nativeRowEmitted: Boolean,
        ): Boolean = !nativeModeEnabled || !nativeHookInstalled || !nativeRowEmitted
    }
}
