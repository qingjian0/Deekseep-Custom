package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline semantic-profile comparison for obfuscated host classes across APK generations.
 *
 * Class/member names are deliberately excluded from structural similarity. The matcher compares
 * normalized type shapes, inheritance, field/method multiplicity, stable platform call references,
 * and stable semantic markers. It is discovery evidence only: it never loads host classes,
 * authorizes a candidate, or installs a Hook.
 */
final class RoiSemanticProfileMatcher {
    static final double DEFAULT_MIN_SCORE = 0.80;
    static final double DEFAULT_MIN_MARGIN = 0.10;

    enum State { MATCHED, AMBIGUOUS, LOW_CONFIDENCE, ROLE_MISMATCH, CONTRADICTORY }

    static final class ClassProfile {
        final String hostBuild;
        final String role;
        final String classDescriptor;
        final String superclassDescriptor;
        final List<String> interfaces;
        final List<String> fields;
        final List<String> methods;
        final List<String> externalCalls;
        final List<String> semanticMarkers;
        final Map<String, String> methodRoles;

        ClassProfile(String hostBuild, String role, String classDescriptor,
                     String superclassDescriptor, List<String> interfaces,
                     List<String> fields, List<String> methods, List<String> externalCalls,
                     List<String> semanticMarkers, Map<String, String> methodRoles) {
            this.hostBuild = clean(hostBuild);
            this.role = clean(role);
            this.classDescriptor = clean(classDescriptor);
            this.superclassDescriptor = clean(superclassDescriptor);
            this.interfaces = immutable(interfaces);
            this.fields = immutable(fields);
            this.methods = immutable(methods);
            this.externalCalls = immutable(externalCalls);
            this.semanticMarkers = immutable(semanticMarkers);
            this.methodRoles = immutableMap(methodRoles);
        }

        boolean valid() {
            return !hostBuild.isEmpty() && !role.isEmpty() && !classDescriptor.isEmpty()
                    && !methods.isEmpty() && !methodRoles.isEmpty();
        }
    }

    static final class Match {
        final ClassProfile source;
        final ClassProfile target;
        final State state;
        final double score;
        final double margin;
        final Map<String, String> resolvedMethodRoles;
        final String reason;
        final boolean activationAuthorized = false;

        Match(ClassProfile source, ClassProfile target, State state, double score,
              double margin, Map<String, String> resolvedMethodRoles, String reason) {
            this.source = source;
            this.target = target;
            this.state = state;
            this.score = score;
            this.margin = margin;
            this.resolvedMethodRoles = immutableMap(resolvedMethodRoles);
            this.reason = reason;
        }
    }

    private static final Pattern OBFUSCATED = Pattern.compile(
            "^L[a-z0-9]{1,4}(?:\\$[A-Za-z0-9_]+)*;$");
    private static final Pattern FIELD = Pattern.compile(
            "^(?:public|protected|private|static|final|volatile|transient|synthetic|\\s)*"
                    + "(\\[*[ZBSCIJFDV]|\\[*L[^;]+;)\\s+[^\\s]+$");

    private RoiSemanticProfileMatcher() {}

