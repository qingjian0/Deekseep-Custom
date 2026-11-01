package ds.dsh.DeepSeek

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local state for independently installed feature-hook groups.
 *
 * The registry tracks installer execution, not reflected members. An installer should represent
 * one stable, idempotent feature boundary. FAILED/DEGRADED boundaries may be retried; INSTALLED
 * and SKIPPED boundaries are terminal for the lifetime of this process.
 */
class FeatureHookInstallRegistry {
    enum class State {
        NEW,
        INSTALLING,
        INSTALLED,
        SKIPPED,
        DEGRADED,
        FAILED,
    }

    fun interface Installer {
        @Throws(Throwable::class)
        fun install()
    }

    class Status(
        @JvmField val id: String,
        @JvmField val state: State,
        @JvmField val attempts: Int,
        @JvmField val startedAtMillis: Long,
        @JvmField val completedAtMillis: Long,
        @JvmField val detail: String?,
    )

    private class Entry {
        var state: State = State.NEW
        var attempts: Int = 0
        var startedAtMillis: Long = 0L
        var completedAtMillis: Long = 0L
        var detail: String? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Runs an installer once per stable ID. A failed installer is eligible for another attempt.
     * The entry lock is held across installation so concurrent callers cannot double-register it.
     */
    @Throws(Throwable::class)
    fun install(id: String?, installer: Installer?) {
        requireId(id)
        requireNotNull(installer) { "installer == null" }
        val stableId = id!!

        val entry = entries.computeIfAbsent(stableId) { Entry() }
        synchronized(entry) {
            if (entry.state == State.INSTALLED || entry.state == State.SKIPPED) return
            if (entry.state == State.INSTALLING) return

            entry.state = State.INSTALLING
            entry.attempts++
            entry.startedAtMillis = System.currentTimeMillis()
            entry.completedAtMillis = 0L
            entry.detail = null

            try {
                installer.install()
                entry.state = State.INSTALLED
                entry.completedAtMillis = System.currentTimeMillis()
            } catch (failure: Throwable) {
                entry.state = State.FAILED
                entry.completedAtMillis = System.currentTimeMillis()
                entry.detail = describe(failure)
                throw failure
            }
        }
    }

    /** Records an explicit host/version gate; skipped hooks are not retried in this process. */
    fun markSkipped(id: String?, reason: String?) {
        requireId(id)
        val entry = entries.computeIfAbsent(id!!) { Entry() }
        synchronized(entry) {
            if (entry.state == State.INSTALLED) return
            entry.state = State.SKIPPED
            entry.completedAtMillis = System.currentTimeMillis()
            entry.detail = reason ?: ""
        }
    }

    /** Records a best-effort installer that returned control but could not complete fully. */
    fun markDegraded(id: String?, failure: Throwable?) {
        requireId(id)
        val entry = entries.computeIfAbsent(id!!) { Entry() }
        synchronized(entry) {
            if (entry.state == State.INSTALLED || entry.state == State.SKIPPED) return
            entry.state = State.DEGRADED
            entry.completedAtMillis = System.currentTimeMillis()
            entry.detail = failure?.let(::describe) ?: "best-effort installation incomplete"
        }
    }

    fun hasFailures(): Boolean = entries.values.any { entry ->
        synchronized(entry) {
            entry.state == State.FAILED || entry.state == State.DEGRADED
        }
    }

    fun snapshot(): List<Status> {
        val result = entries.entries.map { (id, entry) ->
            synchronized(entry) {
                Status(
                    id = id,
                    state = entry.state,
                    attempts = entry.attempts,
                    startedAtMillis = entry.startedAtMillis,
                    completedAtMillis = entry.completedAtMillis,
                    detail = entry.detail,
                )
            }
        }.sortedBy { it.id }
        return Collections.unmodifiableList(result)
    }

    private fun requireId(id: String?) {
        require(!id.isNullOrBlank()) { "hook installation ID must not be blank" }
    }

    private fun describe(failure: Throwable): String {
        val message = failure.message
        val value = failure.javaClass.name +
            if (message.isNullOrEmpty()) "" else ": $message"
        return if (value.length > 320) value.substring(0, 320) else value
    }
}
