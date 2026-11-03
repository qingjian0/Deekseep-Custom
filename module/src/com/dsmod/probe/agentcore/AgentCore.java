package com.dsmod.probe.agentcore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal orchestration boundary for one model adapter.
 *
 * <p>Transport, Android lifecycle, Hook reflection and HTTP concerns are deliberately kept
 * outside this class. The caller supplies an Executor and a durable RunRepository.</p>
 */
public final class AgentCore {
    private final AgentCoreContract.ModelAdapter adapter;
    private final AgentCoreContract.RunRepository repository;
    private final Executor executor;
    private final Map<String, RunControl> active = new ConcurrentHashMap<>();

    public AgentCore(AgentCoreContract.ModelAdapter adapter,
                     AgentCoreContract.RunRepository repository,
                     Executor executor) {
        if (adapter == null || repository == null || executor == null) {
            throw new IllegalArgumentException("adapter, repository and executor are required");
        }
        this.adapter = adapter;
        this.repository = repository;
        this.executor = executor;
    }

    /** Returns null when the request is invalid, expired or already claimed. */
    public AgentCoreContract.Cancellation submit(
            final AgentCoreContract.Request request,
            final AgentCoreContract.EventSink sink) {
        if (request == null || sink == null
                || request.deadlineEpochMs <= System.currentTimeMillis()) return null;
        if (!repository.create(request)) return null;
        if (!repository.transition(request.requestId,
                AgentRunStateMachine.State.QUEUED, "")) return null;

        final RunControl control = new RunControl(request, sink);
        if (active.putIfAbsent(request.requestId, control) != null) {
            repository.transition(request.requestId,
                    AgentRunStateMachine.State.FAILED, "duplicate active request");
            return null;
        }
        try {
            executor.execute(new Runnable() {
                @Override public void run() { execute(control); }
            });
        } catch (RuntimeException rejected) {
            finishFailure(control, "executor rejected request");
        }
        return control;
    }

    public AgentRunStateMachine.State stateOf(String requestId) {
        return repository.stateOf(requestId);
    }

    private void execute(RunControl control) {
        synchronized (control.lock) {
            if (control.cancelled.get()) return;
            if (expired(control.request)) {
                finishFailureLocked(control, "request deadline exceeded");
                return;
            }
            if (!repository.transition(control.request.requestId,
                    AgentRunStateMachine.State.RUNNING, "")) {
                finishFailureLocked(control, "run could not enter running state");
                return;
            }
        }
        try {
            if (!adapter.isAvailable()) {
                finishFailure(control, "model adapter unavailable");
                return;
            }
            if (!emit(control, new AgentCoreContract.Event(
                    AgentCoreContract.EventType.STARTED, control.request.requestId,
                    "", "", null))) return;

            adapter.complete(control.request, new AgentCoreContract.EventSink() {
                @Override public boolean onEvent(AgentCoreContract.Event event)
                        throws Exception {
                    if (event == null
                            || !control.request.requestId.equals(event.requestId)
                            || isCancelled()) return false;
                    if (event.type == AgentCoreContract.EventType.STARTED
                            || event.type == AgentCoreContract.EventType.COMPLETED
                            || event.type == AgentCoreContract.EventType.FAILED) {
                        throw new IllegalArgumentException("adapter emitted a reserved lifecycle event");
                    }
                    if (expired(control.request)) {
                        finishFailure(control, "request deadline exceeded");
                        return false;
                    }
                    if (event.type == AgentCoreContract.EventType.TEXT_DELTA
                            || event.type == AgentCoreContract.EventType.REASONING_DELTA) {
                        synchronized (control.lock) {
                            if (control.cancelled.get()) return false;
                            AgentRunStateMachine.State current =
                                    repository.stateOf(control.request.requestId);
                            if (current == AgentRunStateMachine.State.RUNNING) {
                                repository.transition(control.request.requestId,
                                        AgentRunStateMachine.State.STREAMING, "");
                            }
                        }
                    }
                    return emit(control, event);
                }

                @Override public boolean isCancelled() {
                    return control.cancelled.get() || expired(control.request);
                }
            }, control);

            if (expired(control.request)) {
                finishFailure(control, "request deadline exceeded");
                return;
            }
            synchronized (control.lock) {
                if (control.cancelled.get()
                        || AgentRunStateMachine.isTerminal(
                        repository.stateOf(control.request.requestId))) return;
                AgentRunStateMachine.State current =
                        repository.stateOf(control.request.requestId);
                if (current == AgentRunStateMachine.State.RUNNING
                        || current == AgentRunStateMachine.State.STREAMING) {
                    repository.transition(control.request.requestId,
                            AgentRunStateMachine.State.COMPLETED, "");
                } else {
                    finishFailureLocked(control, "unsupported final run state");
                    return;
                }
            }
            emit(control, new AgentCoreContract.Event(
                    AgentCoreContract.EventType.COMPLETED,
                    control.request.requestId, "", "", null));
        } catch (Exception error) {
            finishFailure(control, "model adapter failed");
        } finally {
            active.remove(control.request.requestId, control);
        }
    }

    private boolean emit(RunControl control, AgentCoreContract.Event event) {
        if (control.cancelled.get() || expired(control.request)) return false;
        try {
            boolean accepted = control.sink.onEvent(event);
            if (!accepted) control.cancel();
            return accepted && !control.cancelled.get();
        } catch (Exception error) {
            control.cancel();
            finishFailure(control, "event sink failed");
            return false;
        }
    }

    private void finishFailure(RunControl control, String detail) {
        synchronized (control.lock) {
            finishFailureLocked(control, detail);
        }
        active.remove(control.request.requestId, control);
    }

    private void finishFailureLocked(RunControl control, String detail) {
        if (control.cancelled.get()) return;
        AgentRunStateMachine.State current =
                repository.stateOf(control.request.requestId);
        if (current != null && !AgentRunStateMachine.isTerminal(current)) {
            repository.transition(control.request.requestId,
                    AgentRunStateMachine.State.FAILED, detail);
        }
        try {
            control.sink.onEvent(new AgentCoreContract.Event(
                    AgentCoreContract.EventType.FAILED,
                    control.request.requestId, "", detail, null));
        } catch (Exception ignored) {}
    }

    private static boolean expired(AgentCoreContract.Request request) {
        return System.currentTimeMillis() >= request.deadlineEpochMs;
    }

    private final class RunControl implements AgentCoreContract.Cancellation {
        final Object lock = new Object();
        final AgentCoreContract.Request request;
        final AgentCoreContract.EventSink sink;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        RunControl(AgentCoreContract.Request request, AgentCoreContract.EventSink sink) {
            this.request = request;
            this.sink = sink;
        }

        @Override public boolean cancel() {
            synchronized (lock) {
                AgentRunStateMachine.State current =
                        repository.stateOf(request.requestId);
                if (current == null || AgentRunStateMachine.isTerminal(current)) return false;
                if (!cancelled.compareAndSet(false, true)) return false;
                repository.transition(request.requestId,
                        AgentRunStateMachine.State.CANCELLED, "cancelled by caller");
            }
            active.remove(request.requestId, this);
            return true;
        }

        @Override public boolean isCancelled() {
            return cancelled.get() || expired(request);
        }
    }
}
