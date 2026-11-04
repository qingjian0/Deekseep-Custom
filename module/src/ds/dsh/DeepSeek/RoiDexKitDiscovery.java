package ds.dsh.DeepSeek;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int MAX_RELATIONS_PER_CANDIDATE = 12;
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
        List<RoiCandidateGraph.Observation> graphObservations =
                new ArrayList<RoiCandidateGraph.Observation>();
        for (String anchor : SEMANTIC_ANCHORS) {
            try {
                FindMethod query = FindMethod.create().matcher(
                        MethodMatcher.create().usingStrings(
                                Collections.singletonList(anchor),
                                StringMatchType.Contains,
                                false)
                                // MethodData.invokes/callers are backed by lazy native indexes.
                                // Request both indexes in this query before reading those accessors.
                                .invokeMethods(MethodsMatcher.create().countMin(0))
                                .callerMethods(MethodsMatcher.create().countMin(0)));
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
                    String descriptor = safe(readString(item, "getDescriptor"));
                    String owner = safe(readString(item, "getClassName"));
                    String method = safe(readString(item, "getMethodName"));
                    append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                            + ".evidence=string_anchor_only");

                    // R1 integration: feed observed DexKit metadata into the resolver, but do
                    // not promote a string hit into method-shape, call-graph, or runtime proof.
                    String hostKey = "unverified:" + HostCompat.diagnosticSummary()
                            .replace('\n', '|').replace('\r', '|');
                    RoiMethodShape shape = RoiMethodShape.parse(descriptor);
                    append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                            + ".shape.valid=" + (shape != null));
                    if (shape != null) {
                        append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                                + ".shape.parameterCount=" + shape.parameterCount);
                        append(out, prefix + ".candidate." + safeKey(anchor) + "." + i
                                + ".shape.return=" + shape.returnDescriptor);
                    }
                    String candidatePrefix = prefix + ".candidate." + safeKey(anchor) + "." + i;
                    appendMethodStructure(item, candidatePrefix, out);
                    RoiCandidateGraph.Observation graphObservation = appendCallRelations(
                            item, candidatePrefix, descriptor, anchor, out);
                    graphObservations.add(graphObservation);
                    appendSemanticRoleHints(candidatePrefix, graphObservation.invokes, out);
                    int relationCount = graphObservation.invokes.size()
                            + graphObservation.callers.size();
                    List<RoiHookResolver.Evidence> evidence = Arrays.asList(
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.HOST_IDENTITY,
                                    0.25, false, "host_diagnostic_only"),
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.SEMANTIC_MARKER,
                                    0.35, true, "dexkit.string_anchor:" + anchor),
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.METHOD_SHAPE,
                                    shape == null ? 0.0 : 0.55, shape != null,
                                    shape == null ? "descriptor_invalid" : "dex_descriptor_shape_validated"),
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.CALL_GRAPH,
                                    relationCount == 0 ? 0.0 : 0.30, false,
                                    relationCount == 0 ? "no_static_call_edges_observed"
                                            : "dexkit_method_data_invokes_and_callers"));
                    RoiHookResolver.Candidate candidate = new RoiHookResolver.Candidate(
                            hostKey, "unresolved", owner, method, descriptor,
                            false, false, Collections.<String>emptySet(), evidence);
                    RoiHookResolver.Decision decision = RoiHookResolver.resolve(
                            candidate, RoiHookResolver.Policy.discoveryOnly());
                    append(out, candidatePrefix + ".resolver.state=" + decision.state.name());
                    append(out, candidatePrefix + ".resolver.score="
                            + String.format(java.util.Locale.ROOT, "%.3f", decision.score));
                    append(out, candidatePrefix + ".resolver.activationAuthorized="
                            + decision.activationAuthorized);
                    append(out, candidatePrefix + ".resolver.reason=" + decision.reason);
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
        appendCandidateGraph(prefix, graphObservations, out);
        return total;
    }

    private static void appendSemanticRoleHints(String prefix, List<String> invokes,
                                                StringBuilder out) {
        RoiSemanticRoleHints.Result hints = RoiSemanticRoleHints.classify(invokes);
        append(out, prefix + ".semanticHints.requestBuilder="
                + hints.has(RoiSemanticRoleHints.Hint.REQUEST_BUILDER));
        append(out, prefix + ".semanticHints.transport="
                + hints.has(RoiSemanticRoleHints.Hint.TRANSPORT));
        append(out, prefix + ".semanticHints.responseParser="
                + hints.has(RoiSemanticRoleHints.Hint.RESPONSE_PARSER));
        append(out, prefix + ".semanticHints.provenance=direct_static_invoke_reference");
        int emitted = Math.min(hints.matchedReferences.size(), 6);
        for (int i = 0; i < emitted; i++) {
            append(out, prefix + ".semanticHints.match." + i + "="
                    + safe(hints.matchedReferences.get(i)));
        }
        if (hints.matchedReferences.size() > emitted) {
            append(out, prefix + ".semanticHints.omitted="
                    + (hints.matchedReferences.size() - emitted));
        }
    }

    private static void appendCandidateGraph(String prefix,
            List<RoiCandidateGraph.Observation> observations, StringBuilder out) {
        RoiCandidateGraph.Result graph = RoiCandidateGraph.build(observations, 128, 256);
        append(out, prefix + ".candidateGraph.observed=" + graph.observed);
        append(out, prefix + ".candidateGraph.nodes=" + graph.uniqueNodes);
        append(out, prefix + ".candidateGraph.duplicates=" + graph.duplicateObservations);
        append(out, prefix + ".candidateGraph.edges=" + graph.edgeCount);
        append(out, prefix + ".candidateGraph.unresolvedReferences="
                + graph.unresolvedReferences);
        append(out, prefix + ".candidateGraph.components=" + graph.connectedComponents);
        append(out, prefix + ".candidateGraph.omittedNodes=" + graph.omittedNodes);
        append(out, prefix + ".candidateGraph.omittedEdges=" + graph.omittedEdges);
        append(out, prefix + ".candidateGraph.edgeSemantics=static_dex_reference_only");
        int emitted = Math.min(graph.edges.size(), 32);
        for (int i = 0; i < emitted; i++) {
            RoiCandidateGraph.Edge edge = graph.edges.get(i);
            append(out, prefix + ".candidateGraph.edge." + i + ".caller=" + safe(edge.caller));
            append(out, prefix + ".candidateGraph.edge." + i + ".callee=" + safe(edge.callee));
        }
        if (graph.edges.size() > emitted) {
            append(out, prefix + ".candidateGraph.reportOmitted="
                    + (graph.edges.size() - emitted));
        }
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

    /** Emit bounded, non-loading structural metadata exposed by DexKit MethodData. */
    private static void appendMethodStructure(Object methodData, String prefix, StringBuilder out)
            throws Exception {
        append(out, prefix + ".shape.accessFlags=" + safe(readString(methodData, "getAccessFlags")));
        append(out, prefix + ".shape.returnTypeName="
                + safe(readString(methodData, "getReturnTypeName")));
        appendBoundedValues(methodData, "getParamTypeNames", prefix + ".shape.paramType", out, 16);
        appendBoundedValues(methodData, "getOpNames", prefix + ".shape.opName", out, 8);
    }

    private static void appendBoundedValues(Object owner, String getter, String key,
                                            StringBuilder out, int limit) throws Exception {
        Object values = readObject(owner, getter);
        int count = invokeSize(values);
        int emitted = Math.min(count, limit);
        append(out, key + ".count=" + count);
        for (int i = 0; i < emitted; i++) {
            Object value = invokeGet(values, i);
            append(out, key + "." + i + "=" + safe(String.valueOf(value)));
        }
        if (count > emitted) append(out, key + ".omitted=" + (count - emitted));
    }

    /**
     * Read a bounded one-hop call neighborhood from DexKit MethodData. These are static DEX
     * references only; they do not prove that an edge executes on a live request path.
     */
    private static RoiCandidateGraph.Observation appendCallRelations(Object methodData,
            String prefix, String descriptor, String anchor, StringBuilder out) throws Exception {
        Object invokes = readObject(methodData, "getInvokes");
        Object callers = readObject(methodData, "getCallers");
        int invokeCount = invokeSize(invokes);
        int callerCount = invokeSize(callers);
        List<String> invokeDescriptors = new ArrayList<String>();
        List<String> callerDescriptors = new ArrayList<String>();
        int invokeEmitted = Math.min(invokeCount, MAX_RELATIONS_PER_CANDIDATE);
        int callerEmitted = Math.min(callerCount, MAX_RELATIONS_PER_CANDIDATE);
        append(out, prefix + ".callGraph.invokes.count=" + invokeCount);
        append(out, prefix + ".callGraph.callers.count=" + callerCount);
        for (int i = 0; i < invokeEmitted; i++) {
            Object edge = invokeGet(invokes, i);
            String edgeDescriptor = safe(readString(edge, "getDescriptor"));
            invokeDescriptors.add(edgeDescriptor);
            append(out, prefix + ".callGraph.edge.invoke." + i + "=" + edgeDescriptor);
        }
        for (int i = 0; i < callerEmitted; i++) {
            Object edge = invokeGet(callers, i);
            String edgeDescriptor = safe(readString(edge, "getDescriptor"));
            callerDescriptors.add(edgeDescriptor);
            append(out, prefix + ".callGraph.edge.caller." + i + "=" + edgeDescriptor);
        }
        if (invokeCount > invokeEmitted) {
            append(out, prefix + ".callGraph.invokes.omitted=" + (invokeCount - invokeEmitted));
        }
        if (callerCount > callerEmitted) {
            append(out, prefix + ".callGraph.callers.omitted=" + (callerCount - callerEmitted));
        }
        return new RoiCandidateGraph.Observation(descriptor, anchor,
                invokeDescriptors, callerDescriptors);
    }

    private static Object readObject(Object value, String getter) {
        if (value == null) return null;
        try {
            return value.getClass().getMethod(getter).invoke(value);
        } catch (Throwable error) {
            rethrowFatal(error);
            return null;
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
