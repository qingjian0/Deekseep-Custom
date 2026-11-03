package ds.dsh.DeepSeek.agent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal model orchestration runtime.
 *
 * It deliberately knows nothing about Android, Xposed, HTTP, DeepSeek obfuscated symbols,
 * or UI lifecycle. The gateway supplies an Executor and the host adapter supplies model I/O.
 */
class AgentCore(
    private val adapter: AgentModelAdapter,
    private val repository: AgentRunRepository,
    private val executor: Executor,
) {
    private val active = ConcurrentHashMap<String, RunControl>()

    init {
        require(adapter.adapterId.isNotBlank()) { "adapterId must not be blank" }
    }

    /** Returns null when the request is invalid, expired, or already claimed. */
    fun submit(request: AgentRequest, sink: AgentEventSink): AgentCancellation? {
        if (request.deadlineEpochMs <= System.currentTimeMillis()) return null
        if (!repository.create(request)) return null
        if (!repository.transition(request.requestId, AgentRunState.QUEUED)) return null

        val control = RunControl(request, sink)
        if (active.putIfAbsent(request.requestId, control) != null) {
            repository.transition(
                request.requestId,
                AgentRunState.FAILED,
                "duplicate active request",
            )
            return null
        }
        try {
            executor.execute { execute(control) }
        } catch (_: RejectedExecutionException) {
            finishFailure(control, "executor rejected request")
        } catch (_: RuntimeException) {
            finishFailure(control, "executor rejected request")
        }
        return control
    }

    fun stateOf(requestId: String): AgentRunState? = repository.stateOf(requestId)

    private fun execute(control: RunControl) {
        synchronized(control.lock) {
            if (control.cancelled.get()) return
            if (isExpired(control.request)) {
                finishFailureLocked(control, "request deadline exceeded")
                return
            }
            if (!repository.transition(control.request.requestId, AgentRunState.RUNNING)) {
                finishFailureLocked(control, "run could not enter running state")
                return
            }
        }

        try {
            if (!adapter.isAvailable()) {
                finishFailure(control, "model adapter unavailable")
                return
            }
            if (!emit(
                    control,
                    AgentEvent(AgentEventType.STARTED, control.request.requestId),
                )
            ) return

            adapter.complete(
                control.request,
                AgentEventSink { event ->
                    if (event.requestId != control.request.requestId) {
                        finishFailure(control, "adapter emitted event for another request")
                        return@AgentEventSink false
                    }
                    if (control.isCancelled()) return@AgentEventSink false
                    require(
                        event.type != AgentEventType.STARTED &&
                            event.type != AgentEventType.COMPLETED &&
                            event.type != AgentEventType.FAILED,
                    ) { "adapter emitted a reserved lifecycle event" }
                    if (isExpired(control.request)) {
                        finishFailure(control, "request deadline exceeded")
                        return@AgentEventSink false
                    }
                    if (event.type == AgentEventType.TEXT_DELTA ||
                        event.type == AgentEventType.REASONING_DELTA
                    ) {
                        synchronized(control.lock) {
                            if (control.cancelled.get()) return@synchronized
                            if (repository.stateOf(control.request.requestId) ==
                                AgentRunState.RUNNING
                            ) {
                                repository.transition(
                                    control.request.requestId,
                                    AgentRunState.STREAMING,
                                )
                            }
                        }
                    }
                    emit(control, event)
                },
                control,
            )

            if (isExpired(control.request)) {
                finishFailure(control, "request deadline exceeded")
                return
            }
            synchronized(control.lock) {
                if (control.cancelled.get() ||
                    AgentRunStateMachine.isTerminal(
                        repository.stateOf(control.request.requestId),
                    )
                ) return
                val current = repository.stateOf(control.request.requestId)
                if (current == AgentRunState.RUNNING || current == AgentRunState.STREAMING) {
                    repository.transition(control.request.requestId, AgentRunState.COMPLETED)
                } else {
                    finishFailureLocked(control, "unsupported final run state")
                    return
                }
            }
            emit(
                control,
                AgentEvent(AgentEventType.COMPLETED, control.request.requestId),
            )
        } catch (_: Exception) {
            finishFailure(control, "model adapter failed")
        } finally {
            active.remove(control.request.requestId, control)
        }
    }

    private fun emit(control: RunControl, event: AgentEvent): Boolean {
        if (control.cancelled.get() || isExpired(control.request)) return false
        return try {
            val accepted = control.sink.onEvent(event)
            if (!accepted) control.cancel()
            accepted && !control.cancelled.get()
        } catch (_: Exception) {
            finishFailure(control, "event sink failed")
            false
        }
    }

    private fun finishFailure(control: RunControl, detail: String) {
        synchronized(control.lock) {
            finishFailureLocked(control, detail)
        }
        active.remove(control.request.requestId, control)
    }

    private fun finishFailureLocked(control: RunControl, detail: String) {
        if (control.cancelled.get()) return
        val current = repository.stateOf(control.request.requestId)
        if (current == null || AgentRunStateMachine.isTerminal(current)) return
        if (!repository.transition(control.request.requestId, AgentRunState.FAILED, detail)) return
        try {
            control.sink.onEvent(
                AgentEvent(
                    AgentEventType.FAILED,
                    control.request.requestId,
                    detail = detail,
                ),
            )
        } catch (_: Exception) {
            // The durable FAILED state is authoritative even when the downstream is gone.
        }
    }

    private fun isExpired(request: AgentRequest): Boolean =
        System.currentTimeMillis() >= request.deadlineEpochMs

    private inner class RunControl(
        val request: AgentRequest,
        val sink: AgentEventSink,
    ) : AgentCancellation {
        val lock = Any()
        val cancelled = AtomicBoolean(false)

        override fun cancel(): Boolean = synchronized(lock) {
            val current = repository.stateOf(request.requestId)
            if (current == null || AgentRunStateMachine.isTerminal(current)) return@synchronized false
            if (!cancelled.compareAndSet(false, true)) return@synchronized false
            repository.transition(
                request.requestId,
                AgentRunState.CANCELLED,
                "cancelled by caller",
            )
            active.remove(request.requestId, this)
            true
        }

        override fun isCancelled(): Boolean = cancelled.get() || isExpired(request)
    }
}
