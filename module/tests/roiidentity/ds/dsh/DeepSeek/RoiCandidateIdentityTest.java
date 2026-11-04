package ds.dsh.DeepSeek;

public final class RoiCandidateIdentityTest {
    private RoiCandidateIdentityTest() {}

    public static void main(String[] args) {
        testClassLoaderIdentity();
        testNullLoaderIdentity();
        testApkPathCanonicalization();
        testCandidateIdComposition();
        System.out.println("ROI candidate identity tests passed");
    }

    private static void testClassLoaderIdentity() {
        ClassLoader first = new ClassLoader(null) {};
        ClassLoader second = new ClassLoader(null) {};

        String firstId = RoiCandidateIdentity.forClassLoader(first);
        equal(firstId, RoiCandidateIdentity.forClassLoader(first),
                "the same live ClassLoader must retain its source identity");
        check(!firstId.equals(RoiCandidateIdentity.forClassLoader(second)),
                "distinct ClassLoader objects with the same implementation class must not collide");
        check(firstId.startsWith("loader:process-"),
                "ClassLoader identity must be explicitly process-local");
    }

    private static void testNullLoaderIdentity() {
        equal("loader:none", RoiCandidateIdentity.forClassLoader(null),
                "null ClassLoader must have an explicit unresolved scope");
    }

    private static void testApkPathCanonicalization() {
        String canonical = RoiCandidateIdentity.forApkPath("/tmp/roi-identity/../sample.apk");
        String normalized = RoiCandidateIdentity.forApkPath("/tmp/sample.apk");
        equal(canonical, normalized, "equivalent APK path aliases must canonicalize identically");
        check(!canonical.equals(RoiCandidateIdentity.forApkPath("/tmp/other.apk")),
                "different APK paths must remain distinct");
        equal("apk:unknown", RoiCandidateIdentity.forApkPath("  "),
                "blank APK path must remain explicitly unknown");
    }

    private static void testCandidateIdComposition() {
        String scope = "loader:process-4:test.Loader";
        String base = RoiCandidateIdentity.candidateId(scope, "Lx/Y;", "call", "()V");
        equal(scope + "|Lx/Y;->call()V", base,
                "candidate ID must preserve scope and complete method signature");
        check(!base.equals(RoiCandidateIdentity.candidateId(scope, "Lx/Y;", "call", "(I)V")),
                "different descriptors must produce different candidate IDs");
        check(!base.equals(RoiCandidateIdentity.candidateId("loader:process-5:test.Loader",
                "Lx/Y;", "call", "()V")),
                "the same method signature in another loader scope must remain distinct");
    }

    private static void equal(String expected, String actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
