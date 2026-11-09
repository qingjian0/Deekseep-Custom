package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, value-free Local API execution diagnostics.
 *
 * Records only stage names, states, error codes, timings and safe detail strings. Never pass
 * prompts, request bodies, headers, tokens, session IDs, PoW values or native payloads here.
 */
final class LocalApiDiagnostics {
    private static final int MAX_REQUESTS = 64;
    private static final int MAX_EVENTS_PER_REQUEST = 128;
    private static final long TTL_MS = 30L * 60L * 1000L;
    private static final Map<String, Trace> TRACES =
            new ConcurrentHashMap<String, Trace>();
    private static final AtomicLong ORDER = new AtomicLong();

    private LocalApiDiagnostics() {}

    static void begin(String requestId, String protocol, String mode) {
        if (empty(requestId)) return;
        Trace trace = new Trace(requestId, safe(protocol, 24), safe(mode, 24));
        TRACES.put(requestId, trace);
        trace.add("request", "running", "", "Request accepted");
        prune();
    }

    static void stage(String requestId, String name, String status,
                      String code, String detail) {
        Trace trace = empty(requestId) ? null : TRACES.get(requestId);
        if (trace != null) trace.add(name, status, code, detail);
    }

    static void finish(String requestId, String status, String code, String detail) {
        stage(requestId, "request", status, code, detail);
    }

    static JSONObject snapshot(String requestId) {
        Trace trace = empty(requestId) ? null : TRACES.get(requestId);
        return trace == null ? null : trace.snapshot();
    }

    static JSONArray recent(int limit) {
        List<Trace> traces = new ArrayList<Trace>(TRACES.values());
        Collections.sort(traces, new Comparator<Trace>() {
            @Override public int compare(Trace left, Trace right) {
                return Long.compare(right.order, left.order);
            }
        });
        JSONArray result = new JSONArray();
        int count = Math.max(1, Math.min(limit, 32));
        for (int i = 0; i < traces.size() && i < count; i++) {
            result.put(traces.get(i).summary());
        }
        return result;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Trace> entry : TRACES.entrySet()) {
            if (now - entry.getValue().updatedAt > TTL_MS) {
                TRACES.remove(entry.getKey(), entry.getValue());
            }
        }
        if (TRACES.size() <= MAX_REQUESTS) return;
        List<Trace> traces = new ArrayList<Trace>(TRACES.values());
        Collections.sort(traces, new Comparator<Trace>() {
            @Override public int compare(Trace left, Trace right) {
                return Long.compare(left.order, right.order);
            }
        });
        for (int i = 0; i < traces.size() - MAX_REQUESTS; i++) {
            TRACES.remove(traces.get(i).requestId, traces.get(i));
        }
    }

    private static boolean empty(String value) {
        return value == null || value.length() == 0;
    }

    private static String safe(String value, int max) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < max; i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static final class Trace {
        final String requestId;
        final String protocol;
        final String mode;
        final long order = ORDER.incrementAndGet();
        final long startedAt = System.currentTimeMillis();
        final List<JSONObject> events = new ArrayList<JSONObject>();
        volatile long updatedAt = startedAt;
        volatile String status = "running";
        volatile String errorCode = "";

        Trace(String requestId, String protocol, String mode) {
            this.requestId = requestId;
            this.protocol = protocol;
            this.mode = mode;
        }

        synchronized void add(String stage, String nextStatus, String code, String detail) {
            long now = System.currentTimeMillis();
            String normalizedStatus = safe(nextStatus, 24);
            if ("failed".equals(normalizedStatus) || "timeout".equals(normalizedStatus)
                    || "cancelled".equals(normalizedStatus)) {
                errorCode = safe(code, 64);
            }
            if ("succeeded".equals(normalizedStatus) || "failed".equals(normalizedStatus)
                    || "cancelled".equals(normalizedStatus) || "timeout".equals(normalizedStatus)) {
                if ("request".equals(stage)) status = normalizedStatus;
            }
            JSONObject event = new JSONObject();
            try {
                event.put("sequence", events.size() + 1);
                event.put("stage", safe(stage, 48));
                event.put("status", normalizedStatus);
                event.put("code", safe(code, 64));
                event.put("detail", safe(detail, 160));
                event.put("timestamp", now);
                event.put("elapsedMs", Math.max(0L, now - startedAt));
                events.add(event);
                while (events.size() > MAX_EVENTS_PER_REQUEST) events.remove(0);
            } catch (Exception ignored) {
            }
            updatedAt = now;
        }

        synchronized JSONObject summary() {
            JSONObject value = new JSONObject();
            try {
                value.put("request_id", requestId);
                value.put("protocol", protocol);
                value.put("mode", mode);
                value.put("status", status);
                value.put("error_code", errorCode);
                value.put("started_at", startedAt);
                value.put("updated_at", updatedAt);
                value.put("duration_ms", Math.max(0L, updatedAt - startedAt));
                value.put("event_count", events.size());
            } catch (Exception ignored) {
            }
            return value;
        }

        synchronized JSONObject snapshot() {
            JSONObject value = summary();
            try {
                JSONArray copy = new JSONArray();
                for (JSONObject event : events) copy.put(new JSONObject(event.toString()));
                value.put("events", copy);
            } catch (Exception ignored) {
            }
            return value;
        }
    }
}
