package ds.dsh.DeepSeek

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local state for independently installed feature-hook groups.
 *
 * The registry tracks installer execution, not reflected members. An installer should represent
 * one stable, idempotent feature boundary. FAILED/DEGRADED boundaries may be retried; SKIPPED
 * boundaries are terminal for the lifetime of this process. INSTALLED boundaries remain
 * idempotent until an explicit uninstall transition makes the same stable ID installable again.
 */
class FeatureHookInstallRegistry {
    enum class State {
        NEW,
        INSTALLING,
        INSTALLED,
        UNINSTALLED,
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

    /**
     * Marks a successfully installed group as logically uninstalled. The caller must first
     * deactivate every owned runtime handle and verify that none remain active.
     *
     * The stable ID may be installed again after this transition. SKIPPED, FAILED, and
     * INSTALLING entries cannot be converted to UNINSTALLED.
     */
    fun markUninstalled(id: String?): Boolean {
        requireId(id)
        val entry = entries[id!!] ?: return false
        synchronized(entry) {
            if (entry.state != State.INSTALLED) return false
            entry.state = State.UNINSTALLED
            entry.completedAtMillis = System.currentTimeMillis()
            entry.detail = "logical hooks uninstalled"
            return true
        }
    }

    /**
     * Completes cleanup of a FAILED install after the caller has deactivated every partial
     * runtime handle and verified that none remain. This is deliberately separate from
     * markUninstalled(), which is reserved for a successfully INSTALLED group.
     */
    fun markFailedInstallCleaned(id: String?): Boolean {
        requireId(id)
        val entry = entries[id!!] ?: return false
        synchronized(entry) {
            if (entry.state != State.FAILED) return false
            entry.state = State.UNINSTALLED
            entry.completedAtMillis = System.currentTimeMillis()
            entry.detail = "failed install cleaned up"
            return true
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

    /** True only when a required installer failed. DEGRADED entries are best-effort groups. */
    fun hasRequiredFailures(): Boolean = entries.values.any { entry ->
        synchronized(entry) {
            entry.state == State.FAILED
        }
    }

    /** True when a failed or degraded boundary is eligible for another install attempt. */
    fun hasRetryableFailures(): Boolean = entries.values.any { entry ->
        synchronized(entry) {
            entry.state == State.FAILED || entry.state == State.DEGRADED
        }
    }

    /** Includes required failures and best-effort degradation for diagnostics. */
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
