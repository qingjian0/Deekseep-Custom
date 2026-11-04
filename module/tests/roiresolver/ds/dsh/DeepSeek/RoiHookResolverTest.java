package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Executable tests for resolver scoring, evidence gates, and fail-closed activation policy. */
public final class RoiHookResolverTest {
    private RoiHookResolverTest() {}

    public static void main(String[] args) {
        RoiHookResolver.Candidate weak = candidate(false, false, false);
        RoiHookResolver.Decision discovered =
                RoiHookResolver.resolve(weak, RoiHookResolver.Policy.discoveryOnly());
        check(discovered.state == RoiHookResolver.State.DISCOVERED, "weak candidate must remain discovered");
        check(!discovered.activationAuthorized, "discovery must never authorize activation");

        RoiHookResolver.Candidate incomplete = new RoiHookResolver.Candidate(
                "fp-289", "request", "a1", "b", "(X)Y", false, true,
                Collections.singleton("fp-289"),
                Arrays.asList(e(RoiHookResolver.EvidenceKind.HOST_IDENTITY, 1, true),
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

        System.out.println("RoiHookResolver executable tests passed");
    }

    private static RoiHookResolver.Candidate candidate(boolean conflict, boolean approved, boolean fullEvidence) {
        Set<String> fingerprints = Collections.singleton("fp-289");
        RoiHookResolver.Evidence[] all = {
                e(RoiHookResolver.EvidenceKind.HOST_IDENTITY, 1, true),
                e(RoiHookResolver.EvidenceKind.DEX_FINGERPRINT, 1, true),
                e(RoiHookResolver.EvidenceKind.SEMANTIC_MARKER, 1, true),
                e(RoiHookResolver.EvidenceKind.METHOD_SHAPE, 1, true),
                e(RoiHookResolver.EvidenceKind.CALL_GRAPH, 1, true),
                e(RoiHookResolver.EvidenceKind.RUNTIME_INVOCATION, 1, true),
                e(RoiHookResolver.EvidenceKind.RETURN_CONTRACT, 1, true),
                e(RoiHookResolver.EvidenceKind.LIFECYCLE_CONTRACT, 1, true)
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

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
