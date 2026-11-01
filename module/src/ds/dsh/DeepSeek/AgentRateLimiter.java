package ds.dsh.DeepSeek;

/** Java compatibility facade for the process-wide Agent pacing policy. */
final class AgentRateLimiter {
    private AgentRateLimiter() {}

    static final long MIN_GAP_MS = 8_000L;
    static final int PER_MINUTE = 5;
    static final int PER_HOUR = 60;

    static synchronized long waitMs(long now) {
        return AgentRateLimiterPolicy.waitMs(now);
    }

    static synchronized void recordSent(long now) {
        AgentRateLimiterPolicy.recordSent(now);
    }

    static synchronized boolean shouldWarnHourCap(long now) {
        return AgentRateLimiterPolicy.shouldWarnHourCap(now);
    }
}
