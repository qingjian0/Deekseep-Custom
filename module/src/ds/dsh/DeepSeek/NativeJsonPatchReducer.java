package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pure reducer for normalized RFC 6902 JSON Patch operations.
 *
 * This class is deliberately not wired to the obfuscated code289 st3 decoder: the host's
 * operation enum and exact path encoding must be proven independently before that mapping is
 * enabled. Applying a batch is atomic; callers receive the original tree on any failure.
 */
public final class NativeJsonPatchReducer {
    private NativeJsonPatchReducer() {}

    public static final class Result {
        public final Object value;
        public final boolean applied;
        public final int failedOperation;
        public final String error;

        private Result(Object value, boolean applied, int failedOperation, String error) {
            this.value = value;
            this.applied = applied;
            this.failedOperation = failedOperation;
            this.error = error;
        }
    }

    public static Result apply(Object source, JSONArray operations) {
        if (source == null || operations == null) {
            return new Result(source, false, -1, "source and operations are required");
        }
        final Object original;
        try {
            original = copy(source);
        } catch (Exception error) {
            return new Result(source, false, -1, "source is not valid JSON");
        }
        Object working = original;
        for (int i = 0; i < operations.length(); i++) {
            try {
                JSONObject operation = operations.getJSONObject(i);
                String op = operation.getString("op");
                String path = operation.getString("path");
                working = applyOne(working, op, path, operation);
            } catch (Exception error) {
                return new Result(source, false, i, safeMessage(error));
            }
        }
        return new Result(working, true, -1, null);
    }

    private static Object applyOne(Object root, String op, String path, JSONObject operation)
            throws JSONException {
        List<String> tokens = parsePointer(path);
        if ("add".equals(op)) {
            return write(root, tokens, requiredValue(operation), false, true);
        }
        if ("replace".equals(op)) {
            return write(root, tokens, requiredValue(operation), true, false);
        }
        if ("remove".equals(op)) {
            return remove(root, tokens).root;
        }
        if ("test".equals(op)) {
            Object actual = read(root, tokens);
            if (!jsonEquals(actual, requiredValue(operation))) {
                throw new JSONException("test operation failed");
            }
            return root;
        }
        if ("copy".equals(op) || "move".equals(op)) {
            List<String> from = parsePointer(operation.getString("from"));
            Object value = copy(read(root, from));
            if ("copy".equals(op)) return write(root, tokens, value, false, true);
            if (isDescendant(from, tokens)) {
                throw new JSONException("cannot move a value into its descendant");
            }
            Removal removed = remove(root, from);
            return write(removed.root, tokens, value, false, true);
        }
        throw new JSONException("unsupported operation: " + op);
    }

    private static Object requiredValue(JSONObject operation) throws JSONException {
        if (!operation.has("value")) throw new JSONException("value is required");
        return operation.get("value");
    }

    private static Object write(Object root, List<String> path, Object value,
                                boolean mustExist, boolean insert) throws JSONException {
        if (path.isEmpty()) {
            if (mustExist && root == null) throw new JSONException("root does not exist");
            return copy(value);
        }
        Object parent = parent(root, path);
        String key = path.get(path.size() - 1);
        if (parent instanceof JSONObject) {
            JSONObject object = (JSONObject) parent;
            if (mustExist && !object.has(key)) throw new JSONException("object member missing");
            object.put(key, copy(value));
            return root;
        }
        if (parent instanceof JSONArray) {
            JSONArray array = (JSONArray) parent;
            if ("-".equals(key) && insert) {
                array.put(copy(value));
                return root;
            }
            int index = index(key, array.length(), insert);
            if (insert) {
                JSONArray rebuilt = new JSONArray();
                for (int i = 0; i < array.length() + 1; i++) {
                    if (i == index) rebuilt.put(copy(value));
                    else rebuilt.put(array.get(i < index ? i : i - 1));
                }
                replaceArrayContents(array, rebuilt);
            } else {
                if (index >= array.length()) throw new JSONException("array index out of range");
                array.put(index, copy(value));
            }
            return root;
        }
        throw new JSONException("parent is not a container");
    }

    private static Removal remove(Object root, List<String> path) throws JSONException {
        if (path.isEmpty()) return new Removal(null);
        Object parent = parent(root, path);
        String key = path.get(path.size() - 1);
        if (parent instanceof JSONObject) {
            JSONObject object = (JSONObject) parent;
            if (!object.has(key)) throw new JSONException("object member missing");
            object.remove(key);
            return new Removal(root);
        }
        if (parent instanceof JSONArray) {
            JSONArray array = (JSONArray) parent;
            int index = index(key, array.length(), false);
            if (index >= array.length()) throw new JSONException("array index out of range");
            JSONArray rebuilt = new JSONArray();
            for (int i = 0; i < array.length(); i++) if (i != index) rebuilt.put(array.get(i));
            replaceArrayContents(array, rebuilt);
            return new Removal(root);
        }
        throw new JSONException("parent is not a container");
    }

