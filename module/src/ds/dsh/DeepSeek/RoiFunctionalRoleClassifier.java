package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts exact, directly referenced platform API calls into conservative feature roles.
 * String anchors and obfuscated method/class names never establish a role by themselves.
 * Results are diagnostic only and cannot authorize resolver or hook activation.
 */
final class RoiFunctionalRoleClassifier {
    enum Role {
        REQUEST_CONSTRUCTION,
        HTTP_TRANSPORT,
        RESPONSE_DECODING,
        UNRESOLVED
    }

    static final class Result {
        final List<Role> roles;
        final List<String> evidence;
        final String provenance;
        final double staticConfidence;

        Result(List<Role> roles, List<String> evidence, String provenance,
               double staticConfidence) {
            this.roles = Collections.unmodifiableList(new ArrayList<Role>(roles));
            this.evidence = Collections.unmodifiableList(new ArrayList<String>(evidence));
            this.provenance = provenance;
            this.staticConfidence = staticConfidence;
        }

        boolean has(Role role) { return roles.contains(role); }
    }

    private RoiFunctionalRoleClassifier() {}

    static Result classify(List<String> directInvokeReferences) {
        RoiSemanticRoleHints.Result hints =
                RoiSemanticRoleHints.classify(directInvokeReferences);
        List<Role> roles = new ArrayList<Role>();
        if (hints.has(RoiSemanticRoleHints.Hint.REQUEST_BUILDER)) {
            roles.add(Role.REQUEST_CONSTRUCTION);
        }
        if (hints.has(RoiSemanticRoleHints.Hint.TRANSPORT)) {
            roles.add(Role.HTTP_TRANSPORT);
        }
        if (hints.has(RoiSemanticRoleHints.Hint.RESPONSE_PARSER)) {
            roles.add(Role.RESPONSE_DECODING);
        }
        if (roles.isEmpty()) roles.add(Role.UNRESOLVED);

        // Direct references are useful static evidence, but do not prove execution.
        double confidence = roles.size() == 1 && roles.get(0) == Role.UNRESOLVED
                ? 0.0 : 0.45;
        return new Result(roles, hints.matchedReferences,
                "direct_static_invoke_reference_only", confidence);
    }
}
