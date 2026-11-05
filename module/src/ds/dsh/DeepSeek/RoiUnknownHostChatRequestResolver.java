package ds.dsh.DeepSeek;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers the DeepSeek ChatFullCompletionRequest constructor from serializer field-name
 * anchors and its exact DEX constructor reference, then binds it to an already-loaded runtime
 * class in the same ClassLoader scope. This resolver never loads or invokes host classes.
 */
final class RoiUnknownHostChatRequestResolver {
    static final int MAX_CANDIDATES = 16;
    static final int MAX_OBSERVED_CLASSES = 128;
    static final int MIN_FIELD_ANCHORS = 4;

    static final String[] FIELD_ANCHORS = {
            "chat_session_id", "parent_message_id", "thinking_enabled",
            "search_enabled", "preempt"
    };

    interface Installer {
        boolean install(Constructor<?> constructor) throws Throwable;
    }

    static final class Candidate {
        final String ownerDescriptor;
        final String constructorDescriptor;
        final String sourceScope;
        final Set<String> fieldAnchors;
        final Set<String> serializerMethods;

        Candidate(String ownerDescriptor, String constructorDescriptor, String sourceScope,
                  Set<String> fieldAnchors, Set<String> serializerMethods) {
            this.ownerDescriptor = ownerDescriptor;
            this.constructorDescriptor = constructorDescriptor;
            this.sourceScope = sourceScope;
            this.fieldAnchors = Collections.unmodifiableSet(
                    new LinkedHashSet<String>(fieldAnchors));
            this.serializerMethods = Collections.unmodifiableSet(
                    new LinkedHashSet<String>(serializerMethods));
        }

        int evidenceCount() { return fieldAnchors.size(); }
    }

    private static final class MutableCandidate {
        final String owner;
        final String constructorDescriptor;
        final String scope;
        final Set<String> anchors = new LinkedHashSet<String>();
        final Set<String> serializerMethods = new LinkedHashSet<String>();

        MutableCandidate(String owner, String descriptor, String scope) {
            this.owner = owner;
            this.constructorDescriptor = descriptor;
            this.scope = scope;
        }

        boolean ready() {
            return anchors.contains("chat_session_id")
                    && anchors.contains("thinking_enabled")
                    && anchors.contains("search_enabled")
                    && (anchors.contains("parent_message_id") || anchors.contains("preempt"))
                    && anchors.size() >= MIN_FIELD_ANCHORS
                    && !serializerMethods.isEmpty();
        }

        Candidate snapshot() {
            return new Candidate(owner, constructorDescriptor, scope, anchors, serializerMethods);
        }
    }

    private final Map<String, MutableCandidate> candidates =
            new LinkedHashMap<String, MutableCandidate>();
    private final List<WeakReference<Class<?>>> observed =
            new ArrayList<WeakReference<Class<?>>>();
    private final Set<String> installed = new LinkedHashSet<String>();

    synchronized void observeDexCandidate(String sourceScope, String anchor,
            String serializerMethodDescriptor, List<String> directInvokes) {
        if (sourceScope == null || !sourceScope.startsWith("loader:")
                || !isFieldAnchor(anchor) || serializerMethodDescriptor == null
                || serializerMethodDescriptor.length() == 0 || directInvokes == null) return;
        for (String invoke : directInvokes) {
            if (!isChatRequestConstructor(invoke)) continue;
            int arrow = invoke.indexOf("->");
            String owner = invoke.substring(0, arrow);
            String key = sourceScope + "|" + owner + "|" + invoke;
            MutableCandidate candidate = candidates.get(key);
            if (candidate == null) {
                if (candidates.size() >= MAX_CANDIDATES) return;
                candidate = new MutableCandidate(owner, invoke, sourceScope);
                candidates.put(key, candidate);
            }
            candidate.anchors.add(anchor);
            candidate.serializerMethods.add(serializerMethodDescriptor);
        }
    }

