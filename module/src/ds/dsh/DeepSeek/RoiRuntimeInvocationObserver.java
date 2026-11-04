package ds.dsh.DeepSeek;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Opt-in observer for real constructor and transport callbacks already installed by the host
 * adapter. It records no request contents and never installs hooks.
 */
final class RoiRuntimeInvocationObserver {
    private static final String ENABLE_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_roi_runtime_observation_enabled";
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final Map<Object, String> REQUEST_KEYS =
            new IdentityHashMap<Object, String>();
    private static final Map<Object, String> FLOW_REQUEST_KEYS =
            new IdentityHashMap<Object, String>();
    private static volatile RoiRuntimeInvocationRecorder recorder;
    private static volatile String hostFingerprint = "";
    private static volatile boolean recordingEnabled;

    private RoiRuntimeInvocationObserver() {}

    static synchronized void initialize() {
        clear();
        boolean approved = HostCompat.supportsMaintainedFeatureRuntime();
        boolean enabled = approved && new File(ENABLE_FILE).isFile();
        recordingEnabled = enabled;
        hostFingerprint = approved ? currentAdapterIdentity() : "";
        recorder = new RoiRuntimeInvocationRecorder(
                enabled
                        ? RoiRuntimeInvocationRecorder.Policy.trustedKnownHost(true, true)
                        : RoiRuntimeInvocationRecorder.Policy.disabled(),
                512, 120000L);
    }

    /** Returns the approved adapter label, never an APK/DEX fingerprint. */
    static String currentAdapterIdentity() {
        if (!HostCompat.supportsMaintainedFeatureRuntime()) return "";
        return "adapter:" + HostCompat.generationName() + ":"
                + (HostCompat.isInternationalChannel() ? "international" : "mainland");
    }

    /** Read-only, opt-in, bounded and TTL-pruned evidence with no request-key exposure. */
    static List<RoiRuntimeInvocationRecorder.EvidenceSnapshot> snapshotEvidence() {
        RoiRuntimeInvocationRecorder active = recorder;
        if (active == null || !activeEnabled(active)) return Collections.emptyList();
        try {
            return active.snapshotEvidence(System.currentTimeMillis());
        } catch (Throwable error) {
            rethrowFatal(error);
            return Collections.emptyList();
        }
    }

    static Object recordBuilt(Object request, Constructor<?> constructor) {
        try {
            RoiRuntimeInvocationRecorder active = recorder;
            if (request == null || constructor == null || active == null
                    || !activeEnabled(active)) return request;
            String key = "req-" + REQUEST_SEQUENCE.incrementAndGet();
            String descriptor = constructorDescriptor(constructor);
            long now = System.currentTimeMillis();
            synchronized (REQUEST_KEYS) {
                if (REQUEST_KEYS.size() >= 1024) REQUEST_KEYS.clear();
                REQUEST_KEYS.put(request, key);
            }
            active.record(new RoiRuntimeInvocationRecorder.Event(key, hostFingerprint,
                    descriptor, RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, now,
                    Thread.currentThread().getId(),
                    RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), now);
        } catch (Throwable ignored) {
            rethrowFatal(ignored);
            // Observation must never affect host request construction.
        }
        return request;
    }

    static void recordTransport(Object request, Method method) {
        try {
            RoiRuntimeInvocationRecorder active = recorder;
            if (request == null || method == null || active == null
                    || !activeEnabled(active)) return;
            String key;
            synchronized (REQUEST_KEYS) { key = REQUEST_KEYS.get(request); }
            if (key == null) return;
            long now = System.currentTimeMillis();
            active.record(new RoiRuntimeInvocationRecorder.Event(key, hostFingerprint,
                    methodDescriptor(method),
                    RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, now,
                    Thread.currentThread().getId(),
                    RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), now);
        } catch (Throwable ignored) {
            rethrowFatal(ignored);
            // Observation must never affect host transport execution.
        }
    }

    static void recordTransportResult(Object request, Object flow, Method transportMethod) {
        try {
            RoiRuntimeInvocationRecorder active = recorder;
            if (request == null || transportMethod == null || active == null
                    || !activeEnabled(active)) return;
            String key;
            synchronized (REQUEST_KEYS) { key = REQUEST_KEYS.remove(request); }
            if (key == null || flow == null) return;
            synchronized (FLOW_REQUEST_KEYS) {
                if (FLOW_REQUEST_KEYS.size() >= 512) FLOW_REQUEST_KEYS.clear();
                FLOW_REQUEST_KEYS.put(flow, key);
            }
        } catch (Throwable ignored) {
            rethrowFatal(ignored);
            // A failed correlation must not affect the original Flow.
        }
    }

