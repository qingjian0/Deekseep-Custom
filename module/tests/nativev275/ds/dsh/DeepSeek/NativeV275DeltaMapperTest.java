package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NativeV275DeltaMapperTest {
    public static void main(String[] args) {
        snapshotAggregatesResponseAndThink();
        setAndAppendFollowHostSemantics();
        setAndAppendFragmentLists();
        batchPathsAreRelativeToBatchTarget();
        nonTextFragmentsDoNotLeakIntoText();
        malformedTextFragmentFailsClosed();
        batchFailureIsAtomic();
        System.out.println("2.7.5 DeltaMapper regression checks passed");
    }

    private static void snapshotAggregatesResponseAndThink() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        Map<String, Object> response = map("fragments", Arrays.asList(
                fragment("RESPONSE", 1, "Hello"),
                fragment("THINK", 2, "private"),
                fragment("RESPONSE", 3, " world")));
        NativeV275DeltaMapper.Result result = apply(state,
                NativeV275DeltaCommandDecoder.Command.snapshot(map("response", response)));
        check(result.accepted, "snapshot accepted");
        check("Hello world".equals(result.text), "response fragments aggregate in order");
        check("private".equals(result.reasoning), "think fragment aggregate");
        check(result.textChanged && result.reasoningChanged, "snapshot reports both changed");
        check(state.fragmentCount() == 3, "snapshot fragment count");
    }

    private static void setAndAppendFollowHostSemantics() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        applySnapshot(state);
        NativeV275DeltaMapper.Result set = apply(state, delta(
                "response/fragments/0/content", "SET", "Replaced"));
        check(set.accepted && "Replaced".equals(set.text), "RESPONSE SET replaces content");
        NativeV275DeltaMapper.Result append = apply(state, delta(
                "response/fragments/0/content", "APPEND", " text"));
        check(append.accepted && "Replaced text".equals(append.text),
                "RESPONSE APPEND concatenates content");
        NativeV275DeltaMapper.Result think = apply(state, delta(
                "response/fragments/1/content", "APPEND", " more"));
        check(think.accepted && "private more".equals(think.reasoning),
                "THINK APPEND concatenates reasoning");
    }

    private static void setAndAppendFragmentLists() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        applySnapshot(state);
        NativeV275DeltaMapper.Result append = apply(state, delta(
                "response/fragments", "APPEND",
                Arrays.asList(fragment("RESPONSE", 3, " tail"))));
        check(append.accepted && "Hello tail".equals(append.text), "APPEND fragments");
        check(state.fragmentCount() == 3, "fragment list append count");

        NativeV275DeltaMapper.Result set = apply(state, delta(
                "response/fragments", "SET",
                Arrays.asList(fragment("THINK", 4, "new reasoning"))));
        check(set.accepted && "".equals(set.text), "SET fragments removes old response");
        check("new reasoning".equals(set.reasoning), "SET fragments installs new think");
        check(state.fragmentCount() == 1, "fragment list replacement count");
    }

    private static void batchPathsAreRelativeToBatchTarget() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        applySnapshot(state);
        NativeV275DeltaCommandDecoder.Command child =
                NativeV275DeltaCommandDecoder.Command.delta("content", "APPEND", " batch", null);
        NativeV275DeltaCommandDecoder.Command batch =
                NativeV275DeltaCommandDecoder.Command.delta(
                        "response/fragments/0", "BATCH", new ArrayList<Object>(),
                        Arrays.asList(child));
        NativeV275DeltaMapper.Result result = apply(state, batch);
        check(result.accepted && "Hello batch".equals(result.text),
                "BATCH child path resolves relative to its target");
    }

    private static void nonTextFragmentsDoNotLeakIntoText() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        NativeV275DeltaMapper.Result result = apply(state,
                NativeV275DeltaCommandDecoder.Command.snapshot(map("response", map(
                        "fragments", Arrays.asList(
                                fragment("TOOL_OPEN", 1, "tool output"),
                                fragment("SEARCH", 2, "search result"),
                                fragment("RESPONSE", 3, "answer"))))));
        check(result.accepted, "non-text fragments accepted");
        check("answer".equals(result.text), "only RESPONSE contributes to text");
        check("".equals(result.reasoning), "non-THINK fragments do not contribute reasoning");
    }

    private static void malformedTextFragmentFailsClosed() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        NativeV275DeltaMapper.Result result = apply(state,
                NativeV275DeltaCommandDecoder.Command.snapshot(map("response", map(
                        "fragments", Arrays.asList(map("type", "RESPONSE", "id", 1))))));
        check(!result.accepted, "RESPONSE without content rejected");
    }

    private static void batchFailureIsAtomic() {
        NativeV275DeltaMapper.State state = new NativeV275DeltaMapper.State();
        applySnapshot(state);
        NativeV275DeltaCommandDecoder.Command good =
                NativeV275DeltaCommandDecoder.Command.delta("content", "APPEND", " partial", null);
        NativeV275DeltaCommandDecoder.Command bad =
                NativeV275DeltaCommandDecoder.Command.delta("content", "SET", Integer.valueOf(7), null);
        NativeV275DeltaCommandDecoder.Command batch =
                NativeV275DeltaCommandDecoder.Command.delta(
                        "response/fragments/0", "BATCH", new ArrayList<Object>(),
                        Arrays.asList(good, bad));
        NativeV275DeltaMapper.Result result = apply(state, batch);
        check(!result.accepted, "invalid batch rejected");
        check("Hello".equals(result.text), "failed batch leaves prior text unchanged");
    }

    private static void applySnapshot(NativeV275DeltaMapper.State state) {
        NativeV275DeltaMapper.Result result = apply(state,
                NativeV275DeltaCommandDecoder.Command.snapshot(map("response", map(
                        "fragments", Arrays.asList(
                                fragment("RESPONSE", 1, "Hello"),
                                fragment("THINK", 2, "private"))))));
        check(result.accepted, "snapshot setup");
    }

    private static NativeV275DeltaMapper.Result apply(
            NativeV275DeltaMapper.State state, NativeV275DeltaCommandDecoder.Command command) {
        return NativeV275DeltaMapper.apply(state, command);
    }

    private static NativeV275DeltaCommandDecoder.Command delta(
            String path, String operation, Object value) {
        return NativeV275DeltaCommandDecoder.Command.delta(path, operation, value, null);
    }

    private static Map<String, Object> fragment(String type, int id, String content) {
        return map("type", type, "id", id, "content", content);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new HashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
