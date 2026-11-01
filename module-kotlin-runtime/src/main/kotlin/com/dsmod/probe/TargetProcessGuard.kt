package com.dsmod.probe

/**
 * Pure process-admission guard for the Xposed package callback.
 *
 * Package scope and duplicate-process suppression stay independent from Android and Xposed
 * APIs so that the contract can be tested as a JVM runtime unit.
 */
class TargetProcessGuard private constructor() {
    enum class Admission {
        ALLOWED,
        NOT_TARGET,
        DUPLICATE_PROCESS,
    }

    companion object {
        private const val PID_PROPERTY = "deekseep.module.injected.pid"

        @JvmStatic
        fun admit(packageName: String?, processId: String?): Admission {
            if (packageName != "com.deepseek.chat" &&
                packageName != "com.deepseek.chat.embedded"
            ) {
                return Admission.NOT_TARGET
            }
            if (processId.isNullOrEmpty()) {
                return Admission.NOT_TARGET
            }
            return synchronized(TargetProcessGuard::class.java) {
                if (processId == System.getProperty(PID_PROPERTY)) {
                    Admission.DUPLICATE_PROCESS
                } else {
                    System.setProperty(PID_PROPERTY, processId)
                    Admission.ALLOWED
                }
            }
        }
    }
}
