package ds.dsh.DeepSeek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only runtime inventory for the Open native completion adapter.
 *
 * This deliberately does not invoke host methods, construct requests, or read field values.
 * It records only class/member signatures so the adapter can be implemented against the
 * actual installed DeepSeek build instead of guessing obfuscated constructor layouts.
 */
final class OpenNativeCompletionProbe {
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private static final AtomicBoolean INSPECTING = new AtomicBoolean();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicInteger ATTEMPTS =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final int MAX_ATTEMPTS = 3;
    private static volatile String report = "not-run";

    private OpenNativeCompletionProbe() {}

    /**
     * Schedule the passive inventory once, without creating a worker for every startz1 call.
     * The probe is diagnostic only and must not run on the host startup/UI caller thread.
     */
    static void scheduleHostInspection(final ClassLoader loader) {
        if (!"Open".equals(BuildInfo.BUILD_EDITION)
                || BuildInfo.LOCAL_API_INCLUDED || loader == null
                || REPORTED.get() || ATTEMPTS.get() >= MAX_ATTEMPTS
                || !SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                runBoundedInspection(loader);
            } finally {
                SCHEDULED.set(false);
            }
        }, "dsh-native-completion-probe");
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (RuntimeException | Error error) {
            SCHEDULED.set(false);
            throw error;
        }
    }

    /**
     * Retry transient or partial inventories in the same daemon worker. This avoids depending
     * on another startz1() callback, which may never occur after the first launch.
     */
    private static void runBoundedInspection(ClassLoader loader) {
        while (!REPORTED.get() && ATTEMPTS.get() < MAX_ATTEMPTS) {
            int completedAttempts = ATTEMPTS.get();
            if (completedAttempts > 0) {
                try {
                    Thread.sleep(completedAttempts == 1 ? 1000L : 3000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (REPORTED.get()) return;
            ATTEMPTS.incrementAndGet();
            inspectHost(loader);
        }
    }

    /**
     * Runs before any legacy q71 capture hook is available. This is the entry point for
     * 2.7.x, where the older network/PoW hook installers are intentionally disabled.
     */
    @SuppressWarnings("unchecked")
    static void inspectHost(ClassLoader loader) {
        if (!"Open".equals(BuildInfo.BUILD_EDITION)
                || BuildInfo.LOCAL_API_INCLUDED || loader == null
                || REPORTED.get() || !INSPECTING.compareAndSet(false, true)) {
            return;
        }
        StringBuilder out = new StringBuilder(8192);
        boolean completed = false;
        final long startedNanos = System.nanoTime();
        int eligibleClassNames = 0;
        int classLoadFailures = 0;
        try {
            line(out, "report.schema", "2");
            Class<?> requestType = HostCompat.load(loader, "ew0");
            line(out, "edition", BuildInfo.BUILD_EDITION);
            line(out, "host", HostCompat.diagnosticSummary());
            line(out, "request.class", requestType.getName());
            line(out, "request.interfaces", interfaces(requestType));
            appendConstructors(out, requestType);
            appendFields(out, "request", requestType, 2);

            List<String> names = null;
            try {
                Method lister = Main.class.getDeclaredMethod(
                        "listDexClasses", ClassLoader.class);
                lister.setAccessible(true);
                Object value = lister.invoke(Main.MODULE, loader);
                if (value instanceof List) names = (List<String>) value;
            } catch (Throwable error) {
                line(out, "dex.scan.error", error.getClass().getName());
            }
            if (names == null) {
                line(out, "dex.scan", "unavailable");
            } else {
                line(out, "dex.classCount", String.valueOf(names.size()));
                int scanned = 0;
                int candidates = 0;
                int managerCandidates = 0;
                java.util.HashSet<Class<?>> transportOwners =
                        new java.util.HashSet<Class<?>>();
                for (String name : names) {
                    if (name == null || name.indexOf('.') >= 0 || name.length() > 6) continue;
                    eligibleClassNames++;
                    Class<?> owner;
                    try {
                        owner = HostRemap.forDexName(name, loader);
                    } catch (Throwable ignored) {
                        classLoadFailures++;
                        continue;
                    }
                    scanned++;
                    for (Method method : owner.getDeclaredMethods()) {
                        Class<?>[] p = method.getParameterTypes();
                        if (p.length != 2 || p[1] != Long.class
                                || !p[0].isAssignableFrom(requestType)
                                || method.getReturnType().isPrimitive()
                                || method.getReturnType() == Void.TYPE) {
                            continue;
                        }
                        if (candidates < 24) {
                            Type generic = method.getGenericReturnType();
                            line(out, "transport.candidate." + candidates,
                                    owner.getName() + "." + method.getName()
                                            + "(" + parameters(p) + ")->"
                                            + method.getReturnType().getName()
                                            + " generic=" + String.valueOf(generic));
                            appendFields(out, "transport" + candidates, owner, 1);
                        }
                        candidates++;
                        transportOwners.add(owner);
                    }
                }
                line(out, "dex.eligibleClassNameCount", String.valueOf(eligibleClassNames));
                line(out, "dex.scannedClassCount", String.valueOf(scanned));
                line(out, "dex.classLoadFailureCount", String.valueOf(classLoadFailures));
                line(out, "transport.candidateCount", String.valueOf(candidates));

                // q71 in earlier host builds owned an r92 transport in an instance field.
                // Find equivalent 2.7.x manager candidates structurally without instantiating
                // classes or reading any live object values.
                if (!transportOwners.isEmpty()) {
                    for (String name : names) {
                        if (name == null || name.indexOf('.') >= 0 || name.length() > 6) continue;
                        Class<?> owner;
                        try {
                            owner = HostRemap.forDexName(name, loader);
                        } catch (Throwable ignored) {
                            continue;
                        }
                        for (Field field : owner.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers())
                                    || field.getType().isPrimitive()) continue;
                            for (Class<?> transportOwner : transportOwners) {
                                if (field.getType().isAssignableFrom(transportOwner)
                                        || transportOwner.isAssignableFrom(field.getType())) {
                                    if (managerCandidates < 24) {
                                        line(out, "manager.candidate." + managerCandidates,
                                                owner.getName() + "." + field.getName()
                                                        + ":" + field.getType().getName());
                                        appendConstructors(out, owner);
                                    }
                                    managerCandidates++;
                                    break;
                                }
                            }
                        }
                    }
                }
                line(out, "manager.candidateCount", String.valueOf(managerCandidates));
                // Per-class load failures mean the inventory is partial, not a terminal result.
                completed = classLoadFailures == 0;
            }
        } catch (Throwable error) {
            line(out, "probe.error", error.getClass().getName() + ": "
                    + String.valueOf(error.getMessage()));
        } finally {
            line(out, "probe.elapsedMs", String.valueOf(
                    (System.nanoTime() - startedNanos) / 1_000_000L));
        }
        report = out.toString();
        // Only a complete DEX inventory is terminal. Class-load/scan failures remain retryable.
        if (completed) REPORTED.set(true);
        INSPECTING.set(false);
        Main.extLog("[OPEN_NATIVE_COMPLETION_PROBE]\n" + report);
    }

    /** Legacy object-capture entry point, retained for hosts with the older q71 mapping. */
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
