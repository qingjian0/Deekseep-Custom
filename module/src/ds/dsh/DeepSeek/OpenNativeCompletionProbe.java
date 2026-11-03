package ds.dsh.DeepSeek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only runtime inventory for the Open native completion adapter.
 *
 * This deliberately does not invoke host methods, construct requests, or read field values.
 * It records only class/member signatures so the adapter can be implemented against the
 * actual installed DeepSeek build instead of guessing obfuscated constructor layouts.
 */
final class OpenNativeCompletionProbe {
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static volatile String report = "not-run";

    private OpenNativeCompletionProbe() {}

    static void inspect(ClassLoader loader, Object powManager, Object transport) {
        if (!"Open".equals(BuildInfo.BUILD_EDITION)
                || BuildInfo.LOCAL_API_INCLUDED || loader == null
                || powManager == null || transport == null
                || !REPORTED.compareAndSet(false, true)) {
            return;
        }
        StringBuilder out = new StringBuilder(4096);
        try {
            Class<?> requestType = HostCompat.load(loader, "ew0");
            Class<?> transportRequestType = HostCompat.load(loader, "rs0");
            line(out, "edition", BuildInfo.BUILD_EDITION);
            line(out, "host", HostCompat.diagnosticSummary());
            line(out, "request.class", requestType.getName());
            line(out, "request.interfaces", interfaces(requestType));
            appendConstructors(out, requestType);
            appendFields(out, "request", requestType, 2);
            line(out, "transport.class", transport.getClass().getName());
            line(out, "transport.requestType", transportRequestType.getName());
            appendTransportMethods(out, transport.getClass(), transportRequestType);
            line(out, "pow.class", powManager.getClass().getName());
            appendFields(out, "pow", powManager.getClass(), 2);
            appendPowMethods(out, powManager.getClass());
        } catch (Throwable error) {
            line(out, "probe.error", error.getClass().getName() + ": "
                    + String.valueOf(error.getMessage()));
        }
        report = out.toString();
        Main.extLog("[OPEN_NATIVE_COMPLETION_PROBE]\n" + report);
    }

    static String status() {
        return report;
    }

    private static void appendConstructors(StringBuilder out, Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        line(out, "request.constructorCount", String.valueOf(constructors.length));
        for (int i = 0; i < constructors.length && i < 24; i++) {
            Constructor<?> constructor = constructors[i];
            line(out, "request.ctor." + i, parameters(constructor.getParameterTypes()));
        }
    }

    private static void appendFields(StringBuilder out, String prefix,
                                     Class<?> type, int maxDepth) {
        Class<?> current = type;
        int depth = 0;
        int count = 0;
        while (current != null && current != Object.class && depth <= maxDepth) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                line(out, prefix + ".field." + count++,
                        current.getSimpleName() + "." + field.getName()
                                + ":" + field.getType().getName());
                if (count >= 48) break;
            }
            if (count >= 48) break;
            current = current.getSuperclass();
            depth++;
        }
    }

    private static void appendTransportMethods(StringBuilder out, Class<?> type,
                                               Class<?> requestType) {
        int count = 0;
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 2 || parameters[1] != Long.class
                        || !parameters[0].isAssignableFrom(requestType)
                                && !requestType.isAssignableFrom(parameters[0])) {
                    continue;
                }
                Type generic = method.getGenericReturnType();
                line(out, "transport.candidate." + count++,
                        current.getSimpleName() + "." + method.getName()
                                + "(" + parameters(parameters) + ")->"
                                + method.getReturnType().getName()
                                + " generic=" + String.valueOf(generic));
            }
        }
        line(out, "transport.candidateCount", String.valueOf(count));
    }

    private static void appendPowMethods(StringBuilder out, Class<?> type) {
        int count = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (count >= 40) break;
            Class<?>[] parameters = method.getParameterTypes();
            line(out, "pow.method." + count++,
                    method.getName() + "(" + parameters(parameters) + ")->"
                            + method.getReturnType().getName());
        }
        line(out, "pow.methodCount", String.valueOf(count));
    }

    private static String interfaces(Class<?> type) {
        Class<?>[] values = type.getInterfaces();
        StringBuilder out = new StringBuilder();
        for (Class<?> value : values) {
            if (out.length() > 0) out.append(',');
            out.append(value.getName());
        }
        return out.toString();
    }

    private static String parameters(Class<?>[] types) {
        StringBuilder out = new StringBuilder();
        for (Class<?> type : types) {
            if (out.length() > 0) out.append(',');
            out.append(type.getName());
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String key, String value) {
        if (out.length() > 12_000) return;
        out.append(key).append('=').append(value == null ? "" : value).append('\n');
    }
}
