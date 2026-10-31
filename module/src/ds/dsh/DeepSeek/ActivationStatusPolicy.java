package ds.dsh.DeepSeek;

/**
 * Classifies target-process injection using a short-lived heartbeat.
 *
 * <p>A heartbeat is evidence that the target process is currently running with the module
 * injected only while it is fresh. Older heartbeats are historical evidence and must never be
 * presented as an active module state.</p>
 */
final class ActivationStatusPolicy {
    static final long HEARTBEAT_FRESH_MS = 90_000L;

    enum State {
        WAITING_FOR_TARGET,
        CURRENTLY_INJECTED,
        STALE
    }

    private ActivationStatusPolicy() {}

    static State classify(long targetHeartbeatAt, long now) {
        if (targetHeartbeatAt <= 0L || targetHeartbeatAt > now) {
            return State.WAITING_FOR_TARGET;
        }
        long age = now - targetHeartbeatAt;
        return age <= HEARTBEAT_FRESH_MS
                ? State.CURRENTLY_INJECTED
                : State.STALE;
    }
}
