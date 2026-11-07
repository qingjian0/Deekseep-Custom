package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

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

    private static RoiHookResolver.Policy policy(String role) {
        return new RoiHookResolver.Policy(true, Collections.singleton(role), 0.45, 0.82);
    }

    public static void main(String[] args) {
        ClassLoader loader = RoiReviewedHookEvidenceRegistryTest.class.getClassLoader();
        RoiUniversalHookRuntime.Spec exact = spec("request.build", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int");
        RoiHookResolver.Candidate approved = candidate(loader, "fp-289", "request",
                "com.deepseek.Request", "build",
                "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;");

        RoiReviewedHookEvidenceRegistry empty = RoiReviewedHookEvidenceRegistry.builder().build();
        check(empty.size() == 0 && empty.find(exact) == null && empty.policy(exact) == null,
                "empty catalog must deny every HookSpec");

        RoiReviewedHookEvidenceRegistry catalog = RoiReviewedHookEvidenceRegistry.builder()
                .addReviewed(exact, approved, policy("request"))
                .build();
        check(catalog.size() == 1, "one reviewed record should be stored");
        check(catalog.find(exact) == approved && catalog.policy(exact) != null,
                "exact HookSpec should retrieve its reviewed record");

        RoiHookResolverActivationGate gate =
                new RoiHookResolverActivationGate(catalog, catalog);
        check(gate.authorized(exact, loader), "reviewed exact record should authorize");

        RoiUniversalHookRuntime.Spec wrongId = spec("request.other", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int");
        check(catalog.find(wrongId) == null && catalog.policy(wrongId) == null,
                "catalog lookup must be bound to stable HookSpec ID");

        boolean rejectedDuplicate = false;
        try {
            RoiReviewedHookEvidenceRegistry.builder()
                    .addReviewed(exact, approved, policy("request"))
                    .addReviewed(exact, approved, policy("request"));
        } catch (IllegalArgumentException expected) {
            rejectedDuplicate = true;
        }
        check(rejectedDuplicate, "duplicate reviewed records must be rejected");

        boolean rejectedDiscoveryOnly = false;
        try {
            RoiReviewedHookEvidenceRegistry.builder()
                    .addReviewed(exact, approved, RoiHookResolver.Policy.discoveryOnly());
        } catch (IllegalArgumentException expected) {
            rejectedDiscoveryOnly = true;
        }
        check(rejectedDiscoveryOnly, "discovery-only policy must never enter activation catalog");

        boolean rejectedWrongCandidate = false;
        try {
            RoiHookResolver.Candidate wrong = candidate(loader, "fp-other", "request",
                    "com.deepseek.Request", "build",
                    "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;");
            RoiReviewedHookEvidenceRegistry.builder().addReviewed(exact, wrong, policy("request"));
        } catch (IllegalArgumentException expected) {
            rejectedWrongCandidate = true;
        }
        check(rejectedWrongCandidate, "candidate mismatch must be rejected before catalog build");

        boolean rejectedAfterBuild = false;
        RoiReviewedHookEvidenceRegistry.Builder builder =
                RoiReviewedHookEvidenceRegistry.builder();
        builder.build();
        try {
            builder.addReviewed(exact, approved, policy("request"));
        } catch (IllegalStateException expected) {
            rejectedAfterBuild = true;
        }
        check(rejectedAfterBuild, "built catalog must be immutable");

        System.out.println("ROI reviewed evidence registry tests passed");
    }
}
