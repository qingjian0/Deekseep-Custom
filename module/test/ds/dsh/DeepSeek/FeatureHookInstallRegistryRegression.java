package ds.dsh.DeepSeek;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FeatureHookInstallRegistryRegression {
    public static void main(String[] args) throws Throwable {
        testSuccessfulInstallIsIdempotent();
        testFailureCanBeRetriedWithoutRerunningCompletedEntries();
        testSkippedEntryIsTerminal();
        testSkipDoesNotOverwriteInstalledEntry();
        testSkipCannotWinAgainstInFlightInstall();
        testDegradedDoesNotOverwriteTerminalEntries();
        testMarkSkippedPreservesFailedEntry();
        testMarkSkippedPreservesDegradedEntry();
        testConcurrentCallersDoNotDoubleInstall();
        testSnapshotIsStableAndUnmodifiable();
        testBlankIdsAreRejected();
        testJavaInteropSurfaceIsStable();
        testFailureIsRethrownAndRecorded();
        testDegradedEntryCanBeRetried();
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

    private static void testSkippedEntryIsTerminal() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.markSkipped("host.v271.optional", "not verified for this host generation");
        registry.install("host.v271.optional", calls::incrementAndGet);
        assertEquals(0, calls.get(), "skipped feature must not execute");
        assertState(registry, "host.v271.optional", FeatureHookInstallRegistry.State.SKIPPED, 0);
    }

    private static void testMarkSkippedPreservesFailedEntry() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        try {
            registry.install("failed.then.skipped", () -> {
                throw new IllegalStateException("original install failure");
            });
            throw new AssertionError("failure was not propagated");
        } catch (IllegalStateException expected) {
            assertEquals("original install failure", expected.getMessage(), "failure message");
        }

        registry.markSkipped("failed.then.skipped", "later host gate");
        assertState(registry, "failed.then.skipped", FeatureHookInstallRegistry.State.FAILED, 1);
        assertTrue(registry.hasFailures(), "markSkipped must not hide a failed installation");
    }

    private static void testMarkSkippedPreservesDegradedEntry() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        registry.markDegraded("degraded.then.skipped",
                new IllegalStateException("partial installation"));
        registry.markSkipped("degraded.then.skipped", "later host gate");
        assertState(registry, "degraded.then.skipped",
                FeatureHookInstallRegistry.State.DEGRADED, 0);
        assertTrue(registry.hasFailures(), "markSkipped must not hide a degraded installation");
    }

    private static void testSkipDoesNotOverwriteInstalledEntry() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.install("installed.before.skip", calls::incrementAndGet);
        registry.markSkipped("installed.before.skip", "installed work is terminal");
        assertState(registry, "installed.before.skip",
                FeatureHookInstallRegistry.State.INSTALLED, 1);
        assertEquals(1, calls.get(), "installed entry must remain installed");
        assertFalse(registry.hasFailures(), "installed entry must remain healthy");
    }

    private static void testSkipCannotWinAgainstInFlightInstall() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch skipStarted = new CountDownLatch(1);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        Thread installer = new Thread(() -> {
            try {
                registry.install("install.races.skip", () -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to release installer");
                    }
                });
            } catch (Throwable failure) {
                threadFailure.compareAndSet(null, failure);
            }
        });
        Thread skipper = new Thread(() -> {
            try {
                if (!entered.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("installer did not enter");
                }
                skipStarted.countDown();
                registry.markSkipped("install.races.skip", "must not replace in-flight install");
            } catch (Throwable failure) {
                threadFailure.compareAndSet(null, failure);
            }
        });

        installer.start();
        skipper.start();
        if (!skipStarted.await(3, TimeUnit.SECONDS)) {
            release.countDown();
            throw new AssertionError("skip caller did not start");
        }
        long blockedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (skipper.getState() != Thread.State.BLOCKED
                && System.nanoTime() < blockedDeadline) {
            Thread.yield();
        }
        if (skipper.getState() != Thread.State.BLOCKED) {
            release.countDown();
            throw new AssertionError("skip caller did not block behind the in-flight installer");
        }
        release.countDown();
        installer.join(3000L);
        skipper.join(3000L);
        assertFalse(installer.isAlive() || skipper.isAlive(),
                "install/skip race threads must finish");
        if (threadFailure.get() != null) throw threadFailure.get();
        assertEquals(1, calls.get(), "in-flight installer must execute exactly once");
        assertState(registry, "install.races.skip", FeatureHookInstallRegistry.State.INSTALLED, 1);
        assertFalse(registry.hasFailures(), "successful in-flight install must remain healthy");
    }

    private static void testDegradedDoesNotOverwriteTerminalEntries() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        registry.install("installed.before.degraded", () -> { });
        registry.markDegraded("installed.before.degraded",
                new IllegalStateException("late degradation must be ignored"));
        assertState(registry, "installed.before.degraded",
                FeatureHookInstallRegistry.State.INSTALLED, 1);

        registry.markSkipped("skipped.before.degraded", "explicit host gate");
        registry.markDegraded("skipped.before.degraded",
                new IllegalStateException("late degradation must be ignored"));
        assertState(registry, "skipped.before.degraded",
                FeatureHookInstallRegistry.State.SKIPPED, 0);
        assertFalse(registry.hasFailures(), "terminal entries must not become degraded");
    }

    private static void testConcurrentCallersDoNotDoubleInstall() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
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
                threadFailure.compareAndSet(null, failure);
            }
        });
        Thread second = new Thread(() -> {
            try {
                if (!entered.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("first installer did not start");
                }
                secondStarted.countDown();
                registry.install("concurrent.feature", calls::incrementAndGet);
            } catch (Throwable failure) {
                threadFailure.compareAndSet(null, failure);
            }
        });

        first.start();
        second.start();
        if (!entered.await(3, TimeUnit.SECONDS)
                || !secondStarted.await(3, TimeUnit.SECONDS)) {
            release.countDown();
            first.join(3000L);
            second.join(3000L);
            throw new AssertionError("both concurrent callers did not reach the install boundary");
        }
        long blockedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (second.getState() != Thread.State.BLOCKED
                && System.nanoTime() < blockedDeadline) {
            Thread.yield();
        }
        if (second.getState() != Thread.State.BLOCKED) {
            release.countDown();
            first.join(3000L);
            second.join(3000L);
            throw new AssertionError("second installer did not block behind the active installer");
        }
        release.countDown();
        first.join(3000L);
        second.join(3000L);
        assertFalse(first.isAlive() || second.isAlive(), "concurrent test threads must finish");
        if (threadFailure.get() != null) throw threadFailure.get();
        assertEquals(1, calls.get(), "concurrent calls must execute installer once");
        assertState(registry, "concurrent.feature", FeatureHookInstallRegistry.State.INSTALLED, 1);
    }

    private static void testSnapshotIsStableAndUnmodifiable() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        registry.install("snapshot.feature", () -> { });
        java.util.List<FeatureHookInstallRegistry.Status> snapshot = registry.snapshot();
        assertEquals(1, snapshot.size(), "snapshot entry count");
        assertState(registry, "snapshot.feature", FeatureHookInstallRegistry.State.INSTALLED, 1);
        try {
            snapshot.clear();
            throw new AssertionError("snapshot list must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // Expected: callers must not be able to mutate registry snapshots.
        }
        registry.markSkipped("snapshot.other", "new entry after snapshot");
        assertEquals(1, snapshot.size(), "previous snapshot must remain stable");
    }

    private static void testBlankIdsAreRejected() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        assertIllegalArgument(() -> registry.install("  ", () -> { }),
                "install must reject blank IDs");
        assertIllegalArgument(() -> registry.markSkipped("", "invalid"),
                "markSkipped must reject blank IDs");
        assertIllegalArgument(() -> registry.markDegraded(null,
                new IllegalStateException("invalid")), "markDegraded must reject null IDs");
        assertTrue(registry.snapshot().isEmpty(), "invalid IDs must not create entries");
    }

    private static void assertIllegalArgument(ThrowingAction action, String label) throws Throwable {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
            // Expected: invalid stable IDs are rejected at the boundary.
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Throwable;
    }

    private static void testJavaInteropSurfaceIsStable() throws Throwable {
        Class<FeatureHookInstallRegistry> registryClass = FeatureHookInstallRegistry.class;
        registryClass.getConstructor();
        registryClass.getMethod("install", String.class, FeatureHookInstallRegistry.Installer.class);
        registryClass.getMethod("markSkipped", String.class, String.class);
        registryClass.getMethod("markDegraded", String.class, Throwable.class);
        registryClass.getMethod("hasFailures");
        registryClass.getMethod("snapshot");

        String[] expectedStates = {
                "NEW", "INSTALLING", "INSTALLED", "SKIPPED", "DEGRADED", "FAILED"
        };
        FeatureHookInstallRegistry.State[] actualStates =
                FeatureHookInstallRegistry.State.values();
        assertEquals(expectedStates.length, actualStates.length, "public state count");
        for (int i = 0; i < expectedStates.length; i++) {
            assertEquals(expectedStates[i], actualStates[i].name(), "public state name " + i);
        }

        Class<FeatureHookInstallRegistry.Status> statusClass =
                FeatureHookInstallRegistry.Status.class;
        for (String field : new String[] {
                "id", "state", "attempts", "startedAtMillis", "completedAtMillis", "detail"
        }) {
            java.lang.reflect.Field reflected = statusClass.getField(field);
            assertTrue(java.lang.reflect.Modifier.isPublic(reflected.getModifiers()),
                    "Status." + field + " must remain a public Java field");
        }
        assertTrue(FeatureHookInstallRegistry.Installer.class.isInterface(),
                "Installer must remain a Java SAM interface");
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

    private static void testDegradedEntryCanBeRetried() throws Throwable {
        FeatureHookInstallRegistry registry = new FeatureHookInstallRegistry();
        AtomicInteger calls = new AtomicInteger();

        try {
            registry.install("attachment.partial", () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("partial hook installation");
            });
            throw new AssertionError("partial installation failure was not propagated");
        } catch (IllegalStateException expected) {
            assertEquals("partial hook installation", expected.getMessage(),
                    "partial installation failure");
        }
        registry.markDegraded("attachment.partial",
                new IllegalStateException("some attachment targets were unavailable"));
        assertState(registry, "attachment.partial",
                FeatureHookInstallRegistry.State.DEGRADED, 1);
        assertTrue(registry.hasFailures(), "degraded registry must remain unhealthy");

        registry.install("attachment.partial", calls::incrementAndGet);
        assertState(registry, "attachment.partial", FeatureHookInstallRegistry.State.INSTALLED, 2);
        assertEquals(2, calls.get(), "degraded feature should retry exactly once");
        assertFalse(registry.hasFailures(), "successful retry should clear degraded state");
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
