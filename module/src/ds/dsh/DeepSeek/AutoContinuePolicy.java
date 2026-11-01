package ds.dsh.DeepSeek;

/**
 * Java compatibility facade for the shared Kotlin policy.
 *
 * Keep this class and its package-private static signature stable for existing Java callers.
 */
final class AutoContinuePolicy {
    private AutoContinuePolicy() {}

    static boolean shouldResume(boolean enabled, String previousStatus,
                                String nextStatus, String role) {
        return AutoContinueGenerationPolicy.shouldResume(
                enabled, previousStatus, nextStatus, role);
    }
}
