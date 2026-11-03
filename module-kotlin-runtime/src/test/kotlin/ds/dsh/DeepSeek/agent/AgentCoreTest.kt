package ds.dsh.DeepSeek.agent

import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AgentCoreTest {
    private class MemoryRepository : AgentRunRepository {
        private val states = linkedMapOf<String, AgentRunState>()

        @Synchronized
        override fun create(request: AgentRequest): Boolean {
            if (states.containsKey(request.requestId)) return false
            states[request.requestId] = AgentRunState.CREATED
            return true
        }

        @Synchronized
        override fun transition(
            requestId: String,
            next: AgentRunState,
            detail: String,
        ): Boolean {
            val current = states[requestId]
            if (!AgentRunStateMachine.canTransition(current, next)) return false
            states[requestId] = next
            return true
        }

        @Synchronized
        override fun stateOf(requestId: String): AgentRunState? = states[requestId]
    }

    private class RecordingSink : AgentEventSink {
        val events = mutableListOf<AgentEvent>()
        override fun onEvent(event: AgentEvent): Boolean {
            events += event
            return true
        }
    }

    private fun request(id: String = "request-1", deadline: Long =
        System.currentTimeMillis() + 30_000L) = AgentRequest(
        requestId = id,
        conversationScope = "scope-1",
        requestedModel = "deepseek-chat",
        input = "hello",
        stream = true,
        deadlineEpochMs = deadline,
    )

    private fun adapter(
        block: (AgentRequest, AgentEventSink, AgentCancellation) -> Unit,
    ) = object : AgentModelAdapter {
        override val adapterId = "test-adapter"
        override fun isAvailable() = true
        override fun complete(
            request: AgentRequest,
            sink: AgentEventSink,
            cancellation: AgentCancellation,
        ) = block(request, sink, cancellation)
    }

    @Test
    fun requestCopiesAndBoundsMetadata() {
        val source = mutableMapOf("client" to "test")
        val request = request().let {
            AgentRequest(
                it.requestId, it.conversationScope, it.requestedModel, it.input,
                it.stream, it.deadlineEpochMs, source,
            )
        }
        source["client"] = "mutated"
        assertEquals("test", request.metadata["client"])
        assertFailsWith<UnsupportedOperationException> {
            (request.metadata as MutableMap<String, String>)["new"] = "blocked"
        }
        assertFailsWith<IllegalArgumentException> {
            AgentRequest(" ", "scope", "model", "input", false, 1L)
        }
    }

    @Test
    fun stateMachineRejectsInvalidAndTerminalTransitions() {
        assertTrue(
            AgentRunStateMachine.canTransition(
                AgentRunState.CREATED,
                AgentRunState.QUEUED,
            ),
        )
        assertFalse(
            AgentRunStateMachine.canTransition(
                AgentRunState.CREATED,
                AgentRunState.COMPLETED,
            ),
        )
        assertFailsWith<IllegalStateException> {
            AgentRunStateMachine.requireTransition(
                AgentRunState.COMPLETED,
                AgentRunState.RUNNING,
            )
        }
    }

    @Test
    fun successfulRunForwardsDeltasAndCompletes() {
        val repository = MemoryRepository()
        val sink = RecordingSink()
        val core = AgentCore(
            adapter { request, events, _ ->
                events.onEvent(
                    AgentEvent(AgentEventType.TEXT_DELTA, request.requestId, text = "world"),
                )
            },
            repository,
            Executor { it.run() },
        )

        assertNotNull(core.submit(request(), sink))
        assertEquals(AgentRunState.COMPLETED, core.stateOf("request-1"))
        assertEquals(
            listOf(
                AgentEventType.STARTED,
                AgentEventType.TEXT_DELTA,
                AgentEventType.COMPLETED,
            ),
            sink.events.map { it.type },
        )
    }

    @Test
    fun duplicateRequestIdIsRejected() {
        val repository = MemoryRepository()
        val core = AgentCore(adapter { _, _, _ -> }, repository, Executor { it.run() })
        val sink = RecordingSink()
        assertNotNull(core.submit(request(), sink))
        assertNull(core.submit(request(), sink))
    }

    @Test
    fun adapterFailureBecomesFailedStateWithoutLeakingExceptionText() {
        val repository = MemoryRepository()
        val sink = RecordingSink()
        val core = AgentCore(
            adapter { _, _, _ -> error("private host detail") },
            repository,
            Executor { it.run() },
        )

        core.submit(request(), sink)
        assertEquals(AgentRunState.FAILED, core.stateOf("request-1"))
        assertEquals("model adapter failed", sink.events.last().detail)
    }

    @Test
    fun sinkFailureIsFailedRatherThanCancelled() {
        val repository = MemoryRepository()
        val core = AgentCore(adapter { _, _, _ -> }, repository, Executor { it.run() })
        val sink = object : AgentEventSink {
            override fun onEvent(event: AgentEvent): Boolean = error("downstream closed")
        }

        core.submit(request(), sink)
        assertEquals(AgentRunState.FAILED, core.stateOf("request-1"))
    }

    @Test
    fun queuedRunCanBeCancelledBeforeExecution() {
        val repository = MemoryRepository()
        val queued = mutableListOf<Runnable>()
        val core = AgentCore(adapter { _, _, _ -> }, repository, Executor { queued += it })
        val cancellation = assertNotNull(core.submit(request(), RecordingSink()))

        assertTrue(cancellation.cancel())
        queued.single().run()
        assertEquals(AgentRunState.CANCELLED, core.stateOf("request-1"))
    }

    @Test
    fun expiredRequestIsRejectedBeforeClaiming() {
        val repository = MemoryRepository()
        val core = AgentCore(adapter { _, _, _ -> }, repository, Executor { it.run() })
        assertNull(core.submit(request(deadline = System.currentTimeMillis() - 1L), RecordingSink()))
        assertNull(core.stateOf("request-1"))
    }
}
