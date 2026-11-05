package ds.dsh.DeepSeek;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connects read-only DexKit candidates to already-loaded runtime classes and installs
 * pass-through invocation diagnostics. It never loads host classes or activates feature hooks.
 */
final class RoiUnknownHostAutoProbeCoordinator {
    static final int MAX_CANDIDATES = 96;
    static final int MAX_OBSERVED_CLASSES = 128;

    interface Installer {
        boolean install(RoiUnknownHostInvocationProbePolicy.Candidate candidate,
                        Method method) throws Throwable;
    }

    private static final class CandidateSpec {
        final String owner;
        final String methodName;
        final String descriptor;
        final String sourceScope;
        final RoiUnknownHostInvocationProbePolicy.Role role;
        final boolean callGraphObserved;

        CandidateSpec(String owner, String methodName, String descriptor, String sourceScope,
                      RoiUnknownHostInvocationProbePolicy.Role role,
                      boolean callGraphObserved) {
            this.owner = clean(owner);
            this.methodName = clean(methodName);
            this.descriptor = clean(descriptor);
            this.sourceScope = clean(sourceScope);
            this.role = role;
            this.callGraphObserved = callGraphObserved;
        }

        String key() { return sourceScope + "|" + descriptor; }
    }

    private final Map<String, CandidateSpec> candidates =
            new LinkedHashMap<String, CandidateSpec>();
    private final List<WeakReference<Class<?>>> observedClasses =
            new ArrayList<WeakReference<Class<?>>>();
    private final Set<String> installed = new LinkedHashSet<String>();

    synchronized int registerCandidate(String owner, String methodName, String descriptor,
            String sourceScope, RoiUnknownHostInvocationProbePolicy.Role role,
            boolean callGraphObserved, ClassLoader hostRoot, Installer installer)
            throws Throwable {
        if (owner == null || methodName == null || descriptor == null || sourceScope == null
                || role == null || role == RoiUnknownHostInvocationProbePolicy.Role.UNRESOLVED
                || !sourceScope.startsWith("loader:") || !callGraphObserved
                || !validIdentity(owner, methodName, descriptor)) return 0;
        CandidateSpec spec = new CandidateSpec(owner, methodName, descriptor, sourceScope,
                role, true);
        candidates.put(spec.key(), spec);
        while (candidates.size() > MAX_CANDIDATES) {
            String eldest = candidates.keySet().iterator().next();
            candidates.remove(eldest);
            installed.remove(eldest);
        }
        return reconcileObserved(hostRoot, installer);
    }

    synchronized int observeLoadedClass(Class<?> type, ClassLoader hostRoot,
                                        Installer installer) throws Throwable {
        if (type == null || hostRoot == null || installer == null
                || !withinHostLoader(type.getClassLoader(), hostRoot)) return 0;
        pruneObserved();
        boolean known = false;
        for (WeakReference<Class<?>> reference : observedClasses) {
            if (reference.get() == type) { known = true; break; }
        }
        if (!known) {
            if (observedClasses.size() >= MAX_OBSERVED_CLASSES) observedClasses.remove(0);
            observedClasses.add(new WeakReference<Class<?>>(type));
        }
        return installForClass(type, hostRoot, installer);
    }

    private int reconcileObserved(ClassLoader hostRoot, Installer installer) throws Throwable {
        if (hostRoot == null || installer == null) return 0;
        pruneObserved();
        int count = 0;
        for (WeakReference<Class<?>> reference : observedClasses) {
            Class<?> type = reference.get();
            if (type != null && withinHostLoader(type.getClassLoader(), hostRoot)) {
                count += installForClass(type, hostRoot, installer);
            }
        }
        return count;
    }

    private int installForClass(Class<?> type, ClassLoader hostRoot, Installer installer)
            throws Throwable {
        RoiLoadedClassInspector.Snapshot snapshot = RoiLoadedClassInspector.inspect(type);
        if (snapshot == null || snapshot.methodsTruncated
                || !"metadata_only".equals(snapshot.inspectionStatus)) return 0;
        String owner = "L" + type.getName().replace('.', '/') + ";";
        String scope = snapshot.loaderScope;
        Map<String, Method> runtimeMethods = new LinkedHashMap<String, Method>();
        try {
            for (Method method : type.getDeclaredMethods()) {
                if (method == null || Modifier.isAbstract(method.getModifiers())
                        || method.isSynthetic() || method.isBridge()) continue;
                runtimeMethods.put(methodDescriptor(method), method);
            }
        } catch (LinkageError | SecurityException unavailable) {
            return 0;
        }

        int installedNow = 0;
        for (CandidateSpec spec : candidates.values()) {
            if (!owner.equals(spec.owner) || !scope.equals(spec.sourceScope)
                    || installed.contains(spec.key())) continue;
            Method method = runtimeMethods.get(spec.descriptor);
            if (method == null || !spec.methodName.equals(method.getName())) continue;
            RoiUnknownHostInvocationProbePolicy.Candidate candidate =
                    new RoiUnknownHostInvocationProbePolicy.Candidate(
                            spec.owner, spec.methodName, spec.descriptor, spec.sourceScope,
                            spec.role, true, false, false, true, spec.callGraphObserved);
            if (installer.install(candidate, method)) {
                installed.add(spec.key());
                installedNow++;
            }
        }
        return installedNow;
    }

    synchronized int candidateCount() { return candidates.size(); }
    synchronized int observedClassCount() { pruneObserved(); return observedClasses.size(); }
    synchronized int installedCount() { return installed.size(); }

    private void pruneObserved() {
        Iterator<WeakReference<Class<?>>> iterator = observedClasses.iterator();
        while (iterator.hasNext()) if (iterator.next().get() == null) iterator.remove();
    }

    private static boolean validIdentity(String owner, String method, String descriptor) {
        if (!owner.startsWith("L") || !owner.endsWith(";") || method.trim().isEmpty()) return false;
        int ownerEnd = descriptor.indexOf(";->");
        if (ownerEnd < 0 || !owner.equals(descriptor.substring(0, ownerEnd + 1))) return false;
        int open = descriptor.indexOf('(', ownerEnd + 3);
        return open > ownerEnd + 3
                && method.equals(descriptor.substring(ownerEnd + 3, open));
    }

    private static boolean withinHostLoader(ClassLoader candidate, ClassLoader root) {
        if (candidate == null || root == null) return false;
        for (ClassLoader cursor = candidate; cursor != null; cursor = cursor.getParent()) {
            if (cursor == root) return true;
        }
        return false;
    }

    private static String methodDescriptor(Method method) {
        StringBuilder out = new StringBuilder();
        out.append("L").append(method.getDeclaringClass().getName().replace('.', '/'))
                .append(";->").append(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) out.append(typeDescriptor(parameter));
        return out.append(')').append(typeDescriptor(method.getReturnType())).toString();
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

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
