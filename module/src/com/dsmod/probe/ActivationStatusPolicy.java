package com.dsmod.probe;

/**
 * Pure policy for the launcher status: framework connectivity is diagnostic only and can never
 * stand in for a heartbeat emitted by the target application process.
 */
public final class ActivationStatusPolicy {
    public static final long TARGET_FRESH_MS = 15L * 60L * 1000L;

    private ActivationStatusPolicy() {}

    public static boolean isTargetRecentlySeen(long heartbeatAt, long now) {
        if (heartbeatAt <= 0L || now < heartbeatAt) return false;
        return now - heartbeatAt <= TARGET_FRESH_MS;
    }

    public static State classify(boolean frameworkConnected, long heartbeatAt, long now) {
        if (heartbeatAt > 0L && now >= heartbeatAt) {
            return isTargetRecentlySeen(heartbeatAt, now)
                    ? State.TARGET_INJECTION_VERIFIED : State.TARGET_INJECTION_SEEN;
        }
        return frameworkConnected ? State.FRAMEWORK_CONNECTED_WAITING
                : State.WAITING_FOR_TARGET;
    }

    public enum State {
        TARGET_INJECTION_VERIFIED,
        TARGET_INJECTION_SEEN,
        FRAMEWORK_CONNECTED_WAITING,
        WAITING_FOR_TARGET
    }
}
