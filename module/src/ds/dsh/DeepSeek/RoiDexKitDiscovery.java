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
 * This class never loads host classes or invokes host methods. It only publishes semantic
 * candidates; Main finalizes the batch and delegates the narrowly scoped prompt adapter to a
 * separate runtime resolver after the scan has completed.</p>
 */
final class RoiDexKitDiscovery {
    private static final int MAX_LOADERS = 12;
    private static final int MAX_APK_PATHS = 8;
    private static final int MAX_REPORTED_DEX = 256;
    private static final int MAX_CANDIDATES_PER_ANCHOR = 8;
    private static final int MAX_RELATIONS_PER_CANDIDATE = 12;
    private static final int MAX_DISCOVERY_PASSES = 3;
    private static final int MAX_TOTAL_QUERIES_PER_SCAN = 48;
    private static final int MAX_TOTAL_CANDIDATES_PER_SCAN = 128;
    private static final int MAX_SERIALIZER_METHODS_PER_OWNER = 24;
    private static final int MAX_REQUEST_CTORS_PER_SERIALIZER = 8;
    private static final long SCAN_BUDGET_NANOS = 45_000_000_000L;
    private static final Object LOADER_LOCK = new Object();
    private static final WeakHashMap<ClassLoader, Boolean> REGISTERED_LOADERS =
            new WeakHashMap<ClassLoader, Boolean>();
    private static final String[] SEMANTIC_ANCHORS = {
            "chat/completions", "application/json", "Authorization", "messages",
            "chat_session_id", "parent_message_id", "thinking_enabled",
            "search_enabled", "preempt"
    };
    private static final java.util.concurrent.atomic.AtomicBoolean SCHEDULED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean nativeLibraryLoaded;
    private static volatile String nativeLibraryError = "not_attempted";

    private RoiDexKitDiscovery() {}

