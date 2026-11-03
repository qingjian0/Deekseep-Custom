package ds.dsh.DeepSeek.agent

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Small versioned, atomic file ledger for Agent run state.
 *
 * Only request IDs, state names, bounded diagnostic details and timestamps are persisted.
 * Prompts, model output, credentials and tool arguments are deliberately excluded.
 * Non-terminal runs are marked FAILED on process restart; side effects are never replayed.
 */
class PersistentAgentRunRepository(
    private val file: File,
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
) : AgentRunRepository {
    private data class Entry(
        var state: AgentRunState,
        var detail: String,
        var updatedAt: Long,
    )

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(maxRecords in 16..MAX_RECORDS_LIMIT) { "maxRecords out of range" }
        recoverBackupIfNeeded()
        load()
        recoverInterruptedRuns()
    }

    @Synchronized
    override fun create(request: AgentRequest): Boolean {
        if (entries.containsKey(request.requestId)) return false
        val snapshot = LinkedHashMap<String, Entry>().apply {
            entries.forEach { (id, entry) -> put(id, entry.copy()) }
        }
        pruneIfNeeded()
        if (entries.size >= maxRecords) {
            restore(snapshot)
            return false
        }
        entries[request.requestId] = Entry(AgentRunState.CREATED, "", System.currentTimeMillis())
        if (persist()) return true
        restore(snapshot)
        return false
    }

    @Synchronized
    override fun transition(
        requestId: String,
        next: AgentRunState,
        detail: String,
    ): Boolean {
        val entry = entries[requestId] ?: return false
        if (!AgentRunStateMachine.canTransition(entry.state, next)) return false
        val oldState = entry.state
        val oldDetail = entry.detail
        val oldUpdatedAt = entry.updatedAt
        entry.state = next
        entry.detail = detail.take(MAX_DETAIL_CHARS)
        entry.updatedAt = System.currentTimeMillis()
        if (persist()) return true
        entry.state = oldState
        entry.detail = oldDetail
        entry.updatedAt = oldUpdatedAt
        return false
    }

    @Synchronized
    override fun stateOf(requestId: String): AgentRunState? = entries[requestId]?.state

    @Synchronized
    fun detailOf(requestId: String): String? = entries[requestId]?.detail

    @Synchronized
    fun size(): Int = entries.size

    private fun recoverBackupIfNeeded() {
        val backup = File(file.absoluteFile.parentFile, file.name + ".bak")
        if (!file.exists() && backup.exists() && !backup.renameTo(file)) {
            throw IOException("Unable to recover Agent run ledger backup")
        }
        File(file.absoluteFile.parentFile, file.name + ".tmp").let {
            if (it.exists()) it.delete()
        }
    }

    private fun load() {
        if (!file.exists()) return
        if (file.length() > MAX_FILE_BYTES) throw IOException("Agent run ledger exceeds size limit")
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Invalid Agent run ledger magic")
            if (input.readInt() != FORMAT_VERSION) throw IOException("Unsupported Agent run ledger version")
            val count = input.readInt()
            if (count !in 0..maxRecords) throw IOException("Invalid Agent run ledger record count")
            repeat(count) {
                val id = input.readUTF()
                val stateName = input.readUTF()
                val detail = input.readUTF()
                val updatedAt = input.readLong()
                require(id.isNotBlank() && id.length <= MAX_REQUEST_ID_CHARS)
                require(detail.length <= MAX_DETAIL_CHARS)
                val state = AgentRunState.valueOf(stateName)
                if (entries.put(id, Entry(state, detail, updatedAt.coerceAtLeast(0L))) != null) {
                    throw IOException("Duplicate Agent run ledger key")
                }
            }
            if (input.read() != -1) throw IOException("Unexpected trailing Agent run ledger data")
        }
    }

    private fun recoverInterruptedRuns() {
        var changed = false
        entries.values.forEach { entry ->
            if (!AgentRunStateMachine.isTerminal(entry.state)) {
                entry.state = AgentRunState.FAILED
                entry.detail = "process restarted before run completed"
                entry.updatedAt = System.currentTimeMillis()
                changed = true
            }
        }
        if (changed && !persist()) throw IOException("Unable to persist Agent run recovery")
    }

    private fun restore(snapshot: LinkedHashMap<String, Entry>) {
        entries.clear()
        snapshot.forEach { (id, entry) -> entries[id] = entry.copy() }
    }

    private fun pruneIfNeeded() {
        if (entries.size < maxRecords) return
        val iterator = entries.entries.iterator()
        while (entries.size >= maxRecords && iterator.hasNext()) {
            if (AgentRunStateMachine.isTerminal(iterator.next().value.state)) iterator.remove()
        }
    }

    private fun persist(): Boolean {
        val parent = file.absoluteFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temporary = File(parent, file.name + ".tmp")
        val backup = File(parent, file.name + ".bak")
        return try {
            FileOutputStream(temporary, false).use { stream ->
                DataOutputStream(BufferedOutputStream(stream)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(FORMAT_VERSION)
                    output.writeInt(entries.size)
                    entries.forEach { (id, entry) ->
                        output.writeUTF(id)
                        output.writeUTF(entry.state.name)
                        output.writeUTF(entry.detail.take(MAX_DETAIL_CHARS))
                        output.writeLong(entry.updatedAt)
                    }
                    output.flush()
                    stream.fd.sync()
                }
            }
            if (file.exists()) {
                if (backup.exists() && !backup.delete()) throw IOException("Cannot clear ledger backup")
                if (!file.renameTo(backup)) throw IOException("Cannot rotate Agent run ledger")
            }
            if (!temporary.renameTo(file)) {
                if (backup.exists()) backup.renameTo(file)
                throw IOException("Cannot commit Agent run ledger")
            }
            if (backup.exists()) backup.delete()
            true
        } catch (_: Exception) {
            if (temporary.exists()) temporary.delete()
            if (!file.exists() && backup.exists()) backup.renameTo(file)
            false
        }
    }

    companion object {
        private const val MAGIC = 0x44534841
        private const val FORMAT_VERSION = 1
        private const val DEFAULT_MAX_RECORDS = 2_000
        private const val MAX_RECORDS_LIMIT = 10_000
        private const val MAX_FILE_BYTES = 16 * 1024 * 1024L
        private const val MAX_REQUEST_ID_CHARS = 128
        private const val MAX_DETAIL_CHARS = 2_048
    }
}
