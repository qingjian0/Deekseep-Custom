package ds.dsh.DeepSeek

/**
 * Target-level lifecycle coordinator for a single resolved Hook target.
 *
 * This complements FeatureHookInstallRegistry: that registry owns feature-group startup,
 * while this coordinator owns the install/verify/uninstall lifecycle of individual targets.
 * It deliberately knows nothing about Xposed and receives an installer adapter from the caller.
 */
class HookLifecycleManager {
    enum class State {
        NEW,
        INSTALLING,
        ACTIVE,
        SKIPPED,
        UNINSTALLING,
        ROLLED_BACK,
        FAILED,
    }

    data class Spec(
        val id: String,
        val dependencies: Set<String> = emptySet(),
        val conflicts: Set<String> = emptySet(),
        val enabled: Boolean = true,
        val required: Boolean = true,
    ) {
        init {
            require(id.isNotBlank()) { "hook target ID must not be blank" }
            require(id !in dependencies) { "hook target cannot depend on itself: $id" }
            require(id !in conflicts) { "hook target cannot conflict with itself: $id" }
            require(dependencies.none { it.isBlank() }) { "dependency ID must not be blank" }
            require(conflicts.none { it.isBlank() }) { "conflict ID must not be blank" }
            require(dependencies.intersect(conflicts).isEmpty()) {
                "a hook target cannot both depend on and conflict with the same target"
            }
        }
    }

    fun interface Installer {
        @Throws(Throwable::class)
        fun install(spec: Spec): Unhook
    }

    fun interface Unhook {
        @Throws(Throwable::class)
        fun uninstall()
    }

    fun interface Verifier {
        @Throws(Throwable::class)
        fun verify(spec: Spec): Boolean
    }

    data class Status(
        val id: String,
        val state: State,
        val attempts: Int,
        val required: Boolean,
        val detail: String? = null,
    )

    private class Entry(val spec: Spec) {
        var state = State.NEW
        var attempts = 0
        var handle: Unhook? = null
        var detail: String? = null

        fun snapshot() = Status(spec.id, state, attempts, spec.required, detail)
    }

    private val coordination = Any()
    private val entries = LinkedHashMap<String, Entry>()

    /**
     * Installs one target exactly once, verifies it before publishing ACTIVE, and rolls it back
     * if verification fails. Installer and verifier callbacks run outside the coordination lock.
     */
    fun install(spec: Spec, installer: Installer, verifier: Verifier): Status {
        val entry: Entry
        synchronized(coordination) {
            val existing = entries[spec.id]
            if (existing != null) {
                require(existing.spec == spec) {
                    "hook target ID is already registered with a different specification: ${spec.id}"
                }
                if (existing.state == State.ACTIVE || existing.state == State.SKIPPED ||
                    existing.state == State.INSTALLING || existing.state == State.UNINSTALLING ||
                    (existing.state == State.FAILED && existing.handle != null)) {
                    return existing.snapshot()
                }
            }

            if (!spec.enabled) {
                val skipped = existing ?: Entry(spec).also { entries[spec.id] = it }
                skipped.state = State.SKIPPED
                skipped.detail = "disabled_by_policy"
                return skipped.snapshot()
            }

            val missingDependency = spec.dependencies.firstOrNull {
                entries[it]?.state != State.ACTIVE
            }
            if (missingDependency != null) {
                return Status(spec.id, State.SKIPPED, existing?.attempts ?: 0,
                    spec.required, "dependency_not_active:$missingDependency")
            }

            val activeConflict = spec.conflicts.firstOrNull {
                val state = entries[it]?.state
                state == State.ACTIVE || state == State.INSTALLING
            } ?: entries.values.firstOrNull {
                (it.state == State.ACTIVE || it.state == State.INSTALLING) &&
                    spec.id in it.spec.conflicts
            }?.spec?.id
            if (activeConflict != null) {
                return Status(spec.id, State.SKIPPED, existing?.attempts ?: 0,
                    spec.required, "conflicts_with_active_target:$activeConflict")
            }

            entry = existing ?: Entry(spec).also { entries[spec.id] = it }
            entry.state = State.INSTALLING
            entry.attempts++
            entry.detail = null
        }

        var handle: Unhook? = null
        return try {
            handle.uninstall()
            synchronized(coordination) {
                entry.handle = null
                entry.state = State.ROLLED_BACK
                entry.detail = "uninstalled"
                entry.snapshot()
            }
        } catch (failure: Throwable) {
            val result = synchronized(coordination) {
                entry.state = State.FAILED
                entry.detail = "uninstall_failed:${describe(failure)}"
                entry.snapshot()
            }
            if (failure is VirtualMachineError) throw failure
            if (failure is ThreadDeath) throw failure
            result
        }
    }

    fun status(id: String): Status = synchronized(coordination) {
        entries[id]?.snapshot() ?: Status(id, State.NEW, 0, true, "target_not_registered")
    }

    fun snapshot(): List<Status> = synchronized(coordination) {
        entries.values.map { it.snapshot() }.sortedBy { it.id }
    }

    fun hasRequiredFailures(): Boolean = synchronized(coordination) {
        entries.values.any { it.spec.required && it.state == State.FAILED }
    }

    private fun describe(failure: Throwable): String {
        val message = failure.message
        val value = failure.javaClass.name +
            if (message.isNullOrEmpty()) "" else ": $message"
        return if (value.length > 240) value.substring(0, 240) else value
    }
}