    static List<Match> rank(ClassProfile source, List<ClassProfile> candidates) {
        if (source == null || !source.valid() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Match> results = new ArrayList<Match>();
        for (ClassProfile target : candidates) {
            if (target == null || !target.valid()) continue;
            if (!source.role.equals(target.role)) {
                results.add(new Match(source, target, State.ROLE_MISMATCH, 0.0, 0.0,
                        Collections.<String, String>emptyMap(), "semantic_role_mismatch"));
                continue;
            }
            results.add(compare(source, target));
        }
        Collections.sort(results, new Comparator<Match>() {
            @Override public int compare(Match a, Match b) {
                return Double.compare(b.score, a.score);
            }
        });
        if (results.isEmpty()) return Collections.emptyList();

        Match best = results.get(0);
        double second = results.size() > 1 ? results.get(1).score : 0.0;
        double margin = best.score - second;
        List<Match> finalized = new ArrayList<Match>(results.size());
        for (Match item : results) {
            State state;
            String reason;
            if (item.state == State.ROLE_MISMATCH || item.state == State.CONTRADICTORY) {
                state = item.state;
                reason = item.reason;
            } else if (best.score < DEFAULT_MIN_SCORE) {
                state = State.LOW_CONFIDENCE;
                reason = "structural_similarity_below_threshold";
            } else if (margin < DEFAULT_MIN_MARGIN && item.score == best.score) {
                state = State.AMBIGUOUS;
                reason = "multiple_profiles_with_indistinguishable_scores";
            } else if (item != best) {
                state = State.LOW_CONFIDENCE;
                reason = "not_the_unique_top_candidate";
            } else if (item.resolvedMethodRoles.size() != source.methodRoles.size()) {
                state = State.LOW_CONFIDENCE;
                reason = "one_or_more_method_roles_not_uniquely_resolved";
            } else {
                state = State.MATCHED;
                reason = "unique_semantic_profile_candidate";
            }
            finalized.add(new Match(item.source, item.target, state, item.score,
                    margin, item.resolvedMethodRoles, reason));
        }
        return Collections.unmodifiableList(finalized);
    }

    private static Match compare(ClassProfile source, ClassProfile target) {
        if (source.semanticMarkers.size() > 0 && target.semanticMarkers.size() > 0
                && intersection(source.semanticMarkers, target.semanticMarkers) == 0) {
            return new Match(source, target, State.CONTRADICTORY, 0.0, 0.0,
                    Collections.<String, String>emptyMap(), "stable_semantic_markers_conflict");
        }

        double weighted = 0.0;
        double weights = 0.0;
        // A missing dimension is negative evidence, not permission to remove its weight.
        // Otherwise a candidate that copies only the method prototypes can score 1.0 while
        // omitting the source profile's inheritance, fields, calls, and semantic anchors.
        if (!source.superclassDescriptor.isEmpty() || !target.superclassDescriptor.isEmpty()) {
            double similarity = !source.superclassDescriptor.isEmpty()
                    && !target.superclassDescriptor.isEmpty()
                    && normalizeType(source.superclassDescriptor)
                            .equals(normalizeType(target.superclassDescriptor)) ? 1.0 : 0.0;
            weighted += 0.15 * similarity;
            weights += 0.15;
        }
        if (!source.interfaces.isEmpty() || !target.interfaces.isEmpty()) {
            double similarity = source.interfaces.isEmpty() || target.interfaces.isEmpty() ? 0.0
                    : jaccard(new LinkedHashSet<String>(normalizedTypes(source.interfaces)),
                            new LinkedHashSet<String>(normalizedTypes(target.interfaces)));
            weighted += 0.15 * similarity;
            weights += 0.15;
        }
        if (!source.fields.isEmpty() || !target.fields.isEmpty()) {
            double similarity = source.fields.isEmpty() || target.fields.isEmpty() ? 0.0
                    : dice(normalizedFields(source.fields), normalizedFields(target.fields));
            weighted += 0.15 * similarity;
            weights += 0.15;
        }
        if (!source.methods.isEmpty() || !target.methods.isEmpty()) {
            double similarity = source.methods.isEmpty() || target.methods.isEmpty() ? 0.0
                    : dice(normalizedMethods(source.methods), normalizedMethods(target.methods));
            weighted += 0.30 * similarity;
            weights += 0.30;
        }
        if (!source.externalCalls.isEmpty() || !target.externalCalls.isEmpty()) {
            double similarity = source.externalCalls.isEmpty() || target.externalCalls.isEmpty() ? 0.0
                    : jaccard(new LinkedHashSet<String>(normalizedExternalCalls(source.externalCalls)),
                            new LinkedHashSet<String>(normalizedExternalCalls(target.externalCalls)));
            weighted += 0.15 * similarity;
            weights += 0.15;
        }
        if (!source.semanticMarkers.isEmpty() || !target.semanticMarkers.isEmpty()) {
            double similarity = source.semanticMarkers.isEmpty() || target.semanticMarkers.isEmpty() ? 0.0
                    : jaccard(new LinkedHashSet<String>(source.semanticMarkers),
                            new LinkedHashSet<String>(target.semanticMarkers));
            weighted += 0.10 * similarity;
            weights += 0.10;
        }
        double score = weights == 0.0 ? 0.0 : weighted / weights;
        Map<String, String> methodRoles = resolveMethodRoles(source, target);
        return new Match(source, target, State.LOW_CONFIDENCE, score, 0.0,
                methodRoles, "profile_comparison_pending_candidate_ranking");
    }

    private static Map<String, String> resolveMethodRoles(ClassProfile source, ClassProfile target) {
        Map<String, String> resolved = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> role : source.methodRoles.entrySet()) {
            String wanted = methodShape(role.getValue());
            String found = null;
            boolean ambiguous = false;
            for (String candidate : target.methods) {
                if (!wanted.equals(methodShape(candidate))) continue;
                if (found != null) { ambiguous = true; break; }
                found = candidate;
            }
            if (!ambiguous && found != null) resolved.put(role.getKey(), found);
        }
        return resolved;
    }

