package ds.dsh.DeepSeek;

import java.util.List;

public final class AgentToolTraceStoreRegressionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void rejectedBytes(byte[] bytes, String message) throws Exception {
        try {
            AgentToolTraceStore.decodeRecords(bytes);
            throw new AssertionError(message);
        } catch (java.nio.charset.CharacterCodingException expected) {
            // Malformed UTF-8 must not be normalized into apparently valid JSON.
        }
    }

    private static void rejected(String json, String message) throws Exception {
        try {
            AgentToolTraceStore.decodeRecords(json);
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Invalid persisted records must fail the entire load, not be silently skipped.
        } catch (org.json.JSONException expected) {
            // Invalid JSON must also fail the entire load.
        }
    }

    public static void main(String[] args) throws Exception {
        String valid = "[{\"key\":\"scope-1\\u001fcall-1\","
                + "\"input\":\"{}\",\"output\":\"{}\","
                + "\"finished\":true,\"success\":false,"
                + "\"started_at\":10,\"finished_at\":20}]";
        List<AgentToolTraceStore.Trace> records = AgentToolTraceStore.decodeRecords(valid);
        check(records.size() == 1, "valid trace record was not decoded");
        check("scope-1\u001fcall-1".equals(records.get(0).key),
                "trace key changed during decode");
        check(records.get(0).finished && !records.get(0).success,
                "trace completion fields changed during decode");

        rejectedBytes(new byte[]{'[', '{', '"', 'k', 'e', 'y', '"', ':', '"',
                        (byte) 0xc3, '(', '"', '}', ']'},
                "malformed UTF-8 trace data was accepted");
        rejected("[null]", "null record was silently skipped");
        rejected("[1]", "non-object record was silently skipped");
        rejected("[{}]", "record without key was accepted");
        rejected("[{\"key\":\"  \"}]", "blank record key was accepted");
        rejected("[{\"key\":\"a\"},{\"key\":\"a\"}]",
                "duplicate record key was accepted");
        rejected("[{\"key\":\"a\",\"input\":7}]",
                "record with invalid input type was accepted");
        rejected("[{\"key\":\"a\",\"finished\":\"true\"}]",
                "record with invalid finished type was accepted");
        rejected("[{\"key\":\"a\",\"started_at\":\"10\"}]",
                "record with invalid timestamp type was accepted");
        System.out.println("AgentToolTraceStore regression tests passed");
    }
}
