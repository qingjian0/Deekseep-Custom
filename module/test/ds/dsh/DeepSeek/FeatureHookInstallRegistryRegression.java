package ds.dsh.DeepSeek;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FeatureHookInstallRegistryRegression {
    public static void main(String[] args) throws Throwable {
        testSuccessfulInstallIsIdempotent();
        testFailureCanBeRetriedWithoutRerunningCompletedEntries();
        testDegradedEntryIsVisibleAsRetryable();
        testSkippedEntryIsTerminal();
        testConcurrentCallersDoNotDoubleInstall();
        testInstallerCallbackRunsOutsideEntryLock();
        testDifferentIdsInstallConcurrently();
        testPartialRegistrationRetryDoesNotDuplicateHooks();
        testFailureIsRethrownAndRecorded();
        testFatalFailureIsRethrown();
        System.out.println("FeatureHookInstallRegistry regression: PASS");
    }

    private static void testSuccessfulInstallIsIdempotent() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        FeatureHookInstallRegistry.Installer install = calls::incrementAndGet;
        registry.install("chat.request", install);
        registry.install("chat.request", install);
        assertEquals(1, calls.get(), "successful installer invocation count");
        assertState(registry, "chat.request", FeatureHookInstallRegistry.State.INSTALLED, 1);
    }

    private static void testFailureCanBeRetriedWithoutRerunningCompletedEntries()
            throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger completedCalls = new AtomicInteger();
        AtomicInteger failedCalls = new AtomicInteger();

        registry.install("core.navigation", completedCalls::incrementAndGet);
        try {
            registry.install("core.chat", () -> {
                failedCalls.incrementAndGet();
                throw new IllegalStateException("expected test failure");
            });
            throw new AssertionError("failure was not propagated");
        } catch (IllegalStateException expected) {
            assertEquals("expected test failure", expected.getMessage(), "failure message");
        }

        registry.install("core.chat", failedCalls::incrementAndGet);
        registry.install("core.navigation", completedCalls::incrementAndGet);

        assertEquals(1, completedCalls.get(), "completed feature must not run twice");
        assertEquals(2, failedCalls.get(), "failed feature should retry exactly once");
        assertState(registry, "core.navigation", FeatureHookInstallRegistry.State.INSTALLED, 1);
        assertState(registry, "core.chat", FeatureHookInstallRegistry.State.INSTALLED, 2);
        assertFalse(registry.hasFailures(), "registry should be healthy after retry");
    }

    private static void testDegradedEntryIsVisibleAsRetryable() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();

        registry.markDegraded("optional.feature", new IllegalStateException("partial install"));
        assertTrue(registry.hasRetryableFailures(), "degraded entry must be retryable");

        registry.install("optional.feature", calls::incrementAndGet);
        assertEquals(1, calls.get(), "degraded installer retry count");
        assertFalse(registry.hasRetryableFailures(), "successful retry must clear retryable state");
        assertState(registry, "optional.feature", FeatureHookInstallRegistry.State.INSTALLED, 1);
    }

    private static void testSkippedEntryIsTerminal() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.markSkipped("host.v271.optional", "not verified for this host generation");
        registry.install("host.v271.optional", calls::incrementAndGet);
        assertEquals(0, calls.get(), "skipped feature must not execute");
        assertState(registry, "host.v271.optional", FeatureHookInstallRegistry.State.SKIPPED, 0);
    }

    private static void testConcurrentCallersDoNotDoubleInstall() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try {
                registry.install("concurrent.feature", () -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting for test release");
                    }
                });
            } catch (Throwable failure) {
                threadFailure.set(failure);
            }
        });
        Thread second = new Thread(() -> {
            try {
                if (!entered.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("first installer did not start");
                }
                registry.install("concurrent.feature", calls::incrementAndGet);
            } catch (Throwable failure) {
                threadFailure.compareAndSet(null, failure);
            }
        });

        first.start();
        second.start();
        if (!entered.await(3, TimeUnit.SECONDS)) {
            release.countDown();
            throw new AssertionError("first installer did not start");
        }
        release.countDown();
        first.join(3000L);
        second.join(3000L);
        assertFalse(first.isAlive() || second.isAlive(), "concurrent test threads must finish");
        if (threadFailure.get() != null) throw threadFailure.get();
        assertEquals(1, calls.get(), "concurrent calls must execute installer once");
        assertState(registry, "concurrent.feature", FeatureHookInstallRegistry.State.INSTALLED, 1);
    }

    private static void testInstallerCallbackRunsOutsideEntryLock() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        CountDownLatch snapshotCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> probeFailure = new AtomicReference<>();

        registry.install("callback.lock", () -> {
            registry.markSkipped("callback.lock", "late skip must not override INSTALLING");
            registry.markDegraded("callback.lock", new IllegalStateException(
                    "late degradation must not override INSTALLING"));
            Thread probe = new Thread(() -> {
                try {
                    FeatureHookInstallRegistry.Status found = null;
                    for (FeatureHookInstallRegistry.Status status : registry.snapshot()) {
                        if ("callback.lock".equals(status.id)) found = status;
                    }
                    check(found != null && found.state == FeatureHookInstallRegistry.State.INSTALLING,
                            "observer must see INSTALLING while callback runs");
                } catch (Throwable failure) {
                    probeFailure.set(failure);
                } finally {
                    snapshotCompleted.countDown();
                }
            }, "registry-callback-lock-probe");
            probe.start();
            check(snapshotCompleted.await(2, TimeUnit.SECONDS),
                    "installer callback must not hold the entry monitor");
            probe.join(2000L);
            check(!probe.isAlive(), "registry observer must finish during callback");
            if (probeFailure.get() != null) {
                throw new AssertionError("registry observer failed", probeFailure.get());
            }
        });

        assertState(registry, "callback.lock", FeatureHookInstallRegistry.State.INSTALLED, 1);
    }

    private static void testDifferentIdsInstallConcurrently() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        Runnable firstInstall = () -> {
            try {
                registry.install("parallel.first", () -> {
                    firstCalls.incrementAndGet();
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("first installer timed out waiting for peer");
                    }
                });
            } catch (Throwable failure) {
                threadFailure.compareAndSet(null, failure);
            }
        };
        Runnable secondInstall = () -> {
            try {
                registry.install("parallel.second", () -> {
                    secondCalls.incrementAndGet();
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("second installer timed out waiting for peer");
                    }
                });
            } catch (Throwable failure) {
                threadFailure.compareAndSet(null, failure);
            }
        };

        Thread first = new Thread(firstInstall, "registry-parallel-first");
        Thread second = new Thread(secondInstall, "registry-parallel-second");
        first.start();
        second.start();
        boolean bothEntered = entered.await(3, TimeUnit.SECONDS);
        release.countDown();
        first.join(3000L);
        second.join(3000L);
        assertTrue(bothEntered, "different hook IDs must install concurrently");
        assertFalse(first.isAlive() || second.isAlive(),
                "parallel installer threads must finish");
        if (threadFailure.get() != null) throw threadFailure.get();
        assertEquals(1, firstCalls.get(), "first independent installer count");
        assertEquals(1, secondCalls.get(), "second independent installer count");
        assertState(registry, "parallel.first", FeatureHookInstallRegistry.State.INSTALLED, 1);
        assertState(registry, "parallel.second", FeatureHookInstallRegistry.State.INSTALLED, 1);
    }

    private static void testPartialRegistrationRetryDoesNotDuplicateHooks()
            throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        java.util.Set<String> registered = java.util.concurrent.ConcurrentHashMap.newKeySet();
        AtomicInteger firstHookRegistrations = new AtomicInteger();
        AtomicInteger secondHookRegistrations = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();

        registry.markDegraded("partial.hooks", new IllegalStateException("partial registration"));
        try {
            registry.install("partial.hooks", () -> {
                if (registered.add("first")) firstHookRegistrations.incrementAndGet();
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("second hook registration failed");
                }
                if (registered.add("second")) secondHookRegistrations.incrementAndGet();
            });
            throw new AssertionError("partial registration failure was not propagated");
        } catch (IllegalStateException expected) {
            assertEquals("second hook registration failed", expected.getMessage(),
                    "partial registration failure");
        }
        assertTrue(registry.hasRetryableFailures(),
                "partially registered hook group must remain retryable");

        registry.install("partial.hooks", () -> {
            if (registered.add("first")) firstHookRegistrations.incrementAndGet();
            if (registered.add("second")) secondHookRegistrations.incrementAndGet();
        });

        assertEquals(1, firstHookRegistrations.get(),
                "already registered hook must not be registered twice");
        assertEquals(1, secondHookRegistrations.get(),
                "missing hook must be registered on retry");
        assertFalse(registry.hasRetryableFailures(),
                "successful partial-registration retry must clear retryable state");
        assertState(registry, "partial.hooks", FeatureHookInstallRegistry.State.INSTALLED, 2);
    }

    private static void testFailureIsRethrownAndRecorded() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        RuntimeException expected = new RuntimeException("identity");
        try {
            registry.install("failed.feature", () -> { throw expected; });
            throw new AssertionError("failure was not propagated");
        } catch (RuntimeException actual) {
            if (actual != expected) throw new AssertionError("failure identity changed");
        }
        assertState(registry, "failed.feature", FeatureHookInstallRegistry.State.FAILED, 1);
        assertTrue(registry.hasFailures(), "failed registry must be unhealthy");
    }

    private static void testFatalFailureIsRethrown() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        ThreadDeath fatal = new ThreadDeath();
        try {
            registry.install("fatal.feature", () -> { throw fatal; });
            throw new AssertionError("fatal failure was not propagated");
        } catch (ThreadDeath actual) {
            if (actual != fatal) throw new AssertionError("fatal failure identity changed");
        }
        assertState(registry, "fatal.feature", FeatureHookInstallRegistry.State.FAILED, 1);
    }

    private static void assertState(FeatureHookInstallRegistry registry, String id,
            FeatureHookInstallRegistry.State state, int attempts) {
        for (FeatureHookInstallRegistry.Status status : registry.snapshot()) {
            if (id.equals(status.id)) {
                if (status.state != state) {
                    throw new AssertionError(id + " expected " + state + " but was " + status.state);
                }
                assertEquals(attempts, status.attempts, id + " attempt count");
                return;
            }
        }
        throw new AssertionError("missing registry status for " + id);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void assertFalse(boolean value, String label) {
        if (value) throw new AssertionError(label);
    }
}
