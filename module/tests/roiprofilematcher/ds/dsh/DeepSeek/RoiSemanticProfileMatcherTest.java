package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RoiSemanticProfileMatcherTest {
    public static void main(String[] args) {
        testCrossVersionRenameMapping();
        testAmbiguousCandidatesFailClosed();
        testLowConfidenceAndRoleMismatch();
        testStableMarkerConflict();
        testAmbiguousMethodRoleFailsClosed();
        testMissingEvidenceDimensionsDoNotInflateScore();
        testProfilesWithoutMethodRolesAreNotMatchable();
        testDetachedMethodRoleEvidenceIsNotMatchable();
        testTargetRoleDeclarationMustAgreeWithResolvedMethod();
        testMalformedMethodDescriptorsAreNotMatchable();
        System.out.println("ROI semantic profile matcher tests passed");
    }

    private static void testCrossVersionRenameMapping() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile target = targetProfile("Labc;");
        List<RoiSemanticProfileMatcher.Match> ranked =
                RoiSemanticProfileMatcher.rank(source, Collections.singletonList(target));
        equal(1, ranked.size(), "one candidate");
        RoiSemanticProfileMatcher.Match match = ranked.get(0);
        equal(RoiSemanticProfileMatcher.State.MATCHED, match.state, "renamed class match");
        check(match.score >= RoiSemanticProfileMatcher.DEFAULT_MIN_SCORE, "score threshold");
        equal("dispatch(Lreq;Ljava/lang/Long;)Lresp;",
                match.resolvedMethodRoles.get("transport.dispatch"),
                "method role resolves by normalized prototype despite method/class rename");
        check(!match.activationAuthorized, "semantic matching never authorizes Hook activation");
    }

    private static void testAmbiguousCandidatesFailClosed() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        List<RoiSemanticProfileMatcher.Match> ranked = RoiSemanticProfileMatcher.rank(
                source, Arrays.asList(targetProfile("Labc;"), targetProfile("Lxyz;")));
        equal(2, ranked.size(), "two candidates retained");
        equal(RoiSemanticProfileMatcher.State.AMBIGUOUS, ranked.get(0).state,
                "top candidate ambiguous");
        equal(RoiSemanticProfileMatcher.State.AMBIGUOUS, ranked.get(1).state,
                "equally strong candidate also ambiguous");
        check(!ranked.get(0).activationAuthorized && !ranked.get(1).activationAuthorized,
                "ambiguous candidates cannot authorize");
    }

    private static void testLowConfidenceAndRoleMismatch() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile unrelated = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lother;", "Ljava/lang/Object;",
                Collections.singletonList("Ljava/io/Serializable;"),
                Collections.singletonList("I"),
                Collections.singletonList("run()V"),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.singletonMap("transport.dispatch", "run()V"));
        RoiSemanticProfileMatcher.Match weak = RoiSemanticProfileMatcher.rank(
                source, Collections.singletonList(unrelated)).get(0);
        equal(RoiSemanticProfileMatcher.State.LOW_CONFIDENCE, weak.state, "weak match rejected");

        RoiSemanticProfileMatcher.ClassProfile wrongRole = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "request.builder", "Lwrong;", "Ljava/lang/Object;",
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("dispatch(Lreq;Ljava/lang/Long;)Lresp;"),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonMap("request.build", "dispatch(Lreq;Ljava/lang/Long;)Lresp;"));
        RoiSemanticProfileMatcher.Match mismatch = RoiSemanticProfileMatcher.rank(
                source, Collections.singletonList(wrongRole)).get(0);
        equal(RoiSemanticProfileMatcher.State.ROLE_MISMATCH, mismatch.state, "role mismatch rejected");
    }

    private static void testStableMarkerConflict() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile conflict = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lconf;", "Ljava/lang/Object;",
                Arrays.asList("Liface;", "Ljava/lang/AutoCloseable;"),
                Arrays.asList("Lhttp;", "Lcfg;"),
                Arrays.asList("<init>(Lcfg;)V", "dispatch(Lreq;Ljava/lang/Long;)Lresp;", "close()V"),
                Collections.singletonList(
                        "Ljava/util/concurrent/ForkJoinPool;->commonPool()Ljava/util/concurrent/ForkJoinPool;"),
                Collections.singletonList("/api/v1/unrelated"),
                Collections.singletonMap("transport.dispatch",
                        "dispatch(Lreq;Ljava/lang/Long;)Lresp;"));
        RoiSemanticProfileMatcher.Match match = RoiSemanticProfileMatcher.rank(
                source, Collections.singletonList(conflict)).get(0);
        equal(RoiSemanticProfileMatcher.State.CONTRADICTORY, match.state,
                "contradictory stable marker must reject");
    }


    private static void testAmbiguousMethodRoleFailsClosed() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile base = targetProfile("Labc;");
        RoiSemanticProfileMatcher.ClassProfile duplicateMethod = new RoiSemanticProfileMatcher.ClassProfile(
                base.hostBuild, base.role, base.classDescriptor, base.superclassDescriptor,
                base.interfaces, base.fields,
                Arrays.asList("<init>(Lcfg;)V", "dispatch(Lreq;Ljava/lang/Long;)Lresp;",
                        "route(Lreq;Ljava/lang/Long;)Lres;", "close()V"),
                base.externalCalls, base.semanticMarkers, base.methodRoles);
        check(!duplicateMethod.valid(), "duplicate normalized method prototypes invalidate role evidence");
        check(RoiSemanticProfileMatcher.rank(source, Collections.singletonList(duplicateMethod)).isEmpty(),
                "ambiguous method role must be rejected before candidate ranking");
    }

    private static void testMissingEvidenceDimensionsDoNotInflateScore() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile methodOnly = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lcandidate;", "",
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("renamed(Lrequest;Ljava/lang/Long;)Lresponse;"),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonMap("transport.dispatch",
                        "renamed(Lrequest;Ljava/lang/Long;)Lresponse;"));
        RoiSemanticProfileMatcher.Match match = RoiSemanticProfileMatcher.rank(
                source, Collections.singletonList(methodOnly)).get(0);
        equal(RoiSemanticProfileMatcher.State.LOW_CONFIDENCE, match.state,
                "missing structural dimensions must not be omitted from score");
        check(match.score < RoiSemanticProfileMatcher.DEFAULT_MIN_SCORE,
                "method-only candidate must not inherit a perfect normalized score");
        check(!match.activationAuthorized, "incomplete profile never authorizes activation");
    }


    private static void testProfilesWithoutMethodRolesAreNotMatchable() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile noRoles = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lcandidate;", "Ljava/lang/Object;",
                Arrays.asList("Liface;", "Ljava/lang/AutoCloseable;"),
                Arrays.asList("Lhttp;", "Lcfg;"),
                Arrays.asList("<init>(Lcfg;)V", "dispatch(Lreq;Ljava/lang/Long;)Lresp;", "close()V"),
                Collections.singletonList(
                        "Ljava/util/concurrent/ForkJoinPool;->commonPool()Ljava/util/concurrent/ForkJoinPool;"),
                Arrays.asList("/api/v0/chat/completion", "/api/v0/chat/regenerate"),
                Collections.<String, String>emptyMap());
        check(RoiSemanticProfileMatcher.rank(source, Collections.singletonList(noRoles)).isEmpty(),
                "class-only profile without semantic method roles must not be reported as matched");
    }


    private static void testDetachedMethodRoleEvidenceIsNotMatchable() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile detachedRole = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lcandidate;", "Ljava/lang/Object;",
                Arrays.asList("Liface;", "Ljava/lang/AutoCloseable;"),
                Arrays.asList("Lhttp;", "Lcfg;"),
                Arrays.asList("<init>(Lcfg;)V", "dispatch(Lreq;Ljava/lang/Long;)Lresp;", "close()V"),
                Collections.singletonList(
                        "Ljava/util/concurrent/ForkJoinPool;->commonPool()Ljava/util/concurrent/ForkJoinPool;"),
                Arrays.asList("/api/v0/chat/completion", "/api/v0/chat/regenerate"),
                Collections.singletonMap("transport.dispatch",
                        "missing(Lreq;Ljava/lang/Long;)Lresp;"));
        check(RoiSemanticProfileMatcher.rank(source, Collections.singletonList(detachedRole)).isEmpty(),
                "method role must map to exactly one declared method prototype");
    }


    private static void testTargetRoleDeclarationMustAgreeWithResolvedMethod() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile contradictoryRole = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lcandidate;", "Ljava/lang/Object;",
                Arrays.asList("Liface;", "Ljava/lang/AutoCloseable;"),
                Arrays.asList("Lhttp;", "Lcfg;"),
                Arrays.asList("<init>(Lcfg;)V", "dispatch(Lreq;Ljava/lang/Long;)Lresp;",
                        "route(Lreq;Ljava/lang/Long;)Lresp;", "close()V"),
                Collections.singletonList(
                        "Ljava/util/concurrent/ForkJoinPool;->commonPool()Ljava/util/concurrent/ForkJoinPool;"),
                Arrays.asList("/api/v0/chat/completion", "/api/v0/chat/regenerate"),
                Collections.singletonMap("transport.dispatch",
                        "route(Lreq;Ljava/lang/Long;)Lresp;"));
        RoiSemanticProfileMatcher.Match match = RoiSemanticProfileMatcher.rank(
                source, Collections.singletonList(contradictoryRole)).get(0);
        equal(RoiSemanticProfileMatcher.State.LOW_CONFIDENCE, match.state,
                "target's explicit role assignment must agree with source role resolution");
        check(!match.resolvedMethodRoles.containsKey("transport.dispatch"),
                "contradictory target role declaration must not resolve");
        check(!match.activationAuthorized, "contradictory role evidence cannot authorize activation");
    }

    private static void testMalformedMethodDescriptorsAreNotMatchable() {
        RoiSemanticProfileMatcher.ClassProfile source = sourceProfile();
        RoiSemanticProfileMatcher.ClassProfile malformed = new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", "Lcandidate;", "Ljava/lang/Object;",
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("dispatch(Ljava/lang/String;)?"),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonMap("transport.dispatch", "dispatch(Ljava/lang/String;)?"));
        check(RoiSemanticProfileMatcher.rank(source, Collections.singletonList(malformed)).isEmpty(),
                "malformed method descriptor must not become a semantic profile candidate");
    }

    private static RoiSemanticProfileMatcher.ClassProfile sourceProfile() {
        Map<String, String> roles = new LinkedHashMap<String, String>();
        roles.put("transport.dispatch", "o(Lwu1;Ljava/lang/Long;)Ly60;");
        return new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.2/code289-cn", "transport.dispatch", "Lzn3;",
                "Ljava/lang/Object;",
                Arrays.asList("Lqz0;", "Ljava/lang/AutoCloseable;"),
                Arrays.asList("Ljw3;", "Lbi5;"),
                Arrays.asList("<init>(Lbi5;)V", "o(Lwu1;Ljava/lang/Long;)Ly60;", "close()V"),
                Collections.singletonList(
                        "Ljava/util/concurrent/ForkJoinPool;->commonPool()Ljava/util/concurrent/ForkJoinPool;"),
                Arrays.asList("/api/v0/chat/completion", "/api/v0/chat/regenerate"),
                roles);
    }

    private static RoiSemanticProfileMatcher.ClassProfile targetProfile(String owner) {
        return new RoiSemanticProfileMatcher.ClassProfile(
                "2.7.5", "transport.dispatch", owner, "Ljava/lang/Object;",
                Arrays.asList("Liface;", "Ljava/lang/AutoCloseable;"),
                Arrays.asList("Lhttp;", "Lcfg;"),
                Arrays.asList("<init>(Lcfg;)V", "dispatch(Lreq;Ljava/lang/Long;)Lresp;", "close()V"),
                Collections.singletonList(
                        "Ljava/util/concurrent/ForkJoinPool;->commonPool()Ljava/util/concurrent/ForkJoinPool;"),
                Arrays.asList("/api/v0/chat/completion", "/api/v0/chat/regenerate"),
                Collections.singletonMap("transport.dispatch",
                        "dispatch(Lreq;Ljava/lang/Long;)Lresp;"));
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static void equal(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
