package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;

public final class RoiReviewedHookEvidenceRegistryTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static RoiUniversalHookRuntime.Spec spec(String id, String fingerprint,
            String role, String owner, String method, String result, String... params) {
        return RoiUniversalHookRuntime.Spec.builder(id).fingerprint(fingerprint).role(role)
                .className(owner).method(method).returns(result).params(params)
                .callback(new RoiUniversalHookRuntime.Callback() {
                    @Override public void before(RoiUniversalHookRuntime.Invocation call) {}
                    @Override public void after(RoiUniversalHookRuntime.Invocation call) {}
                }).build();
    }

    private static RoiHookResolver.Candidate candidate(
            ClassLoader loader, String fingerprint, String role, String owner,
            String method, String descriptor) {
        String scope = RoiCandidateIdentity.forClassLoader(loader);
        RoiHookResolver.Evidence[] evidence = new RoiHookResolver.Evidence[8];
        int i = 0;
        for (RoiHookResolver.EvidenceKind kind : RoiHookResolver.EvidenceKind.values()) {
            evidence[i++] = new RoiHookResolver.Evidence(kind, 1.0, true, "reviewed-test-fixture");
        }
        return new RoiHookResolver.Candidate(fingerprint, role, owner, method, descriptor,
                scope, RoiCandidateIdentity.candidateId(scope, owner, method, descriptor),
                false, true, Collections.singleton(fingerprint), Arrays.asList(evidence));
    }

    private static RoiHostBuildIdentity identity(ClassLoader loader, String artifact, String signer) {
        RoiHostArtifactFingerprint.Result result = new RoiHostArtifactFingerprint.Result(
                true, repeat('a'), artifact, 2, 2048L, "none");
        return RoiHostBuildIdentity.create("com.deepseek.chat", 2890207L, "2.7.2",
                "cn", result, signer, RoiCandidateIdentity.forClassLoader(loader));
    }

    private static RoiHookResolver.Policy policy(String role) {
        return new RoiHookResolver.Policy(true, Collections.singleton(role), 0.45, 0.82);
    }

    private static String repeat(char c) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 64; i++) out.append(c);
        return out.toString();
    }

    public static void main(String[] args) {
        ClassLoader loader = RoiReviewedHookEvidenceRegistryTest.class.getClassLoader();
        RoiUniversalHookRuntime.Spec exact = spec("request.build", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int");
        RoiHookResolver.Candidate approved = candidate(loader, "fp-289", "request",
                "com.deepseek.Request", "build",
                "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;");
        RoiHostBuildIdentity host = identity(loader, repeat('b'), repeat('c'));

        RoiReviewedHookEvidenceRegistry empty = RoiReviewedHookEvidenceRegistry.builder().build();
        check(empty.size() == 0 && empty.find(exact) == null && empty.policy(exact) == null
                        && empty.reviewedIdentity(exact) == null,
                "empty catalog must deny every HookSpec");

        RoiReviewedHookEvidenceRegistry catalog = RoiReviewedHookEvidenceRegistry.builder()
                .addReviewed(exact, approved, policy("request"), host)
                .build();
        check(catalog.size() == 1, "one reviewed record should be stored");
        check(catalog.find(exact) == approved && catalog.policy(exact) != null
                        && catalog.reviewedIdentity(exact) == host,
                "exact HookSpec should retrieve evidence, policy and host identity");

        RoiHookResolverActivationGate gate = new RoiHookResolverActivationGate(
                catalog, catalog, catalog,
                new RoiHookResolverActivationGate.CurrentIdentitySource() {
                    @Override public RoiHostBuildIdentity currentIdentity() { return host; }
                });
        check(gate.authorized(exact, loader), "reviewed exact host identity should authorize");

        RoiHostBuildIdentity wrongArtifact = identity(loader, repeat('d'), repeat('c'));
        RoiHookResolverActivationGate mismatched = new RoiHookResolverActivationGate(
                catalog, catalog, catalog,
                new RoiHookResolverActivationGate.CurrentIdentitySource() {
                    @Override public RoiHostBuildIdentity currentIdentity() { return wrongArtifact; }
                });
        check(!mismatched.authorized(exact, loader),
                "different installed APK-set digest must fail closed");

        RoiUniversalHookRuntime.Spec wrongId = spec("request.other", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int");
        check(catalog.find(wrongId) == null && catalog.policy(wrongId) == null
                        && catalog.reviewedIdentity(wrongId) == null,
                "catalog lookup must be bound to stable HookSpec ID");

        boolean rejectedDuplicate = false;
        try {
            RoiReviewedHookEvidenceRegistry.builder()
                    .addReviewed(exact, approved, policy("request"), host)
                    .addReviewed(exact, approved, policy("request"), host);
        } catch (IllegalArgumentException expected) { rejectedDuplicate = true; }
        check(rejectedDuplicate, "duplicate reviewed records must be rejected");

        boolean rejectedDiscoveryOnly = false;
        try {
            RoiReviewedHookEvidenceRegistry.builder()
                    .addReviewed(exact, approved, RoiHookResolver.Policy.discoveryOnly(), host);
        } catch (IllegalArgumentException expected) { rejectedDiscoveryOnly = true; }
        check(rejectedDiscoveryOnly, "discovery-only policy must never enter activation catalog");

        boolean rejectedWrongCandidate = false;
        try {
            RoiHookResolver.Candidate wrong = candidate(loader, "fp-other", "request",
                    "com.deepseek.Request", "build",
                    "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;");
            RoiReviewedHookEvidenceRegistry.builder().addReviewed(exact, wrong, policy("request"), host);
        } catch (IllegalArgumentException expected) { rejectedWrongCandidate = true; }
        check(rejectedWrongCandidate, "candidate mismatch must be rejected before catalog build");

        boolean rejectedWrongScope = false;
        try {
            RoiHostBuildIdentity wrongScope = identity(new ClassLoader(loader) {},
                    repeat('b'), repeat('c'));
            RoiReviewedHookEvidenceRegistry.builder()
                    .addReviewed(exact, approved, policy("request"), wrongScope);
        } catch (IllegalArgumentException expected) { rejectedWrongScope = true; }
        check(rejectedWrongScope, "host identity ClassLoader scope must match reviewed candidate");

        boolean rejectedIncomplete = false;
        try {
            RoiHostBuildIdentity incomplete = RoiHostBuildIdentity.create(
                    "com.deepseek.chat", 2890207L, "2.7.2", "cn",
                    new RoiHostArtifactFingerprint.Result(false, "", "", 0, 0L, "missing"),
                    repeat('c'), RoiCandidateIdentity.forClassLoader(loader));
            RoiReviewedHookEvidenceRegistry.builder()
                    .addReviewed(exact, approved, policy("request"), incomplete);
        } catch (IllegalArgumentException expected) { rejectedIncomplete = true; }
        check(rejectedIncomplete, "incomplete host identity must never be reviewed");

        boolean rejectedAfterBuild = false;
        RoiReviewedHookEvidenceRegistry.Builder builder = RoiReviewedHookEvidenceRegistry.builder();
        builder.build();
        try {
            builder.addReviewed(exact, approved, policy("request"), host);
        } catch (IllegalStateException expected) { rejectedAfterBuild = true; }
        check(rejectedAfterBuild, "built catalog must be immutable");

        System.out.println("ROI reviewed evidence registry tests passed");
    }
}