    static Object wrapFlowCollector(final Object flow, final Object collector,
                                    Class<?> collectorType) {
        try {
            RoiRuntimeInvocationRecorder active = recorder;
            if (flow == null || collector == null || collectorType == null
                    || !collectorType.isInterface() || !collectorType.isInstance(collector)
                    || active == null || !activeEnabled(active)) return collector;
            final String key;
            synchronized (FLOW_REQUEST_KEYS) { key = FLOW_REQUEST_KEYS.remove(flow); }
            if (key == null) return collector;
            // Kotlin FlowCollector.emit is a suspend method. Do not proxy its collector:
            // the current observer does not wrap Continuation completion, and proxy identity
            // changes are unnecessary when no trustworthy response event can be emitted.
            if (hasSuspendingEmit(collectorType)) return collector;
            ClassLoader loader = collectorType.getClassLoader();
            if (loader == null) loader = collector.getClass().getClassLoader();
            return Proxy.newProxyInstance(loader, new Class<?>[]{collectorType},
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args)
                                throws Throwable {
                            if (method.getDeclaringClass() == Object.class) {
                                if ("equals".equals(method.getName())) {
                                    return Boolean.valueOf(proxy == (args == null ? null : args[0]));
                                }
                                if ("hashCode".equals(method.getName())) {
                                    return Integer.valueOf(System.identityHashCode(proxy));
                                }
                                if ("toString".equals(method.getName())) {
                                    return "RoiObservedFlowCollector";
                                }
                            }
                            try {
                                Object result = method.invoke(collector, args);
                                if ("emit".equals(method.getName()) && args != null
                                        && args.length > 0
                                        && !hasContinuationParameter(method)) {
                                    // A Kotlin suspend emit may return COROUTINE_SUSPENDED before
                                    // delivery completes. Without observing continuation success,
                                    // that return is not response-receipt evidence.
                                    recordResponseReceived(key, method);
                                }
                                return result;
                            } catch (InvocationTargetException error) {
                                throw error.getCause() == null ? error : error.getCause();
                            }
                        }
                    });
        } catch (Throwable ignored) {
            rethrowFatal(ignored);
            return collector;
        }
    }

    private static boolean hasSuspendingEmit(Class<?> collectorType) {
        for (Method method : collectorType.getMethods()) {
            if ("emit".equals(method.getName()) && hasContinuationParameter(method)) return true;
        }
        return false;
    }

    private static boolean hasContinuationParameter(Method method) {
        for (Class<?> parameter : method.getParameterTypes()) {
            if ("kotlin.coroutines.Continuation".equals(parameter.getName())) return true;
        }
        return false;
    }

    private static void recordResponseReceived(String key, Method emitMethod) {
        try {
            RoiRuntimeInvocationRecorder active = recorder;
            if (active == null || !activeEnabled(active)) return;
            long now = System.currentTimeMillis();
            active.record(new RoiRuntimeInvocationRecorder.Event(key, hostFingerprint,
                    methodDescriptor(emitMethod),
                    RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, now,
                    Thread.currentThread().getId(),
                    RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), now);
        } catch (Throwable ignored) {
            rethrowFatal(ignored);
            // Response observation is strictly best effort.
        }
    }

    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
        if (error instanceof ThreadDeath) throw (ThreadDeath) error;
    }

    static synchronized void clear() {
        RoiRuntimeInvocationRecorder previous = recorder;
        if (previous != null) previous.clear();
        synchronized (REQUEST_KEYS) { REQUEST_KEYS.clear(); }
        synchronized (FLOW_REQUEST_KEYS) { FLOW_REQUEST_KEYS.clear(); }
        recorder = null;
        hostFingerprint = "";
        recordingEnabled = false;
    }

    private static boolean activeEnabled(RoiRuntimeInvocationRecorder active) {
        return active != null && recordingEnabled && active == recorder;
    }

    private static String constructorDescriptor(Constructor<?> constructor) {
        StringBuilder out = new StringBuilder("L")
                .append(constructor.getDeclaringClass().getName().replace('.', '/'))
                .append(";-><init>(");
        for (Class<?> parameter : constructor.getParameterTypes()) appendType(out, parameter);
        return out.append(")V").toString();
    }

    private static String methodDescriptor(Method method) {
        StringBuilder out = new StringBuilder("L")
                .append(method.getDeclaringClass().getName().replace('.', '/'))
                .append(";->").append(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) appendType(out, parameter);
        out.append(')');
        appendType(out, method.getReturnType());
        return out.toString();
    }

    private static void appendType(StringBuilder out, Class<?> type) {
        if (type.isArray()) {
            out.append(type.getName().replace('.', '/'));
        } else if (!type.isPrimitive()) {
            out.append('L').append(type.getName().replace('.', '/')).append(';');
        } else if (type == void.class) out.append('V');
        else if (type == boolean.class) out.append('Z');
        else if (type == byte.class) out.append('B');
        else if (type == char.class) out.append('C');
        else if (type == short.class) out.append('S');
        else if (type == int.class) out.append('I');
        else if (type == long.class) out.append('J');
        else if (type == float.class) out.append('F');
        else if (type == double.class) out.append('D');
    }
}
