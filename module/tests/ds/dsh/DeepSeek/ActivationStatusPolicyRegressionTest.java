package ds.dsh.DeepSeek;

/** Regression coverage for activation evidence classification. */
public final class ActivationStatusPolicyRegressionTest {
    public static void main(String[] args) {
        check(ActivationStatusPolicy.classify(0L, false)
                        == ActivationStatusPolicy.State.WAITING_FOR_TARGET,
                "no evidence must remain pending, not inactive");
        check(ActivationStatusPolicy.classify(0L, true)
                        == ActivationStatusPolicy.State.WAITING_FOR_TARGET,
                "framework Binder alone must not claim target injection");
        check(ActivationStatusPolicy.classify(1L, false)
                        == ActivationStatusPolicy.State.TARGET_SEEN,
                "target heartbeat must be recognized without modern Binder");
        check(ActivationStatusPolicy.classify(System.currentTimeMillis()
                        - 8L * 24L * 60L * 60L * 1000L, true)
                        == ActivationStatusPolicy.State.TARGET_SEEN,
                "old heartbeat remains historical evidence, not a false negative");
        System.out.println("Activation status policy regression tests passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
