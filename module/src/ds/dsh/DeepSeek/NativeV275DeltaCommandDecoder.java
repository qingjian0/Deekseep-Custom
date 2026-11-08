package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decoder for the mainland DeepSeek 2.7.5/code295 common.protocol.DeltaCommand wire shape.
 *
 * Wire fields are p (optional path), o (optional Operation string), and v (required JSON value).
 * This is NOT RFC 6902. The caller supplies a parsed JSON object as a Map and owns one State per
 * top-level SSE stream. No host methods are invoked and no state tree is mutated here.
 */
final class NativeV275DeltaCommandDecoder {
    static final String OP_SET = "SET";
    static final String OP_APPEND = "APPEND";
    static final String OP_BATCH = "BATCH";
    private static final int MAX_PATH_CHARS = 4096;
    private static final int MAX_BATCH_DEPTH = 16;
    private static final int MAX_BATCH_COMMANDS = 4096;

    static final class State {
        private String lastPath = "";
        private String lastOperation = OP_SET;

        String lastPath() { return lastPath; }
        String lastOperation() { return lastOperation; }

        void reset() {
            lastPath = "";
            lastOperation = OP_SET;
        }
    }

    static final class Command {
        final String path;
        final String operation;
        final Object value;
        final List<Command> batch;

        Command(String path, String operation, Object value, List<Command> batch) {
            this.path = path;
            this.operation = operation;
            this.value = value;
            this.batch = batch == null
                    ? Collections.<Command>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Command>(batch));
        }

        boolean isBatch() { return OP_BATCH.equals(operation); }
    }

    static final class Result {
        final boolean accepted;
        final Command command;
        final String error;

        private Result(boolean accepted, Command command, String error) {
            this.accepted = accepted;
            this.command = command;
            this.error = error;
        }

        static Result accepted(Command command) {
            return new Result(true, command, null);
        }

        static Result rejected(String error) {
            return new Result(false, null, error);
        }
    }

    private static final class Budget {
        int commands;
    }

    private NativeV275DeltaCommandDecoder() {}

    /**
     * Decode one top-level DeltaCommand. Missing p/o inherit from the previous top-level command.
     * Initial defaults match the host reducer: empty path and SET.
     */
    static Result decode(Map<?, ?> wire, State state) {
        if (state == null) return Result.rejected("stream state is required");
        return decodeOne(wire, state, new Budget(), 0);
    }

    private static Result decodeOne(Map<?, ?> wire, State state, Budget budget, int depth) {
        if (wire == null) return Result.rejected("command must be an object");
        if (depth > MAX_BATCH_DEPTH) return Result.rejected("batch nesting limit exceeded");
        if (++budget.commands > MAX_BATCH_COMMANDS) {
            return Result.rejected("batch command limit exceeded");
        }

        if (!wire.containsKey("v")) return Result.rejected("required field v is missing");

        String explicitPath = null;
        String explicitOperation = null;
        if (wire.containsKey("p")) {
            Object rawPath = wire.get("p");
            if (!(rawPath instanceof String)) {
                return Result.rejected("field p must be a string when present");
            }
            explicitPath = (String) rawPath;
            if (explicitPath.length() > MAX_PATH_CHARS) {
                return Result.rejected("field p exceeds the path limit");
            }
        }
        if (wire.containsKey("o")) {
            Object rawOperation = wire.get("o");
            if (!(rawOperation instanceof String)) {
                return Result.rejected("field o must be a string when present");
            }
            explicitOperation = (String) rawOperation;
            if (explicitOperation.length() > 32) {
                return Result.rejected("field o exceeds the operation limit");
            }
        }

        // The host stores p/o carry-forward state before applying the command. Preserve that
        // behavior even when the operation itself is unsupported and rejected below.
        if (explicitPath != null) state.lastPath = explicitPath;
        if (explicitOperation != null) state.lastOperation = explicitOperation;

        String path = state.lastPath;
        String operation = state.lastOperation;
        if (!OP_SET.equals(operation)
                && !OP_APPEND.equals(operation)
                && !OP_BATCH.equals(operation)) {
            return Result.rejected("unsupported operation");
        }

        Object value = wire.get("v"); // null is a valid JSON null value; presence was checked above.
        if (!OP_BATCH.equals(operation)) {
            return Result.accepted(new Command(path, operation, value, null));
        }
        if (!(value instanceof List<?>)) {
            return Result.rejected("BATCH value must be an array of DeltaCommand objects");
        }

        State batchState = new State(); // BATCH children start with empty path and SET.
        List<Command> children = new ArrayList<Command>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map<?, ?>)) {
                return Result.rejected("BATCH entries must be command objects");
            }
            Result child = decodeOne((Map<?, ?>) item, batchState, budget, depth + 1);
            if (!child.accepted) return child;
            children.add(child.command);
        }
        return Result.accepted(new Command(path, operation, value, children));
    }
}