    private static List<String> normalizedTypes(List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) result.add(normalizeType(value));
        return result;
    }

    private static List<String> normalizedFields(List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            String clean = clean(value);
            Matcher matcher = FIELD.matcher(clean);
            String descriptor = matcher.matches() ? matcher.group(1) : clean;
            result.add(normalizeType(descriptor));
        }
        return result;
    }

    private static List<String> normalizedMethods(List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) result.add(methodShape(value));
        return result;
    }

    private static List<String> normalizedExternalCalls(List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            String clean = clean(value);
            int arrow = clean.indexOf("->");
            if (arrow <= 0) continue;
            String owner = clean.substring(0, arrow);
            if (isObfuscated(owner)) continue;
            String member = clean.substring(arrow + 2);
            result.add(normalizeType(owner) + "->" + methodShape(member));
        }
        return result;
    }

    /** Ignores the obfuscated method name but preserves the complete normalized prototype. */
    private static String methodShape(String descriptor) {
        String value = clean(descriptor);
        int arrow = value.indexOf("->");
        if (arrow >= 0) value = value.substring(arrow + 2);
        int open = value.indexOf('(');
        int close = value.indexOf(')', open + 1);
        if (open < 0 || close < 0 || close == value.length() - 1) return value;
        String args = normalizeTypeSequence(value.substring(open + 1, close));
        String returns = normalizeType(value.substring(close + 1));
        return "(" + args + ")" + returns;
    }

    private static String normalizeTypeSequence(String descriptors) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < descriptors.length()) {
            int start = index;
            while (index < descriptors.length() && descriptors.charAt(index) == '[') index++;
            if (index >= descriptors.length()) return result.append(descriptors.substring(start)).toString();
            char kind = descriptors.charAt(index);
            if (kind == 'L') {
                int end = descriptors.indexOf(';', index);
                if (end < 0) return result.append(descriptors.substring(start)).toString();
                result.append(normalizeType(descriptors.substring(start, end + 1)));
                index = end + 1;
            } else {
                result.append(descriptors.substring(start, index + 1));
                index++;
            }
        }
        return result.toString();
    }

    private static String normalizeType(String descriptor) {
        String value = clean(descriptor);
        int arrays = 0;
        while (arrays < value.length() && value.charAt(arrays) == '[') arrays++;
        String base = value.substring(arrays);
        if (isObfuscated(base)) base = "O";
        return repeat('[', arrays) + base;
    }

    private static boolean isObfuscated(String descriptor) {
        return OBFUSCATED.matcher(clean(descriptor)).matches();
    }

    private static double dice(List<String> left, List<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 1.0;
        Map<String, Integer> a = counts(left), b = counts(right);
        int common = 0;
        for (Map.Entry<String, Integer> entry : a.entrySet()) {
            common += Math.min(entry.getValue(), b.containsKey(entry.getKey())
                    ? b.get(entry.getKey()) : 0);
        }
        int total = left.size() + right.size();
        return total == 0 ? 1.0 : (2.0 * common) / total;
    }

    private static Map<String, Integer> counts(List<String> values) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (String value : values) {
            Integer old = result.get(value);
            result.put(value, old == null ? 1 : old + 1);
        }
        return result;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 1.0;
        Set<String> union = new LinkedHashSet<String>(left);
        union.addAll(right);
        Set<String> common = new LinkedHashSet<String>(left);
        common.retainAll(right);
        return union.isEmpty() ? 1.0 : (double) common.size() / union.size();
    }

    private static int intersection(List<String> left, List<String> right) {
        Set<String> common = new LinkedHashSet<String>(left);
        common.retainAll(new LinkedHashSet<String>(right));
        return common.size();
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static List<String> immutable(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
