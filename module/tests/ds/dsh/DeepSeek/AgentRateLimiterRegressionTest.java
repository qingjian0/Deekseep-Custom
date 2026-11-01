package ds.dsh.DeepSeek;

public final class AgentRateLimiterRegressionTest {
    public static void main(String[] args) {
        final long base = 10_000_000L;

        require(AgentRateLimiter.waitMs(base) == 0L,
                "an empty limiter must allow the first turn");
        AgentRateLimiter.recordSent(base);
        require(AgentRateLimiter.waitMs(base + AgentRateLimiter.MIN_GAP_MS - 1L) == 1L,
                "the minimum inter-turn gap must be enforced");
        require(AgentRateLimiter.waitMs(base + AgentRateLimiter.MIN_GAP_MS) == 0L,
                "the minimum-gap boundary must be inclusive");

        AgentRateLimiter.recordSent(base + 8_000L);
        AgentRateLimiter.recordSent(base + 16_000L);
        AgentRateLimiter.recordSent(base + 24_000L);
        AgentRateLimiter.recordSent(base + 32_000L);
        require(AgentRateLimiter.waitMs(base + 32_000L) == 28_000L,
                "the fifth turn in a rolling minute must wait for the oldest turn to expire");
        require(AgentRateLimiter.waitMs(base + 60_000L) == 0L,
                "a turn exactly one minute old must leave the rolling-minute window");

        for (long elapsed = 40_000L; elapsed <= 472_000L; elapsed += 8_000L) {
            AgentRateLimiter.recordSent(base + elapsed);
        }
        require(AgentRateLimiter.waitMs(base + 472_000L) == 3_128_000L,
                "the sixtieth turn must wait for the oldest turn to leave the rolling-hour window");
        require(AgentRateLimiter.shouldWarnHourCap(base + 472_000L),
                "the hourly-cap warning must be shown when the cap is first reached");
        require(!AgentRateLimiter.shouldWarnHourCap(base + 472_000L + 599_999L),
                "the hourly-cap warning must be suppressed for ten minutes");
        require(AgentRateLimiter.shouldWarnHourCap(base + 472_000L + 600_000L),
                "the hourly-cap warning must be allowed at the ten-minute boundary");
        require(AgentRateLimiter.waitMs(base + 3_600_000L) == 0L,
                "a turn exactly one hour old must leave the rolling-hour window");

        System.out.println("Agent rate limiter regression passed.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