    /**
     * Inspect a Class object supplied by a trusted module-owned load observation point.
     * This does not ask the ClassLoader to load a class and does not activate hooks.
     */
    static String inspectObservedClass(Class<?> loadedClass) {
        RoiLoadedClassInspector.Snapshot snapshot =
                RoiLoadedClassInspector.inspect(loadedClass);
        StringBuilder out = new StringBuilder(1024);
        append(out, "classInspection.mode=already_loaded_class_metadata_only");
        append(out, "classInspection.status=" + snapshot.inspectionStatus);
        append(out, "classInspection.name=" + snapshot.className);
        append(out, "classInspection.descriptor=" + snapshot.classDescriptor);
        append(out, "classInspection.sourceScope=" + snapshot.loaderScope);
        append(out, "classInspection.superclass=" + snapshot.superclassName);
        append(out, "classInspection.interfaces=" + snapshot.interfaces);
        append(out, "classInspection.methodCount=" + snapshot.methodCountReported);
        append(out, "classInspection.methodsTruncated=" + snapshot.methodsTruncated);
        for (int i = 0; i < snapshot.methods.size(); i++) {
            append(out, "classInspection.method." + i + "=" + snapshot.methods.get(i));
        }
        append(out, "classInspection.fieldCount=" + snapshot.fieldCountReported);
        append(out, "classInspection.fieldsTruncated=" + snapshot.fieldsTruncated);
        for (int i = 0; i < snapshot.fields.size(); i++) {
            append(out, "classInspection.field." + i + "=" + snapshot.fields.get(i));
        }
        append(out, "classInspection.methodInvocation=none");
        append(out, "classInspection.fieldValueRead=none");
        append(out, "classInspection.hookActivation=none");
        return out.toString();
    }

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
                if (!Thread.currentThread().isInterrupted()) {
                    Main.finalizeRoiUnknownHostChatRequestDiscovery();
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
        RoiDiscoveryBudget budget = new RoiDiscoveryBudget(
                MAX_TOTAL_QUERIES_PER_SCAN,
                MAX_TOTAL_CANDIDATES_PER_SCAN,
                System.nanoTime() + SCAN_BUDGET_NANOS);
        append(out, "scan.apkPathsIncluded=" + includeApkPaths);
        append(out, "scan.budget.maxQueries=" + MAX_TOTAL_QUERIES_PER_SCAN);
        append(out, "scan.budget.maxCandidates=" + MAX_TOTAL_CANDIDATES_PER_SCAN);
        append(out, "scan.budget.softDeadlineMs=" + (SCAN_BUDGET_NANOS / 1_000_000L));
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

        List<RoiRuntimeInvocationRecorder.EvidenceSnapshot> runtimeEvidence =
                RoiRuntimeInvocationObserver.snapshotEvidence();
        List<RoiRuntimeInvocationRecorder.CoverageSnapshot> runtimeCoverage =
                RoiRuntimeInvocationObserver.snapshotCoverage();
        String adapterIdentity = RoiRuntimeInvocationObserver.currentAdapterIdentity();
        append(out, "runtimeEvidence.enabled="
                + RoiRuntimeInvocationObserver.isObservationEnabled());
        append(out, "runtimeEvidence.adapterIdentityKind=adapter_generation_channel_not_apk_fingerprint");
        append(out, "runtimeEvidence.matching=exact_method_descriptor_adapter_identity_and_classloader_scope");
        append(out, "runtimeEvidence.snapshotEntries=" + runtimeEvidence.size());
        append(out, "runtimeEvidence.coverageSnapshotEntries=" + runtimeCoverage.size());
        append(out, "runtimeEvidence.resolverContribution=none_read_only_diagnostic_only");

        registerClassLoader(root);
        List<ClassLoader> loaders = collectLoaders(root);
        int memorySuccess = 0;
        int memoryFailure = 0;
        int totalDex = 0;
        int candidateCount = 0;
        for (int i = 0; i < loaders.size(); i++) {
            if (!budget.canContinue(System.nanoTime())) {
                append(out, "scan.budget.stopBeforeLoader=" + budget.stopReason());
                break;
            }
            DexKitBridge bridge = null;
            try {
                bridge = DexKitBridge.create(loaders.get(i), true);
                int count = boundedDexCount(bridge.getDexNum());
                append(out, "loader." + (i + 1) + ".mode=class_loader_memory_requested");
                append(out, "loader." + (i + 1) + ".dexCount=" + count);
                memorySuccess++;
                totalDex += count;
                String sourceScope = RoiCandidateIdentity.forClassLoader(loaders.get(i));
                append(out, "loader." + (i + 1) + ".sourceScope=" + sourceScope);
                RoiClassLoaderProfile loaderProfile =
                        RoiClassLoaderProfile.inspect(loaders.get(i));
                append(out, "loader." + (i + 1) + ".profile.kinds="
                        + loaderProfile.kinds.toString());
                append(out, "loader." + (i + 1) + ".profile.chain="
                        + loaderProfile.chainClassNames.toString());
                append(out, "loader." + (i + 1) + ".profile.dynamicDexObserved="
                        + loaderProfile.dynamicDexLoaderObserved);
                append(out, "loader." + (i + 1) + ".profile.inMemoryDexObserved="
                        + loaderProfile.inMemoryDexLoaderObserved);
                append(out, "loader." + (i + 1) + ".profile.customLoaderObserved="
                        + loaderProfile.customLoaderObserved);
                append(out, "loader." + (i + 1) + ".profile.encryptedDexAssessment="
                        + loaderProfile.encryptedDexAssessment);
                candidateCount += querySemanticAnchors(
                        bridge, "loader." + (i + 1), sourceScope, out, budget,
                        runtimeEvidence, runtimeCoverage, adapterIdentity);
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
                if (!budget.canContinue(System.nanoTime())) {
                    append(out, "scan.budget.stopBeforeApk=" + budget.stopReason());
                    break;
                }
                DexKitBridge bridge = null;
                try {
                    bridge = DexKitBridge.create(apkPaths.get(i));
                    int count = boundedDexCount(bridge.getDexNum());
                    append(out, "apk." + (i + 1) + ".name="
                            + new File(apkPaths.get(i)).getName());
                    append(out, "apk." + (i + 1) + ".dexCount=" + count);
                    apkSuccess++;
                    totalDex += count;
                    String sourceScope = RoiCandidateIdentity.forApkPath(apkPaths.get(i));
                    append(out, "apk." + (i + 1) + ".sourceScope=" + sourceScope);
                    candidateCount += querySemanticAnchors(
                            bridge, "apk." + (i + 1), sourceScope, out, budget,
                            runtimeEvidence, runtimeCoverage, adapterIdentity);
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
        append(out, "scan.budget.queriesStarted=" + budget.queriesStarted());
        append(out, "scan.budget.candidatesAccepted=" + budget.candidatesAccepted());
        append(out, "scan.budget.exhausted=" + budget.isExhausted(System.nanoTime()));
        append(out, "scan.budget.stopReason=" + budget.stopReason());
        append(out, "scan.budget.enforcement=query_start_and_candidate_iteration_only");
        append(out, "semanticEvidence=string_anchor_only");
        append(out, "activation=chat_prompt_adapter_only_after_explicit_opt_in_and_scan_finalization");
        append(out, "otherFeatureHooks=forbidden_without_reviewed_adapter");
        append(out, "dynamicLoaderRegistration=trusted_callers_only");
        append(out, "loader.rescan=bounded_thread_context_snapshot");
        return out.toString();
    }


    private static int querySemanticAnchors(DexKitBridge bridge, String prefix, String sourceScope,
            StringBuilder out, RoiDiscoveryBudget budget,
            List<RoiRuntimeInvocationRecorder.EvidenceSnapshot> runtimeEvidence,
            List<RoiRuntimeInvocationRecorder.CoverageSnapshot> runtimeCoverage,
            String adapterIdentity) {
        int total = 0;
        List<RoiCandidateGraph.Observation> graphObservations =
                new ArrayList<RoiCandidateGraph.Observation>();
        Map<String, List<SerializerConstructorRef>> serializerConstructorCache =
                new java.util.LinkedHashMap<String, List<SerializerConstructorRef>>();
        for (String anchor : SEMANTIC_ANCHORS) {
            if (!budget.tryStartQuery(System.nanoTime())) {
                append(out, prefix + ".budget.querySkipped=" + budget.stopReason());
                break;
            }
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
                int emittedLimit = Math.min(count, MAX_CANDIDATES_PER_ANCHOR);
                int emitted = 0;
                append(out, prefix + ".anchor." + safeKey(anchor) + ".matches=" + count);
                for (int i = 0; i < emittedLimit; i++) {
                    if (!budget.tryAcceptCandidate(System.nanoTime())) break;
                    Object item = invokeGet(results, i);
                    emitted++;
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
                    String hostKey = adapterIdentity.length() > 0 ? adapterIdentity
                            : "unverified:" + HostCompat.diagnosticSummary()
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
                    String candidateId = RoiCandidateIdentity.candidateId(sourceScope, owner, method, descriptor);
                    append(out, candidatePrefix + ".sourceScope=" + safe(sourceScope));
                    append(out, candidatePrefix + ".candidateId=" + safe(candidateId));
                    appendMethodStructure(item, candidatePrefix, out);
                    RoiCandidateGraph.Observation graphObservation = appendCallRelations(
                            item, candidatePrefix, sourceScope, descriptor, anchor, out);
                    graphObservations.add(graphObservation);
                    if (sourceScope.startsWith("loader:")
                            && RoiUnknownHostChatRequestResolver.isFieldAnchor(anchor)) {
                        RoiUnknownHostChatRequestResolver resolver =
                                Main.ROI_UNKNOWN_HOST_CHAT_REQUEST_RESOLVER;
                        resolver.observeDexCandidate(sourceScope, anchor, descriptor,
                                graphObservation.invokes);
                        inspectSerializerOwnerForRequestConstructors(bridge, prefix,
                                sourceScope, anchor, owner, budget,
                                serializerConstructorCache, out);
                    }
                    appendSemanticRoleHints(candidatePrefix, graphObservation.invokes, out);
                    appendFunctionalRoleHints(candidatePrefix, graphObservation.invokes, out);
                    appendRuntimeEvidence(candidatePrefix, hostKey, sourceScope, descriptor,
                            runtimeEvidence, runtimeCoverage, adapterIdentity, out);
                    int relationCount = graphObservation.invokes.size()
                            + graphObservation.callers.size();
                    RoiFunctionalRoleClassifier.Result autoProbeRoles =
                            RoiFunctionalRoleClassifier.classify(graphObservation.invokes);
                    if (sourceScope.startsWith("loader:") && shape != null
                            && relationCount > 0 && autoProbeRoles.roles.size() == 1
                            && autoProbeRoles.roles.get(0)
                                    != RoiFunctionalRoleClassifier.Role.UNRESOLVED) {
                        Main.registerRoiUnknownHostAutoProbeCandidate(owner, method, descriptor,
                                sourceScope, autoProbeRoles.roles.get(0).name());
                    }
                    List<RoiHookResolver.Evidence> evidence = Arrays.asList(
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.HOST_IDENTITY,
                                    0.25, false, "host_diagnostic_only"),
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.SEMANTIC_MARKER,
                                    0.35, true, "dexkit.string_anchor:" + anchor),
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.METHOD_SHAPE,
                                    shape == null ? 0.0 : 0.55, shape != null,                                    shape == null ? "descriptor_invalid" : "dex_descriptor_shape_validated"),
                            new RoiHookResolver.Evidence(
                                    RoiHookResolver.EvidenceKind.CALL_GRAPH,
                                    relationCount == 0 ? 0.0 : 0.30, false,
                                    relationCount == 0 ? "no_static_call_edges_observed"
                                            : "dexkit_method_data_invokes_and_callers"));
                    RoiHookResolver.Candidate candidate = new RoiHookResolver.Candidate(
                            hostKey, "unresolved", owner, method, descriptor,
                            sourceScope, candidateId,
                            false, false, Collections.<String>emptySet(), evidence);
                    if (sourceScope.startsWith("loader:") && shape != null) {
                        String priorClassMatches = RoiClassCandidateMatcher.registerCandidate(
                                new RoiClassCandidateMatcher.Candidate(
                                        sourceScope, owner, method, descriptor));
                        if (priorClassMatches.length() > 0) {
                            append(out, candidatePrefix + ".runtimeClassMatches="
                                    + priorClassMatches.replace("\n", "|"));
                        }
                    }
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
            if (out.length() > 8200 || !budget.canContinue(System.nanoTime())) break;
        }
        if (sourceScope.startsWith("loader:")) {
            RoiUnknownHostChatRequestResolver.Candidate chatRequest =
                    Main.ROI_UNKNOWN_HOST_CHAT_REQUEST_RESOLVER.bestCandidate(sourceScope);
            if (chatRequest != null) {
                append(out, prefix + ".autoFeature.chatRequestCandidate="
                        + chatRequest.ownerDescriptor + ",anchors="
                        + chatRequest.evidenceCount() + ",activation=deferred_until_scan_finalization");
            }
        }
        appendCandidateGraph(prefix, graphObservations, out);
        return total;
    }

    private static final class SerializerConstructorRef {
        final String methodDescriptor;
        final String constructorDescriptor;
        SerializerConstructorRef(String methodDescriptor, String constructorDescriptor) {
            this.methodDescriptor = methodDescriptor;
            this.constructorDescriptor = constructorDescriptor;
        }
    }

    /**
     * Field-name strings commonly live in a generated serializer descriptor method while the
     * actual DTO constructor is referenced from its deserialize method. Search the same serializer
     * class once, cache its constructor references, then associate each independently observed
     * field anchor with those references.
     */
    private static void inspectSerializerOwnerForRequestConstructors(DexKitBridge bridge,
            String prefix, String sourceScope, String anchor, String owner,
            RoiDiscoveryBudget budget,
            Map<String, List<SerializerConstructorRef>> cache, StringBuilder out) {
        String className = normalizeDexClassName(owner);
        if (className.length() == 0) return;
        String cacheKey = sourceScope + "|" + className;
        List<SerializerConstructorRef> references = cache.get(cacheKey);
        if (references == null) {
            references = new ArrayList<SerializerConstructorRef>();
            if (!budget.tryStartQuery(System.nanoTime())) {
                append(out, prefix + ".serializerOwner." + safeKey(className)
                        + ".querySkipped=" + budget.stopReason());
                cache.put(cacheKey, references);
                return;
            }
            try {
                FindMethod query = FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(className)
                                .invokeMethods(MethodsMatcher.create().countMin(0)));
                Object methods = bridge.findMethod(query);
                int count = Math.min(invokeSize(methods), MAX_SERIALIZER_METHODS_PER_OWNER);
                for (int i = 0; i < count; i++) {
                    if (!budget.tryAcceptCandidate(System.nanoTime())) break;
                    Object methodData = invokeGet(methods, i);
                    String methodDescriptor = safe(readString(methodData, "getDescriptor"));
                    if (methodDescriptor.length() == 0) continue;
                    Object invokes = readObject(methodData, "getInvokes");
                    int invokeCount = Math.min(invokeSize(invokes), 128);
                    for (int j = 0; j < invokeCount; j++) {
                        Object invoked = invokeGet(invokes, j);
                        String invokedDescriptor = safe(readString(invoked, "getDescriptor"));
                        if (!RoiUnknownHostChatRequestResolver
                                .isChatRequestConstructor(invokedDescriptor)) continue;
                        boolean duplicate = false;
                        for (SerializerConstructorRef existing : references) {
                            if (existing.methodDescriptor.equals(methodDescriptor)
                                    && existing.constructorDescriptor.equals(invokedDescriptor)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            references.add(new SerializerConstructorRef(
                                    methodDescriptor, invokedDescriptor));
                            if (references.size() >= MAX_REQUEST_CTORS_PER_SERIALIZER) break;
                        }
                    }
                    if (references.size() >= MAX_REQUEST_CTORS_PER_SERIALIZER) break;
                }
                append(out, prefix + ".serializerOwner." + safeKey(className)
                        + ".methodsScanned=" + count
                        + ",requestConstructorRefs=" + references.size());
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable error) {
                rethrowFatal(error);
                append(out, prefix + ".serializerOwner." + safeKey(className)
                        + ".error=" + error.getClass().getName());
            }
            cache.put(cacheKey, references);
        }
        if (references.isEmpty()) return;
        for (SerializerConstructorRef reference : references) {
            Main.ROI_UNKNOWN_HOST_CHAT_REQUEST_RESOLVER.observeDexCandidate(
                    sourceScope, anchor, reference.methodDescriptor,
                    Collections.singletonList(reference.constructorDescriptor));
        }
    }

    private static String normalizeDexClassName(String owner) {
        if (owner == null) return "";
        String value = owner.trim();
        if (value.startsWith("L") && value.endsWith(";") && value.length() > 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace('/', '.');
    }

    private static void appendRuntimeEvidence(String prefix, String candidateHostIdentity,
            String candidateSourceScope, String candidateDescriptor,
            List<RoiRuntimeInvocationRecorder.EvidenceSnapshot> snapshots,
            List<RoiRuntimeInvocationRecorder.CoverageSnapshot> coverage,
            String adapterIdentity, StringBuilder out) {
        int matches = 0;
        int requestBuilt = 0;
        int transportEntered = 0;
        int responseReceived = 0;
        int responseParsed = 0;
        int otherScopeEvidenceEntries = 0;
        int exactScopeCoverageObserved = 0;
        int exactScopeCoverageUnsupportedSuspend = 0;
        int otherScopeCoverageEntries = 0;
        boolean orderedRequestTransportResponse = false;
        boolean orderedParsedResponse = false;
        if (adapterIdentity.length() > 0
                && adapterIdentity.equals(candidateHostIdentity)
                && candidateDescriptor.length() > 0) {
            for (RoiRuntimeInvocationRecorder.EvidenceSnapshot item : snapshots) {
                if (!adapterIdentity.equals(item.adapterIdentity)
                        || !candidateDescriptor.equals(item.candidateDescriptor)) continue;
                if (!candidateSourceScope.equals(item.sourceScope)) {
                    if (candidateSourceScope.startsWith("loader:")) otherScopeEvidenceEntries++;
                    continue;
                }
                matches++;
                switch (item.phase) {
                    case REQUEST_BUILT: requestBuilt++; break;
                    case TRANSPORT_ENTERED: transportEntered++; break;
                    case RESPONSE_RECEIVED: responseReceived++; break;
                    case RESPONSE_PARSED: responseParsed++; break;
                    default: break;
                }
                orderedRequestTransportResponse |= item.orderedRequestTransportResponse;
                orderedParsedResponse |= item.orderedParsedResponse;
            }
            if (candidateSourceScope.startsWith("loader:")) {
                for (RoiRuntimeInvocationRecorder.CoverageSnapshot item : coverage) {
                    if (!adapterIdentity.equals(item.adapterIdentity)
                            || !candidateDescriptor.equals(item.candidateDescriptor)) continue;
                    if (!candidateSourceScope.equals(item.sourceScope)) {
                        otherScopeCoverageEntries++;
                        continue;
                    }
                    if (item.status == RoiRuntimeInvocationRecorder.CoverageStatus.RESPONSE_OBSERVED) {
                        exactScopeCoverageObserved++;
                    } else if (item.status == RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_SUSPEND_COLLECTOR) {
                        exactScopeCoverageUnsupportedSuspend++;
                    }
                }
            }
        }
        append(out, prefix + ".runtimeEvidence.exactScopedDescriptorMatches=" + matches);
        append(out, prefix + ".runtimeEvidence.otherClassLoaderScopeEvidenceEntries=" + otherScopeEvidenceEntries);
        append(out, prefix + ".runtimeEvidence.exactScopeCoverage.responseObserved=" + exactScopeCoverageObserved);
        append(out, prefix + ".runtimeEvidence.exactScopeCoverage.unsupportedSuspendCollector=" + exactScopeCoverageUnsupportedSuspend);
        append(out, prefix + ".runtimeEvidence.otherClassLoaderScopeCoverageEntries=" + otherScopeCoverageEntries);
        append(out, prefix + ".runtimeEvidence.phaseCounts=requestBuilt:" + requestBuilt
                + ",transportEntered:" + transportEntered + ",responseReceived:" + responseReceived
                + ",responseParsed:" + responseParsed);
        append(out, prefix + ".runtimeEvidence.orderedRequestTransportResponse="
                + orderedRequestTransportResponse);
        append(out, prefix + ".runtimeEvidence.orderedParsedResponse=" + orderedParsedResponse);
    }

    private static void appendFunctionalRoleHints(String prefix, List<String> invokes,
                                                   StringBuilder out) {
        RoiFunctionalRoleClassifier.Result result =
                RoiFunctionalRoleClassifier.classify(invokes);
        append(out, prefix + ".functionalRole.provenance=" + result.provenance);
        append(out, prefix + ".functionalRole.staticConfidence="
                + String.format(java.util.Locale.ROOT, "%.2f", result.staticConfidence));
        append(out, prefix + ".functionalRole.activationAuthorized=false");
        for (RoiFunctionalRoleClassifier.Role role : result.roles) {
            append(out, prefix + ".functionalRole.role=" + role.name());
        }
        int emitted = Math.min(result.evidence.size(), 6);
        for (int i = 0; i < emitted; i++) {
            append(out, prefix + ".functionalRole.evidence." + i + "="
                    + safe(result.evidence.get(i)));
        }
        if (result.evidence.size() > emitted) {
            append(out, prefix + ".functionalRole.evidenceOmitted="
                    + (result.evidence.size() - emitted));
        }
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
            String prefix, String sourceScope, String descriptor, String anchor, StringBuilder out) throws Exception {
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
        return new RoiCandidateGraph.Observation(sourceScope, descriptor, anchor,
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