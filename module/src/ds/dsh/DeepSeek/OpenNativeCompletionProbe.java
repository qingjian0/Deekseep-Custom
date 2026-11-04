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
    private static final java.util.concurrent.atomic.AtomicBoolean OBSERVATION_JW3 =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean OBSERVATION_ZN3 =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicLong OBSERVATION_IDS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<ObservationFrame> OBSERVATION_FRAME =
            new ThreadLocal<ObservationFrame>();

    private static final class ObservationFrame {
        final long id;
        int depth;
        ObservationFrame(long id) { this.id = id; }
    }


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
            appendLoaderChain(out, loader);
            line(out, "dex.scan.scope", "supplied BaseDexClassLoader pathList.dexElements only; parent and separate split/dynamic loaders are not recursively enumerated");
            line(out, "request.class", requestType.getName());
            line(out, "request.interfaces", interfaces(requestType));
            appendConstructors(out, "request", requestType);
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
                int exactManagerFields = 0;
                int interfaceManagerFields = 0;
                int supertypeManagerFields = 0;
                int weakObjectFields = 0;
                List<Class<?>> scannedTypes = new java.util.ArrayList<Class<?>>();
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
                    scannedTypes.add(owner);
                    Method[] declaredMethods;
                    try {
                        declaredMethods = owner.getDeclaredMethods();
                    } catch (LinkageError | SecurityException error) {
                        classLoadFailures++;
                        line(out, "dex.methodInventoryError." + scanned,
                                error.getClass().getName());
                        continue;
                    }
                    int methodIndex = 0;
                    for (Method method : declaredMethods) {
                        Class<?>[] p;
                        Class<?> returnType;
                        try {
                            p = method.getParameterTypes();
                            returnType = method.getReturnType();
                        } catch (LinkageError | RuntimeException error) {
                            classLoadFailures++;
                            line(out, "dex.methodMetadataError." + scanned + "." + methodIndex,
                                    error.getClass().getName());
                            methodIndex++;
                            continue;
                        }
                        if (p.length != 2 || p[1] != Long.class
                                || !p[0].isAssignableFrom(requestType)
                                || returnType.isPrimitive() || returnType == Void.TYPE) {
                            methodIndex++;
                            continue;
                        }
                        if (candidates < 4) {
                            String generic;
                            try {
                                generic = String.valueOf(method.getGenericReturnType());
                            } catch (LinkageError | RuntimeException error) {
                                classLoadFailures++;
                                generic = "<unavailable:" + error.getClass().getName() + ">";
                                line(out, "dex.genericMetadataError." + scanned + "." + methodIndex,
                                        error.getClass().getName());
                            }
                            line(out, "transport.candidate." + candidates,
                                    owner.getName() + "." + method.getName()
                                            + "(" + parameters(p) + ")->"
                                            + returnType.getName()
                                            + " generic=" + generic);
                            appendFields(out, "transport" + candidates, owner, 1, 6);
                            // The returned type is the response/stream boundary candidate.
                            // Record its declared state shape without invoking it.
                            line(out, "transportReturn" + candidates + ".class",
                                    returnType.getName());
                            String returnInterfaces;
                            try {
                                returnInterfaces = interfaces(returnType);
                            } catch (LinkageError | RuntimeException error) {
                                classLoadFailures++;
                                returnInterfaces = "<unavailable:"
                                        + error.getClass().getName() + ">";
                                line(out, "transportReturn" + candidates
                                        + ".interfaceMetadataError",
                                        error.getClass().getName());
                            }
                            line(out, "transportReturn" + candidates + ".interfaces",
                                    returnInterfaces);
                            appendFields(out, "transportReturn" + candidates, returnType, 1, 6);
                        }
                        candidates++;
                        transportOwners.add(owner);
                        methodIndex++;
                    }
                }
                line(out, "dex.eligibleClassNameCount", String.valueOf(eligibleClassNames));
                line(out, "dex.scannedClassCount", String.valueOf(scanned));
                line(out, "transport.candidateCount", String.valueOf(candidates));

                // q71 in earlier host builds owned an r92 transport in an instance field.
                // Find equivalent 2.7.x manager candidates structurally without instantiating
                // classes or reading any live object values.
                if (!transportOwners.isEmpty()) {
                    // Reuse the successfully loaded classes from the first pass. Reloading each
                    // name would duplicate class-loader work and could silently make the manager
                    // inventory partial after the DEX scan had already been marked complete.
                    for (Class<?> owner : scannedTypes) {
                        Field[] declaredFields;
                        try {
                            declaredFields = owner.getDeclaredFields();
                        } catch (LinkageError | SecurityException error) {
                            classLoadFailures++;
                            line(out, "manager.fieldInventoryError." + managerCandidates,
                                    error.getClass().getName());
                            continue;
                        }
                        int fieldIndex = 0;
                        for (Field field : declaredFields) {
                            int modifiers;
                            Class<?> fieldType;
                            try {
                                modifiers = field.getModifiers();
                                fieldType = field.getType();
                            } catch (LinkageError | RuntimeException error) {
                                classLoadFailures++;
                                line(out, "manager.fieldMetadataError." + managerCandidates
                                                + "." + fieldIndex,
                                        error.getClass().getName());
                                fieldIndex++;
                                continue;
                            }
                            if (Modifier.isStatic(modifiers) || fieldType.isPrimitive()) {
                                fieldIndex++;
                                continue;
                            }
                            // Object accepts every transport instance and made the old manager
                            // inventory report thousands of unrelated classes. Keep that weak
                            // signal visible, but never count it as a typed manager candidate.
                            if (fieldType == Object.class) {
                                weakObjectFields++;
                                fieldIndex++;
                                continue;
                            }
                            // A field can accept more than one discovered transport owner.
                            // Select its strongest match deterministically instead of letting
                            // HashSet iteration order decide whether it is reported as exact,
                            // interface, or supertype.
                            String matchKind = null;
                            int matchRank = 0;
                            for (Class<?> transportOwner : transportOwners) {
                                if (fieldType == transportOwner) {
                                    matchKind = "exact";
                                    matchRank = 3;
                                    break;
                                }
                                if (fieldType.isAssignableFrom(transportOwner)) {
                                    int candidateRank = fieldType.isInterface() ? 2 : 1;
                                    if (candidateRank > matchRank) {
                                        matchKind = fieldType.isInterface()
                                                ? "interface" : "supertype";
                                        matchRank = candidateRank;
                                    }
                                }
                            }
                            if ("exact".equals(matchKind)) {
                                exactManagerFields++;
                            } else if ("interface".equals(matchKind)) {
                                interfaceManagerFields++;
                            } else if ("supertype".equals(matchKind)) {
                                supertypeManagerFields++;
                            }
                            if (matchKind != null) {
                                if (managerCandidates < 6) {
                                    line(out, "manager.candidate." + managerCandidates,
                                            owner.getName() + "." + field.getName()
                                                    + ":" + fieldType.getName()
                                                    + " match=" + matchKind);
                                    appendConstructors(out, "manager" + managerCandidates, owner, 8);
                                }
                                managerCandidates++;
                            }
                            fieldIndex++;
                        }
                    }
                }
                // These are declared field matches, not discovered manager instances.
                line(out, "transport.detailCount", String.valueOf(Math.min(candidates, 4)));
                line(out, "manager.detailCount", String.valueOf(Math.min(managerCandidates, 6)));
                line(out, "manager.candidateUnit", "typed_non_object_fields");
                line(out, "manager.typedFieldCount", String.valueOf(managerCandidates));
                line(out, "manager.candidateCount", String.valueOf(managerCandidates));
                line(out, "manager.exactFieldCount", String.valueOf(exactManagerFields));
                line(out, "manager.interfaceFieldCount", String.valueOf(interfaceManagerFields));
                line(out, "manager.supertypeFieldCount", String.valueOf(supertypeManagerFields));
                line(out, "manager.objectFieldCount", String.valueOf(weakObjectFields));
                line(out, "dex.classLoadFailureCount", String.valueOf(classLoadFailures));
                // Per-class load failures mean the inventory is partial, not a terminal result.
                completed = !names.isEmpty() && classLoadFailures == 0;
            }
        } catch (Throwable error) {
            line(out, "probe.error", error.getClass().getName() + ": "
                    + String.valueOf(error.getMessage()));
        } finally {
            line(out, "probe.elapsedMs", String.valueOf(
                    (System.nanoTime() - startedNanos) / 1_000_000L));
        }
        installRuntimeObservation(loader);
        report = out.toString();
        // Only a complete DEX inventory is terminal. Class-load/scan failures remain retryable.
        if (completed) REPORTED.set(true);
        INSPECTING.set(false);
        Main.extLog("[OPEN_NATIVE_COMPLETION_PROBE]\n" + report);
    }


    /**
     * Passive call-boundary observation for the verified mainland 2.7.2/code289 host only.
     * Never invokes candidates itself and never serializes request/response objects or values.
     */
    private static void installRuntimeObservation(ClassLoader loader) {
        if (!"Open".equals(BuildInfo.BUILD_EDITION) || BuildInfo.LOCAL_API_INCLUDED
                || HostCompat.hostCode271() != 289 || loader == null || Main.MODULE == null) {
            return;
        }
        try {
            Class<?> requestType = Class.forName("ly1", false, loader);
            Class<?> requestMarker = Class.forName("wu1", false, loader);
            if (!requestMarker.isAssignableFrom(requestType)) return;
            installObservedMethod(loader, "jw3", "g", requestMarker,
                    OBSERVATION_JW3, "transport-low-level");
            installObservedMethod(loader, "zn3", "o", requestMarker,
                    OBSERVATION_ZN3, "transport-dispatch");
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] setup failed: "
                    + error.getClass().getName());
        }
    }

    private static void installObservedMethod(ClassLoader loader, String ownerName,
            String methodName, Class<?> requestMarker,
            java.util.concurrent.atomic.AtomicBoolean installed, String role) {
        if (installed.get()) return;
        try {
            Class<?> owner = Class.forName(ownerName, false, loader);
            Method selected = null;
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (methodName.equals(method.getName()) && parameters.length == 2
                        && parameters[0] == requestMarker
                        && parameters[1] == Long.class
                        && !method.getReturnType().isPrimitive()
                        && method.getReturnType() != Void.TYPE) {
                    if (selected != null) {
                        Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] ambiguous "
                                + ownerName + "." + methodName);
                        return;
                    }
                    selected = method;
                }
            }
            if (selected == null) {
                Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] method unavailable "
                        + ownerName + "." + methodName);
                return;
            }
            // Mark before registration: if the framework partially registers and then throws,
            // a later probe pass must not append a duplicate callback.
            if (!installed.compareAndSet(false, true)) return;
            final Method target = selected;
            Main.MODULE.hook(target).intercept(new LegacyXposedModule.Hooker() {
                @Override public Object intercept(LegacyXposedModule.Chain chain)
                        throws Throwable {
                    ObservationFrame frame = null;
                    boolean root = false;
                    boolean depthIncremented = false;
                    int depth = 0;
                    try {
                        frame = OBSERVATION_FRAME.get();
                        root = frame == null;
                        if (root) {
                            frame = new ObservationFrame(OBSERVATION_IDS.incrementAndGet());
                            OBSERVATION_FRAME.set(frame);
                        }
                        frame.depth++;
                        depthIncremented = true;
                        depth = frame.depth;
                        String thread = Thread.currentThread().getName() + "#"
                                + Thread.currentThread().getId();
                        String args = observedArgumentTypes(chain.getArgs());
                        if (root) {
                            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] enter id=" + frame.id
                                    + " role=" + role + " method=" + target.getDeclaringClass().getName()
                                    + "." + target.getName() + " modifiers="
                                    + Modifier.toString(target.getModifiers())
                                    + " thread=" + thread + " depth=" + depth + " args=" + args
                                    + " stack=" + observedStack());
                        } else {
                            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] enter id=" + frame.id
                                    + " role=" + role + " method=" + target.getDeclaringClass().getName()
                                    + "." + target.getName() + " thread=" + thread
                                    + " depth=" + depth + " args=" + args);
                        }
                    } catch (VirtualMachineError fatal) {
                        throw fatal;
                    } catch (ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable observerError) {
                        // Diagnostics must never prevent the original host method from running.
                        if (depthIncremented && frame != null) frame.depth--;
                        if (root) OBSERVATION_FRAME.remove();
                        frame = null;
                        root = false;
                    }
                    try {
                        Object result = chain.proceed();
                        try {
                            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] exit id="
                                    + (frame == null ? "unknown" : frame.id)
                                    + " role=" + role + " depth=" + depth + " result="
                                    + (result == null ? "null" : result.getClass().getName()));
                        } catch (Throwable ignored) {}
                        return result;
                    } catch (Throwable original) {
                        try {
                            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] throw id="
                                    + (frame == null ? "unknown" : frame.id)
                                    + " role=" + role + " depth=" + depth + " exception="
                                    + original.getClass().getName());
                        } catch (Throwable ignored) {}
                        throw original;
                    } finally {
                        if (frame != null && depthIncremented) {
                            frame.depth--;
                            if (root) OBSERVATION_FRAME.remove();
                        }
                    }
                }
            });
            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] installed role=" + role
                    + " method=" + target.getDeclaringClass().getName() + "."
                    + target.getName() + " return=" + target.getReturnType().getName());
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            Main.extLog("[OPEN_NATIVE_CALL_OBSERVER] install failed role=" + role
                    + " exception=" + error.getClass().getName());
        }
    }

    private static String observedArgumentTypes(List<?> args) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) out.append(',');
            Object value = args.get(i);
            out.append(value == null ? "null" : value.getClass().getName());
        }
        return out.append(']').toString();
    }

    private static String observedStack() {
        StringBuilder out = new StringBuilder("[");
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int count = 0;
        for (StackTraceElement element : stack) {
            String name = element.getClassName();
            if (name.startsWith("java.") || name.startsWith("android.")
                    || name.startsWith("dalvik.") || name.startsWith("ds.dsh.DeepSeek.")) {
                continue;
            }
            if (count > 0) out.append(',');
            out.append(name).append('#').append(element.getMethodName());
            if (++count >= 8) break;
        }
        return out.append(']').toString();
    }

    /** Legacy object-capture entry point, retained for hosts with the older q71 mapping. */
    static void inspect(ClassLoader loader, Object powManager, Object transport) {
        if (!"Open".equals(BuildInfo.BUILD_EDITION)
                || BuildInfo.LOCAL_API_INCLUDED || loader == null
                || powManager == null || transport == null
                || REPORTED.get() || !INSPECTING.compareAndSet(false, true)) {
            return;
        }
        StringBuilder out = new StringBuilder(4096);
        boolean completed = false;
        try {
            Class<?> requestType = HostCompat.load(loader, "ew0");
            Class<?> transportRequestType = HostCompat.load(loader, "rs0");
            line(out, "edition", BuildInfo.BUILD_EDITION);
            line(out, "host", HostCompat.diagnosticSummary());
            line(out, "request.class", requestType.getName());
            line(out, "request.interfaces", interfaces(requestType));
            appendConstructors(out, "request", requestType);
            appendFields(out, "request", requestType, 2);
            line(out, "transport.class", transport.getClass().getName());
            line(out, "transport.requestType", transportRequestType.getName());
            appendTransportMethods(out, transport.getClass(), transportRequestType);
            line(out, "pow.class", powManager.getClass().getName());
            appendFields(out, "pow", powManager.getClass(), 2);
            appendPowMethods(out, powManager.getClass());
            completed = true;
        } catch (Throwable error) {
            line(out, "probe.error", error.getClass().getName() + ": "
                    + String.valueOf(error.getMessage()));
        } finally {
            report = out.toString();
            if (completed) REPORTED.set(true);
            INSPECTING.set(false);
        }
        Main.extLog("[OPEN_NATIVE_COMPLETION_PROBE]\n" + report);
    }

    static String status() {
        return report;
    }

    private static void appendLoaderChain(StringBuilder out, ClassLoader loader) {
        ClassLoader current = loader;
        int depth = 0;
        while (current != null && depth < 8) {
            line(out, "loader.chain." + depth, current.getClass().getName());
            current = current.getParent();
            depth++;
        }
        line(out, "loader.chain.depth", String.valueOf(depth));
        line(out, "loader.chain.bootstrapParent", String.valueOf(current == null));
    }

    private static void appendConstructors(StringBuilder out, String prefix, Class<?> type) {
        appendConstructors(out, prefix, type, 24);
    }

    private static void appendConstructors(StringBuilder out, String prefix,
                                           Class<?> type, int maxDetails) {
        Constructor<?>[] constructors;
        try {
            constructors = type.getDeclaredConstructors();
        } catch (LinkageError | SecurityException error) {
            line(out, prefix + ".constructorError", error.getClass().getName());
            return;
        }
        line(out, prefix + ".constructorCount", String.valueOf(constructors.length));
        for (int i = 0; i < constructors.length && i < maxDetails; i++) {
            Constructor<?> constructor = constructors[i];
            try {
                line(out, prefix + ".ctor." + i, parameters(constructor.getParameterTypes()));
            } catch (LinkageError | RuntimeException error) {
                line(out, prefix + ".constructorMetadataError." + i,
                        error.getClass().getName());
            }
        }
    }

    private static void appendFields(StringBuilder out, String prefix,
                                     Class<?> type, int maxDepth) {
        appendFields(out, prefix, type, maxDepth, 48);
    }

    /** Bounded variant for noisy candidate inventories so summary fields remain reportable. */
    private static void appendFields(StringBuilder out, String prefix,
                                     Class<?> type, int maxDepth, int maxFields) {
        Class<?> current = type;
        int depth = 0;
        int count = 0;
        int fieldIndex = 0;
        while (current != null && current != Object.class && depth <= maxDepth) {
            Field[] declaredFields;
            try {
                declaredFields = current.getDeclaredFields();
            } catch (LinkageError | SecurityException error) {
                line(out, prefix + ".fieldInventoryError." + depth,
                        error.getClass().getName());
                try {
                    current = current.getSuperclass();
                } catch (LinkageError | RuntimeException superclassError) {
                    line(out, prefix + ".superclassError." + depth,
                            superclassError.getClass().getName());
                    break;
                }
                depth++;
                continue;
            }
            for (Field field : declaredFields) {
                int memberIndex = fieldIndex++;
                try {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    line(out, prefix + ".field." + count++,
                            current.getSimpleName() + "." + field.getName()
                                    + ":" + field.getType().getName());
                    if (count >= maxFields) break;
                } catch (LinkageError | RuntimeException error) {
                    line(out, prefix + ".fieldMetadataError." + memberIndex,
                            error.getClass().getName());
                }
            }
            if (count >= 48) break;
            try {
                current = current.getSuperclass();
            } catch (LinkageError | RuntimeException error) {
                line(out, prefix + ".superclassError." + depth,
                        error.getClass().getName());
                break;
            }
            depth++;
        }
    }

    private static void appendTransportMethods(StringBuilder out, Class<?> type,
                                               Class<?> requestType) {
        int count = 0;
        int classIndex = 0;
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Method[] declaredMethods;
            try {
                declaredMethods = current.getDeclaredMethods();
            } catch (LinkageError | SecurityException error) {
                line(out, "transport.inventoryError." + classIndex,
                        error.getClass().getName());
                declaredMethods = null;
            }
            if (declaredMethods != null) {
                for (int methodIndex = 0; methodIndex < declaredMethods.length; methodIndex++) {
                    Method method = declaredMethods[methodIndex];
                    try {
                        Class<?>[] methodParameters = method.getParameterTypes();
                        Class<?> returnType = method.getReturnType();
                        Type generic = method.getGenericReturnType();
                        if (methodParameters.length != 2 || methodParameters[1] != Long.class
                                || !methodParameters[0].isAssignableFrom(requestType)
                                        && !requestType.isAssignableFrom(methodParameters[0])) {
                            continue;
                        }
                        line(out, "transport.candidate." + count++,
                                current.getSimpleName() + "." + method.getName()
                                        + "(" + parameters(methodParameters) + ")->"
                                        + returnType.getName()
                                        + " generic=" + String.valueOf(generic));
                    } catch (LinkageError | RuntimeException error) {
                        line(out, "transport.methodMetadataError." + classIndex + "."
                                + methodIndex, error.getClass().getName());
                    }
                }
            }
            try {
                current = current.getSuperclass();
            } catch (LinkageError | RuntimeException error) {
                line(out, "transport.superclassError." + classIndex,
                        error.getClass().getName());
                break;
            }
            classIndex++;
        }
        line(out, "transport.candidateCount", String.valueOf(count));
    }

    private static void appendPowMethods(StringBuilder out, Class<?> type) {
        int count = 0;
        Method[] declaredMethods;
        try {
            declaredMethods = type.getDeclaredMethods();
        } catch (LinkageError | SecurityException error) {
            line(out, "pow.inventoryError", error.getClass().getName());
            declaredMethods = null;
        }
        if (declaredMethods != null) {
            for (int methodIndex = 0; methodIndex < declaredMethods.length; methodIndex++) {
                if (count >= 40) break;
                Method method = declaredMethods[methodIndex];
                try {
                    Class<?>[] methodParameters = method.getParameterTypes();
                    Class<?> returnType = method.getReturnType();
                    line(out, "pow.method." + count++,
                            method.getName() + "(" + parameters(methodParameters) + ")->"
                                    + returnType.getName());
                } catch (LinkageError | RuntimeException error) {
                    line(out, "pow.methodMetadataError." + methodIndex,
                            error.getClass().getName());
                }
            }
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
        NativeProbeReport.line(out, key, value);
    }
}
