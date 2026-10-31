package ds.dsh.DeepSeek;

/**
 * Classifies activation evidence without confusing framework connectivity with target injection.
 *
 * <p>A persisted target heartbeat is historical proof that the target process loaded the module.
 * It is deliberately not called "currently active": process liveness is not tracked.</p>
 */
final class ActivationStatusPolicy {
    enum State {
        WAITING_FOR_TARGET,
        TARGET_SEEN
    }

    private ActivationStatusPolicy() {}

    static State classify(long targetHeartbeatAt, boolean frameworkConnected) {
        // Framework connectivity is useful diagnostic context, but cannot prove target scope.
        return targetHeartbeatAt > 0L ? State.TARGET_SEEN : State.WAITING_FOR_TARGET;
    }
}
