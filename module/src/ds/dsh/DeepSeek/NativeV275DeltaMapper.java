package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maps only code295 RESPONSE and THINK fragment content into the Local API text/reasoning view.
 *
 * It follows the inspected host fragment paths and SET/APPEND behavior. Search, tool and other
 * fragment types are retained for index alignment but do not produce text/reasoning output.
 */
final class NativeV275DeltaMapper {
    static final String TYPE_RESPONSE = "RESPONSE";
    static final String TYPE_THINK = "THINK";
    static final String TYPE_TOOL_OPEN = "TOOL_OPEN";
    static final String TYPE_TOOL_FIND = "TOOL_FIND";
    static final String TYPE_SEARCH = "SEARCH";

    static final class Fragment {
        final String type;
        String content;

        Fragment(String type, String content) {
            this.type = type;
            this.content = content;
        }
    }

    static final class State {
        private final List<Fragment> fragments = new ArrayList<Fragment>();

        int fragmentCount() { return fragments.size(); }
    }

    static final class Result {
        final boolean accepted;
        final String text;
        final String reasoning;
        final boolean textChanged;
        final boolean reasoningChanged;
        final String error;

        private Result(boolean accepted, String text, String reasoning,
                boolean textChanged, boolean reasoningChanged, String error) {
            this.accepted = accepted;
            this.text = text;
            this.reasoning = reasoning;
            this.textChanged = textChanged;
            this.reasoningChanged = reasoningChanged;
            this.error = error;
        }
    }

    private NativeV275DeltaMapper() {}

    static Result apply(State state, NativeV275DeltaCommandDecoder.Command command) {
        if (state == null || command == null) return rejected("state and command are required");
        String beforeText = aggregate(state, TYPE_RESPONSE);
        String beforeReasoning = aggregate(state, TYPE_THINK);
        State working = copyState(state);
        try {
            if (command.isInitialSnapshot()) {
                loadSnapshot(working, command.value);
            } else {
                applyCommand(working, "", command, 0);
            }
            String text = aggregate(working, TYPE_RESPONSE);
            String reasoning = aggregate(working, TYPE_THINK);
            state.fragments.clear();
            state.fragments.addAll(copyFragments(working.fragments));
            return new Result(true, text, reasoning,
                    !beforeText.equals(text), !beforeReasoning.equals(reasoning), null);
        } catch (IllegalArgumentException error) {
            return new Result(false, beforeText, beforeReasoning, false, false, error.getMessage());
        }
    }

    private static void loadSnapshot(State state, Object raw) {
        Map<?, ?> root = requireMap(raw, "initial snapshot must be an object");
        Object response = root.get("response");
        Map<?, ?> message = requireMap(response, "snapshot response must be an object");
        Object rawFragments = message.containsKey("fragments")
                ? message.get("fragments") : Collections.emptyList();
        List<Fragment> parsed = parseFragments(rawFragments, "snapshot response.fragments");
        state.fragments.clear();
        state.fragments.addAll(parsed);
    }

    private static void applyCommand(State state, String prefix,
            NativeV275DeltaCommandDecoder.Command command, int depth) {
        if (depth > 16) throw new IllegalArgumentException("nested BATCH limit exceeded");
        String path = join(prefix, command.path == null ? "" : command.path);
        if (command.isBatch()) {
            for (NativeV275DeltaCommandDecoder.Command child : command.batch) {
                applyCommand(state, path, child, depth + 1);
            }
            return;
        }
        if (!NativeV275DeltaCommandDecoder.OP_SET.equals(command.operation)
                && !NativeV275DeltaCommandDecoder.OP_APPEND.equals(command.operation)) {
            throw new IllegalArgumentException("unsupported operation in mapper");
        }
        applyPath(state, path, command.operation, command.value);
    }

    private static void applyPath(State state, String path, String operation, Object value) {
        String[] parts = path.split("/", -1);
        int offset = parts.length > 0 && parts[0].length() == 0 ? 1 : 0;
        int count = parts.length - offset;
        if (count == 2 && "response".equals(parts[offset])
                && "fragments".equals(parts[offset + 1])) {
            List<Fragment> incoming = parseFragments(value, "delta fragments");
            if (NativeV275DeltaCommandDecoder.OP_SET.equals(operation)) {
                state.fragments.clear();
                state.fragments.addAll(incoming);
            } else {
                state.fragments.addAll(incoming);
            }
            return;
        }
        if (count == 4 && "response".equals(parts[offset])
                && "fragments".equals(parts[offset + 1])
                && "content".equals(parts[offset + 3])) {
            int index = parseIndex(parts[offset + 2], state.fragments.size());
            Fragment fragment = state.fragments.get(index);
            if (!TYPE_RESPONSE.equals(fragment.type) && !TYPE_THINK.equals(fragment.type)) {
                return; // Other fragment types have their own host-specific field semantics.
            }
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("text fragment content must be a string");
            }
            String next = (String) value;
            if (NativeV275DeltaCommandDecoder.OP_APPEND.equals(operation)) {
                fragment.content = fragment.content + next;
            } else {
                fragment.content = next;
            }
        }
        // All other known message fields/fragments are outside this text/reasoning mapper.
    }

    private static List<Fragment> parseFragments(Object raw, String label) {
        if (!(raw instanceof List<?>)) {
            throw new IllegalArgumentException(label + " must be an array");
        }
        List<Fragment> parsed = new ArrayList<Fragment>();
        for (Object item : (List<?>) raw) {
            Map<?, ?> object = requireMap(item, label + " entry must be an object");
            Object typeValue = object.get("type");
            Object idValue = object.get("id");
            if (!(typeValue instanceof String) || !(idValue instanceof Number)) {
                throw new IllegalArgumentException(label + " entry requires string type and numeric id");
            }
            String type = (String) typeValue;
            Object contentValue = object.get("content");
            String content = "";
            if (TYPE_RESPONSE.equals(type) || TYPE_THINK.equals(type)) {
                if (!(contentValue instanceof String)) {
                    throw new IllegalArgumentException(label + " text fragment requires string content");
                }
                content = (String) contentValue;
            }
            parsed.add(new Fragment(type, content));
        }
        return parsed;
    }

    private static int parseIndex(String token, int size) {
        if (token.length() == 0) throw new IllegalArgumentException("empty fragment index");
        final int raw;
        try {
            raw = Integer.parseInt(token);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid fragment index");
        }
        int index = raw < 0 ? size + raw : raw;
        if (index < 0 || index >= size) throw new IllegalArgumentException("fragment index out of range");
        return index;
    }

    private static State copyState(State source) {
        State copy = new State();
        copy.fragments.addAll(copyFragments(source.fragments));
        return copy;
    }

    private static List<Fragment> copyFragments(List<Fragment> source) {
        List<Fragment> copy = new ArrayList<Fragment>(source.size());
        for (Fragment fragment : source) copy.add(new Fragment(fragment.type, fragment.content));
        return copy;
    }

    private static String aggregate(State state, String type) {
        StringBuilder out = new StringBuilder();
        for (Fragment fragment : state.fragments) {
            if (type.equals(fragment.type)) out.append(fragment.content);
        }
        return out.toString();
    }

    private static String join(String prefix, String path) {
        if (prefix == null || prefix.length() == 0) return path;
        if (path == null || path.length() == 0) return prefix;
        if (path.startsWith("/")) return prefix + path;
        return prefix + "/" + path;
    }

    private static Map<?, ?> requireMap(Object value, String message) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(message);
        return (Map<?, ?>) value;
    }

    private static Result rejected(String error) {
        return new Result(false, "", "", false, false, error);
    }
}
