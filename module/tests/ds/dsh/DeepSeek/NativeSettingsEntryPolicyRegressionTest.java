package ds.dsh.DeepSeek;

public final class NativeSettingsEntryPolicyRegressionTest {
    public static void main(String[] args) {
        require(NativeSettingsEntryPolicy.showFloating(false, false, false),
                "floating entry must remain when native mode is disabled");
        require(NativeSettingsEntryPolicy.showFloating(true, false, false),
                "floating entry must remain when native hook is not installed");
        require(NativeSettingsEntryPolicy.showFloating(true, true, false),
                "floating entry must remain until a native row is emitted");
        require(!NativeSettingsEntryPolicy.showFloating(true, true, true),
                "floating entry may hide after the native row is emitted");
        require(NativeSettingsEntryPolicy.showFloating(false, true, true),
                "disabled native mode must keep the floating entry visible");
        require(NativeSettingsEntryPolicy.showFloating(true, false, true),
                "an emitted row must not mask a missing native hook");

        System.out.println("Native settings entry policy regression passed.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
