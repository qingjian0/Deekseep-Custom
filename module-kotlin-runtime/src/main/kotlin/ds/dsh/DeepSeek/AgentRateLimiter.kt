package ds.dsh.DeepSeek

import java.util.ArrayDeque

/**
 * Paces Agent-generated chat turns without dropping pending results.
 *
 * The policy state is isolated from Android time and UI APIs so rolling-window boundaries
 * can be tested deterministically. The public object preserves the existing Java static ABI.
 */
internal class AgentRateLimiterState {
    private val sent = ArrayDeque<Long>()
    private var lastHourWarn = 0L

    @Synchronized
    fun waitMs(now: Long): Long {
        while (sent.isNotEmpty() && now - sent.peekFirst() >= HOUR) {
            sent.pollFirst()
        }
        if (sent.isEmpty()) return 0L

        var wait = maxOf(0L, sent.peekLast() + AgentRateLimiter.MIN_GAP_MS - now)
        var inMinute = 0
        var oldestInMinute: Long? = null
        for (timestamp in sent) {
            if (now - timestamp < MINUTE) {
                inMinute++
                if (oldestInMinute == null) oldestInMinute = timestamp
            }
        }
        if (inMinute >= AgentRateLimiter.PER_MINUTE && oldestInMinute != null) {
            wait = maxOf(wait, oldestInMinute + MINUTE - now)
        }
        if (sent.size >= AgentRateLimiter.PER_HOUR) {
            wait = maxOf(wait, sent.peekFirst() + HOUR - now)
        }
        return wait
    }

    @Synchronized
    fun recordSent(now: Long) {
        sent.addLast(now)
    }

    @Synchronized
    fun shouldWarnHourCap(now: Long): Boolean {
        if (sent.size < AgentRateLimiter.PER_HOUR || now - lastHourWarn < WARN_INTERVAL_MS) {
            return false
        }
        lastHourWarn = now
        return true
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val WARN_INTERVAL_MS = 600_000L
    }
}

/** Java-compatible static facade used by the existing Agent send loop. */
object AgentRateLimiter {
    const val MIN_GAP_MS = 8_000L
    const val PER_MINUTE = 5
    const val PER_HOUR = 60

    private val state = AgentRateLimiterState()

    @JvmStatic
    fun waitMs(now: Long): Long = state.waitMs(now)

    @JvmStatic
    fun recordSent(now: Long) = state.recordSent(now)

    @JvmStatic
    fun shouldWarnHourCap(now: Long): Boolean = state.shouldWarnHourCap(now)
}
