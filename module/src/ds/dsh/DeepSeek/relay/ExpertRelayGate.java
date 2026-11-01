package ds.dsh.DeepSeek.relay;

/** Java compatibility facade for expert image relay model resolution. */
public final class ExpertRelayGate {
    private ExpertRelayGate() {}

    public static String resolveModel(Object explicitModel, String capturedModel) {
        return ExpertRelayGatePolicy.resolveModel(explicitModel, capturedModel);
    }

    public static boolean matches(Object explicitModel, String capturedModel, boolean hasFiles) {
        return ExpertRelayGatePolicy.matches(explicitModel, capturedModel, hasFiles);
    }
}
