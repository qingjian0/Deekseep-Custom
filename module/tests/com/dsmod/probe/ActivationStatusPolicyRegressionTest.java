package com.dsmod.probe;

public final class ActivationStatusPolicyRegressionTest {
    private static void expect(ActivationStatusPolicy.State wanted,
                              ActivationStatusPolicy.State actual, String name) {
        if (wanted != actual) {
            throw new AssertionError(name + ": expected " + wanted + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(ActivationStatusPolicy.State.WAITING,
                ActivationStatusPolicy.classify(false, 0L, false, "", false),
                "no evidence is waiting, not inactive");
        expect(ActivationStatusPolicy.State.FRAMEWORK_CONNECTED,
                ActivationStatusPolicy.classify(false, 0L, false, "", true),
                "framework connection is not target injection");
        expect(ActivationStatusPolicy.State.TARGET_INJECTED,
                ActivationStatusPolicy.classify(true, 0L, false, "", false),
                "target heartbeat without hook report is not hook success");
        expect(ActivationStatusPolicy.State.HOOKS_ACTIVE,
                ActivationStatusPolicy.classify(true, 0L, true, "hooks_installed", false),
                "fresh successful hook report");
        expect(ActivationStatusPolicy.State.HOOKS_FAILED,
                ActivationStatusPolicy.classify(true, 0L, true, "hooks_failed", true),
                "hook failure takes precedence over framework connection");
        expect(ActivationStatusPolicy.State.HOOKS_PARTIAL,
                ActivationStatusPolicy.classify(true, 0L, true, "hooks_partial", false),
                "partial feature failures remain visible");
        expect(ActivationStatusPolicy.State.HOOKS_INSTALLING,
                ActivationStatusPolicy.classify(true, 0L, true, "hooks_installing", false),
                "installation in progress");
        expect(ActivationStatusPolicy.State.LAST_SEEN,
                ActivationStatusPolicy.classify(false, 123L, false, "", false),
                "stale heartbeat is historical only");
        expect(ActivationStatusPolicy.State.FRAMEWORK_CONNECTED,
                ActivationStatusPolicy.classify(false, 123L, false, "", true),
                "live framework evidence wins over historical target evidence");
        System.out.println("ActivationStatusPolicyRegressionTest: PASS");
    }
}
