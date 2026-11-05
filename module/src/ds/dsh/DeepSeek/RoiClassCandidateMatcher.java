package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conservative association between DexKit candidates and classes already observed by
 * the host ClassLoader. Matching is metadata-only and never authorizes or installs hooks.
 */
final class RoiClassCandidateMatcher {
    enum Status { EXACT_MATCH, STRUCTURAL_MATCH, PARTIAL_MATCH, CONFLICT, UNMATCHED }

    private static final int MAX_REGISTERED_CANDIDATES = 512;
    private static final int MAX_MATCH_REPORTS_PER_CLASS = 8;
    private static final int MAX_RECENT_LOADED_CLASSES = 256;
    private static final Map<String, Candidate> REGISTERED =
            new LinkedHashMap<String, Candidate>();
    // Metadata-only snapshots avoid retaining host Class/ClassLoader instances.
    private static final Map<String, LoadedClass> RECENT_LOADED =
            new LinkedHashMap<String, LoadedClass>();

    static final class Candidate {
        final String sourceScope, owner, methodName, descriptor;
        Candidate(String sourceScope, String owner, String methodName, String descriptor) {
            this.sourceScope = clean(sourceScope);
            this.owner = clean(owner);
            this.methodName = clean(methodName);
            this.descriptor = clean(descriptor);
        }
    }

    static final class LoadedClass {
        final String sourceScope, className;
        final List<String> methodDescriptors;
        final boolean methodsTruncated;
        final String inspectionStatus;
        LoadedClass(String sourceScope, String className, List<String> methodDescriptors,
                    boolean methodsTruncated, String inspectionStatus) {
            this.sourceScope = clean(sourceScope);
            this.className = clean(className);
            this.methodDescriptors = immutable(methodDescriptors);
            this.methodsTruncated = methodsTruncated;
            this.inspectionStatus = clean(inspectionStatus);
        }
    }

    static final class Result {
        final Status status;
        final String reason;
        final boolean activationAuthorized;
        Result(Status status, String reason) {
            this.status = status;
            this.reason = reason;
            this.activationAuthorized = false;
        }
    }

    private RoiClassCandidateMatcher() {}

    /** Register a candidate and reconcile it with classes observed before discovery completed. */
    static synchronized String registerCandidate(Candidate candidate) {
        if (candidate == null || !candidate.sourceScope.startsWith("loader:")
                || candidate.owner.length() == 0 || candidate.methodName.length() == 0
                || candidate.descriptor.length() == 0) return "";
        REGISTERED.put(candidateKey(candidate), candidate);
        while (REGISTERED.size() > MAX_REGISTERED_CANDIDATES) {
            String eldest = REGISTERED.keySet().iterator().next();
            REGISTERED.remove(eldest);
        }
        StringBuilder report = new StringBuilder();
        int emitted = 0;
        for (LoadedClass loaded : RECENT_LOADED.values()) {
            if (!candidate.sourceScope.equals(loaded.sourceScope)) continue;
            Result result = match(candidate, loaded);
            if (result.status == Status.UNMATCHED) continue;
            if (emitted++ >= MAX_MATCH_REPORTS_PER_CLASS) break;
            appendMatch(report, candidate, result);
        }
        return report.toString();
    }

    /** Cache bounded metadata and correlate in either discovery/load order. */
    static synchronized String observeLoadedClass(Class<?> type) {
        if (type == null) return "";
        RoiLoadedClassInspector.Snapshot snapshot = RoiLoadedClassInspector.inspect(type);
        LoadedClass loaded = new LoadedClass(snapshot.loaderScope, snapshot.className,
                snapshot.dexMethodDescriptors, snapshot.methodsTruncated,
                snapshot.inspectionStatus);
        String key = loaded.sourceScope + "|" + loaded.className;
        RECENT_LOADED.put(key, loaded);
        while (RECENT_LOADED.size() > MAX_RECENT_LOADED_CLASSES) {
            String eldest = RECENT_LOADED.keySet().iterator().next();
            RECENT_LOADED.remove(eldest);
        }
        StringBuilder report = new StringBuilder();
        int emitted = 0;
        for (Candidate candidate : REGISTERED.values()) {
            if (!loaded.sourceScope.equals(candidate.sourceScope)) continue;
            Result result = match(candidate, loaded);
            if (result.status == Status.UNMATCHED) continue;
            if (emitted++ >= MAX_MATCH_REPORTS_PER_CLASS) break;
            appendMatch(report, candidate, result);
        }
        return report.toString();
    }

