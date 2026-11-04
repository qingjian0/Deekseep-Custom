package ds.dsh.DeepSeek;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
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
    private static volatile RoiRuntimeInvocationRecorder recorder;
    private static volatile String hostFingerprint = "";

    private RoiRuntimeInvocationObserver() {}

    static synchronized void initialize() {
        clear();
        boolean approved = HostCompat.supportsMaintainedFeatureRuntime();
        boolean enabled = approved && new File(ENABLE_FILE).isFile();
        hostFingerprint = approved
                ? "adapter:" + HostCompat.generationName() + ":"
                        + (HostCompat.isInternationalChannel() ? "international" : "mainland")
                : "";
        recorder = new RoiRuntimeInvocationRecorder(
                enabled
                        ? RoiRuntimeInvocationRecorder.Policy.trustedKnownHost(true, true)
                        : RoiRuntimeInvocationRecorder.Policy.disabled(),
                512, 120000L);
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
                REQUEST_KEYS.put(request, key);
                if (REQUEST_KEYS.size() > 1024) REQUEST_KEYS.clear();
                REQUEST_KEYS.put(request, key);
            }
            active.record(new RoiRuntimeInvocationRecorder.Event(key, hostFingerprint,
                    descriptor, RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, now,
                    Thread.currentThread().getId(),
                    RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), now);
        } catch (Throwable ignored) {
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
            // Observation must never affect host transport execution.
        }
    }

    static synchronized void clear() {
        RoiRuntimeInvocationRecorder previous = recorder;
        if (previous != null) previous.clear();
        synchronized (REQUEST_KEYS) { REQUEST_KEYS.clear(); }
        recorder = null;
        hostFingerprint = "";
    }

    private static boolean activeEnabled(RoiRuntimeInvocationRecorder active) {
        return active != null && !active.snapshotChains(System.currentTimeMillis()).isEmpty()
                || active != null && new File(ENABLE_FILE).isFile()
                        && HostCompat.supportsMaintainedFeatureRuntime();
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
