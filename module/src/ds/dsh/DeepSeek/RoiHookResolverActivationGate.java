package ds.dsh.DeepSeek;

/**
 * Production authorization bridge from a universal HookSpec to evidence-backed ROI decisions.
 * Missing evidence, policy, or an identity mismatch always denies activation.
 */
final class RoiHookResolverActivationGate implements RoiUniversalHookRuntime.ActivationGate {
    interface EvidenceSource {
        RoiHookResolver.Candidate find(RoiUniversalHookRuntime.Spec spec);
    }

    interface PolicySource {
        RoiHookResolver.Policy policy(RoiUniversalHookRuntime.Spec spec);
    }

    private final EvidenceSource evidenceSource;
    private final PolicySource policySource;

    RoiHookResolverActivationGate(EvidenceSource evidenceSource, PolicySource policySource) {
        if (evidenceSource == null || policySource == null) {
            throw new IllegalArgumentException("evidence and policy sources are required");
        }
        this.evidenceSource = evidenceSource;
        this.policySource = policySource;
    }

    @Override public boolean authorized(
            RoiUniversalHookRuntime.Spec spec, ClassLoader loader) {
        if (spec == null || loader == null) return false;
        try {
            String expectedScope = RoiCandidateIdentity.forClassLoader(loader);
            RoiHookResolver.Candidate candidate = evidenceSource.find(spec);
            RoiHookResolver.Policy policy = policySource.policy(spec);
            if (!matches(spec, candidate, expectedScope) || policy == null) return false;
            return RoiHookResolver.resolve(candidate, policy).activationAuthorized;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    static boolean matches(
            RoiUniversalHookRuntime.Spec spec, RoiHookResolver.Candidate candidate,
            String expectedScope) {
        if (candidate == null || expectedScope == null || expectedScope.length() == 0
                || !expectedScope.equals(candidate.sourceScope)
                || !RoiCandidateIdentity.candidateId(candidate.sourceScope, candidate.className,
                        candidate.methodName, candidate.descriptor).equals(candidate.candidateId)
                || !spec.fingerprint.equals(candidate.hostFingerprint)
                || !spec.role.equals(candidate.role)
                || !spec.className.equals(candidate.className)
                || !spec.name.equals(candidate.methodName)) return false;

        RoiMethodShape shape = RoiMethodShape.parse(candidate.descriptor);
        if (shape == null || !spec.className.equals(javaType(shape.ownerDescriptor))
                || !spec.name.equals(shape.methodName)
                || shape.parameterCount != spec.params.size()) return false;

        String descriptor = candidate.descriptor;
        int open = descriptor.indexOf('(');
        int close = descriptor.indexOf(')', open + 1);
        int cursor = open + 1;
        for (int i = 0; i < spec.params.size(); i++) {
            int end = typeEnd(descriptor, cursor);
            if (end <= cursor || !spec.params.get(i).equals(javaType(descriptor.substring(cursor, end)))) {
                return false;
            }
            cursor = end;
        }
        if (cursor != close) return false;

        String returnType = javaType(descriptor.substring(close + 1));
        if (spec.kind == RoiUniversalHookRuntime.Kind.CONSTRUCTOR) {
            return "<init>".equals(spec.name) && "void".equals(returnType)
                    && spec.returnType.length() == 0;
        }
        return spec.returnType.equals(returnType);
    }

    private static int typeEnd(String value, int start) {
        int cursor = start;
        while (cursor < value.length() && value.charAt(cursor) == '[') cursor++;
        if (cursor >= value.length()) return -1;
        char type = value.charAt(cursor);
        if ("ZBCSIJFDV".indexOf(type) >= 0) return cursor + 1;
        if (type != 'L') return -1;
        int semicolon = value.indexOf(';', cursor + 1);
        return semicolon < 0 ? -1 : semicolon + 1;
    }

    private static String javaType(String descriptor) {
        int dimensions = 0;
        while (dimensions < descriptor.length() && descriptor.charAt(dimensions) == '[') dimensions++;
        if (dimensions == descriptor.length()) return "";
        String base = descriptor.substring(dimensions);
        String value;
        switch (base) {
            case "V": value = "void"; break;
            case "Z": value = "boolean"; break;
            case "B": value = "byte"; break;
            case "C": value = "char"; break;
            case "S": value = "short"; break;
            case "I": value = "int"; break;
            case "J": value = "long"; break;
            case "F": value = "float"; break;
            case "D": value = "double"; break;
            default:
                if (base.length() < 3 || base.charAt(0) != 'L'
                        || base.charAt(base.length() - 1) != ';') return "";
                value = base.substring(1, base.length() - 1).replace('/', '.');
        }
        StringBuilder result = new StringBuilder(value);
        for (int i = 0; i < dimensions; i++) result.append("[]");
        return result.toString();
    }
}
