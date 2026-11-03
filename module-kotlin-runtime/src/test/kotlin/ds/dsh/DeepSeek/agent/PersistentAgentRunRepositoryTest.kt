package ds.dsh.DeepSeek.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentAgentRunRepositoryTest {
    private fun request(id: String) = AgentRequest(
        requestId = id,
        conversationScope = "test-scope",
        requestedModel = "deepseek-chat",
        input = "not persisted",
        stream = false,
        deadlineEpochMs = System.currentTimeMillis() + 60_000L,
    )

    @Test
    fun persistsTerminalStateAndRejectsDuplicateIdsAfterRestart() {
        val directory = createTempDirectory()
        try {
            val file = File(directory, "runs.bin")
            val first = PersistentAgentRunRepository(file)
            assertTrue(first.create(request("run-1")))
            assertTrue(first.transition("run-1", AgentRunState.QUEUED))
            assertTrue(first.transition("run-1", AgentRunState.RUNNING))
            assertTrue(first.transition("run-1", AgentRunState.COMPLETED))
            assertFalse(first.create(request("run-1")))

            val restored = PersistentAgentRunRepository(file)
            assertEquals(AgentRunState.COMPLETED, restored.stateOf("run-1"))
            assertFalse(restored.create(request("run-1")))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun interruptedRunsAreFailedRatherThanReplayed() {
        val directory = createTempDirectory()
        try {
            val file = File(directory, "runs.bin")
            val first = PersistentAgentRunRepository(file)
            assertTrue(first.create(request("run-active")))
            assertTrue(first.transition("run-active", AgentRunState.QUEUED))
            assertTrue(first.transition("run-active", AgentRunState.RUNNING))

            val restored = PersistentAgentRunRepository(file)
            assertEquals(AgentRunState.FAILED, restored.stateOf("run-active"))
            assertEquals(
                "process restarted before run completed",
                restored.detailOf("run-active"),
            )
            assertFalse(restored.transition("run-active", AgentRunState.RUNNING))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidTransitionsAndBoundsLedgerCapacity() {
        val directory = createTempDirectory()
        try {
            val repository = PersistentAgentRunRepository(File(directory, "runs.bin"), maxRecords = 16)
            assertTrue(repository.create(request("run-1")))
            assertFalse(repository.transition("run-1", AgentRunState.COMPLETED))
            assertEquals(AgentRunState.CREATED, repository.stateOf("run-1"))
            assertEquals(1, repository.size())
            assertNull(repository.stateOf("missing"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File {
        val file = kotlin.io.path.createTempDirectory("dsh-agent-ledger-").toFile()
        return file
    }
}
