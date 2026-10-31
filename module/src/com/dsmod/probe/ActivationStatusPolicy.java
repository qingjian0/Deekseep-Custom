package com.dsmod.probe;

/**
 * Keeps framework connection, target-process injection and hook installation as separate facts.
 * A missing observation is unknown, never proof that LSPosed has disabled the module.
 */
final class ActivationStatusPolicy {
    enum State {
        HOOKS_ACTIVE,
        HOOKS_FAILED,
        HOOKS_INSTALLING,
        TARGET_INJECTED,
        FRAMEWORK_CONNECTED,
        LAST_SEEN,
        WAITING
    }

    private ActivationStatusPolicy() {}

    static State classify(boolean targetFresh, long targetLastSeenAt,
                          boolean hookStatusFresh, String hookStage,
                          boolean frameworkConnected) {
        if (targetFresh) {
            if (hookStatusFresh && "hooks_installed".equals(hookStage)) {
                return State.HOOKS_ACTIVE;
            }
            if (hookStatusFresh && "hooks_failed".equals(hookStage)) {
                return State.HOOKS_FAILED;
            }
            if (hookStatusFresh && "hooks_installing".equals(hookStage)) {
                return State.HOOKS_INSTALLING;
            }
            return State.TARGET_INJECTED;
        }
        if (frameworkConnected) return State.FRAMEWORK_CONNECTED;
        if (targetLastSeenAt > 0L) return State.LAST_SEEN;
        return State.WAITING;
    }
}
