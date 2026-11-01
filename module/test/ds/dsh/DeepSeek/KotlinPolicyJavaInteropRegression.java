package ds.dsh.DeepSeek;

/**
 * Compile-time and runtime guard for Java/Xposed callers of policies migrated into the
 * Kotlin-only runtime JAR. This protects the existing static Java call sites during migration.
 */
public final class KotlinPolicyJavaInteropRegression {
    public static void main(String[] args) {
        require(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 15_000L, true),
                "Java caller could not use the Kotlin heartbeat policy");
        require(!LocalApiKeepAliveDecision.throttleSameDirection(
                        13_000L, 10_000L, true, true),
                "Java caller observed a changed launch-throttle boundary");
        require(NativeSettingsEntryPolicy.showFloating(true, true, false),
                "Java caller could not use the Kotlin settings fallback policy");
        require(!NativeSettingsEntryPolicy.showFloating(true, true, true),
                "Java caller observed a changed native-entry visibility decision");
        System.out.println("Kotlin policy Java interop regression passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
