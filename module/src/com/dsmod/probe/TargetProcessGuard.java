package com.dsmod.probe;

/**
 * Pure process-admission guard for the Xposed package callback.
 *
 * <p>Keeping package scope and duplicate-process suppression outside Main makes this boundary
 * independently testable without Android or an Xposed runtime.</p>
 */
final class TargetProcessGuard {
    private static final String PID_PROPERTY = "deekseep.module.injected.pid";

    enum Admission {
        ALLOWED,
        NOT_TARGET,
        DUPLICATE_PROCESS
    }

    private TargetProcessGuard() {}

    static Admission admit(String packageName, String processId) {
        if (!"com.deepseek.chat".equals(packageName)
                && !"com.deepseek.chat.embedded".equals(packageName)) {
            return Admission.NOT_TARGET;
        }
        if (processId == null || processId.isEmpty()) {
            return Admission.NOT_TARGET;
        }
        synchronized (TargetProcessGuard.class) {
            if (processId.equals(System.getProperty(PID_PROPERTY))) {
                return Admission.DUPLICATE_PROCESS;
            }
            System.setProperty(PID_PROPERTY, processId);
            return Admission.ALLOWED;
        }
    }
}
