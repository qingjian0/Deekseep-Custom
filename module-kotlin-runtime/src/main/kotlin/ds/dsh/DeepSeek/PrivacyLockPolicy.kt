package ds.dsh.DeepSeek

/** Pure transition policy for the existing host-resume application-lock state machine. */
object PrivacyLockPolicy {
    private const val MINIMUM_LOCK_DELAY_MS = 2_000L
    private const val MINUTE_MS = 60_000L

    /**
     * True only when a previously unlocked app must transition to locked after a host pause.
     * Authentication-in-progress, missing pause timestamps and elapsed-time rollback stay inert.
     */
    @JvmStatic
    fun shouldTransitionToLocked(
        lastPauseAt: Long,
        now: Long,
        lockMinutes: Int,
        currentlyLocked: Boolean,
        authInProgress: Boolean,
    ): Boolean {
        if (authInProgress || currentlyLocked || lastPauseAt <= 0L) return false
        val requiredDelay = maxOf(lockMinutes, 0).toLong() * MINUTE_MS
        val delay = maxOf(requiredDelay, MINIMUM_LOCK_DELAY_MS)
        return now - lastPauseAt >= delay
    }
}
