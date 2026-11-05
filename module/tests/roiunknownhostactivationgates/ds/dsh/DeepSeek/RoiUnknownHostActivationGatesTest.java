package ds.dsh.DeepSeek;

public final class RoiUnknownHostActivationGatesTest {
    public static void main(String[] args) {
        for (int mask = 0; mask < 8; mask++) {
            boolean unknown = (mask & 1) != 0;
            boolean diagnostics = (mask & 2) != 0;
            boolean features = (mask & 4) != 0;
            RoiUnknownHostActivationGates gates =
                    RoiUnknownHostActivationGates.of(unknown, diagnostics, features);
            check(gates.unknownVersionAttemptsEnabled == unknown, "unknown gate mismatch " + mask);
            check(gates.diagnosticProbesEnabled == diagnostics, "diagnostic gate mismatch " + mask);
            check(gates.featureHooksAuthorized == features, "feature gate mismatch " + mask);
            check(gates.allowsMetadataObservation() == unknown, "metadata cross-gate " + mask);
            check(gates.allowsDiagnosticProbes() == diagnostics, "probe cross-gate " + mask);
            check(gates.allowsFeatureHookAttempts() == features, "feature cross-gate " + mask);
            check(gates.requiresClassLoadDispatcher() == (unknown || diagnostics || features),
                    "dispatcher activation mismatch " + mask);
        }
        RoiUnknownHostActivationGates defaults = RoiUnknownHostActivationGates.of(false, false, false);
        check(!defaults.requiresClassLoadDispatcher(), "fresh-install defaults must be all OFF");
        System.out.println("ROI unknown-host activation gate tests passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