    synchronized Candidate bestCandidate(String sourceScope) {
        MutableCandidate best = null;
        int bestScore = -1;
        boolean tied = false;
        for (MutableCandidate candidate : candidates.values()) {
            if (!candidate.scope.equals(sourceScope) || !candidate.ready()) continue;
            int score = candidate.anchors.size();
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                tied = false;
            } else if (score == bestScore) {
                tied = true;
            }
        }
        return best == null || tied ? null : best.snapshot();
    }

    synchronized int registerCandidate(Candidate candidate, ClassLoader hostRoot,
            Installer installer) throws Throwable {
        if (candidate == null || hostRoot == null || installer == null
                || !candidateReady(candidate)) return 0;
        int installedNow = 0;
        pruneObserved();
        for (WeakReference<Class<?>> reference : observed) {
            Class<?> type = reference.get();
            if (type != null) installedNow += installForClass(type, candidate, hostRoot, installer);
        }
        return installedNow;
    }

    synchronized int observeLoadedClass(Class<?> type, ClassLoader hostRoot,
            Installer installer) throws Throwable {
        if (type == null || hostRoot == null || installer == null
                || !withinHostLoader(type.getClassLoader(), hostRoot)) return 0;
        pruneObserved();
        boolean known = false;
        for (WeakReference<Class<?>> reference : observed) {
            if (reference.get() == type) { known = true; break; }
        }
        if (!known) {
            if (observed.size() >= MAX_OBSERVED_CLASSES) observed.remove(0);
            observed.add(new WeakReference<Class<?>>(type));
        }
        int installedNow = 0;
        for (MutableCandidate mutable : candidates.values()) {
            if (!mutable.ready()) continue;
            installedNow += installForClass(type, mutable.snapshot(), hostRoot, installer);
        }
        return installedNow;
    }

    private int installForClass(Class<?> type, Candidate candidate, ClassLoader hostRoot,
                                Installer installer) throws Throwable {
        if (type == null || !withinHostLoader(type.getClassLoader(), hostRoot)
                || !candidate.sourceScope.equals(
                        RoiCandidateIdentity.forClassLoader(type.getClassLoader()))
                || !candidate.ownerDescriptor.equals(
                        "L" + type.getName().replace('.', '/') + ";")) return 0;
        Constructor<?> target = null;
        try {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (!candidate.constructorDescriptor.equals(constructorDescriptor(constructor))) continue;
                if (!hasExpectedConstructorShape(constructor.getParameterTypes())) return 0;
                target = constructor;
                break;
            }
        } catch (LinkageError | SecurityException unavailable) {
            return 0;
        }
        if (target == null) return 0;
        String key = candidate.sourceScope + "|" + candidate.constructorDescriptor;
        if (installed.contains(key)) return 0;
        if (installer.install(target)) {
            installed.add(key);
            return 1;
        }
        return 0;
    }

    synchronized int candidateCount() { return candidates.size(); }
    synchronized int observedClassCount() { pruneObserved(); return observed.size(); }
    synchronized int installedCount() { return installed.size(); }

    static boolean isChatRequestConstructor(String descriptor) {
        if (descriptor == null || descriptor.length() > 4096) return false;
        int arrow = descriptor.indexOf("->");
        int open = descriptor.indexOf('(', arrow + 2);
        int close = descriptor.indexOf(')', open + 1);
        if (arrow <= 1 || open <= arrow + 2 || close < 0
                || !"<init>".equals(descriptor.substring(arrow + 2, open))
                || close != descriptor.length() - 2
                || !"V".equals(descriptor.substring(close + 1))) return false;
        List<String> parameters = parseParameters(descriptor, open + 1, close);
        return parameters != null && hasExpectedConstructorShape(parameters);
    }

    static boolean hasExpectedConstructorShape(Class<?>[] parameters) {
        if (parameters == null || parameters.length != 11) return false;
        return parameters[0] == String.class
                && parameters[2] == String.class
                && parameters[4] == boolean.class
                && parameters[5] == boolean.class
                && parameters[7] == boolean.class
                && parameters[8] == String.class
                && parameters[9] == String.class
                && parameters[10] == int.class;
    }

    private static boolean hasExpectedConstructorShape(List<String> p) {
        return p.size() == 11
                && "Ljava/lang/String;".equals(p.get(0))
                && "Ljava/lang/String;".equals(p.get(2))
                && "Z".equals(p.get(4))
                && "Z".equals(p.get(5))
                && "Z".equals(p.get(7))
                && "Ljava/lang/String;".equals(p.get(8))
                && "Ljava/lang/String;".equals(p.get(9))
                && "I".equals(p.get(10));
    }

    private static List<String> parseParameters(String descriptor, int start, int end) {
        List<String> result = new ArrayList<String>();
        int cursor = start;
        while (cursor < end) {
            int next = consumeType(descriptor, cursor, end);
            if (next <= cursor) return null;
            result.add(descriptor.substring(cursor, next));
            if (result.size() > 32) return null;
            cursor = next;
        }
        return cursor == end ? result : null;
    }

    private static int consumeType(String value, int start, int end) {
        if (start >= end) return -1;
        int cursor = start;
        while (cursor < end && value.charAt(cursor) == '[') cursor++;
        if (cursor >= end) return -1;
        char type = value.charAt(cursor);
        if ("ZBCSIJFD".indexOf(type) >= 0) return cursor + 1;
        if (type != 'L') return -1;
        int semicolon = value.indexOf(';', cursor + 1);
        return semicolon > cursor + 1 && semicolon < end ? semicolon + 1 : -1;
    }

    private static String constructorDescriptor(Constructor<?> constructor) {
        StringBuilder out = new StringBuilder();
        out.append('L').append(constructor.getDeclaringClass().getName().replace('.', '/'))
                .append(";-><init>(");
        for (Class<?> parameter : constructor.getParameterTypes()) out.append(typeDescriptor(parameter));
        return out.append(")V").toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        return "?";
    }

    private static boolean candidateReady(Candidate candidate) {
        return candidate.fieldAnchors != null
                && candidate.fieldAnchors.contains("chat_session_id")
                && candidate.fieldAnchors.contains("thinking_enabled")
                && candidate.fieldAnchors.contains("search_enabled")
                && (candidate.fieldAnchors.contains("parent_message_id")
                    || candidate.fieldAnchors.contains("preempt"))
                && candidate.fieldAnchors.size() >= MIN_FIELD_ANCHORS
                && candidate.serializerMethods != null && !candidate.serializerMethods.isEmpty()
                && candidate.ownerDescriptor != null
                && candidate.constructorDescriptor != null
                && candidate.sourceScope != null && candidate.sourceScope.startsWith("loader:");
    }

    private static boolean isFieldAnchor(String value) {
        if (value == null) return false;
        for (String anchor : FIELD_ANCHORS) if (anchor.equals(value)) return true;
        return false;
    }

    private void pruneObserved() {
        Iterator<WeakReference<Class<?>>> iterator = observed.iterator();
        while (iterator.hasNext()) if (iterator.next().get() == null) iterator.remove();
    }

    private static boolean withinHostLoader(ClassLoader candidate, ClassLoader root) {
        if (candidate == null || root == null) return false;
        ClassLoader cursor = candidate;
        for (int depth = 0; cursor != null && depth < 64; depth++, cursor = cursor.getParent()) {
            if (cursor == root) return true;
        }
        return false;
    }
}
