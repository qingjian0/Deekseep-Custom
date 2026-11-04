package ds.dsh.DeepSeek;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, side-effect-free resolver for evidence-backed host hook candidates.
 *
 * This class never loads host classes and never installs hooks. It produces an authorization
 * decision for a separate installer, which must still verify installation and runtime behavior.
 */
final class RoiHookResolver {
    enum EvidenceKind {
        HOST_IDENTITY(0.10),
        DEX_FINGERPRINT(0.10),
        SEMANTIC_MARKER(0.08),
        METHOD_SHAPE(0.16),
        CALL_GRAPH(0.16),
        RUNTIME_INVOCATION(0.16),
        RETURN_CONTRACT(0.12),
        LIFECYCLE_CONTRACT(0.12);

        final double weight;
        EvidenceKind(double weight) { this.weight = weight; }
    }

    enum State { DISCOVERED, CORRELATED, VERIFIED, REJECTED }

    static final class Evidence {
        final EvidenceKind kind;
        final double confidence;
        final boolean confirmed;
        final String source;

        Evidence(EvidenceKind kind, double confidence, boolean confirmed, String source) {
            if (kind == null) throw new IllegalArgumentException("evidence kind is required");
            if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
            this.kind = kind;
            this.confidence = confidence;
            this.confirmed = confirmed;
            this.source = safe(source);
        }
    }

    static final class Candidate {
        final String hostFingerprint;
        final String role;
        final String className;
        final String methodName;
        final String descriptor;
        final boolean contradictoryEvidence;
        final boolean adapterApproved;
        final Set<String> approvedFingerprints;
        final Map<EvidenceKind, Evidence> evidence;

        Candidate(String hostFingerprint, String role, String className, String methodName,
                  String descriptor, boolean contradictoryEvidence, boolean adapterApproved,
                  Set<String> approvedFingerprints, Iterable<Evidence> evidence) {
            this.hostFingerprint = safe(hostFingerprint);
            this.role = safe(role);
            this.className = safe(className);
            this.methodName = safe(methodName);
            this.descriptor = safe(descriptor);
            this.contradictoryEvidence = contradictoryEvidence;
            this.adapterApproved = adapterApproved;
            this.approvedFingerprints = approvedFingerprints == null
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<String>(approvedFingerprints));
            EnumMap<EvidenceKind, Evidence> collected = new EnumMap<EvidenceKind, Evidence>(EvidenceKind.class);
            if (evidence != null) {
                for (Evidence item : evidence) {
                    if (item == null) continue;
                    Evidence previous = collected.get(item.kind);
                    // One evidence family contributes at most once; retain its strongest observation.
                    if (previous == null || item.confidence > previous.confidence) {
                        collected.put(item.kind, item);
                    } else if (item.confidence == previous.confidence && item.confirmed && !previous.confirmed) {
                        collected.put(item.kind, item);
                    }
                }
            }
            this.evidence = Collections.unmodifiableMap(collected);
        }
    }

    static final class Policy {
        final boolean automaticActivationEnabled;
        final Set<String> allowedRoles;
        final double correlatedThreshold;
        final double verifiedThreshold;

        Policy(boolean automaticActivationEnabled, Set<String> allowedRoles,
               double correlatedThreshold, double verifiedThreshold) {
            if (Double.isNaN(correlatedThreshold) || Double.isNaN(verifiedThreshold)
                    || correlatedThreshold < 0 || correlatedThreshold > 1
                    || verifiedThreshold < correlatedThreshold || verifiedThreshold > 1) {
                throw new IllegalArgumentException("invalid resolver thresholds");
            }
            this.automaticActivationEnabled = automaticActivationEnabled;
            this.allowedRoles = allowedRoles == null
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<String>(allowedRoles));
            this.correlatedThreshold = correlatedThreshold;
            this.verifiedThreshold = verifiedThreshold;
        }

        static Policy discoveryOnly() {
            return new Policy(false, Collections.<String>emptySet(), 0.45, 0.82);
        }
    }

    static final class Decision {
        final State state;
        final double score;
        final boolean activationAuthorized;
        final String reason;

        Decision(State state, double score, boolean activationAuthorized, String reason) {
            this.state = state;
            this.score = score;
            this.activationAuthorized = activationAuthorized;
            this.reason = reason;
        }
    }

    private static final EvidenceKind[] REQUIRED = {
            EvidenceKind.HOST_IDENTITY,
            EvidenceKind.METHOD_SHAPE,
            EvidenceKind.CALL_GRAPH,
            EvidenceKind.RUNTIME_INVOCATION,
            EvidenceKind.RETURN_CONTRACT,
            EvidenceKind.LIFECYCLE_CONTRACT
    };

    private RoiHookResolver() {}

    static Decision resolve(Candidate candidate, Policy policy) {
        if (candidate == null || policy == null) {
            return new Decision(State.REJECTED, 0.0, false, "missing_candidate_or_policy");
        }
        if (candidate.contradictoryEvidence) {
            return new Decision(State.REJECTED, score(candidate), false, "contradictory_evidence");
        }
        if (candidate.hostFingerprint.length() == 0 || candidate.role.length() == 0
                || candidate.className.length() == 0 || candidate.methodName.length() == 0
                || candidate.descriptor.length() == 0) {
            return new Decision(State.DISCOVERED, score(candidate), false, "incomplete_candidate_identity");
        }

        double score = score(candidate);
        int corroboratedKinds = candidate.evidence.size();
        if (corroboratedKinds < 2 || score < policy.correlatedThreshold) {
            return new Decision(State.DISCOVERED, score, false, "insufficient_correlated_evidence");
        }

        for (EvidenceKind kind : REQUIRED) {
            Evidence item = candidate.evidence.get(kind);
            if (item == null || !item.confirmed || item.confidence < 0.80) {
                return new Decision(State.CORRELATED, score, false, "required_evidence_missing_or_unconfirmed:" + kind.name());
            }
        }
        if (score < policy.verifiedThreshold) {
            return new Decision(State.CORRELATED, score, false, "verified_score_threshold_not_met");
        }

        if (!candidate.adapterApproved) {
            return new Decision(State.VERIFIED, score, false, "adapter_rule_not_approved");
        }
        if (!candidate.approvedFingerprints.contains(candidate.hostFingerprint)) {
            return new Decision(State.VERIFIED, score, false, "host_fingerprint_not_approved_for_adapter");
        }
        if (!policy.automaticActivationEnabled || !policy.allowedRoles.contains(candidate.role)) {
            return new Decision(State.VERIFIED, score, false, "activation_policy_denied");
        }

        // Authorization is not installation. The installer must report success and run its own
        // post-install verification before the candidate can be considered ACTIVE.
        return new Decision(State.VERIFIED, score, true, "verified_and_authorized_for_installer");
    }

    private static double score(Candidate candidate) {
        if (candidate == null) return 0.0;
        double total = 0.0;
        for (EvidenceKind kind : EvidenceKind.values()) {
            Evidence item = candidate.evidence.get(kind);
            if (item != null) total += kind.weight * item.confidence;
        }
        return Math.max(0.0, Math.min(1.0, total));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
