package ds.dsh.DeepSeek

import java.util.Collections
import java.util.LinkedHashSet

/**
 * Target-level lifecycle coordinator for a single resolved Hook target.
 *
 * FeatureHookInstallRegistry owns feature-group startup. This coordinator owns the
 * install/verify/uninstall lifecycle of individual targets and delegates platform-specific
 * installation to an injected adapter.
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
     * Installs a target once, verifies it before publishing ACTIVE, and rolls it back if
     * verification fails. Installer and verifier callbacks run outside the coordination lock.
     */
    fun install(spec: Spec, installer: Installer, verifier: Verifier): Status {
        // Callers may pass mutable Sets. Freeze one defensive snapshot before any decision or
        // callback so the dependency/conflict graph cannot change after registration begins.
        val stableSpec = spec.copy(
            dependencies = Collections.unmodifiableSet(LinkedHashSet(spec.dependencies)),
            conflicts = Collections.unmodifiableSet(LinkedHashSet(spec.conflicts)),
        )
        val entry: Entry
        synchronized(coordination) {
            val existing = entries[stableSpec.id]
            if (existing != null) {
                require(existing.spec == stableSpec) {
                    "hook target ID is already registered with a different specification: ${stableSpec.id}"
                }
                if (existing.state == State.ACTIVE || existing.state == State.SKIPPED ||
                    existing.state == State.INSTALLING || existing.state == State.UNINSTALLING ||
                    (existing.state == State.FAILED && existing.handle != null)) {
                    return existing.snapshot()
                }
            }

            if (!stableSpec.enabled) {
                val skipped = existing ?: Entry(stableSpec).also { entries[stableSpec.id] = it }
                skipped.state = State.SKIPPED
                skipped.detail = "disabled_by_policy"
                return skipped.snapshot()
            }

            val missingDependency = stableSpec.dependencies.firstOrNull {
                entries[it]?.state != State.ACTIVE
            }
            if (missingDependency != null) {
                return Status(stableSpec.id, State.SKIPPED, existing?.attempts ?: 0,
                    stableSpec.required, "dependency_not_active:$missingDependency")
            }

            // A target continues to occupy its conflict domain until unhook has
            // definitely completed. UNINSTALLING and FAILED-with-handle are not free slots.
            fun occupiesConflictDomain(candidate: Entry): Boolean =
                candidate.state == State.ACTIVE ||
                    candidate.state == State.INSTALLING ||
                    candidate.state == State.UNINSTALLING ||
                    (candidate.state == State.FAILED && candidate.handle != null)

            val activeConflict = stableSpec.conflicts.firstOrNull {
                entries[it]?.let(::occupiesConflictDomain) == true
            } ?: entries.values.firstOrNull {
                occupiesConflictDomain(it) && stableSpec.id in it.spec.conflicts
            }?.spec?.id
            if (activeConflict != null) {
                return Status(stableSpec.id, State.SKIPPED, existing?.attempts ?: 0,
                    stableSpec.required, "conflicts_with_active_target:$activeConflict")
            }

            entry = existing ?: Entry(stableSpec).also { entries[stableSpec.id] = it }
            entry.state = State.INSTALLING
            entry.attempts++
            entry.detail = null
        }

        var handle: Unhook? = null
        try {
            handle = installer.install(stableSpec)
            synchronized(coordination) {
                entry.handle = handle
            }

            if (!verifier.verify(stableSpec)) {
                val rollbackFailure = try {
                    handle.uninstall()
                    null
                } catch (failure: Throwable) {
                    failure
                }
                val result = synchronized(coordination) {
                    entry.handle = if (rollbackFailure == null) null else handle
                    entry.state = if (rollbackFailure == null) State.ROLLED_BACK else State.FAILED
                    entry.detail = if (rollbackFailure == null) {
                        "post_install_verification_failed"
                    } else {
                        "verification_failed_and_rollback_failed:${describe(rollbackFailure)}"
                    }
                    entry.snapshot()
                }
                if (rollbackFailure is VirtualMachineError) throw rollbackFailure
                if (rollbackFailure is ThreadDeath) throw rollbackFailure
                return result
            }

            return synchronized(coordination) {
                entry.state = State.ACTIVE
                entry.detail = null
                entry.snapshot()
            }
        } catch (failure: Throwable) {
            var rollbackFailure: Throwable? = null
            if (handle != null) {
                try {
                    handle.uninstall()
                } catch (rollback: Throwable) {
                    rollbackFailure = rollback
                    if (rollback !== failure) failure.addSuppressed(rollback)
                }
            }
            val result = synchronized(coordination) {
                entry.handle = if (rollbackFailure == null) null else handle
                entry.state = State.FAILED
                entry.detail = if (rollbackFailure == null) describe(failure)
                    else "install_or_verification_failed_rollback_failed:${describe(rollbackFailure)}"
                entry.snapshot()
            }
            if (failure is VirtualMachineError) throw failure
            if (failure is ThreadDeath) throw failure
            if (rollbackFailure is VirtualMachineError) throw rollbackFailure
            if (rollbackFailure is ThreadDeath) throw rollbackFailure
            return result
        }
    }

    /** Uninstalls an ACTIVE target. Repeated calls are idempotent. */
    fun uninstall(id: String): Status {
        require(id.isNotBlank()) { "hook target ID must not be blank" }
        val entry: Entry
        val handle: Unhook
        synchronized(coordination) {
            entry = entries[id] ?: return Status(id, State.NEW, 0, true, "target_not_registered")
            if (entry.state == State.ROLLED_BACK || entry.state == State.SKIPPED ||
                entry.state == State.NEW) {
                return entry.snapshot()
            }
            if (entry.state == State.FAILED && entry.handle == null) return entry.snapshot()
            if (entry.state != State.ACTIVE && entry.state != State.FAILED) return entry.snapshot()

            // A dependent protects its prerequisite from the moment installation starts
            // until its unhook has definitely completed. INSTALLING is important here: its
            // installer callback runs outside this lock, so the prerequisite could otherwise be
            // removed while the dependent is being attached. FAILED with a retained handle also
            // means rollback did not prove that the dependent is detached.
            val dependent = entries.values.firstOrNull {
                id in it.spec.dependencies && (
                    it.state == State.ACTIVE ||
                    it.state == State.INSTALLING ||
                    it.state == State.UNINSTALLING ||
                    (it.state == State.FAILED && it.handle != null)
                )
            }
            if (dependent != null) {
                return Status(id, entry.state, entry.attempts, entry.spec.required,
                    "required_by_active_target:${dependent.spec.id}")
            }

            handle = entry.handle ?: run {
                entry.state = State.FAILED
                entry.detail = "active_target_missing_unhook_handle"
                return entry.snapshot()
            }
            entry.state = State.UNINSTALLING
        }

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
