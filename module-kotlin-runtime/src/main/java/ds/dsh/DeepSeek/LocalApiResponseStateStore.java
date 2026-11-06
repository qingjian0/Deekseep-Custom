package ds.dsh.DeepSeek;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded in-memory continuation state for the OpenAI Responses endpoint.
 * State intentionally remains process-local and is cleared when the gateway stops.
 */
public final class LocalApiResponseStateStore {
    private static final int MAX_ENTRIES = 64;
    private static final long TTL_MS = 30L * 60L * 1000L;
    private static final int MAX_TRANSCRIPT_CHARS = 256 * 1024;
    private static final Map<String, State> STATES =
            new LinkedHashMap<String, State>(16, 0.75f, true);

    private LocalApiResponseStateStore() {}

    public static synchronized State get(String id) {
        return getAt(id, System.currentTimeMillis());
    }

    static synchronized State getAt(String id, long nowMs) {
        if (id == null) return null;
        Iterator<Map.Entry<String, State>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next().getValue();
            if (nowMs - state.updatedAtMs > TTL_MS) iterator.remove();
        }
        return STATES.get(id);
    }

    public static synchronized boolean put(String id, String transcript,
            Set<String> pendingToolCallIds) {
        return putAt(id, transcript, pendingToolCallIds, System.currentTimeMillis());
    }

    static synchronized boolean putAt(String id, String transcript,
            Set<String> pendingToolCallIds, long nowMs) {
        if (id == null || transcript == null || transcript.length() > MAX_TRANSCRIPT_CHARS) {
            return false;
        }
        STATES.put(id, new State(transcript, pendingToolCallIds, nowMs));
        while (STATES.size() > MAX_ENTRIES) {
            Iterator<String> iterator = STATES.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public static synchronized void clear() {
        STATES.clear();
    }

    static synchronized int sizeForTest() {
        return STATES.size();
    }

    public static final class State {
        public final String transcript;
        public final Set<String> pendingToolCallIds;
        final long updatedAtMs;

        State(String transcript, Set<String> pendingToolCallIds, long updatedAtMs) {
            this.transcript = transcript;
            this.pendingToolCallIds = pendingToolCallIds == null
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<String>(pendingToolCallIds));
            this.updatedAtMs = updatedAtMs;
        }
    }
}
