package com.dsmod.probe.agentcore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AgentCoreContractRegressionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void expectInvalid(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            // Expected validation failure.
        }
    }

    public static void main(String[] args) {
        long deadline = System.currentTimeMillis() + 60_000L;
        Map<String, String> metadata = new HashMap<>();
        metadata.put("client", "test");
        AgentCoreContract.Request request = new AgentCoreContract.Request(
                "req-1", "conversation-1", "deepseek-chat", "hello",
                true, deadline, metadata);
        check("hello".equals(request.input), "request input changed");
        check("test".equals(request.metadata.get("client")), "metadata missing");
        expectInvalid(() -> request.metadata.put("mutate", "blocked"),
                "request metadata must be immutable");
        expectInvalid(() -> new AgentCoreContract.Request(
                "", "scope", "model", "x", false, deadline,
                Collections.<String, String>emptyMap()),
                "empty request id must be rejected");
        expectInvalid(() -> new AgentCoreContract.Request(
                "req", "scope", "model", "x", false, 0L, null),
                "non-positive deadline must be rejected");
        char[] oversized = new char[64 * 1024 + 1];
        java.util.Arrays.fill(oversized, (char) 120);
        expectInvalid(() -> new AgentCoreContract.Event(
                AgentCoreContract.EventType.TEXT_DELTA, "req-1",
                new String(oversized), "", null),
                "oversized event text must be rejected");

        AgentRunStateMachine.State state = AgentRunStateMachine.State.CREATED;
        state = AgentRunStateMachine.requireTransition(
                state, AgentRunStateMachine.State.QUEUED);
        state = AgentRunStateMachine.requireTransition(
                state, AgentRunStateMachine.State.RUNNING);
        state = AgentRunStateMachine.requireTransition(
                state, AgentRunStateMachine.State.AWAITING_CONFIRMATION);
        state = AgentRunStateMachine.requireTransition(
                state, AgentRunStateMachine.State.RUNNING);
        state = AgentRunStateMachine.requireTransition(
                state, AgentRunStateMachine.State.STREAMING);
        state = AgentRunStateMachine.requireTransition(
                state, AgentRunStateMachine.State.COMPLETED);
        check(AgentRunStateMachine.isTerminal(state), "completed run is not terminal");
        check(!AgentRunStateMachine.canTransition(
                state, AgentRunStateMachine.State.RUNNING),
                "terminal run must not be restarted");
        expectInvalid(() -> AgentRunStateMachine.requireTransition(
                AgentRunStateMachine.State.CREATED,
                AgentRunStateMachine.State.COMPLETED),
                "created run cannot jump directly to completed");
        System.out.println("Agent Core contract regression tests passed");
    }
}
