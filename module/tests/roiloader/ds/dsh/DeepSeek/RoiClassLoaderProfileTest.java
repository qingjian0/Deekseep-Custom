package ds.dsh.DeepSeek;

public final class RoiClassLoaderProfileTest {
    private RoiClassLoaderProfileTest() {}

    public static void main(String[] args) {
        testKnownLoaderKinds();
        testCustomLoaderIsNotEncryptionProof();
        testLiveChainIsBoundedAndImmutable();
        testBootstrap();
        System.out.println("ROI class loader profile tests passed");
    }

    private static void testKnownLoaderKinds() {
        equal(RoiClassLoaderProfile.Kind.PATH_DEX,
                RoiClassLoaderProfile.classifyName("dalvik.system.PathClassLoader"),
                "PathClassLoader classification");
        equal(RoiClassLoaderProfile.Kind.DEX,
                RoiClassLoaderProfile.classifyName("dalvik.system.DexClassLoader"),
                "DexClassLoader classification");
        equal(RoiClassLoaderProfile.Kind.IN_MEMORY_DEX,
                RoiClassLoaderProfile.classifyName("dalvik.system.InMemoryDexClassLoader"),
                "InMemoryDexClassLoader classification");
        equal(RoiClassLoaderProfile.Kind.DELEGATE_LAST,
                RoiClassLoaderProfile.classifyName("dalvik.system.DelegateLastClassLoader"),
                "DelegateLastClassLoader classification");
    }

    private static void testCustomLoaderIsNotEncryptionProof() {
        RoiClassLoaderProfile profile = RoiClassLoaderProfile.inspect(
                new ClassLoader(ClassLoader.getSystemClassLoader()) {});
        check(profile.customLoaderObserved, "custom loader must be reported");
        check(!profile.dynamicDexLoaderObserved,
                "custom loader must not be mislabeled as a known DEX loader");
        equal("unconfirmed_custom_loader_requires_runtime_dex_evidence",
                profile.encryptedDexAssessment,
                "custom loader is only an unconfirmed protection signal");
    }

    private static void testLiveChainIsBoundedAndImmutable() {
        RoiClassLoaderProfile profile = RoiClassLoaderProfile.inspect(
                RoiClassLoaderProfileTest.class.getClassLoader());
        check(!profile.chainClassNames.isEmpty(), "live loader chain should be inventoried");
        check(profile.chainClassNames.size() <= RoiClassLoaderProfile.MAX_CHAIN_DEPTH + 1,
                "loader chain must be bounded");
        try {
            profile.chainClassNames.add("mutation");
            throw new AssertionError("loader chain must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void testBootstrap() {
        RoiClassLoaderProfile profile = RoiClassLoaderProfile.inspect(null);
        equal(RoiClassLoaderProfile.Kind.BOOTSTRAP, profile.kinds.get(0),
                "null loader must be represented as bootstrap");
        equal("not_observable_from_loader_type", profile.encryptedDexAssessment,
                "bootstrap loader provides no encryption evidence");
    }

    private static void equal(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
