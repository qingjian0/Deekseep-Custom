package ds.dsh.DeepSeek;

public final class LocalApiKeepAliveDecisionRegressionTest {
    public static void main(String[] args) {
        require(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 0L, true),
                "fresh requested heartbeat must satisfy enabling");
        require(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 15_000L, true),
                "heartbeat age boundary must be inclusive");
        require(!LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 15_001L, true),
                "stale heartbeat must not satisfy enabling");
        require(!LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, -1L, true),
                "unknown heartbeat age must not satisfy enabling");
        require(!LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(false, 0L, true),
                "disabling must not be short-circuited by heartbeat");
        require(!LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 0L, false),
                "unrequested keep-alive must not satisfy enabling");

        require(LocalApiKeepAliveDecision.throttleSameDirection(
                        12_999L, 10_000L, true, true),
                "same-direction launches inside the three-second window must throttle");
        require(!LocalApiKeepAliveDecision.throttleSameDirection(
                        13_000L, 10_000L, true, true),
                "the three-second boundary must not throttle");
        require(!LocalApiKeepAliveDecision.throttleSameDirection(
                        9_000L, 10_000L, true, true),
                "clock rollback (now before the previous launch) must not throttle");
        require(!LocalApiKeepAliveDecision.throttleSameDirection(
                        12_000L, 10_000L, true, false),
                "direction changes must not throttle");
        require(!LocalApiKeepAliveDecision.throttleSameDirection(
                        12_000L, 0L, true, true),
                "missing previous launch must not throttle");

        System.out.println("Local API keep-alive decision regression passed.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
