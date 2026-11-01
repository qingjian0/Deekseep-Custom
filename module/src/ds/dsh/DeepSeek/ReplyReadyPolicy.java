package ds.dsh.DeepSeek;

/**
 * Java compatibility facade for the shared Kotlin reply-ready policy.
 *
 * Keep this class and its package-private static signatures stable for existing Java callers.
 */
final class ReplyReadyPolicy {
    private ReplyReadyPolicy() {}

    static boolean shouldNotify(String previousStatus, String nextStatus,
                                String role, boolean hostForeground) {
        return ReplyReadyNotificationPolicy.shouldNotify(
                previousStatus, nextStatus, role, hostForeground);
    }

    static boolean isGenerating(String status) {
        return ReplyReadyNotificationPolicy.isGenerating(status);
    }

    static boolean isCompleted(String status) {
        return ReplyReadyNotificationPolicy.isCompleted(status);
    }
}
