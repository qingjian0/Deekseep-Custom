package ds.dsh.DeepSeek;

/**
 * Process-wide MCP session state. A generation prevents a late HTTP response from restoring
 * a session ID after a newer handshake, configuration update, or session shutdown.
 */
final class AgentMcpSessionState {
    static final class Snapshot {
        final long generation;
        final String sessionId;

        Snapshot(long generation, String sessionId) {
            this.generation = generation;
            this.sessionId = sessionId;
        }
    }

    private long generation;
    private String sessionId = "";
    private String connectedKey = "";

    synchronized long beginHandshake() {
        generation++;
        sessionId = "";
        connectedKey = "";
        return generation;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(generation, sessionId);
    }

    synchronized String connectedKey() {
        return connectedKey;
    }

    synchronized boolean updateSessionId(long expectedGeneration, String value) {
        if (generation != expectedGeneration || value == null || value.trim().isEmpty()) {
            return false;
        }
        sessionId = value.trim();
        return true;
    }

    synchronized boolean publishConnectedKey(long expectedGeneration, String value) {
        if (generation != expectedGeneration || value == null || value.trim().isEmpty()) {
            return false;
        }
        connectedKey = value;
        return true;
    }

    synchronized void invalidate() {
        generation++;
        sessionId = "";
        connectedKey = "";
    }

    synchronized void failHandshake(long expectedGeneration) {
        if (generation != expectedGeneration) return;
        generation++;
        sessionId = "";
        connectedKey = "";
    }

    synchronized void endSession(long expectedGeneration) {
        if (generation != expectedGeneration) return;
        generation++;
        sessionId = "";
        connectedKey = "";
    }
}
