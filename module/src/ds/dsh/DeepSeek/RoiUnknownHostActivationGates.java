package ds.dsh.DeepSeek;

/**
 * Immutable snapshot of the three independent unknown-host runtime switches.
 * This class contains no persistence or Hook logic; callers supply the live switch values.
 */
final class RoiUnknownHostActivationGates {
    final boolean unknownVersionAttemptsEnabled;
    final boolean diagnosticProbesEnabled;
    final boolean featureHooksAuthorized;

    private RoiUnknownHostActivationGates(boolean unknownVersionAttemptsEnabled,
            boolean diagnosticProbesEnabled, boolean featureHooksAuthorized) {
        this.unknownVersionAttemptsEnabled = unknownVersionAttemptsEnabled;
        this.diagnosticProbesEnabled = diagnosticProbesEnabled;
        this.featureHooksAuthorized = featureHooksAuthorized;
    }

    static RoiUnknownHostActivationGates of(boolean unknownVersionAttemptsEnabled,
            boolean diagnosticProbesEnabled, boolean featureHooksAuthorized) {
        return new RoiUnknownHostActivationGates(unknownVersionAttemptsEnabled,
                diagnosticProbesEnabled, featureHooksAuthorized);
    }

    boolean requiresClassLoadDispatcher() {
        // Feature authorization alone must not start an unknown-version attempt.
        return unknownVersionAttemptsEnabled || diagnosticProbesEnabled;
    }

    boolean allowsMetadataObservation() {
        return unknownVersionAttemptsEnabled;
    }

    boolean allowsDiagnosticProbes() {
        return diagnosticProbesEnabled;
    }

    boolean allowsFeatureHookAttempts() {
        // Unknown-host feature Hooks require both the version-attempt gate and user authorization.
        return unknownVersionAttemptsEnabled && featureHooksAuthorized;
    }
}
