package ds.dsh.DeepSeek;

public final class AgentMcpSessionStateRegressionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AgentMcpSessionState state = new AgentMcpSessionState();
        check(state.snapshot().sessionId.isEmpty(), "initial session ID must be empty");
        check(state.connectedKey().isEmpty(), "initial connected key must be empty");

        long first = state.beginHandshake();
        check(state.updateSessionId(first, " session-A "), "current generation must accept session ID");
        check(state.publishConnectedKey(first, "server-A"), "current generation must publish key");
        check("session-A".equals(state.snapshot().sessionId), "session ID must be trimmed");
        check("server-A".equals(state.connectedKey()), "connected key must be visible");

        long second = state.beginHandshake();
        check(second > first, "new handshake must advance generation");
        check(!state.updateSessionId(first, "stale-session"),
                "late response from old handshake must not restore session ID");
        check(!state.publishConnectedKey(first, "stale-server"),
                "old handshake must not publish a connected key");
        check(state.snapshot().sessionId.isEmpty(), "new handshake must clear old ID");
        check(state.connectedKey().isEmpty(), "new handshake must clear old key");

        check(state.updateSessionId(second, "session-B"), "current generation must accept ID");
        check(state.publishConnectedKey(second, "server-B"), "current generation must publish key");
        state.invalidate();
        check(state.snapshot().sessionId.isEmpty(), "invalidation must clear ID");
        check(state.connectedKey().isEmpty(), "invalidation must clear key");
        check(!state.updateSessionId(second, "late-after-invalidate"),
                "late response after invalidation must be ignored");

        long third = state.beginHandshake();
        check(state.updateSessionId(third, "session-C"), "third handshake must accept ID");
        state.failHandshake(third);
        check(state.snapshot().sessionId.isEmpty(), "failed handshake must clear ID");
        check(state.connectedKey().isEmpty(), "failed handshake must clear key");
        check(!state.updateSessionId(third, "late-after-failure"),
                "failed generation must be retired");

        long fourth = state.beginHandshake();
        check(state.updateSessionId(fourth, "session-D"), "fourth handshake must accept ID");
        state.endSession(fourth);
        check(state.snapshot().sessionId.isEmpty(), "session end must clear ID");
        check(state.connectedKey().isEmpty(), "session end must clear key");
        check(!state.updateSessionId(fourth, "late-after-end"),
                "ended generation must be retired");

        System.out.println("AgentMcpSessionStateRegressionTest passed");
    }
}
