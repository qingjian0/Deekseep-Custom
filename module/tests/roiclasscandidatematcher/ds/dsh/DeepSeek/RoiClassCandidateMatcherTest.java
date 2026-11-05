package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;

public final class RoiClassCandidateMatcherTest {
    private static final class RuntimeFixture {
        private String send(String value) { return value; }
    }

    public static void main(String[] args) {
        testRuntimeCandidateCorrelation();
        String scope = "loader:process-7:dalvik.system.PathClassLoader";
        String desc = "Lx/y;->send(Ljava/lang/String;)Lokhttp3/Call;";
        RoiClassCandidateMatcher.Candidate candidate =
                new RoiClassCandidateMatcher.Candidate(scope, "x.y", "send",
                        "(Ljava/lang/String;)Lokhttp3/Call;");
        RoiClassCandidateMatcher.LoadedClass exact =
                new RoiClassCandidateMatcher.LoadedClass(scope, "x.y",
                        Collections.singletonList(desc), false, "metadata_only");
        equal(RoiClassCandidateMatcher.Status.EXACT_MATCH,
                RoiClassCandidateMatcher.match(candidate, exact).status, "exact");

        RoiClassCandidateMatcher.LoadedClass structural =
                new RoiClassCandidateMatcher.LoadedClass(scope, "a.b",
                        Collections.singletonList(desc), false, "metadata_only");
        equal(RoiClassCandidateMatcher.Status.STRUCTURAL_MATCH,
                RoiClassCandidateMatcher.match(candidate, structural).status, "structural");

        RoiClassCandidateMatcher.LoadedClass partial =
                new RoiClassCandidateMatcher.LoadedClass(scope, "x.y",
                        Collections.<String>emptyList(), true, "metadata_only");
        equal(RoiClassCandidateMatcher.Status.PARTIAL_MATCH,
                RoiClassCandidateMatcher.match(candidate, partial).status, "partial");

        RoiClassCandidateMatcher.LoadedClass conflict =
                new RoiClassCandidateMatcher.LoadedClass(
                        "loader:process-8:dalvik.system.PathClassLoader", "x.y",
                        Collections.singletonList(desc), false, "metadata_only");
        equal(RoiClassCandidateMatcher.Status.CONFLICT,
                RoiClassCandidateMatcher.match(candidate, conflict).status, "loader conflict");

        RoiClassCandidateMatcher.LoadedClass unmatched =
                new RoiClassCandidateMatcher.LoadedClass(scope, "a.b",
                        Arrays.asList("La/b;->other()V"), false, "metadata_only");
        equal(RoiClassCandidateMatcher.Status.UNMATCHED,
                RoiClassCandidateMatcher.match(candidate, unmatched).status, "unmatched");

        check(!RoiClassCandidateMatcher.match(candidate, exact).activationAuthorized,
                "matching must never authorize activation");
        System.out.println("ROI class candidate matcher tests passed");
    }

    private static void testRuntimeCandidateCorrelation() {
        String scope = RoiCandidateIdentity.forClassLoader(RuntimeFixture.class.getClassLoader());
        RoiClassCandidateMatcher.registerCandidate(new RoiClassCandidateMatcher.Candidate(
                scope, RuntimeFixture.class.getName(), "send",
                "(Ljava/lang/String;)Ljava/lang/String;"));
        String report = RoiClassCandidateMatcher.matchObservedClass(RuntimeFixture.class);
        check(report.contains("status=EXACT_MATCH"),
                "DexKit candidate must correlate with canonical loaded method descriptor");
        check(report.contains("activationAuthorized=false"),
                "runtime class correlation must never authorize activation");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static void equal(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
