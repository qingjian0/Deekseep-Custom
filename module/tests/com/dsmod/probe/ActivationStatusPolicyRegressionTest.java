package com.dsmod.probe;

public final class ActivationStatusPolicyRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        long now = 1_800_000_000_000L;
        check(ActivationStatusPolicy.isTargetRecentlySeen(now, now), "current heartbeat is fresh");
        check(ActivationStatusPolicy.isTargetRecentlySeen(
                now - ActivationStatusPolicy.TARGET_FRESH_MS, now), "freshness boundary is inclusive");
        check(!ActivationStatusPolicy.isTargetRecentlySeen(
                now - ActivationStatusPolicy.TARGET_FRESH_MS - 1L, now), "expired heartbeat is stale");
        check(!ActivationStatusPolicy.isTargetRecentlySeen(now + 1L, now), "future heartbeat is rejected");
        check(!ActivationStatusPolicy.isTargetRecentlySeen(0L, now), "missing heartbeat is rejected");
        eq(ActivationStatusPolicy.State.TARGET_INJECTION_VERIFIED,
                ActivationStatusPolicy.classify(false, now, now),
                "target heartbeat proves target injection without framework binder");
        eq(ActivationStatusPolicy.State.FRAMEWORK_CONNECTED_WAITING,
                ActivationStatusPolicy.classify(true, 0L, now),
                "framework binder alone does not prove target injection");
        eq(ActivationStatusPolicy.State.WAITING_FOR_TARGET,
                ActivationStatusPolicy.classify(false, 0L, now),
                "no evidence remains pending, not inactive");
        System.out.println("PASS: " + assertions + " activation policy assertions");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(Object expected, Object actual, String message) {
        assertions++;
        if (expected != actual) throw new AssertionError(
                message + " expected=" + expected + " actual=" + actual);
    }
}
