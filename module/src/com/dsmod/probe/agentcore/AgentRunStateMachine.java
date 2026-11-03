package com.dsmod.probe.agentcore;

/** Deterministic, side-effect-free Agent run state transition rules. */
public final class AgentRunStateMachine {
    public enum State {
        CREATED, QUEUED, RUNNING, AWAITING_CONFIRMATION, STREAMING,
        COMPLETED, FAILED, CANCELLED
    }

    private AgentRunStateMachine() {}

    public static boolean isTerminal(State state) {
        return state == State.COMPLETED || state == State.FAILED
                || state == State.CANCELLED;
    }

    public static boolean canTransition(State from, State to) {
        if (from == null || to == null || from == to || isTerminal(from)) return false;
        switch (from) {
            case CREATED:
                return to == State.QUEUED || to == State.CANCELLED || to == State.FAILED;
            case QUEUED:
                return to == State.RUNNING || to == State.CANCELLED || to == State.FAILED;
            case RUNNING:
                return to == State.AWAITING_CONFIRMATION || to == State.STREAMING
                        || to == State.COMPLETED || to == State.FAILED
                        || to == State.CANCELLED;
            case AWAITING_CONFIRMATION:
                return to == State.RUNNING || to == State.CANCELLED || to == State.FAILED;
            case STREAMING:
                return to == State.AWAITING_CONFIRMATION || to == State.COMPLETED
                        || to == State.FAILED || to == State.CANCELLED;
            default:
                return false;
        }
    }

    public static State requireTransition(State from, State to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid Agent run transition: "
                    + from + " -> " + to);
        }
        return to;
    }
}
