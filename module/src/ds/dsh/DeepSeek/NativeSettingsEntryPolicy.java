package ds.dsh.DeepSeek;

/** Java compatibility facade for the shared native-settings entry policy. */
final class NativeSettingsEntryPolicy {
    private NativeSettingsEntryPolicy() {}

    static boolean showFloating(boolean nativeModeEnabled,
                                boolean nativeHookInstalled,
                                boolean nativeRowEmitted) {
        return ds.dsh.DeepSeek.NativeSettingsEntryVisibilityPolicy.showFloating(
                nativeModeEnabled, nativeHookInstalled, nativeRowEmitted);
    }
}