    private static Object read(Object root, List<String> path) throws JSONException {
        Object current = root;
        for (String token : path) {
            if (current instanceof JSONObject) {
                JSONObject object = (JSONObject) current;
                if (!object.has(token)) throw new JSONException("object member missing");
                current = object.get(token);
            } else if (current instanceof JSONArray) {
                JSONArray array = (JSONArray) current;
                int index = index(token, array.length(), false);
                if (index >= array.length()) throw new JSONException("array index out of range");
                current = array.get(index);
            } else {
                throw new JSONException("path traverses a non-container");
            }
        }
        return current;
    }

    private static Object parent(Object root, List<String> path) throws JSONException {
        if (path.isEmpty()) throw new JSONException("root has no parent");
        return read(root, path.subList(0, path.size() - 1));
    }

    private static List<String> parsePointer(String pointer) throws JSONException {
        if (pointer == null) throw new JSONException("path is required");
        List<String> tokens = new ArrayList<String>();
        if (pointer.length() == 0) return tokens;
        if (pointer.charAt(0) != '/') throw new JSONException("path must be a JSON Pointer");
        String[] parts = pointer.substring(1).split("/", -1);
        for (String part : parts) {
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c != '~') decoded.append(c);
                else {
                    if (++i >= part.length()) throw new JSONException("invalid JSON Pointer escape");
                    char escaped = part.charAt(i);
                    if (escaped == '0') decoded.append('~');
                    else if (escaped == '1') decoded.append('/');
                    else throw new JSONException("invalid JSON Pointer escape");
                }
            }
            tokens.add(decoded.toString());
        }
        return tokens;
    }

    private static int index(String token, int length, boolean allowEnd) throws JSONException {
        if (token.length() == 0 || (token.length() > 1 && token.charAt(0) == '0')) {
            throw new JSONException("invalid array index");
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) < '0' || token.charAt(i) > '9') {
                throw new JSONException("invalid array index");
            }
        }
        final int value;
        try { value = Integer.parseInt(token); }
        catch (NumberFormatException error) { throw new JSONException("invalid array index"); }
        if (value < 0 || value > length || (!allowEnd && value == length)) {
            throw new JSONException("array index out of range");
        }
        return value;
    }

    private static boolean isDescendant(List<String> from, List<String> path) {
        if (path.size() <= from.size()) return false;
        for (int i = 0; i < from.size(); i++) if (!from.get(i).equals(path.get(i))) return false;
        return true;
    }

    private static boolean jsonEquals(Object left, Object right) {
        if (left == right) return true;
        if (isJsonNull(left) || isJsonNull(right)) {
            return isJsonNull(left) && isJsonNull(right);
        }
        if (left == null || right == null) return false;
        if (left instanceof JSONObject && right instanceof JSONObject) {
            JSONObject a = (JSONObject) left;
            JSONObject b = (JSONObject) right;
            if (a.length() != b.length()) return false;
            Iterator<String> keys = a.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!b.has(key) || !jsonEquals(a.opt(key), b.opt(key))) return false;
            }
            return true;
        }
        if (left instanceof JSONArray && right instanceof JSONArray) {
            JSONArray a = (JSONArray) left;
            JSONArray b = (JSONArray) right;
            if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) {
                if (!jsonEquals(a.opt(i), b.opt(i))) return false;
            }
            return true;
        }
        if (left instanceof Number && right instanceof Number) {
            try {
                return new BigDecimal(left.toString()).compareTo(
                        new BigDecimal(right.toString())) == 0;
            } catch (NumberFormatException ignored) {
                return Double.compare(((Number) left).doubleValue(),
                        ((Number) right).doubleValue()) == 0;
            }
        }
        return left.equals(right);
    }

    private static boolean isJsonNull(Object value) {
        return value == null || value == JSONObject.NULL;
    }

    private static Object copy(Object value) throws JSONException {
        if (value instanceof JSONObject) return new JSONObject(value.toString());
        if (value instanceof JSONArray) return new JSONArray(value.toString());
        return value;
    }

    private static void replaceArrayContents(JSONArray target, JSONArray replacement)
            throws JSONException {
        while (target.length() > 0) target.remove(target.length() - 1);
        for (int i = 0; i < replacement.length(); i++) target.put(replacement.get(i));
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.length() == 0
                ? error.getClass().getSimpleName() : message;
    }

    private static final class Removal {
        final Object root;
        Removal(Object root) { this.root = root; }
    }
}
