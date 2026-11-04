package ds.dsh.DeepSeek;

import org.luckypray.dexkit.DexKitBridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded, read-only DexKit inventory for hosts without a maintained symbol map.
 *
 * <p>The first pass asks DexKit to inspect each visible BaseDexClassLoader using ART's
 * in-memory DEX path. If no loader can be inspected, it falls back to the installed base
 * APK and split APK paths. This class never loads host classes, invokes host methods, or
 * installs hooks. Semantic query profiles are deliberately kept separate from activation.</p>
 */
final class RoiDexKitDiscovery {
    private static final int MAX_LOADERS = 12;
    private static final int MAX_APK_PATHS = 8;
    private static final int MAX_REPORTED_DEX = 256;
    private static volatile boolean nativeLibraryLoaded;
    private static volatile String nativeLibraryError = "not_attempted";

    private RoiDexKitDiscovery() {}

    static String inspect(ClassLoader root, String apkPath, String[] splitApkPaths) {
        StringBuilder out = new StringBuilder(2048);
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

        List<ClassLoader> loaders = collectLoaders(root);
        int memorySuccess = 0;
        int memoryFailure = 0;
        int totalDex = 0;
        for (int i = 0; i < loaders.size(); i++) {
            DexKitBridge bridge = null;
            try {
                bridge = DexKitBridge.create(loaders.get(i), true);
                int count = boundedDexCount(bridge.getDexNum());
                append(out, "loader." + (i + 1) + ".mode=class_loader_memory_requested");
                append(out, "loader." + (i + 1) + ".dexCount=" + count);
                memorySuccess++;
                totalDex += count;
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
        if (memorySuccess == 0) {
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
            append(out, "apk.fallback=not_needed");
        }

        append(out, "loader.count=" + loaders.size());
        append(out, "loader.memorySuccess=" + memorySuccess);
        append(out, "loader.memoryFailure=" + memoryFailure);
        append(out, "apk.fallbackSuccess=" + apkSuccess);
        append(out, "dex.totalReported=" + totalDex);
        append(out, "dex.totalCapped=" + (totalDex >= MAX_REPORTED_DEX));
        append(out, "semanticCandidates=not_queried");
        append(out, "activation=forbidden_without_external_validation");
        return out.toString();
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
