package ds.dsh.DeepSeek;

/** Java compatibility facade for local API keep-alive timing decisions. */
final class LocalApiKeepAliveDecision {
    private LocalApiKeepAliveDecision() {}

    static boolean heartbeatAlreadySatisfies(boolean enabling, long heartbeatAgeMs,
                                             boolean apiKeepaliveRequested) {
        return LocalApiKeepAlivePolicy.heartbeatAlreadySatisfies(
                enabling, heartbeatAgeMs, apiKeepaliveRequested);
    }

    static boolean throttleSameDirection(long nowMs, long lastLaunchMs,
                                         boolean lastEnabled, boolean nextEnabled) {
        return LocalApiKeepAlivePolicy.throttleSameDirection(
                nowMs, lastLaunchMs, lastEnabled, nextEnabled);
    }
}
