package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HookLifecycleManagerTest {
    @Test
    fun installsAndVerifiesTargetOnlyOnce() {
        val manager = HookLifecycleManager()
        val installs = AtomicInteger()
        val verifies = AtomicInteger()
        val spec = HookLifecycleManager.Spec("chat.transport")
        val installer = HookLifecycleManager.Installer {
            installs.incrementAndGet()
            HookLifecycleManager.Unhook { }
        }
        val verifier = HookLifecycleManager.Verifier {
            verifies.incrementAndGet()
            true
        }

        assertEquals(HookLifecycleManager.State.ACTIVE, manager.install(spec, installer, verifier).state)
        assertEquals(HookLifecycleManager.State.ACTIVE, manager.install(spec, installer, verifier).state)
        assertEquals(1, installs.get())
        assertEquals(1, verifies.get())
    }

    @Test
    fun failedPostInstallVerificationRollsBackTarget() {
        val manager = HookLifecycleManager()
        val unhooks = AtomicInteger()
        val status = manager.install(
            HookLifecycleManager.Spec("chat.transport"),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { unhooks.incrementAndGet() } },
            HookLifecycleManager.Verifier { false }
        )

        assertEquals(HookLifecycleManager.State.ROLLED_BACK, status.state)
        assertEquals("post_install_verification_failed", status.detail)
        assertEquals(1, unhooks.get())
        assertFalse(manager.hasRequiredFailures())
    }

    @Test
    fun verifierExceptionAlsoRollsBackBeforeReportingFailure() {
        val manager = HookLifecycleManager()
        val unhooks = AtomicInteger()
        val status = manager.install(
            HookLifecycleManager.Spec("chat.transport"),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { unhooks.incrementAndGet() } },
            HookLifecycleManager.Verifier { throw IllegalStateException("health check failed") }
        )

        assertEquals(HookLifecycleManager.State.FAILED, status.state)
        assertEquals(1, unhooks.get())
        assertTrue(status.detail!!.contains("health check failed"))
    }

    @Test
    fun dependencyMustBeActiveBeforeDependentCanInstall() {
        val manager = HookLifecycleManager()
        val dependent = HookLifecycleManager.Spec(
            id = "chat.stream",
            dependencies = setOf("chat.transport")
        )
        val blocked = manager.install(
            dependent,
            HookLifecycleManager.Installer { error("must not install") },
            HookLifecycleManager.Verifier { true }
        )
        assertEquals(HookLifecycleManager.State.SKIPPED, blocked.state)
        assertTrue(blocked.detail!!.startsWith("dependency_not_active:"))

        manager.install(
            HookLifecycleManager.Spec("chat.transport"),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
            HookLifecycleManager.Verifier { true }
        )
        val installed = manager.install(
            dependent,
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
            HookLifecycleManager.Verifier { true }
        )
        assertEquals(HookLifecycleManager.State.ACTIVE, installed.state)
    }

    @Test
    fun conflictingActiveTargetBlocksInstall() {
        val manager = HookLifecycleManager()
        manager.install(
            HookLifecycleManager.Spec("transport.v1", conflicts = setOf("transport.v2")),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
            HookLifecycleManager.Verifier { true }
        )
        val blocked = manager.install(
            HookLifecycleManager.Spec("transport.v2"),
            HookLifecycleManager.Installer { error("conflicting target must not install") },
            HookLifecycleManager.Verifier { true }
        )

        assertEquals(HookLifecycleManager.State.SKIPPED, blocked.state)
        assertTrue(blocked.detail!!.contains("transport.v1"))
    }

    @Test
    fun dependencyCannotBeUninstalledWhileDependentIsActive() {
        val manager = HookLifecycleManager()
        manager.install(
            HookLifecycleManager.Spec("transport"),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
            HookLifecycleManager.Verifier { true }
        )
        manager.install(
            HookLifecycleManager.Spec("stream", dependencies = setOf("transport")),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
            HookLifecycleManager.Verifier { true }
        )

        val blocked = manager.uninstall("transport")
        assertEquals(HookLifecycleManager.State.ACTIVE, blocked.state)
        assertEquals("required_by_active_target:stream", blocked.detail)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("stream").state)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("transport").state)
    }

    @Test
    fun failedInstallCanBeRetriedButActiveTargetCannotBeReplaced() {
        val manager = HookLifecycleManager()
        val attempts = AtomicInteger()
        val spec = HookLifecycleManager.Spec("chat.transport")
        val installer = HookLifecycleManager.Installer {
            if (attempts.incrementAndGet() == 1) error("transient install failure")
            HookLifecycleManager.Unhook { }
        }
        val verifier = HookLifecycleManager.Verifier { true }

        assertEquals(HookLifecycleManager.State.FAILED, manager.install(spec, installer, verifier).state)
        assertEquals(HookLifecycleManager.State.ACTIVE, manager.install(spec, installer, verifier).state)
        assertEquals(2, attempts.get())
    }

    @Test
    fun failedRollbackRetainsHandleForExplicitCleanupRetry() {
        val manager = HookLifecycleManager()
        val unhooks = AtomicInteger()
        val status = manager.install(
            HookLifecycleManager.Spec("chat.transport"),
            HookLifecycleManager.Installer {
                HookLifecycleManager.Unhook {
                    if (unhooks.incrementAndGet() == 1) error("temporary unhook failure")
                }
            },
            HookLifecycleManager.Verifier { false }
        )

        assertEquals(HookLifecycleManager.State.FAILED, status.state)
        assertTrue(status.detail!!.contains("rollback_failed"))
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("chat.transport").state)
        assertEquals(2, unhooks.get())
    }

    @Test
    fun conflictingTargetIsReservedWhileFirstInstallIsInProgress() {
        val manager = HookLifecycleManager()
        val installerStarted = CountDownLatch(1)
        val allowInstallToFinish = CountDownLatch(1)
        val firstResult = java.util.concurrent.atomic.AtomicReference<HookLifecycleManager.Status>()
        val first = Thread {
            firstResult.set(manager.install(
                HookLifecycleManager.Spec("transport.v1", conflicts = setOf("transport.v2")),
                HookLifecycleManager.Installer {
                    installerStarted.countDown()
                    check(allowInstallToFinish.await(5, TimeUnit.SECONDS))
                    HookLifecycleManager.Unhook { }
                },
                HookLifecycleManager.Verifier { true }
            ))
        }
        first.start()
        assertTrue(installerStarted.await(5, TimeUnit.SECONDS))

        val second = manager.install(
            HookLifecycleManager.Spec("transport.v2"),
            HookLifecycleManager.Installer { error("conflicting install must be blocked") },
            HookLifecycleManager.Verifier { true }
        )
        allowInstallToFinish.countDown()
        first.join(5000)

        assertFalse(first.isAlive)
        assertEquals(HookLifecycleManager.State.SKIPPED, second.state)
        assertTrue(second.detail!!.contains("transport.v1"))
        assertEquals(HookLifecycleManager.State.ACTIVE, firstResult.get().state)
    }


    @Test
    fun installingDependentProtectsItsActivePrerequisite() {
        val manager = HookLifecycleManager()
        assertEquals(
            HookLifecycleManager.State.ACTIVE,
            manager.install(
                HookLifecycleManager.Spec("transport"),
                HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
                HookLifecycleManager.Verifier { true }
            ).state
        )

        val installerStarted = CountDownLatch(1)
        val allowInstallToFinish = CountDownLatch(1)
        val dependentResult = java.util.concurrent.atomic.AtomicReference<HookLifecycleManager.Status>()
        val dependent = Thread {
            dependentResult.set(manager.install(
                HookLifecycleManager.Spec("stream", dependencies = setOf("transport")),
                HookLifecycleManager.Installer {
                    installerStarted.countDown()
                    check(allowInstallToFinish.await(5, TimeUnit.SECONDS))
                    HookLifecycleManager.Unhook { }
                },
                HookLifecycleManager.Verifier { true }
            ))
        }

        dependent.start()
        try {
            assertTrue(installerStarted.await(5, TimeUnit.SECONDS))
            val blocked = manager.uninstall("transport")
            assertEquals(HookLifecycleManager.State.ACTIVE, blocked.state)
            assertEquals("required_by_active_target:stream", blocked.detail)
        } finally {
            allowInstallToFinish.countDown()
            dependent.join(5000)
        }

        assertFalse(dependent.isAlive)
        assertEquals(HookLifecycleManager.State.ACTIVE, dependentResult.get().state)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("stream").state)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("transport").state)
    }


    @Test
    fun failedDependentWithRetainedHandleProtectsPrerequisite() {
        val manager = HookLifecycleManager()
        assertEquals(
            HookLifecycleManager.State.ACTIVE,
            manager.install(
                HookLifecycleManager.Spec("transport"),
                HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
                HookLifecycleManager.Verifier { true }
            ).state
        )

        val unhooks = AtomicInteger()
        val failedDependent = manager.install(
            HookLifecycleManager.Spec("stream", dependencies = setOf("transport")),
            HookLifecycleManager.Installer {
                HookLifecycleManager.Unhook {
                    if (unhooks.incrementAndGet() == 1) error("transient rollback failure")
                }
            },
            HookLifecycleManager.Verifier { false }
        )
        assertEquals(HookLifecycleManager.State.FAILED, failedDependent.state)

        val blocked = manager.uninstall("transport")
        assertEquals(HookLifecycleManager.State.ACTIVE, blocked.state)
        assertEquals("required_by_active_target:stream", blocked.detail)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("stream").state)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("transport").state)
        assertEquals(2, unhooks.get())
    }

    @Test
    fun uninstallFailureRetainsHandleForRetry() {
        val manager = HookLifecycleManager()
        val unhooks = AtomicInteger()
        val spec = HookLifecycleManager.Spec("chat.transport")
        val installed = manager.install(
            spec,
            HookLifecycleManager.Installer {
                HookLifecycleManager.Unhook {
                    if (unhooks.incrementAndGet() == 1) error("temporary unhook failure")
                }
            },
            HookLifecycleManager.Verifier { true }
        )
        assertEquals(HookLifecycleManager.State.ACTIVE, installed.state)

        val failed = manager.uninstall(spec.id)
        assertEquals(HookLifecycleManager.State.FAILED, failed.state)
        assertTrue(failed.detail!!.contains("temporary unhook failure"))

        val retried = manager.uninstall(spec.id)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, retried.state)
        assertEquals(2, unhooks.get())
    }


    @Test
    fun mutableDependencySetCannotChangeRegisteredLifecycleGraph() {
        val manager = HookLifecycleManager()
        manager.install(
            HookLifecycleManager.Spec("transport"),
            HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
            HookLifecycleManager.Verifier { true }
        )

        val dependencies = mutableSetOf("transport")
        val dependent = HookLifecycleManager.Spec("stream", dependencies = dependencies)
        assertEquals(
            HookLifecycleManager.State.ACTIVE,
            manager.install(
                dependent,
                HookLifecycleManager.Installer { HookLifecycleManager.Unhook { } },
                HookLifecycleManager.Verifier { true }
            ).state
        )

        // Mutating the caller-owned collection must not erase the installed dependency edge.
        dependencies.clear()
        val blocked = manager.uninstall("transport")
        assertEquals(HookLifecycleManager.State.ACTIVE, blocked.state)
        assertEquals("required_by_active_target:stream", blocked.detail)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("stream").state)
        assertEquals(HookLifecycleManager.State.ROLLED_BACK, manager.uninstall("transport").state)
    }

    @Test
    fun disabledTargetIsSkippedWithoutCallingInstaller() {
        val manager = HookLifecycleManager()
        val installs = AtomicInteger()
        val status = manager.install(
            HookLifecycleManager.Spec("chat.experimental", enabled = false),
            HookLifecycleManager.Installer {
                installs.incrementAndGet()
                HookLifecycleManager.Unhook { }
            },
            HookLifecycleManager.Verifier { true }
        )

        assertEquals(HookLifecycleManager.State.SKIPPED, status.state)
        assertEquals("disabled_by_policy", status.detail)
        assertEquals(0, installs.get())
    }
}
