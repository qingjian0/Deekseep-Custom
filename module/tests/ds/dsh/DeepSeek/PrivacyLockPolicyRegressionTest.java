package ds.dsh.DeepSeek;

public final class PrivacyLockPolicyRegressionTest {
    public static void main(String[] args) {
        requiresMinimumTwoSecondDelay();
        respectsConfiguredMinuteThreshold();
        preservesStateMachineGuards();
        handlesClockRollbackAndInvalidConfiguration();
        System.out.println("Privacy lock policy regression passed.");
    }

    private static void requiresMinimumTwoSecondDelay() {
        check(!should(10_000L, 11_999L, 0, false, false), "1,999 ms is too early");
        check(should(10_000L, 12_000L, 0, false, false), "2,000 ms reaches minimum delay");
    }

    private static void respectsConfiguredMinuteThreshold() {
        check(!should(100_000L, 159_999L, 1, false, false), "one minute minus one ms");
        check(should(100_000L, 160_000L, 1, false, false), "one minute exact boundary");
        check(!should(100_000L, 1_899_999L, 30, false, false), "thirty minute threshold");
        check(should(100_000L, 1_900_000L, 30, false, false), "thirty minute exact boundary");
    }

    private static void preservesStateMachineGuards() {
        check(!should(0L, 60_000L, 0, false, false), "missing pause timestamp");
        check(!should(10_000L, 20_000L, 0, true, false), "already locked");
        check(!should(10_000L, 20_000L, 0, false, true), "authentication in progress");
    }

    private static void handlesClockRollbackAndInvalidConfiguration() {
        check(!should(20_000L, 19_999L, 0, false, false), "elapsed clock rollback");
        check(should(10_000L, 12_000L, -5, false, false),
                "negative minutes normalize to the existing minimum delay");
    }

    private static boolean should(long paused, long now, int minutes,
            boolean locked, boolean authenticating) {
        return PrivacyLockPolicy.shouldTransitionToLocked(
                paused, now, minutes, locked, authenticating);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
