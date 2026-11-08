package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decoder for the mainland DeepSeek 2.7.5/code295 common.protocol.DeltaCommand wire shape.
 *
 * The first DeltaCommand in a collected stream carries the complete SseMessageRoot snapshot
 * in v. Later commands are path operations. Input is a parsed JSON object represented as Map.
 * This class never invokes host methods or mutates a host message model.
 */
final class NativeV275DeltaCommandDecoder {
    static final String OP_SET = "SET";
    static final String OP_APPEND = "APPEND";
    static final String OP_BATCH = "BATCH";
    private static final int MAX_PATH_CHARS = 4096;
    private static final int MAX_BATCH_DEPTH = 16;
    private static final int MAX_BATCH_COMMANDS = 4096;

    enum Kind { INITIAL_SNAPSHOT, DELTA }

    static final class State {
        private boolean initialized;
        private String lastPath = "";
        private String lastOperation = OP_SET;

        boolean initialized() { return initialized; }
        String lastPath() { return lastPath; }
        String lastOperation() { return lastOperation; }

        void reset() {
            initialized = false;
            lastPath = "";
            lastOperation = OP_SET;
        }
    }

    static final class Command {
        final Kind kind;
        final String path;
        final String operation;
        final Object value;
        final List<Command> batch;

        private Command(Kind kind, String path, String operation, Object value,
                List<Command> batch) {
            this.kind = kind;
            this.path = path;
            this.operation = operation;
            this.value = value;
            this.batch = batch == null
                    ? Collections.<Command>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Command>(batch));
        }

        static Command snapshot(Object value) {
            return new Command(Kind.INITIAL_SNAPSHOT, null, null, value, null);
        }

        static Command delta(String path, String operation, Object value, List<Command> batch) {
            return new Command(Kind.DELTA, path, operation, value, batch);
        }

        boolean isInitialSnapshot() { return kind == Kind.INITIAL_SNAPSHOT; }
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
     * Decode one command from a stream. The first command initializes the complete message
     * snapshot from v; later commands apply SET/APPEND/BATCH semantics with p/o carry-forward.
     */
    static Result decode(Map<?, ?> wire, State state) {
        if (state == null) return Result.rejected("stream state is required");
        if (wire == null) return Result.rejected("command must be an object");
        if (!wire.containsKey("v")) return Result.rejected("required field v is missing");

        String explicitPath = optionalString(wire, "p");
        if (explicitPath == INVALID) return Result.rejected("field p must be a string when present");
        if (explicitPath != null && explicitPath.length() > MAX_PATH_CHARS) {
            return Result.rejected("field p exceeds the path limit");
        }
        String explicitOperation = optionalString(wire, "o");
        if (explicitOperation == INVALID) {
            return Result.rejected("field o must be a string when present");
        }
        if (explicitOperation != null && explicitOperation.length() > 32) {
            return Result.rejected("field o exceeds the operation limit");
        }

        Object value = wire.get("v"); // null is valid JSON null because presence was checked above.
        if (!state.initialized) {
            // pv1.e(i == 0) calls coc.c0(v), whose host implementation resets p="" and o=SET.
            if (!(value instanceof Map<?, ?>)
                    || !(((Map<?, ?>) value).get("response") instanceof Map<?, ?>)) {
                return Result.rejected("first v must contain an object-valued SseMessageRoot response");
            }
            state.initialized = true;
            state.lastPath = "";
            state.lastOperation = OP_SET;
            return Result.accepted(Command.snapshot(value));
        }

        return decodePatch(wire, state, new Budget(), 0);
    }

    private static Result decodePatch(Map<?, ?> wire, State state, Budget budget, int depth) {
        if (depth > MAX_BATCH_DEPTH) return Result.rejected("batch nesting limit exceeded");
        if (++budget.commands > MAX_BATCH_COMMANDS) {
            return Result.rejected("batch command limit exceeded");
        }
        if (wire == null) return Result.rejected("BATCH entry must be an object");
        if (!wire.containsKey("v")) return Result.rejected("required field v is missing");

        String explicitPath = optionalString(wire, "p");
        if (explicitPath == INVALID) return Result.rejected("field p must be a string when present");
        if (explicitPath != null && explicitPath.length() > MAX_PATH_CHARS) {
            return Result.rejected("field p exceeds the path limit");
        }
        String explicitOperation = optionalString(wire, "o");
        if (explicitOperation == INVALID) {
            return Result.rejected("field o must be a string when present");
        }
        if (explicitOperation != null && explicitOperation.length() > 32) {
            return Result.rejected("field o exceeds the operation limit");
        }

        // coc.W stores the effective p/o values before calling l83.H.
        if (explicitPath != null) state.lastPath = explicitPath;
        if (explicitOperation != null) state.lastOperation = explicitOperation;

        String path = state.lastPath;
        String operation = state.lastOperation;
        if (!OP_SET.equals(operation)
                && !OP_APPEND.equals(operation)
                && !OP_BATCH.equals(operation)) {
            return Result.rejected("unsupported operation");
        }

        Object value = wire.get("v");
        if (!OP_BATCH.equals(operation)) {
            return Result.accepted(Command.delta(path, operation, value, null));
        }
        if (!(value instanceof List<?>)) {
            return Result.rejected("BATCH value must be an array of DeltaCommand objects");
        }

        State batchState = new State();
        batchState.initialized = true; // BATCH children are patch commands, not a stream snapshot.
        List<Command> children = new ArrayList<Command>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map<?, ?>)) {
                return Result.rejected("BATCH entries must be command objects");
            }
            Result child = decodePatch((Map<?, ?>) item, batchState, budget, depth + 1);
            if (!child.accepted) return child;
            children.add(child.command);
        }
        return Result.accepted(Command.delta(path, operation, value, children));
    }

    private static final String INVALID = new String("invalid");

    private static String optionalString(Map<?, ?> wire, String key) {
        if (!wire.containsKey(key)) return null;
        Object value = wire.get(key);
        return value instanceof String ? (String) value : INVALID;
    }
}
