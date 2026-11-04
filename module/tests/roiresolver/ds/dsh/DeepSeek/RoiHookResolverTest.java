package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Executable tests for resolver scoring, evidence gates, and fail-closed activation policy. */
public final class RoiHookResolverTest {
    private RoiHookResolverTest() {}

    public static void main(String[] args) {
        RoiHookResolver.Candidate scoped = new RoiHookResolver.Candidate(
                "fp-289", "unresolved", "a1", "b", "(X)Y",
                "loader:process-7:test.Loader", "loader:process-7:test.Loader|a1->b(X)Y",
                false, false, Collections.<String>emptySet(),
                Collections.<RoiHookResolver.Evidence>emptyList());
        check("loader:process-7:test.Loader".equals(scoped.sourceScope),
                "resolver candidate must retain source scope");
        check("loader:process-7:test.Loader|a1->b(X)Y".equals(scoped.candidateId),
                "resolver candidate must retain candidate ID");

        RoiHookResolver.Candidate weak = candidate(false, false, false);
        RoiHookResolver.Decision discovered =
                RoiHookResolver.resolve(weak, RoiHookResolver.Policy.discoveryOnly());
        check(discovered.state == RoiHookResolver.State.DISCOVERED, "weak candidate must remain discovered");
        check(!discovered.activationAuthorized, "discovery must never authorize activation");

        RoiHookResolver.Candidate incomplete = new RoiHookResolver.Candidate(
                "fp-289", "request", "a1", "b", "(X)Y", false, true,
                Collections.singleton("fp-289"),
                Arrays.asList(e(RoiHookResolver.EvidenceKind.HOST_IDENTITY, 1, true),
                        e(RoiHookResolver.EvidenceKind.DEX_FINGERPRINT, 1, true),
                        e(RoiHookResolver.EvidenceKind.METHOD_SHAPE, 1, true),
                        e(RoiHookResolver.EvidenceKind.CALL_GRAPH, 1, false)));
        RoiHookResolver.Decision correlated =
                RoiHookResolver.resolve(incomplete, autoPolicy());
        check(correlated.state == RoiHookResolver.State.CORRELATED, "unconfirmed call graph must not verify");
        check(!correlated.activationAuthorized, "incomplete evidence must not authorize activation");

        RoiHookResolver.Candidate conflict = candidate(true, true, true);
        RoiHookResolver.Decision rejected =
                RoiHookResolver.resolve(conflict, autoPolicy());
        check(rejected.state == RoiHookResolver.State.REJECTED, "conflicting evidence must reject candidate");

        RoiHookResolver.Candidate verifiedButNotApproved = candidate(false, false, true);
        RoiHookResolver.Decision held =
                RoiHookResolver.resolve(verifiedButNotApproved, autoPolicy());
        check(held.state == RoiHookResolver.State.VERIFIED, "complete evidence should reach verified state");
        check(!held.activationAuthorized, "unapproved adapter must not authorize activation");

        RoiHookResolver.Candidate authorized = candidate(false, true, true);
        RoiHookResolver.Decision allowed =
                RoiHookResolver.resolve(authorized, autoPolicy());
        check(allowed.state == RoiHookResolver.State.VERIFIED, "authorization must not forge ACTIVE state");
        check(allowed.activationAuthorized, "approved matching adapter may be handed to installer");

        RoiHookResolver.Decision deniedByDefault =
                RoiHookResolver.resolve(authorized, RoiHookResolver.Policy.discoveryOnly());
        check(!deniedByDefault.activationAuthorized, "default policy must remain discovery-only");

        RoiHookResolver.Candidate wrongFingerprint = new RoiHookResolver.Candidate(
                authorized.hostFingerprint, authorized.role, authorized.className, authorized.methodName,
                authorized.descriptor, false, true, Collections.singleton("other-fingerprint"),
                authorized.evidence.values());
        check(!RoiHookResolver.resolve(wrongFingerprint, autoPolicy()).activationAuthorized,
                "adapter approval for another host fingerprint must not transfer");

        RoiHookResolver.Candidate roleMismatch = new RoiHookResolver.Candidate(
                authorized.hostFingerprint, "response", authorized.className, authorized.methodName,
                authorized.descriptor, false, true, Collections.singleton("fp-289"),
                authorized.evidence.values());
        check(!RoiHookResolver.resolve(roleMismatch, autoPolicy()).activationAuthorized,
                "adapter authorization must be scoped to its semantic role");

        RoiHookResolver.Candidate duplicateEvidence = new RoiHookResolver.Candidate(
                "fp-289", "request", "a1", "b", "(X)Y", false, false,
                Collections.<String>emptySet(),
                Arrays.asList(e(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER, 0.4, false),
                        e(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER, 0.4, true),
                        e(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER, 0.4, true)));
        check(duplicateEvidence.evidence.size() == 1,
                "repeated observations in one evidence family must not inflate evidence count");
        check(duplicateEvidence.evidence.get(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER).confirmed,
                "equal-confidence confirmed evidence must win deterministic deduplication");

        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                e(RoiHookResolver.EvidenceKind.HOST_IDENTITY, 1.01, true);
            }
        }, "out-of-range evidence confidence must be rejected");
        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                new RoiHookResolver.Policy(true, Collections.singleton("request"), 0.9, 0.8);
            }
        }, "verified threshold below correlated threshold must be rejected");
        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                new RoiHookResolver.Policy(true, Collections.singleton("request"), Double.NaN, 0.9);
            }
        }, "NaN thresholds must be rejected");

        testMethodShapeParsing();

        System.out.println("RoiHookResolver executable tests passed");
    }

    private static void testMethodShapeParsing() {
        RoiMethodShape shape = RoiMethodShape.parse(
                "Lcom/example/Request;->build(Ljava/lang/String;[I)Ljava/lang/Object;");
        check(shape != null, "valid method descriptor must parse");
        check(shape.parameterCount == 2, "parameter arity must be counted");
        check("Ljava/lang/Object;".equals(shape.returnDescriptor), "return descriptor must be preserved");
        check("build".equals(shape.methodName), "method name must be extracted");

        RoiMethodShape noArgs = RoiMethodShape.parse("Lx/Y;->create()V");
        check(noArgs != null && noArgs.parameterCount == 0, "zero-argument void descriptor must parse");
        check(RoiMethodShape.parse("Lx/Y;->bad(V)V") == null, "void must not be accepted as a parameter");
        check(RoiMethodShape.parse("Lx/Y;->bad(Ljava/lang/String;)") == null,
                "missing return descriptor must be rejected");
        check(RoiMethodShape.parse("Lx/Y;->bad([)V") == null,
                "incomplete array descriptor must be rejected");
        check(RoiMethodShape.parse("not-a-method") == null, "non-method descriptor must be rejected");
    }

    private static RoiHookResolver.Candidate candidate(boolean conflict, boolean approved, boolean fullEvidence) {
        Set<String> fingerprints = Collections.singleton("fp-289");
        RoiHookResolver.Evidence[] all = fullEvidence
                ? new RoiHookResolver.Evidence[] {
                        e(RoiHookResolver.EvidenceKind.HOST_IDENTITY, 1, true),
                        e(RoiHookResolver.EvidenceKind.DEX_FINGERPRINT, 1, true),
                        e(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER, 1, true),
                        e(RoiHookResolver.EvidenceKind.METHOD_SHAPE, 1, true),
                        e(RoiHookResolver.EvidenceKind.CALL_GRAPH, 1, true),
                        e(RoiHookResolver.EvidenceKind.RUNTIME_INVOCATION, 1, true),
                        e(RoiHookResolver.EvidenceKind.RETURN_CONTRACT, 1, true),
                        e(RoiHookResolver.EvidenceKind.LIFECYCLE_CONTRACT, 1, true)
                }
                : new RoiHookResolver.Evidence[] {
                        e(RoiHookResolver.EvidenceKind.HOST_IDENTITY, 0.25, false),
                        e(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER, 0.35, true)
                };
        return new RoiHookResolver.Candidate("fp-289", "request", "a1", "b", "(X)Y",
                conflict, approved, fingerprints, Arrays.asList(all));
    }

    private static RoiHookResolver.Evidence e(RoiHookResolver.EvidenceKind kind, double score, boolean confirmed) {
        return new RoiHookResolver.Evidence(kind, score, confirmed, "test");
    }

    private static RoiHookResolver.Policy autoPolicy() {
        return new RoiHookResolver.Policy(true, new HashSet<String>(Collections.singleton("request")), 0.45, 0.82);
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
