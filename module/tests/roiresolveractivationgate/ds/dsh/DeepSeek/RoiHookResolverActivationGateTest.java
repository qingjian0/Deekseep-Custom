package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;

public final class RoiHookResolverActivationGateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static RoiHookResolver.Candidate candidate(
            String fingerprint, String role, String owner, String method, String descriptor,
            String sourceScope) {
        RoiHookResolver.Evidence[] evidence = new RoiHookResolver.Evidence[] {
                evidence(RoiHookResolver.EvidenceKind.HOST_IDENTITY),
                evidence(RoiHookResolver.EvidenceKind.DEX_FINGERPRINT),
                evidence(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER),
                evidence(RoiHookResolver.EvidenceKind.METHOD_SHAPE),
                evidence(RoiHookResolver.EvidenceKind.CALL_GRAPH),
                evidence(RoiHookResolver.EvidenceKind.RUNTIME_INVOCATION),
                evidence(RoiHookResolver.EvidenceKind.RETURN_CONTRACT),
                evidence(RoiHookResolver.EvidenceKind.LIFECYCLE_CONTRACT)
        };
        return new RoiHookResolver.Candidate(fingerprint, role, owner, method, descriptor,
                sourceScope, RoiCandidateIdentity.candidateId(sourceScope, owner, method, descriptor),
                false, true, Collections.singleton(fingerprint), Arrays.asList(evidence));
    }

    private static RoiHookResolver.Evidence evidence(RoiHookResolver.EvidenceKind kind) {
        return new RoiHookResolver.Evidence(kind, 1.0, true, "test-fixture");
    }

    private static RoiUniversalHookRuntime.Spec spec(String id, String fingerprint, String role,
            String owner, String method, String result, String... params) {
        return RoiUniversalHookRuntime.Spec.builder(id).fingerprint(fingerprint).role(role)
                .className(owner).method(method).returns(result).params(params)
                .callback(new RoiUniversalHookRuntime.Callback() {
                    @Override public void before(RoiUniversalHookRuntime.Invocation call) {}
                    @Override public void after(RoiUniversalHookRuntime.Invocation call) {}
                }).build();
    }

    private static RoiHostBuildIdentity identity(ClassLoader loader, char artifact, char signer,
            String versionName, String channel) {
        String artifactDigest = repeat(artifact);
        RoiHostArtifactFingerprint.Result result = new RoiHostArtifactFingerprint.Result(
                true, repeat('a'), artifactDigest, 2, 2048L, "none");
        return RoiHostBuildIdentity.create("com.deepseek.chat", 42L, versionName,
                channel, result, repeat(signer), RoiCandidateIdentity.forClassLoader(loader));
    }

    private static String repeat(char c) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 64; i++) out.append(c);
        return out.toString();
    }

    private static RoiHookResolverActivationGate gate(
            RoiHookResolver.Candidate candidate, ClassLoader loader,
            RoiHostBuildIdentity reviewed, RoiHostBuildIdentity current,
            RoiHookResolver.Policy policy) {
        return new RoiHookResolverActivationGate(
                ignored -> candidate,
                ignored -> policy,
                ignored -> reviewed,
                () -> current);
    }

    public static void main(String[] args) {
        final ClassLoader exactLoader = RoiHookResolverActivationGateTest.class.getClassLoader();
        final String exactScope = RoiCandidateIdentity.forClassLoader(exactLoader);
        final RoiHostBuildIdentity reviewedIdentity = identity(exactLoader, 'b', 'c', "2.7.2", "mainland");
        final String activationFingerprint = reviewedIdentity.activationFingerprint();
        final RoiHookResolver.Candidate approved = candidate(activationFingerprint, "request",
                "com.deepseek.Request", "build",
                "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;",
                exactScope);
        final RoiHookResolver.Policy policy = new RoiHookResolver.Policy(true,
                Collections.singleton("request"), 0.45, 0.82);
        RoiHookResolverActivationGate gate = gate(
                approved, exactLoader, reviewedIdentity, reviewedIdentity, policy);

        RoiUniversalHookRuntime.Spec exact = spec("request.build", activationFingerprint, "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int");
        check(gate.authorized(exact, exactLoader),
                "fully corroborated exact host build and classloader should authorize");

        ClassLoader otherLoader = new ClassLoader(exactLoader) {};
        check(!gate.authorized(exact, otherLoader),
                "evidence from another classloader must fail closed");

        check(!gate(approved, exactLoader, reviewedIdentity,
                identity(exactLoader, 'd', 'c', "2.7.2", "mainland"), policy).authorized(exact, exactLoader),
                "APK set digest mismatch must fail closed");
        check(!gate(approved, exactLoader, reviewedIdentity,
                identity(exactLoader, 'b', 'd', "2.7.2", "mainland"), policy).authorized(exact, exactLoader),
                "signing certificate digest mismatch must fail closed");
        check(!gate(approved, exactLoader, reviewedIdentity,
                identity(exactLoader, 'b', 'c', "2.7.3", "mainland"), policy).authorized(exact, exactLoader),
                "version name mismatch must fail closed");
        RoiHostArtifactFingerprint.Result sameArtifacts = new RoiHostArtifactFingerprint.Result(
                true, repeat('a'), repeat('b'), 2, 2048L, "none");
        RoiHostBuildIdentity wrongVersionCode = RoiHostBuildIdentity.create(
                "com.deepseek.chat", 43L, "2.7.2", "mainland",
                sameArtifacts, repeat('c'), exactScope);
        check(!gate(approved, exactLoader, reviewedIdentity, wrongVersionCode, policy)
                        .authorized(exact, exactLoader),
                "version code mismatch must fail closed");
        RoiHostBuildIdentity wrongPackage = RoiHostBuildIdentity.create(
                "com.example.fake", 42L, "2.7.2", "mainland",
                sameArtifacts, repeat('c'), exactScope);
        check(!wrongPackage.isComplete(),
                "a package identity other than the exact DeepSeek host must be incomplete");
        check(!gate(approved, exactLoader, reviewedIdentity,
                identity(exactLoader, 'b', 'c', "2.7.2", "global"), policy).authorized(exact, exactLoader),
                "channel mismatch must fail closed");

        RoiHookResolver.Candidate forgedId = new RoiHookResolver.Candidate(
                approved.hostFingerprint, approved.role, approved.className, approved.methodName,
                approved.descriptor, approved.sourceScope, "forged-candidate-id",
                false, true, Collections.singleton(approved.hostFingerprint), approved.evidence.values());
        check(!gate(forgedId, exactLoader, reviewedIdentity, reviewedIdentity, policy)
                        .authorized(exact, exactLoader),
                "candidate identity key must be derived from its exact scope and descriptor");

        check(!gate(approved, exactLoader, reviewedIdentity, reviewedIdentity, policy).authorized(
                spec("wrong-fingerprint", "fp-other", "request", "com.deepseek.Request", "build",
                        "java.lang.Object", "java.lang.String", "int"), exactLoader),
                "fingerprint mismatch must fail closed");
        RoiHookResolver.Candidate legacyLabelCandidate = candidate("code289-cn", "request",
                "com.deepseek.Request", "build",
                "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;",
                exactScope);
        check(!gate(legacyLabelCandidate, exactLoader, reviewedIdentity,
                reviewedIdentity, policy).authorized(exact, exactLoader),
                "human-readable adapter labels must not masquerade as installed-build fingerprints");
        check(!gate(approved, exactLoader, reviewedIdentity, reviewedIdentity, policy).authorized(
                spec("wrong-role", activationFingerprint, "response", "com.deepseek.Request", "build",
                        "java.lang.Object", "java.lang.String", "int"), exactLoader),
                "role mismatch must fail closed");
        check(!gate(approved, exactLoader, reviewedIdentity, reviewedIdentity, policy).authorized(
                spec("wrong-class", activationFingerprint, "request", "com.deepseek.Other", "build",
                        "java.lang.Object", "java.lang.String", "int"), exactLoader),
                "class mismatch must fail closed");
        check(!gate(approved, exactLoader, reviewedIdentity, reviewedIdentity, policy).authorized(
                spec("wrong-params", activationFingerprint, "request", "com.deepseek.Request", "build",
                        "java.lang.Object", "long", "int"), exactLoader),
                "parameter descriptor mismatch must fail closed");
        check(!gate(approved, exactLoader, reviewedIdentity, reviewedIdentity, policy).authorized(
                spec("wrong-return", activationFingerprint, "request", "com.deepseek.Request", "build",
                        "java.lang.String", "java.lang.String", "int"), exactLoader),
                "return descriptor mismatch must fail closed");

        RoiHookResolverActivationGate missing = new RoiHookResolverActivationGate(
                ignored -> null, ignored -> policy, ignored -> null, () -> reviewedIdentity);
        check(!missing.authorized(exact, exactLoader), "missing evidence must fail closed");

        RoiHookResolverActivationGate disabled = gate(approved, exactLoader,
                reviewedIdentity, reviewedIdentity, RoiHookResolver.Policy.discoveryOnly());
        check(!disabled.authorized(exact, exactLoader), "discovery-only policy must not activate");

        System.out.println("ROI resolver activation gate tests passed");
    }
}
