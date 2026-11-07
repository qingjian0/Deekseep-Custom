package ds.dsh.DeepSeek;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, code-reviewed activation catalog for Universal Hook adapters.
 *
 * <p>The catalog is intentionally populated by compiled adapter code, not by a preference file,
 * discovery report, remote response, or arbitrary runtime callback. An empty catalog is valid and
 * denies every activation. Builder entries are accepted only when the candidate identity exactly
 * matches the HookSpec and the resolver independently authorizes every required evidence family.</p>
 */
final class RoiReviewedHookEvidenceRegistry
        implements RoiHookResolverActivationGate.EvidenceSource,
                   RoiHookResolverActivationGate.PolicySource,
                   RoiHookResolverActivationGate.ReviewedIdentitySource {

    private static final class Entry {
        final RoiHookResolver.Candidate candidate;
        final RoiHookResolver.Policy policy;
        final RoiHostBuildIdentity hostIdentity;

        Entry(RoiHookResolver.Candidate candidate, RoiHookResolver.Policy policy,
              RoiHostBuildIdentity hostIdentity) {
            this.candidate = candidate;
            this.policy = policy;
            this.hostIdentity = hostIdentity;
        }
    }

    private final Map<String, Entry> entries;

    private RoiReviewedHookEvidenceRegistry(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(
                new LinkedHashMap<String, Entry>(entries));
    }

    static Builder builder() { return new Builder(); }

    @Override public RoiHookResolver.Candidate find(RoiUniversalHookRuntime.Spec spec) {
        Entry entry = spec == null ? null : entries.get(specKey(spec));
        return entry == null ? null : entry.candidate;
    }

    @Override public RoiHookResolver.Policy policy(RoiUniversalHookRuntime.Spec spec) {
        Entry entry = spec == null ? null : entries.get(specKey(spec));
        return entry == null ? null : entry.policy;
    }

    @Override public RoiHostBuildIdentity reviewedIdentity(RoiUniversalHookRuntime.Spec spec) {
        Entry entry = spec == null ? null : entries.get(specKey(spec));
        return entry == null ? null : entry.hostIdentity;
    }

    int size() { return entries.size(); }

    static final class Builder {
        private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
        private boolean built;

        /**
         * Adds one reviewed adapter record. This method is intended for source-controlled,
         * version-scoped adapter declarations. It is not a discovery/import API.
         */
        synchronized Builder addReviewed(
                RoiUniversalHookRuntime.Spec spec,
                RoiHookResolver.Candidate candidate,
                RoiHookResolver.Policy policy,
                RoiHostBuildIdentity hostIdentity) {
            if (built) throw new IllegalStateException("reviewed evidence catalog already built");
            if (spec == null || candidate == null || policy == null || hostIdentity == null) {
                throw new IllegalArgumentException("spec, candidate, policy and host identity are required");
            }
            if (!hostIdentity.isComplete()) {
                throw new IllegalArgumentException("reviewed host build identity is incomplete");
            }
            if (!hostIdentity.classLoaderScope.equals(candidate.sourceScope)) {
                throw new IllegalArgumentException("host identity ClassLoader scope does not match candidate");
            }
            if (!RoiHookResolverActivationGate.matches(
                    spec, candidate, candidate.sourceScope)) {
                throw new IllegalArgumentException("reviewed candidate does not exactly match HookSpec");
            }
            RoiHookResolver.Decision decision = RoiHookResolver.resolve(candidate, policy);
            if (!decision.activationAuthorized) {
                throw new IllegalArgumentException(
                        "reviewed candidate is not activation-authorized: " + decision.reason);
            }
            String key = specKey(spec);
            if (entries.containsKey(key)) {
                throw new IllegalArgumentException("duplicate reviewed HookSpec identity");
            }
            entries.put(key, new Entry(candidate, policy, hostIdentity));
            return this;
        }

        synchronized RoiReviewedHookEvidenceRegistry build() {
            if (built) throw new IllegalStateException("reviewed evidence catalog already built");
            built = true;
            return new RoiReviewedHookEvidenceRegistry(entries);
        }
    }

    private static String specKey(RoiUniversalHookRuntime.Spec spec) {
        StringBuilder out = new StringBuilder(256);
        appendKey(out, spec.id);
        appendKey(out, spec.fingerprint);
        appendKey(out, spec.role);
        appendKey(out, spec.kind.name());
        appendKey(out, spec.className);
        appendKey(out, spec.name);
        out.append(spec.params.size()).append(':');
        for (String parameter : spec.params) appendKey(out, parameter);
        appendKey(out, spec.returnType);
        return out.toString();
    }

    private static void appendKey(StringBuilder out, String value) {
        String safe = value == null ? "" : value;
        out.append(safe.length()).append(':').append(safe);
    }
}