    /** Compatibility alias retained for existing integration callers. */
    static String matchObservedClass(Class<?> type) {
        return observeLoadedClass(type);
    }

    private static void appendMatch(StringBuilder report, Candidate candidate, Result result) {
        if (report.length() > 0) report.append('\\n');
        report.append("status=").append(result.status.name())
                .append(",owner=").append(candidate.owner)
                .append(",method=").append(candidate.methodName)
                .append(",descriptor=").append(candidate.descriptor)
                .append(",reason=").append(result.reason)
                .append(",activationAuthorized=false");
    }

    static Result match(Candidate candidate, LoadedClass loaded) {
        if (candidate == null || loaded == null
                || candidate.sourceScope.length() == 0 || loaded.sourceScope.length() == 0
                || candidate.owner.length() == 0 || loaded.className.length() == 0
                || candidate.descriptor.length() == 0) {
            return result(Status.PARTIAL_MATCH, "required_identity_metadata_missing");
        }
        if (!candidate.sourceScope.equals(loaded.sourceScope)) {
            return result(Status.CONFLICT, "classloader_scope_mismatch");
        }
        boolean sameOwner = normalizeOwner(candidate.owner).equals(
                normalizeOwner(loaded.className));
        String exact = normalizeMethodDescriptor(candidate.owner, candidate.methodName,
                candidate.descriptor);
        boolean exactMethod = loaded.methodDescriptors.contains(exact);
        if (sameOwner && exactMethod) {
            return result(Status.EXACT_MATCH, "owner_scope_and_full_method_descriptor_match");
        }
        if (sameOwner) {
            if (loaded.methodsTruncated || loaded.inspectionStatus.startsWith("partial_")) {
                return result(Status.PARTIAL_MATCH, "owner_matches_but_method_inventory_incomplete");
            }
            return result(Status.CONFLICT, "owner_matches_but_full_method_descriptor_absent");
        }
        if (exactMethod) {
            return result(Status.STRUCTURAL_MATCH,
                    "full_method_descriptor_matches_under_same_scope_but_owner_differs");
        }
        if (loaded.methodsTruncated || loaded.inspectionStatus.startsWith("partial_")) {
            return result(Status.PARTIAL_MATCH, "different_owner_and_method_inventory_incomplete");
        }
        return result(Status.UNMATCHED, "no_exact_owner_or_method_descriptor_match");
    }

    private static String normalizeMethodDescriptor(String owner, String method, String descriptor) {
        String value = clean(descriptor);
        String prefix = normalizeOwner(owner) + "->";
        if (value.startsWith(prefix)) return value;
        if (value.startsWith("->")) return normalizeOwner(owner) + value;
        return prefix + clean(method) + value;
    }

    private static String normalizeOwner(String owner) {
        String value = clean(owner).replace('.', '/');
        if (value.startsWith("L") && value.endsWith(";")) return value;
        return "L" + value + ";";
    }

    private static String candidateKey(Candidate candidate) {
        return candidate.sourceScope + "|" + normalizeOwner(candidate.owner) + "->"
                + candidate.methodName + candidate.descriptor;
    }

    private static ClassLoader safeLoader(Class<?> type) {
        try { return type.getClassLoader(); }
        catch (SecurityException denied) { return null; }
    }

    private static Result result(Status status, String reason) {
        return new Result(status, reason);
    }

    private static List<String> immutable(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
