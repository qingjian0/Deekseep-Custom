package ds.dsh.DeepSeek;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for the shared Kotlin feature-hook installation registry. */
public final class FeatureHookInstallRegistryRegressionTest {
    public static void main(String[] args) throws Throwable {
        retriesFailedInstallWithoutRepeatingSuccessfulInstall();
        retriesDegradedBestEffortInstall();
        separatesRequiredFailuresFromBestEffortDegradation();
        serializesConcurrentInstallAttempts();
        installsDifferentIdsConcurrently();
        retriesPartialRegistrationWithoutDuplicateHooks();
        keepsSkippedInstallTerminal();
        allowsExplicitUninstallAndReinstall();
        System.out.println("FeatureHookInstallRegistryRegressionTest OK");
    }

    private static void retriesFailedInstallWithoutRepeatingSuccessfulInstall()
            throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger attempts = new AtomicInteger();

        boolean failed = false;
        try {
            registry.install("test.required", () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("first attempt");
            });
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed, "failed installer must propagate its failure");
        check(status(registry, "test.required").state
                        == FeatureHookInstallRegistry.State.FAILED,
                "failed installer state");
        check(status(registry, "test.required").attempts == 1,
                "first attempt count");

        registry.install("test.required", () -> attempts.incrementAndGet());
        check(status(registry, "test.required").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "retry must install the feature");
        check(status(registry, "test.required").attempts == 2,
                "retry attempt count");

        registry.install("test.required", () -> {
            throw new AssertionError("installed feature must not run again");
        });
        check(attempts.get() == 2, "successful installation must remain idempotent");
    }

    private static void retriesDegradedBestEffortInstall() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger attempts = new AtomicInteger();
        try {
            registry.install("test.optional", () -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("partial optional install");
            });
        } catch (IllegalArgumentException expected) {
            registry.markDegraded("test.optional", expected);
        }

        check(status(registry, "test.optional").state
                        == FeatureHookInstallRegistry.State.DEGRADED,
                "best-effort failure must be recorded as degraded");
        check(registry.hasFailures(), "degraded installation must be visible");

        registry.install("test.optional", () -> attempts.incrementAndGet());
        check(status(registry, "test.optional").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "degraded installation must be retryable");
        check(status(registry, "test.optional").attempts == 2,
                "degraded retry attempt count");
        check(!registry.hasFailures(), "successful retry must clear failure state");
    }

    private static void separatesRequiredFailuresFromBestEffortDegradation()
            throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();

        try {
            registry.install("test.required-failed", () -> {
                throw new IllegalStateException("required hook unavailable");
            });
        } catch (IllegalStateException expected) {
            // Required failures remain FAILED until a later retry succeeds.
        }
        try {
            registry.install("test.optional-degraded", () -> {
                throw new IllegalArgumentException("optional hook unavailable");
            });
        } catch (IllegalArgumentException expected) {
            registry.markDegraded("test.optional-degraded", expected);
        }

        check(registry.hasRequiredFailures(),
                "required FAILED entry must block core installation completion");
        check(registry.hasFailures(),
                "diagnostic failure query must include optional degradation");

        registry.install("test.required-failed", () -> { });
        check(!registry.hasRequiredFailures(),
                "successful required retry must unblock core installation completion");
        check(registry.hasFailures(),
                "optional degradation must remain visible after required retry");
    }

    private static void serializesConcurrentInstallAttempts() throws Throwable {
        final FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        final AtomicInteger installerCalls = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch start = new CountDownLatch(1);
        Runnable install = () -> {
            try {
                start.await();
                registry.install("test.concurrent", () -> {
                    installerCalls.incrementAndGet();
                    Thread.sleep(40L);
                });
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };

        Thread first = new Thread(install, "hook-registry-test-1");
        Thread second = new Thread(install, "hook-registry-test-2");
        first.start();
        second.start();
        start.countDown();
        first.join(2000L);
        second.join(2000L);

        check(!first.isAlive() && !second.isAlive(),
                "concurrent installers must complete without deadlock");
        if (failure.get() != null) throw new AssertionError(
                "concurrent installer failed", failure.get());
        check(installerCalls.get() == 1,
                "concurrent calls must not execute the same installer twice");
        check(status(registry, "test.concurrent").attempts == 1,
                "concurrent calls must count exactly one installation attempt");
        check(status(registry, "test.concurrent").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "concurrent installation must end in INSTALLED");
    }

    private static void installsDifferentIdsConcurrently() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable firstInstall = () -> {
            try {
                registry.install("test.parallel.first", () -> {
                    firstCalls.incrementAndGet();
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("first independent installer timed out");
                    }
                });
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };
        Runnable secondInstall = () -> {
            try {
                registry.install("test.parallel.second", () -> {
                    secondCalls.incrementAndGet();
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("second independent installer timed out");
                    }
                });
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };
        Thread first = new Thread(firstInstall, "hook-registry-parallel-1");
        Thread second = new Thread(secondInstall, "hook-registry-parallel-2");
        first.start();
        second.start();
        boolean concurrent = entered.await(3, TimeUnit.SECONDS);
        release.countDown();
        first.join(3000L);
        second.join(3000L);
        check(concurrent, "different hook IDs must not share a global install lock");
        check(!first.isAlive() && !second.isAlive(),
                "independent installers must complete");
        if (failure.get() != null) throw new AssertionError(
                "independent installer failed", failure.get());
        check(firstCalls.get() == 1 && secondCalls.get() == 1,
                "both independent installers must execute exactly once");
        check(status(registry, "test.parallel.first").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "first independent entry must install");
        check(status(registry, "test.parallel.second").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "second independent entry must install");
    }

    private static void retriesPartialRegistrationWithoutDuplicateHooks()
            throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        java.util.Set<String> registered =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
        AtomicInteger firstHook = new AtomicInteger();
        AtomicInteger secondHook = new AtomicInteger();
        AtomicInteger firstAttempt = new AtomicInteger();
        registry.markDegraded("test.partial", new IllegalStateException("partial hook group"));
        try {
            registry.install("test.partial", () -> {
                if (registered.add("first")) firstHook.incrementAndGet();
                if (firstAttempt.incrementAndGet() == 1) {
                    throw new IllegalStateException("second hook unavailable");
                }
                if (registered.add("second")) secondHook.incrementAndGet();
            });
            throw new AssertionError("partial hook failure must propagate");
        } catch (IllegalStateException expected) {
            check("second hook unavailable".equals(expected.getMessage()),
                    "partial hook failure message");
        }
        check(registry.hasRetryableFailures(),
                "partial hook failure must remain retryable");
        registry.install("test.partial", () -> {
            if (registered.add("first")) firstHook.incrementAndGet();
            if (registered.add("second")) secondHook.incrementAndGet();
        });
        check(firstHook.get() == 1, "first registered hook must not be duplicated");
        check(secondHook.get() == 1, "missing hook must be registered on retry");
        check(!registry.hasRetryableFailures(),
                "successful partial retry must clear retryable state");
        check(status(registry, "test.partial").attempts == 2,
                "partial retry attempt count");
        check(status(registry, "test.partial").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "partial retry must finish installed");
    }

    private static void allowsExplicitUninstallAndReinstall() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger attempts = new AtomicInteger();
        registry.install("test.reinstallable", () -> attempts.incrementAndGet());
        check(status(registry, "test.reinstallable").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "first install state");

        check(registry.markUninstalled("test.reinstallable"),
                "installed group should transition to UNINSTALLED");
        check(status(registry, "test.reinstallable").state
                        == FeatureHookInstallRegistry.State.UNINSTALLED,
                "uninstall state");
        check(!registry.markUninstalled("test.reinstallable"),
                "already uninstalled state must not transition twice");
        check(!registry.hasFailures(), "uninstall is not an installation failure");

        registry.install("test.reinstallable", () -> attempts.incrementAndGet());
        check(attempts.get() == 2, "explicitly uninstalled group should be reinstallable");
        check(status(registry, "test.reinstallable").state
                        == FeatureHookInstallRegistry.State.INSTALLED,
                "reinstalled group state");
        check(status(registry, "test.reinstallable").attempts == 2,
                "reinstall attempt count");

        check(!registry.markUninstalled("test.missing"),
                "missing registry entry must not be uninstalled");
    }

    private static void keepsSkippedInstallTerminal() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        registry.markSkipped("test.unsupported", "host version gate");
        registry.install("test.unsupported", () -> {
            throw new AssertionError("skipped feature must not be installed");
        });
        check(status(registry, "test.unsupported").state
                        == FeatureHookInstallRegistry.State.SKIPPED,
                "skipped feature must remain terminal");
        check(status(registry, "test.unsupported").attempts == 0,
                "skipped feature must not increment installer attempts");
    }

    private static FeatureHookInstallRegistry.Status status(
            FeatureHookInstallRegistry registry, String id) {
        for (FeatureHookInstallRegistry.Status value : registry.snapshot()) {
            if (id.equals(value.id)) return value;
        }
        throw new AssertionError("missing registry entry: " + id);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
