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
        return unknownVersionAttemptsEnabled || diagnosticProbesEnabled || featureHooksAuthorized;
    }

    boolean allowsMetadataObservation() {
        return unknownVersionAttemptsEnabled;
    }

    boolean allowsDiagnosticProbes() {
        return diagnosticProbesEnabled;
    }

    boolean allowsFeatureHookAttempts() {
        return featureHooksAuthorized;
    }
}
