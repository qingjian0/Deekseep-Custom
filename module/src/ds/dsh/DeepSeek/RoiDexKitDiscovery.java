package ds.dsh.DeepSeek;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Set;

/**
 * Bounded, read-only DexKit inventory for hosts without a maintained symbol map.
 *
 * <p>The first pass inspects visible BaseDexClassLoader instances through ART and scans
 * installed base/split APKs as an independent supplement. Two bounded follow-up passes
 * rescan runtime loaders to catch late thread-context loaders and newly attached DEX files.
 * This class never loads host classes, invokes host methods, or installs hooks. Semantic
 * candidates are evidence only and are deliberately kept separate from activation.</p>
 */
final class RoiDexKitDiscovery {
    private static final int MAX_LOADERS = 12;
    private static final int MAX_APK_PATHS = 8;
    private static final int MAX_REPORTED_DEX = 256;
    private static final int MAX_CANDIDATES_PER_ANCHOR = 8;
    private static final int MAX_DISCOVERY_PASSES = 3;
    private static final Object LOADER_LOCK = new Object();
    private static final WeakHashMap<ClassLoader, Boolean> REGISTERED_LOADERS =
            new WeakHashMap<ClassLoader, Boolean>();
    private static final String[] SEMANTIC_ANCHORS = {
            "chat/completions", "application/json", "Authorization", "messages"
    };
    private static final java.util.concurrent.atomic.AtomicBoolean SCHEDULED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean nativeLibraryLoaded;
    private static volatile String nativeLibraryError = "not_attempted";

    private RoiDexKitDiscovery() {}

    /** Register a dynamic loader only from a trusted, module-owned observation point. */
    static void registerClassLoader(ClassLoader loader) {
        if (loader == null) return;
        synchronized (LOADER_LOCK) {
            REGISTERED_LOADERS.put(loader, Boolean.TRUE);
        }
    }

