package com.dsmod.probe.agentcore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public final class AgentCoreRuntimeRegressionTest {
    private static final class MemoryRepository implements AgentCoreContract.RunRepository {
        final Map<String, AgentRunStateMachine.State> states = new HashMap<>();
        @Override public synchronized boolean create(AgentCoreContract.Request request) {
            if (states.containsKey(request.requestId)) return false;
            states.put(request.requestId, AgentRunStateMachine.State.CREATED);
            return true;
        }
        @Override public synchronized boolean transition(String id,
                AgentRunStateMachine.State next, String detail) {
            AgentRunStateMachine.State current = states.get(id);
            if (!AgentRunStateMachine.canTransition(current, next)) return false;
            states.put(id, next);
            return true;
        }
        @Override public synchronized AgentRunStateMachine.State stateOf(String id) {
            return states.get(id);
        }
    }

    private static final class Sink implements AgentCoreContract.EventSink {
        final List<AgentCoreContract.EventType> events = new ArrayList<>();
        @Override public synchronized boolean onEvent(AgentCoreContract.Event event) {
            events.add(event.type);
            return true;
        }
        @Override public boolean isCancelled() { return false; }
    }

    private static AgentCoreContract.Request request(String id) {
        return new AgentCoreContract.Request(id, "scope", "deepseek-chat", "hello",
                true, System.currentTimeMillis() + 30_000L, null);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MemoryRepository repository = new MemoryRepository();
        Sink sink = new Sink();
        AgentCoreContract.ModelAdapter adapter = new AgentCoreContract.ModelAdapter() {
            @Override public String adapterId() { return "fake"; }
            @Override public boolean isAvailable() { return true; }
            @Override public void complete(AgentCoreContract.Request request,
                    AgentCoreContract.EventSink events,
                    AgentCoreContract.Cancellation cancellation) throws Exception {
                events.onEvent(new AgentCoreContract.Event(
                        AgentCoreContract.EventType.TEXT_DELTA, request.requestId,
                        "world", "", null));
            }
        };
        AgentCore core = new AgentCore(adapter, repository, new Executor() {
            @Override public void execute(Runnable task) { task.run(); }
        });
        check(core.submit(request("success"), sink) != null,
                "valid request was not accepted");
        check(repository.stateOf("success") == AgentRunStateMachine.State.COMPLETED,
                "successful model call did not complete");
        check(sink.events.contains(AgentCoreContract.EventType.TEXT_DELTA)
                        && sink.events.contains(AgentCoreContract.EventType.COMPLETED),
                "stream events were not forwarded");
        check(core.submit(request("success"), sink) == null,
                "duplicate request id was accepted");

        MemoryRepository failureRepository = new MemoryRepository();
        Sink failureSink = new Sink();
        AgentCore failingCore = new AgentCore(new AgentCoreContract.ModelAdapter() {
            @Override public String adapterId() { return "failure"; }
            @Override public boolean isAvailable() { return true; }
            @Override public void complete(AgentCoreContract.Request request,
                    AgentCoreContract.EventSink events,
                    AgentCoreContract.Cancellation cancellation) {
                throw new IllegalStateException("private host details");
            }
        }, failureRepository, new Executor() {
            @Override public void execute(Runnable task) { task.run(); }
        });
        failingCore.submit(request("failure"), failureSink);
        check(failureRepository.stateOf("failure") == AgentRunStateMachine.State.FAILED,
                "adapter exception did not fail the run");
        check(failureSink.events.contains(AgentCoreContract.EventType.FAILED),
                "adapter failure event was not emitted");

        MemoryRepository sinkFailureRepository = new MemoryRepository();
        AgentCore sinkFailureCore = new AgentCore(adapter, sinkFailureRepository,
                new Executor() {
                    @Override public void execute(Runnable task) { task.run(); }
                });
        sinkFailureCore.submit(request("sink-failure"),
                new AgentCoreContract.EventSink() {
                    @Override public boolean onEvent(AgentCoreContract.Event event)
                            throws Exception {
                        throw new Exception("client output closed");
                    }
                    @Override public boolean isCancelled() { return false; }
                });
        check(sinkFailureRepository.stateOf("sink-failure")
                        == AgentRunStateMachine.State.FAILED,
                "event sink exception must fail the run, not mark it cancelled");

        MemoryRepository cancelRepository = new MemoryRepository();
        final List<Runnable> queued = new ArrayList<>();
        AgentCore cancelCore = new AgentCore(adapter, cancelRepository,
                new Executor() {
                    @Override public void execute(Runnable task) { queued.add(task); }
                });
        AgentCoreContract.Cancellation cancellation = cancelCore.submit(
                request("cancel"), new Sink());
        check(cancellation != null && cancellation.cancel(),
                "queued run could not be cancelled");
        queued.get(0).run();
        check(cancelRepository.stateOf("cancel") == AgentRunStateMachine.State.CANCELLED,
                "cancelled queued run was executed");

        System.out.println("Agent Core runtime regression tests passed");
    }
}
