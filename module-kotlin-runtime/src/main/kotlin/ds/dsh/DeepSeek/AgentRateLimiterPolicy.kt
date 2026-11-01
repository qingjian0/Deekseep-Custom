package ds.dsh.DeepSeek

import java.util.ArrayDeque

/**
 * Process-wide pacing state for Agent-generated chat turns.
 *
 * The queue and warning timestamp intentionally remain singleton state. All methods are
 * synchronized individually to preserve the original Java facade's concurrency semantics.
 */
object AgentRateLimiterPolicy {
    private const val MIN_GAP_MS = 8_000L
    private const val PER_MINUTE = 5
    private const val PER_HOUR = 60
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val HOUR_WARNING_INTERVAL_MS = 600_000L

    private val sent = ArrayDeque<Long>()
    private var lastHourWarn = 0L

    @JvmStatic
    @Synchronized
    fun waitMs(now: Long): Long {
        while (sent.isNotEmpty() && now - sent.peekFirst() >= HOUR_MS) {
            sent.pollFirst()
        }
        if (sent.isEmpty()) return 0L

        var wait = maxOf(0L, sent.peekLast() + MIN_GAP_MS - now)
        var inMinute = 0
        var oldestInMinute: Long? = null
        for (timestamp in sent) {
            if (now - timestamp < MINUTE_MS) {
                inMinute++
                if (oldestInMinute == null) oldestInMinute = timestamp
            }
        }
        if (inMinute >= PER_MINUTE && oldestInMinute != null) {
            wait = maxOf(wait, oldestInMinute + MINUTE_MS - now)
        }
        if (sent.size >= PER_HOUR) {
            wait = maxOf(wait, sent.peekFirst() + HOUR_MS - now)
        }
        return wait
    }

    @JvmStatic
    @Synchronized
    fun recordSent(now: Long) {
        sent.addLast(now)
    }

    @JvmStatic
    @Synchronized
    fun shouldWarnHourCap(now: Long): Boolean {
        if (sent.size < PER_HOUR || now - lastHourWarn < HOUR_WARNING_INTERVAL_MS) {
            return false
        }
        lastHourWarn = now
        return true
    }
}
