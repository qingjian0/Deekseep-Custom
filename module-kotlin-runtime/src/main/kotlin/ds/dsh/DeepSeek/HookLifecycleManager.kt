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
                entries[it]?.state == State.ACTIVE
            } ?: entries.values.firstOrNull {
                it.state == State.ACTIVE && spec.id in it.spec.conflicts
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
        try {
            handle = installer.install(spec)
            synchronized(coordination) {
                entry.handle = handle
            }
            if (!verifier.verify(spec)) {
                val rollbackFailure = try {
                    handle.uninstall()
                    null
                } catch (failure: Throwable) {
                    failure
                }
                synchronized(coordination) {
                    entry.handle = null
                    entry.state = if (rollbackFailure == null) State.ROLLED_BACK else State.FAILED
                    entry.detail = if (rollbackFailure == null) {
                        "post_install_verification_failed"
                    } else {
                        "verification_failed_and_rollback_failed:${describe(rollbackFailure)}"
                    }
                }
                if (rollbackFailure is VirtualMachineError) throw rollbackFailure
                if (rollbackFailure is ThreadDeath) throw rollbackFailure
                return entry.snapshot()
            }

            synchronized(coordination) {
                entry.state = State.ACTIVE
                entry.detail = null
                entry.snapshot()
            }.also { return it }
        } catch (failure: Throwable) {
            var rollbackFailure: Throwable? = null
            if (handle != null) {
                try {
                    handle.uninstall()
                } catch (rollback: Throwable) {
                    rollbackFailure = rollback
                    failure.addSuppressed(rollback)
                }
            }
            val result = synchronized(coordination) {
                if (rollbackFailure == null) entry.handle = null else entry.handle = handle
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
            val dependent = entries.values.firstOrNull {
                it.state == State.ACTIVE && id in it.spec.dependencies
            }
            if (dependent != null) {
                entry.detail = "required_by_active_target:${dependent.spec.id}"
                return entry.snapshot()
            }
            handle = entry.handle ?: run {
                entry.state = State.FAILED
                entry.detail = "active_target_missing_unhook_handle"
                return entry.snapshot()
            }
            entry.state = State.UNINSTALLING
        }

        try {
            handle.uninstall()
            synchronized(coordination) {
                entry.handle = null
                entry.state = State.ROLLED_BACK
                entry.detail = "uninstalled"
                entry.snapshot()
            }
        } catch (failure: Throwable) {
            synchronized(coordination) {
                entry.state = State.FAILED
                entry.detail = "uninstall_failed:${describe(failure)}"
                entry.snapshot()
            }.also {
                if (failure is VirtualMachineError) throw failure
                if (failure is ThreadDeath) throw failure
                return it
            }
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
