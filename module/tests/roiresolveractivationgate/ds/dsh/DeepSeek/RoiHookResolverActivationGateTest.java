package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

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
                sourceScope, "test-candidate", false, true,
                Collections.singleton(fingerprint), Arrays.asList(evidence));
    }

    private static RoiHookResolver.Evidence evidence(RoiHookResolver.EvidenceKind kind) {
        return new RoiHookResolver.Evidence(kind, 1.0, true, "test-fixture");
    }

    private static RoiUniversalHookRuntime.Spec spec(String id, String fingerprint, String role,
                                                       String owner, String method, String result,
                                                       String... params) {
        return RoiUniversalHookRuntime.Spec.builder(id).fingerprint(fingerprint).role(role)
                .className(owner).method(method).returns(result).params(params)
                .callback(new RoiUniversalHookRuntime.Callback() {
                    @Override public void before(RoiUniversalHookRuntime.Invocation call) {}
                    @Override public void after(RoiUniversalHookRuntime.Invocation call) {}
                }).build();
    }

    public static void main(String[] args) {
        final ClassLoader exactLoader = RoiHookResolverActivationGateTest.class.getClassLoader();
        final String exactScope = RoiCandidateIdentity.forClassLoader(exactLoader);
        final RoiHookResolver.Candidate approved = candidate("fp-289", "request",
                "com.deepseek.Request", "build",
                "Lcom/deepseek/Request;->build(Ljava/lang/String;I)Ljava/lang/Object;",
                exactScope);
        RoiHookResolverActivationGate gate = new RoiHookResolverActivationGate(
                ignored -> approved,
                ignored -> new RoiHookResolver.Policy(true,
                        new HashSet<String>(Collections.singleton("request")), 0.45, 0.82));

        RoiUniversalHookRuntime.Spec exact = spec("request.build", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int");
        check(gate.authorized(exact, exactLoader),
                "fully corroborated exact identity and classloader should authorize");
        ClassLoader otherLoader = new ClassLoader(exactLoader) {};
        check(!gate.authorized(exact, otherLoader),
                "evidence from another classloader must fail closed");

        check(!gate.authorized(spec("wrong-fingerprint", "fp-other", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int"), exactLoader),
                "fingerprint mismatch must fail closed");
        check(!gate.authorized(spec("wrong-role", "fp-289", "response",
                "com.deepseek.Request", "build", "java.lang.Object", "java.lang.String", "int"), exactLoader),
                "role mismatch must fail closed");
        check(!gate.authorized(spec("wrong-class", "fp-289", "request",
                "com.deepseek.Other", "build", "java.lang.Object", "java.lang.String", "int"), exactLoader),
                "class mismatch must fail closed");
        check(!gate.authorized(spec("wrong-params", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.Object", "long", "int"), exactLoader),
                "parameter descriptor mismatch must fail closed");
        check(!gate.authorized(spec("wrong-return", "fp-289", "request",
                "com.deepseek.Request", "build", "java.lang.String", "java.lang.String", "int"), exactLoader),
                "return descriptor mismatch must fail closed");

        RoiHookResolverActivationGate missing = new RoiHookResolverActivationGate(
                ignored -> null, ignored -> new RoiHookResolver.Policy(true,
                Collections.singleton("request"), 0.45, 0.82));
        check(!missing.authorized(exact, exactLoader), "missing evidence must fail closed");

        RoiHookResolverActivationGate disabled = new RoiHookResolverActivationGate(
                ignored -> approved, ignored -> RoiHookResolver.Policy.discoveryOnly());
        check(!disabled.authorized(exact, exactLoader), "discovery-only policy must not activate");

        System.out.println("ROI resolver activation gate tests passed");
    }
}
