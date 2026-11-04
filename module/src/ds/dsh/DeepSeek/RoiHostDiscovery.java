package ds.dsh.DeepSeek;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only runtime inventory for host versions without a maintained symbol map.
 *
 * <p>This scanner inventories visible BaseDexClassLoader instances and their DEX entries.
 * It does not load host classes, initialize host code, invoke business methods, read field
 * values from host objects, or install hooks. Advanced semantic analysis is a later phase.</p>
 */
final class RoiHostDiscovery {
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private static final int MAX_LOADERS = 12;
    private static final int MAX_DEX_ELEMENTS_PER_LOADER = 64;
    private static final int MAX_CLASS_NAMES = 60000;
    private static final int MAX_TOTAL_CLASS_ENTRIES = 100000;
    private static final int MAX_REPORT_CHARS = 7000;

    private RoiHostDiscovery() {}

    static void schedule(final ClassLoader root, final String apkPath) {
        if (root == null || !SCHEDULED.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                inspect(root, apkPath);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable error) {
                Main.log("[ROI_DISCOVERY] fatal=" + error.getClass().getName());
            } finally {
                SCHEDULED.set(false);
            }
        }, "roi-host-discovery");
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (RuntimeException | Error error) {
            SCHEDULED.set(false);
            throw error;
        }
    }

    private static void inspect(ClassLoader root, String apkPath) {
        final long started = System.nanoTime();
        StringBuilder out = new StringBuilder(4096);
        Set<ClassLoader> seen = Collections.newSetFromMap(
                new IdentityHashMap<ClassLoader, Boolean>());
        Set<String> classNames = new java.util.HashSet<String>();
        int loaderCount = 0;
        int dexElementCount = 0;
        int dexReadFailures = 0;
        int classNameCount = 0;
        int truncated = 0;

        append(out, "schema=1");
        append(out, "mode=read_only_unknown_host");
        append(out, "host=" + safe(HostCompat.diagnosticSummary()));
        appendApkFingerprint(out, apkPath);

        ClassLoader current = root;
        while (current != null && loaderCount < MAX_LOADERS && seen.add(current)) {
            loaderCount++;
            String prefix = "loader." + loaderCount;
            append(out, prefix + ".type=" + current.getClass().getName());
            append(out, prefix + ".identity=" + Integer.toHexString(
                    System.identityHashCode(current)));

            try {
                ClassLoader parent = current.getParent();
                append(out, prefix + ".parent="
                        + (parent == null ? "bootstrap" : parent.getClass().getName()));
            } catch (Throwable error) {
                rethrowFatal(error);
                append(out, prefix + ".parentError=" + error.getClass().getName());
            }

            Object elements = null;
            try {
                Class<?> baseDex = Class.forName(
                        "dalvik.system.BaseDexClassLoader", false, current.getClass().getClassLoader());
                if (baseDex.isInstance(current)) {
                    Field pathList = findField(baseDex, "pathList");
                    Object pathListValue = pathList.get(current);
                    Field dexElements = findField(pathListValue.getClass(), "dexElements");
                    elements = dexElements.get(pathListValue);
                }
            } catch (Throwable error) {
                rethrowFatal(error);
                append(out, prefix + ".dexInventoryError=" + error.getClass().getName());
            }

            int elementCount = elements == null ? 0 : Array.getLength(elements);
            append(out, prefix + ".dexElements=" + elementCount);
            int loaderDexCount = Math.min(elementCount, MAX_DEX_ELEMENTS_PER_LOADER);
            if (elementCount > loaderDexCount) {
                append(out, prefix + ".dexElementsTruncated="
                        + (elementCount - loaderDexCount));
            }

            for (int i = 0; i < loaderDexCount; i++) {
                Object element = Array.get(elements, i);
                if (element == null) continue;
                try {
                    Field dexFileField = findField(element.getClass(), "dexFile");
                    Object dexFile = dexFileField.get(element);
                    if (dexFile == null) continue;
                    dexElementCount++;
                    String dexName = dexName(dexFile);
                    append(out, prefix + ".dex." + i + ".name=" + dexName);
                    Method entriesMethod = findMethod(dexFile.getClass(), "entries");
                    Object value = entriesMethod.invoke(dexFile);
                    if (!(value instanceof Enumeration)) {
                        append(out, prefix + ".dex." + i + ".entries=unavailable");
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Enumeration<String> entries = (Enumeration<String>) value;
                    int count = 0;
                    while (entries.hasMoreElements() && classNameCount < MAX_TOTAL_CLASS_ENTRIES) {
                        String name = entries.nextElement();
                        count++;
                        if (name != null && classNames.size() < MAX_CLASS_NAMES) {
                            classNames.add(name);
                        } else if (name != null) {
                            truncated++;
                        }
                    }
                    classNameCount += count;
                    append(out, prefix + ".dex." + i + ".classCount=" + count);
                    if (classNameCount >= MAX_TOTAL_CLASS_ENTRIES) {
                        append(out, "dex.entryLimitReached=true");
                        break;
                    }
                } catch (Throwable error) {
                    rethrowFatal(error);
                    dexReadFailures++;
                    append(out, prefix + ".dex." + i + ".error="
                            + error.getClass().getName());
                }
                if (out.length() > MAX_REPORT_CHARS - 600) break;
            }

            try {
                current = current.getParent();
            } catch (Throwable error) {
                rethrowFatal(error);
                append(out, prefix + ".parentTraversalError=" + error.getClass().getName());
                break;
            }
            if (out.length() > MAX_REPORT_CHARS - 600) break;
        }

        append(out, "loader.count=" + loaderCount);
        append(out, "dex.elementCount=" + dexElementCount);
        append(out, "dex.readFailures=" + dexReadFailures);
        append(out, "dex.classNameCount=" + classNameCount);
        append(out, "dex.uniqueClassNames=" + classNames.size());
        append(out, "dex.classNameLimitReached=" + (truncated > 0));
        append(out, "discovery.elapsedMs="
                + ((System.nanoTime() - started) / 1000000L));
        append(out, "hooksInstalled=false");
        append(out, "classesInitialized=false");
        if (out.length() > MAX_REPORT_CHARS) {
            out.setLength(MAX_REPORT_CHARS);
            out.append("\nreport.truncated=true");
        }
        Main.log("[ROI_DISCOVERY]\n" + out);
    }

    private static void appendApkFingerprint(StringBuilder out, String apkPath) {
        if (apkPath == null || apkPath.length() == 0) {
            append(out, "apk.fingerprint=unavailable");
            return;
        }
        try {
            File apk = new File(apkPath);
            append(out, "apk.name=" + apk.getName());
            append(out, "apk.length=" + apk.length());
            append(out, "apk.lastModified=" + apk.lastModified());
        } catch (Throwable error) {
            rethrowFatal(error);
            append(out, "apk.fingerprintError=" + error.getClass().getName());
        }
    }

    private static String dexName(Object dexFile) {
        try {
            Method getName = findMethod(dexFile.getClass(), "getName");
            Object value = getName.invoke(dexFile);
            return value == null ? "unknown" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static void append(StringBuilder out, String line) {
        if (out.length() >= MAX_REPORT_CHARS) return;
        out.append(line).append('\n');
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
        if (error instanceof ThreadDeath) throw (ThreadDeath) error;
    }
}