    static void schedule(final ClassLoader root, final String apkPath,
                         final String[] splitApkPaths) {
        if (root == null || !SCHEDULED.compareAndSet(false, true)) return;
        registerClassLoader(root);
        Thread worker = new Thread(() -> {
            try {
                for (int pass = 0; pass < MAX_DISCOVERY_PASSES; pass++) {
                    if (pass > 0) {
                        try {
                            Thread.sleep(pass == 1 ? 5000L : 10000L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    String report = inspect(root, apkPath, splitApkPaths, pass == 0);
                    Main.log("[ROI_DEXKIT]\npass=" + (pass + 1) + "\n" + report);
                }
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable error) {
                Main.log("[ROI_DEXKIT] fatal=" + error.getClass().getName());
            } finally {
                SCHEDULED.set(false);
            }
        }, "roi-dexkit-discovery");
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (RuntimeException | Error error) {
            SCHEDULED.set(false);
            throw error;
        }
    }

    static String inspect(ClassLoader root, String apkPath, String[] splitApkPaths) {
        return inspect(root, apkPath, splitApkPaths, true);
    }

    private static String inspect(ClassLoader root, String apkPath, String[] splitApkPaths,
                                  boolean includeApkPaths) {
        StringBuilder out = new StringBuilder(2048);
        append(out, "scan.apkPathsIncluded=" + includeApkPaths);
        append(out, "schema=1");
        append(out, "mode=read_only");
        append(out, "loader.discovery=root_parent_and_thread_context_snapshot");
        append(out, "hooksInstalled=false");
        append(out, "hostClassesLoaded=false");

        if (!loadNativeLibrary()) {
            append(out, "dexkit.available=false");
            append(out, "dexkit.loadError=" + safe(nativeLibraryError));
            return out.toString();
        }
        append(out, "dexkit.available=true");

        registerClassLoader(root);
        List<ClassLoader> loaders = collectLoaders(root);
        int memorySuccess = 0;
        int memoryFailure = 0;
        int totalDex = 0;
        int candidateCount = 0;
        for (int i = 0; i < loaders.size(); i++) {
            DexKitBridge bridge = null;
            try {
                bridge = DexKitBridge.create(loaders.get(i), true);
                int count = boundedDexCount(bridge.getDexNum());
                append(out, "loader." + (i + 1) + ".mode=class_loader_memory_requested");
                append(out, "loader." + (i + 1) + ".dexCount=" + count);
                memorySuccess++;
                totalDex += count;
                candidateCount += querySemanticAnchors(bridge, "loader." + (i + 1), out);
            } catch (Throwable error) {
                rethrowFatal(error);
                memoryFailure++;
                append(out, "loader." + (i + 1) + ".memoryError="
                        + error.getClass().getName());
            } finally {
                closeQuietly(bridge);
            }
            if (out.length() > 6000) {
                append(out, "report.truncated=true");
                break;
            }
        }

        int apkSuccess = 0;
        if (includeApkPaths) {
            List<String> apkPaths = collectApkPaths(apkPath, splitApkPaths);
            for (int i = 0; i < apkPaths.size(); i++) {
                DexKitBridge bridge = null;
                try {
                    bridge = DexKitBridge.create(apkPaths.get(i));
                    int count = boundedDexCount(bridge.getDexNum());
                    append(out, "apk." + (i + 1) + ".name="
                            + new File(apkPaths.get(i)).getName());
                    append(out, "apk." + (i + 1) + ".dexCount=" + count);
                    apkSuccess++;
                    totalDex += count;
                    candidateCount += querySemanticAnchors(bridge, "apk." + (i + 1), out);
                } catch (Throwable error) {
                    rethrowFatal(error);
                    append(out, "apk." + (i + 1) + ".error="
                            + error.getClass().getName());
                } finally {
                    closeQuietly(bridge);
                }
                if (out.length() > 6000) {
                    append(out, "report.truncated=true");
                    break;
                }
            }
        } else {
            append(out, "apk.scanSkipped=runtime_rescan");
        }

        append(out, "loader.count=" + loaders.size());
        append(out, "loader.memorySuccess=" + memorySuccess);
        append(out, "loader.memoryFailure=" + memoryFailure);
        append(out, "apk.scanSuccess=" + apkSuccess);
        append(out, "apk.scanMode=installed_package_supplement");
        append(out, "dex.totalReported=" + totalDex);
        append(out, "dex.totalCapped=" + (totalDex >= MAX_REPORTED_DEX));
        append(out, "semanticCandidates=" + candidateCount);
        append(out, "semanticEvidence=string_anchor_only");
        append(out, "activation=forbidden_without_external_validation");
        append(out, "dynamicLoaderRegistration=trusted_callers_only");
        append(out, "loader.rescan=bounded_thread_context_snapshot");
        return out.toString();
    }


    private static int querySemanticAnchors(DexKitBridge bridge, String prefix, StringBuilder out) {
        int total = 0;
        for (String anchor : SEMANTIC_ANCHORS) {
            try {
                FindMethod query = FindMethod.create().matcher(
                        MethodMatcher.create().usingStrings(
                                Collections.singletonList(anchor),
                                StringMatchType.Contains,
                                false));
                Object results = bridge.findMethod(query);
                int count = invokeSize(results);
                int emitted = Math.min(count, MAX_CANDIDATES_PER_ANCHOR);
                append(out, prefix + ".anchor." + safeKey(anchor) + ".matches=" + count);
                for (int i = 0; i < emitted; i++) {
                    Object item = invokeGet(results, i);
                    append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                            + ".descriptor=" + safe(readString(item, "getDescriptor")));
                    append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                            + ".owner=" + safe(readString(item, "getClassName")));
                    append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                            + ".method=" + safe(readString(item, "getMethodName")));
                    append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                            + ".evidence=string_anchor_only");
                    total++;
                }
                if (count > emitted) append(out, prefix + ".anchor." + safeKey(anchor)
                        + ".omitted=" + (count - emitted));
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable error) {
                rethrowFatal(error);
                append(out, prefix + ".anchor." + safeKey(anchor)
                        + ".queryError=" + error.getClass().getName());
            }
            if (out.length() > 8200) break;
        }
        return total;
    }

    private static int invokeSize(Object value) throws Exception {
        if (value == null) return 0;
        Object size = value.getClass().getMethod("size").invoke(value);
        return size instanceof Number ? ((Number) size).intValue() : 0;
    }

    private static Object invokeGet(Object value, int index) throws Exception {
        return value.getClass().getMethod("get", int.class).invoke(value, index);
    }

    private static String readString(Object value, String getter) {
        if (value == null) return "unavailable";
        try {
            Object result = value.getClass().getMethod(getter).invoke(value);
            return result == null ? "null" : String.valueOf(result);
        } catch (Throwable error) {
            rethrowFatal(error);
            return "unavailable";
        }
    }

    private static String safeKey(String value) {
        return value.replace('/', '_').replace('.', '_').replace(' ', '_');
    }

    private static boolean loadNativeLibrary() {
        if (nativeLibraryLoaded) return true;
        synchronized (RoiDexKitDiscovery.class) {
            if (nativeLibraryLoaded) return true;
            try {
                System.loadLibrary("dexkit");
                nativeLibraryLoaded = true;
                nativeLibraryError = "";
                return true;
            } catch (Throwable error) {
                rethrowFatal(error);
                nativeLibraryError = error.getClass().getName();
                return false;
            }
        }
    }

    private static List<ClassLoader> collectLoaders(ClassLoader root) {
        Set<ClassLoader> seen = Collections.newSetFromMap(
                new IdentityHashMap<ClassLoader, Boolean>());
        List<ClassLoader> result = new ArrayList<ClassLoader>();
        addLoader(result, seen, root);
        synchronized (LOADER_LOCK) {
            for (ClassLoader registered : REGISTERED_LOADERS.keySet()) {
                addLoader(result, seen, registered);
            }
        }
        ClassLoader current = root;
        while (current != null && result.size() < MAX_LOADERS) {
            try {
                current = current.getParent();
            } catch (Throwable error) {
                rethrowFatal(error);
                break;
            }
            addLoader(result, seen, current);
        }
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (result.size() >= MAX_LOADERS) break;
                try {
                    addLoader(result, seen, thread.getContextClassLoader());
                } catch (Throwable error) {
                    rethrowFatal(error);
                }
            }
        } catch (Throwable error) {
            rethrowFatal(error);
        }
        return result;
    }

    private static void addLoader(List<ClassLoader> result, Set<ClassLoader> seen,
                                  ClassLoader loader) {
        if (loader != null && seen.add(loader) && result.size() < MAX_LOADERS) {
            result.add(loader);
        }
    }

    private static List<String> collectApkPaths(String apkPath, String[] splitApkPaths) {
        LinkedHashSet<String> paths = new LinkedHashSet<String>();
        if (apkPath != null && apkPath.length() > 0) paths.add(apkPath);
        if (splitApkPaths != null) {
            for (String path : splitApkPaths) {
                if (path != null && path.length() > 0) paths.add(path);
                if (paths.size() >= MAX_APK_PATHS) break;
            }
        }
        return new ArrayList<String>(paths);
    }

    private static int boundedDexCount(int count) {
        if (count < 0) return 0;
        return Math.min(count, MAX_REPORTED_DEX);
    }

    private static void closeQuietly(DexKitBridge bridge) {
        if (bridge == null) return;
        try {
            bridge.close();
        } catch (Throwable error) {
            rethrowFatal(error);
        }
    }

    private static void append(StringBuilder out, String line) {
        if (out.length() < 7000) out.append(line).append('\n');
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
