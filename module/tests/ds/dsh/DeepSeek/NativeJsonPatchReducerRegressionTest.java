package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

/** Executable tests for the isolated normalized RFC 6902 reducer. */
public final class NativeJsonPatchReducerRegressionTest {
    public static void main(String[] args) throws Exception {
        appliesNestedAndArrayOperations();
        supportsEscapedJsonPointerTokens();
        supportsCopyMoveAndTest();
        rejectsInvalidOperationsAtomically();
        System.out.println("NativeJsonPatchReducer regression tests passed");
    }

    private static void appliesNestedAndArrayOperations() throws Exception {
        JSONObject source = new JSONObject()
                .put("response", new JSONObject()
                        .put("fragments", new JSONArray()
                                .put(new JSONObject().put("type", "ANSWER").put("text", "A"))
                                .put(new JSONObject().put("type", "THINK").put("text", "B"))));
        JSONArray patch = new JSONArray()
                .put(op("add", "/response/fragments/1",
                        new JSONObject().put("type", "TOOL").put("text", "C")))
                .put(op("replace", "/response/fragments/0/text", "A1"))
                .put(op("remove", "/response/fragments/2"));
        NativeJsonPatchReducer.Result result = NativeJsonPatchReducer.apply(source, patch);
        check(result.applied, "valid patch was rejected: " + result.error);
        JSONArray fragments = result.value instanceof JSONObject
                ? ((JSONObject) result.value).getJSONObject("response").getJSONArray("fragments")
                : null;
        check(fragments != null && fragments.length() == 2, "array edits were not applied");
        check("A1".equals(fragments.getJSONObject(0).getString("text")),
                "nested replacement failed");
        check("TOOL".equals(fragments.getJSONObject(1).getString("type")),
                "array insertion failed");
        check("A".equals(source.getJSONObject("response").getJSONArray("fragments")
                        .getJSONObject(0).getString("text")),
                "source tree was mutated");
    }

    private static void supportsEscapedJsonPointerTokens() throws Exception {
        JSONObject source = new JSONObject().put("a/b", new JSONObject().put("x~y", 1));
        JSONArray patch = new JSONArray().put(op("replace", "/a~1b/x~0y", 2));
        NativeJsonPatchReducer.Result result = NativeJsonPatchReducer.apply(source, patch);
        check(result.applied, "escaped pointer rejected");
        check(((JSONObject) result.value).getJSONObject("a/b").getInt("x~y") == 2,
                "JSON Pointer escapes were decoded incorrectly");
    }

    private static void supportsCopyMoveAndTest() throws Exception {
        JSONObject source = new JSONObject().put("items", new JSONArray()
                .put("a").put("b").put("c"));
        JSONArray patch = new JSONArray()
                .put(op("test", "/items/0", "a"))
                .put(new JSONObject().put("op", "copy").put("from", "/items/0")
                        .put("path", "/items/3"))
                .put(new JSONObject().put("op", "move").put("from", "/items/1")
                        .put("path", "/items/0"));
        NativeJsonPatchReducer.Result result = NativeJsonPatchReducer.apply(source, patch);
        check(result.applied, "copy/move/test rejected: " + result.error);
        JSONArray items = ((JSONObject) result.value).getJSONArray("items");
        check(items.length() == 4, "copy/move produced wrong array length");
        check("b".equals(items.getString(0)) && "a".equals(items.getString(1))
                        && "c".equals(items.getString(2)) && "a".equals(items.getString(3)),
                "copy/move ordering is wrong");
    }

    private static void rejectsInvalidOperationsAtomically() throws Exception {
        JSONObject source = new JSONObject().put("value", 1);
        JSONArray patch = new JSONArray()
                .put(op("replace", "/value", 2))
                .put(op("remove", "/missing"));
        NativeJsonPatchReducer.Result result = NativeJsonPatchReducer.apply(source, patch);
        check(!result.applied && result.failedOperation == 1,
                "invalid second operation was not reported");
        check(source.getInt("value") == 1, "source mutated after failed batch");
        check(((JSONObject) result.value).getInt("value") == 1,
                "failed batch returned partially modified state");
        check(!NativeJsonPatchReducer.apply(source,
                new JSONArray().put(op("unknown", "/value", 2))).applied,
                "unknown operation was accepted");
    }

    private static JSONObject op(String name, String path, Object value) throws Exception {
        return new JSONObject().put("op", name).put("path", path).put("value", value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
