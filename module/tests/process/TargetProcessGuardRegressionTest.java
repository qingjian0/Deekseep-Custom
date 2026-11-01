package ds.dsh.DeepSeek;

public final class TargetProcessGuardRegressionTest {
    private static final String PID_PROPERTY = "deekseep.module.injected.pid";

    public static void main(String[] args) {
        System.clearProperty(PID_PROPERTY);
        check(TargetProcessGuard.admit("com.example.other", "101")
                        == TargetProcessGuard.Admission.NOT_TARGET,
                "unrelated package must be rejected");
        check(System.getProperty(PID_PROPERTY) == null,
                "unrelated package must not claim the process");

        check(TargetProcessGuard.admit("com.deepseek.chat", "202")
                        == TargetProcessGuard.Admission.ALLOWED,
                "production host package must be admitted");
        check("202".equals(System.getProperty(PID_PROPERTY)),
                "admitted process id must be recorded");
        check(TargetProcessGuard.admit("com.deepseek.chat", "202")
                        == TargetProcessGuard.Admission.DUPLICATE_PROCESS,
                "duplicate callback in the same process must be rejected");

        System.clearProperty(PID_PROPERTY);
        check(TargetProcessGuard.admit("com.deepseek.chat.embedded", "303")
                        == TargetProcessGuard.Admission.ALLOWED,
                "embedded coexistence package must remain admitted");
        System.out.println("Target process admission regression OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
