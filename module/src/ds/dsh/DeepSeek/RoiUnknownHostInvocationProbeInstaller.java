package ds.dsh.DeepSeek;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installs diagnostic around-hooks only for fully evidenced, exact runtime methods.
 * It has no candidate discovery of its own: callers must supply a separately reviewed proof.
 */
final class RoiUnknownHostInvocationProbeInstaller {
    interface HookRegistrar {
        void install(Method method, InvocationCallback callback) throws Throwable;
    }

    interface InvocationCallback {
        Object invoke(RoiUnknownHostInvocationProbe.ProceedingCall call) throws Throwable;
    }

    private final RoiUnknownHostInvocationProbe probe;
    private final Set<String> installed = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    RoiUnknownHostInvocationProbeInstaller(RoiUnknownHostInvocationProbe probe) {
        if (probe == null) throw new IllegalArgumentException("probe is required");
        this.probe = probe;
    }

    synchronized boolean install(File switchFile,
            RoiUnknownHostInvocationProbePolicy.Candidate candidate,
            Method method, ClassLoader hostRoot, HookRegistrar registrar) throws Throwable {
        if (registrar == null || method == null || candidate == null || hostRoot == null) {
            return false;
        }
        if (!RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled(switchFile)) return false;
        if (Modifier.isAbstract(method.getModifiers()) || method.isSynthetic()
                || method.isBridge()) return false;

        Class<?> declaringClass = method.getDeclaringClass();
        ClassLoader methodLoader = declaringClass.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(methodLoader);
        if (!isWithinHostLoader(methodLoader, hostRoot)) return false;

        RoiLoadedClassInspector.Snapshot snapshot = RoiLoadedClassInspector.inspect(declaringClass);
        if (snapshot == null || snapshot.methodsTruncated
                || !declaringClass.getName().equals(snapshot.className)
                || !scope.equals(snapshot.sourceScope)) return false;
        if (!snapshot.dexMethodDescriptors.contains(candidate.descriptor)) return false;

        String owner = "L" + declaringClass.getName().replace('.', '/') + ";";
        RoiUnknownHostInvocationProbePolicy.RuntimeMethod runtime =
                new RoiUnknownHostInvocationProbePolicy.RuntimeMethod(
                        owner, candidate.descriptor, scope);
        if (!RoiUnknownHostInvocationProbePolicy.allows(true, candidate, runtime)) return false;

        String installKey = scope + "|" + candidate.descriptor;
        if (installed.contains(installKey)) return true;
        if (!probe.register(candidate.descriptor)) return false;
        try {
            final String descriptor = candidate.descriptor;
            final File liveSwitch = switchFile;
            registrar.install(method, new InvocationCallback() {
                @Override public Object invoke(RoiUnknownHostInvocationProbe.ProceedingCall call)
                        throws Throwable {
                    if (!RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled(liveSwitch)) {
                        return call.proceed();
                    }
                    return probe.invoke(descriptor, call);
                }
            });
            installed.add(installKey);
            return true;
        } catch (Throwable installationFailure) {
            probe.unregister(candidate.descriptor);
            throw installationFailure;
        }
    }

    synchronized int installedCount() { return installed.size(); }

    private static boolean isWithinHostLoader(ClassLoader candidate, ClassLoader hostRoot) {
        if (candidate == null || hostRoot == null) return false;
        ClassLoader cursor = candidate;
        for (int depth = 0; cursor != null && depth < 64; depth++) {
            if (cursor == hostRoot) return true;
            cursor = cursor.getParent();
        }
        return false;
    }
}
