package ds.dsh.DeepSeek;

import java.util.concurrent.atomic.AtomicInteger;

/** Regression coverage for the shared Kotlin feature-hook installation registry. */
public final class FeatureHookInstallRegistryRegressionTest {
    public static void main(String[] args) throws Throwable {
        retriesFailedInstallWithoutRepeatingSuccessfulInstall();
        retriesDegradedBestEffortInstall();
        separatesRequiredFailuresFromBestEffortDegradation();
        keepsSkippedInstallTerminal();
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
