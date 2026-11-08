package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class NativeV275DeltaCommandDecoderTest {
    public static void main(String[] args) {
        firstCommandInitializesSnapshot();
        laterCommandsCarryPathAndOperation();
        batchHasIndependentDefaultsAndCarriesWithinBatch();
        nullJsonValueIsAllowedWhenPresent();
        malformedAndUnknownCommandsFailClosed();
        limitsAreEnforced();
        System.out.println("2.7.5 DeltaCommand decoder regression checks passed");
    }

    private static void firstCommandInitializesSnapshot() {
        NativeV275DeltaCommandDecoder.State state =
                new NativeV275DeltaCommandDecoder.State();
        Map<String, Object> snapshot = new HashMap<String, Object>();
        snapshot.put("response", new HashMap<String, Object>());
        NativeV275DeltaCommandDecoder.Result first =
                decode(state, command("ignored/path", "APPEND", snapshot));
        check(first.accepted, "initial snapshot accepted");
        check(first.command.isInitialSnapshot(), "first DeltaCommand is a snapshot");
        check(first.command.path == null && first.command.operation == null,
                "first command p/o are not applied as a patch");
        check(state.initialized(), "stream initialized");
        check("".equals(state.lastPath()), "snapshot resets path");
        check("SET".equals(state.lastOperation()), "snapshot resets operation");
    }

    private static void laterCommandsCarryPathAndOperation() {
        NativeV275DeltaCommandDecoder.State state = initializedState();
        NativeV275DeltaCommandDecoder.Result first = decode(state,
                command("response/fragments/0/content", "APPEND", "A"));
        check(first.accepted && !first.command.isInitialSnapshot(), "first patch accepted");
        check("response/fragments/0/content".equals(first.command.path), "field p");
        check("APPEND".equals(first.command.operation), "field o");
        check("A".equals(first.command.value), "field v");

        NativeV275DeltaCommandDecoder.Result second = decode(state, command(null, null, "B"));
        check(second.accepted, "omitted p/o inherit");
        check(first.command.path.equals(second.command.path), "path carry-forward");
        check(first.command.operation.equals(second.command.operation), "operation carry-forward");
        check("B".equals(second.command.value), "second value");
    }

    private static void batchHasIndependentDefaultsAndCarriesWithinBatch() {
        NativeV275DeltaCommandDecoder.State state = initializedState();
        Map<String, Object> child1 = command("content", "APPEND", "hello");
        Map<String, Object> child2 = command(null, null, " world");
        Map<String, Object> outer = command("response/fragments/0", "BATCH",
                Arrays.asList(child1, child2));

        NativeV275DeltaCommandDecoder.Result result = decode(state, outer);
        check(result.accepted, "batch accepted");
        check(result.command.isBatch(), "outer operation is BATCH");
        check("response/fragments/0".equals(result.command.path), "batch target path");
        check(result.command.batch.size() == 2, "batch child count");
        check("content".equals(result.command.batch.get(0).path), "batch child path");
        check("APPEND".equals(result.command.batch.get(0).operation), "batch child operation");
        check("content".equals(result.command.batch.get(1).path), "batch path inheritance");
        check("APPEND".equals(result.command.batch.get(1).operation), "batch operation inheritance");
        check(" world".equals(result.command.batch.get(1).value), "batch child value");

        check("response/fragments/0".equals(state.lastPath()), "outer path state retained");
        check("BATCH".equals(state.lastOperation()), "outer operation state retained");
        NativeV275DeltaCommandDecoder.Result next =
                decode(state, command(null, null, "next"));
        check(!next.accepted, "next command inherits BATCH and rejects non-array value");
    }

    private static void nullJsonValueIsAllowedWhenPresent() {
        NativeV275DeltaCommandDecoder.State state = initializedState();
        NativeV275DeltaCommandDecoder.Result result =
                decode(state, command("response/fragments/0/content", "SET", null));
        check(result.accepted, "explicit JSON null is a present value");
        check(result.command.value == null, "null value preserved");
    }

    private static void malformedAndUnknownCommandsFailClosed() {
        NativeV275DeltaCommandDecoder.State state = initializedState();
        Map<String, Object> missingValue = new HashMap<String, Object>();
        missingValue.put("p", "response/fragments");
        check(!decode(state, missingValue).accepted, "missing v rejected");
        check(!decode(state, command(Integer.valueOf(1), "SET", "x")).accepted,
                "non-string p rejected");
        check(!decode(state, command("response", Integer.valueOf(1), "x")).accepted,
                "non-string o rejected");

        NativeV275DeltaCommandDecoder.Result unknown =
                decode(state, command("response", "REMOVE", "x"));
        check(!unknown.accepted, "unknown operation rejected");
        check("REMOVE".equals(state.lastOperation()),
                "host-compatible carry-forward stores unsupported operation");

        check(!decode(state, command("response", "BATCH", "not-an-array")).accepted,
                "BATCH requires an array");
        check(!decode(state, command("response", "BATCH",
                Arrays.asList("not-a-command"))).accepted, "BATCH entries must be objects");

        Map<String, Object> badChild = new HashMap<String, Object>();
        badChild.put("p", "content");
        check(!decode(state, command("response", "BATCH",
                Arrays.asList(badChild))).accepted, "BATCH child requires v");
    }

    private static void limitsAreEnforced() {
        NativeV275DeltaCommandDecoder.State state = initializedState();
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < 4097; i++) path.append('x');
        check(!decode(state, command(path.toString(), "SET", "x")).accepted,
                "oversized path rejected");

        ArrayList<Object> many = new ArrayList<Object>();
        for (int i = 0; i < 4097; i++) many.add(command("content", "SET", "x"));
        check(!decode(state, command("response", "BATCH", many)).accepted,
                "oversized batch rejected");
    }

    private static NativeV275DeltaCommandDecoder.State initializedState() {
        NativeV275DeltaCommandDecoder.State state =
                new NativeV275DeltaCommandDecoder.State();
        Map<String, Object> snapshot = new HashMap<String, Object>();
        snapshot.put("response", new HashMap<String, Object>());
        check(decode(state, command(null, null, snapshot)).accepted, "snapshot setup");
        return state;
    }

    private static NativeV275DeltaCommandDecoder.Result decode(
            NativeV275DeltaCommandDecoder.State state, Map<String, Object> wire) {
        return NativeV275DeltaCommandDecoder.decode(wire, state);
    }

    private static Map<String, Object> command(Object path, Object operation, Object value) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (path != null) map.put("p", path);
        if (operation != null) map.put("o", operation);
        map.put("v", value);
        return map;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
