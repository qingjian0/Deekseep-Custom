package ds.dsh.DeepSeek;

/** Regression coverage for current versus historical injection evidence. */
public final class ActivationStatusPolicyRegressionTest {
    public static void main(String[] args) {
        long now = 1_000_000L;
        check(ActivationStatusPolicy.classify(0L, now)
                        == ActivationStatusPolicy.State.WAITING_FOR_TARGET,
                "no heartbeat must remain waiting");
        check(ActivationStatusPolicy.classify(now - 30_000L, now)
                        == ActivationStatusPolicy.State.CURRENTLY_INJECTED,
                "fresh target heartbeat must report current injection");
        check(ActivationStatusPolicy.classify(
                        now - ActivationStatusPolicy.HEARTBEAT_FRESH_MS, now)
                        == ActivationStatusPolicy.State.CURRENTLY_INJECTED,
                "heartbeat at the freshness boundary must remain current");
        check(ActivationStatusPolicy.classify(
                        now - ActivationStatusPolicy.HEARTBEAT_FRESH_MS - 1L, now)
                        == ActivationStatusPolicy.State.STALE,
                "expired heartbeat must not report active injection");
        check(ActivationStatusPolicy.classify(now + 1L, now)
                        == ActivationStatusPolicy.State.WAITING_FOR_TARGET,
                "future timestamp must not report current injection");
        check(ActivationStatusPolicy.classify(
                        now - 8L * 24L * 60L * 60L * 1000L, now)
                        == ActivationStatusPolicy.State.STALE,
                "old persisted heartbeat must be historical, not active");
        System.out.println("Activation status policy regression tests passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
