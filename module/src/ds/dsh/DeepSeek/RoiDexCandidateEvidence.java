package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Joins parsed DEX method definitions, invoke references and per-method register
 * analysis into a bounded, source-scoped candidate graph.
 *
 * The graph represents static DEX references only. It does not load classes,
 * invoke host code, install hooks, or authorize runtime behavior.
 */
public final class RoiDexCandidateEvidence {
    private static final int MAX_SELECTED_METHODS = 544;
    private static final int MAX_SERIALIZER_PIPELINE_ANALYSES = 8;
    private RoiDexCandidateEvidence() {}

    public static final class EvidenceException extends Exception {
        public EvidenceException(String message) { super(message); }
    }

    public static final class MethodEvidence {
        public final int methodIndex;
        public final String methodKey;
        public final List<String> invokes;
        public final List<String> reachableInvokes;
        public final List<String> unreachableInvokes;
        public final boolean invokeReachabilityComplete;
        public final RoiDexMethodAnalysis.Result analysis;
        /** Empty when analysis succeeded; otherwise a stable fail-closed reason. */
        public final String analysisFailure;
        /** Empty only when the method's invoke references were decoded completely. */
        public final String invokeAnalysisFailure;
        public final RoiDexSerializerPipeline.Result serializerPipeline;
        public final String serializerPipelineFailure;

        MethodEvidence(int index, String key, List<String> calls,
                List<String> reachable, List<String> unreachable, boolean reachabilityComplete,
                RoiDexMethodAnalysis.Result result, String failure, String invokeFailure,
                RoiDexSerializerPipeline.Result serializerResult, String serializerFailure) {
            methodIndex = index;
            methodKey = key;
            invokes = Collections.unmodifiableList(new ArrayList<String>(calls));
            reachableInvokes = Collections.unmodifiableList(new ArrayList<String>(reachable));
            unreachableInvokes = Collections.unmodifiableList(new ArrayList<String>(unreachable));
            invokeReachabilityComplete = reachabilityComplete;
            analysis = result;
            analysisFailure = failure == null ? "" : failure;
            invokeAnalysisFailure = invokeFailure == null ? "" : invokeFailure;
            serializerPipeline = serializerResult;
            serializerPipelineFailure = serializerFailure == null ? "" : serializerFailure;
        }
    }

    public static final class Result {
        public final String sourceScope;
        public final RoiCandidateGraph.Result graph;
        public final List<MethodEvidence> methods;
        public final int analyzedMethods;
        public final int analysisFailures;
        /** Static references and analysis are candidate evidence only. */
        public final boolean candidateEvidenceOnly = true;
        /** This pipeline can never grant runtime Hook authorization. */
        public final boolean hookAuthorizationGranted = false;

        Result(String scope, RoiCandidateGraph.Result graph,
                List<MethodEvidence> methods, int analyzed, int failures) {
            sourceScope = scope;
            this.graph = graph;
            this.methods = Collections.unmodifiableList(
                    new ArrayList<MethodEvidence>(methods));
            analyzedMethods = analyzed;
            analysisFailures = failures;
        }
    }

    public static Result analyze(RoiDexFileReader.DexFile dex, String sourceScope)
            throws EvidenceException {
        return analyzeInternal(dex, sourceScope, null);
    }

    /**
     * Analyze only exact, caller-selected method descriptors. This is the bounded path
     * for runtime discovery after DexKit has identified a small set of serializer candidates.
     * A missing or duplicate selected definition is rejected instead of silently widening
     * the analysis to neighboring methods.
     */
    public static Result analyzeSelected(RoiDexFileReader.DexFile dex, String sourceScope,
            Set<String> selectedMethodDescriptors) throws EvidenceException {
        if (selectedMethodDescriptors == null)
            throw new EvidenceException("selected_method_set_required");
        if (selectedMethodDescriptors.size() > MAX_SELECTED_METHODS)
            throw new EvidenceException("selected_method_budget_exceeded");
        return analyzeInternal(dex, sourceScope, new HashSet<String>(selectedMethodDescriptors));
    }

    private static Result analyzeInternal(RoiDexFileReader.DexFile dex, String sourceScope,
            Set<String> selected) throws EvidenceException {
        if (dex == null) throw new EvidenceException("null_dex_file");
        String scope = clean(sourceScope);
        if (scope.length() == 0) throw new EvidenceException("source_scope_required");

        List<RoiCandidateGraph.Observation> observations =
                new ArrayList<RoiCandidateGraph.Observation>();
        List<MethodEvidence> methodEvidence = new ArrayList<MethodEvidence>();
        Set<String> found = new HashSet<String>();
        int analyzed = 0;
        int failures = 0;
        int serializerAttempts = 0;
        Map<String, RoiDexSerializerPipeline.Result> serializerCache =
                new LinkedHashMap<String, RoiDexSerializerPipeline.Result>();
        Map<String, String> serializerFailureCache = new LinkedHashMap<String, String>();

        for (RoiDexFileReader.ResolvedMethod method : dex.definedMethods) {
            String key = methodKey(dex, method.methodIndex);
            if (selected != null && !selected.contains(key)) continue;
            if (selected != null && !found.add(key))
                throw new EvidenceException("selected_method_definition_ambiguous:" + key);
            InvokeCollection invokeCollection = method.codeItem == null
                    ? new InvokeCollection(Collections.<InvokeReference>emptyList(), "")
                    : collectInvokes(dex, method.codeItem, method.methodIndex);
            // Preserve syntactic references separately from entry-reachable call edges.
            List<String> invokes = invokeCollection.complete
                    ? invokeKeys(invokeCollection.invokes) : Collections.<String>emptyList();

            RoiDexMethodAnalysis.Result analysis = null;
            String failure = "";
            if (method.codeItem != null) {
                try {
                    analysis = RoiDexMethodAnalysis.analyze(method);
                    analyzed++;
                } catch (RoiDexMethodAnalysis.AnalysisException error) {
                    failure = error.getMessage();
                    failures++;
                }
            }
            RoiDexInvokeReachability.Partition partition = partitionInvokes(invokeCollection, analysis);
            RoiDexSerializerPipeline.Result serializerPipeline = null;
            String serializerFailure = "";
            if (method.codeItem != null && partition.complete) {
                String expectedConstructor = null;
                boolean constructorAmbiguous = false;
                for (String invoked : partition.reachableInvokes) {
                    if (!RoiDexSerializerPipeline.isRequestConstructorDescriptor(invoked)) continue;
                    if (expectedConstructor == null) expectedConstructor = invoked;
                    else if (!expectedConstructor.equals(invoked)) constructorAmbiguous = true;
                }
                if (constructorAmbiguous) {
                    serializerFailure = "request_constructor_evidence_ambiguous";
                } else if (expectedConstructor != null) {
                    String pipelineKey = method.declaringClassDescriptor + "|" + key + "|" + expectedConstructor;
                    if (serializerCache.containsKey(pipelineKey)) {
                        serializerPipeline = serializerCache.get(pipelineKey);
                        serializerFailure = serializerFailureCache.get(pipelineKey);
                    } else if (serializerAttempts >= MAX_SERIALIZER_PIPELINE_ANALYSES) {
                        serializerFailure = "serializer_pipeline_budget_exceeded";
                    } else {
                        serializerAttempts++;
                        serializerPipeline = RoiDexSerializerPipeline.analyze(dex, method, expectedConstructor);
                        serializerFailure = serializerPipeline.rejectionReason == null
                                ? "" : serializerPipeline.rejectionReason;
                        serializerCache.put(pipelineKey, serializerPipeline);
                        serializerFailureCache.put(pipelineKey, serializerFailure);
                    }
                }
            }
            observations.add(new RoiCandidateGraph.Observation(scope, key, "",
                    partition.reachableInvokes, Collections.<String>emptyList()));
            methodEvidence.add(new MethodEvidence(method.methodIndex, key, invokes,
                    partition.reachableInvokes, partition.unreachableInvokes, partition.complete,
                    analysis, failure, invokeCollection.failure, serializerPipeline, serializerFailure));
        }

        if (selected != null) {
            for (String descriptor : selected) {
                if (descriptor == null || descriptor.trim().length() == 0)
                    throw new EvidenceException("selected_method_descriptor_invalid");
                if (!found.contains(descriptor))
                    throw new EvidenceException("selected_method_definition_missing:" + descriptor);
            }
        }

        RoiCandidateGraph.Result graph = RoiCandidateGraph.build(observations);
        return new Result(scope, graph, methodEvidence, analyzed, failures);
    }

    private static final class InvokeReference {
        final int offset;
        final String methodKey;
        InvokeReference(int offset, String key) { this.offset = offset; this.methodKey = key; }
    }

    private static final class InvokeCollection {
        final List<InvokeReference> invokes;
        final String failure;
        final boolean complete;
        InvokeCollection(List<InvokeReference> values, String error) {
            invokes = Collections.unmodifiableList(new ArrayList<InvokeReference>(values));
            failure = error == null ? "" : error;
            complete = failure.length() == 0;
        }
    }

    private static List<String> invokeKeys(List<InvokeReference> refs) {
        List<String> keys = new ArrayList<String>(refs.size());
        for (InvokeReference ref : refs) keys.add(ref.methodKey);
        return keys;
    }

    private static RoiDexInvokeReachability.Partition partitionInvokes(
            InvokeCollection collection, RoiDexMethodAnalysis.Result analysis) {
        if (collection == null || !collection.complete || analysis == null)
            return RoiDexInvokeReachability.Partition.incomplete();
        RoiDexReachingDefinitions.BlockResult[] blocks = analysis.reachingDefinitions.blocks;
        int[] starts = new int[blocks.length], ends = new int[blocks.length];
        boolean[] reachable = new boolean[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            starts[i] = blocks[i].startOffset;
            ends[i] = blocks[i].endOffset;
            reachable[i] = blocks[i].reachableFromEntry;
        }
        List<RoiDexInvokeReachability.Invocation> calls =
                new ArrayList<RoiDexInvokeReachability.Invocation>(collection.invokes.size());
        for (InvokeReference ref : collection.invokes)
            calls.add(new RoiDexInvokeReachability.Invocation(ref.offset, ref.methodKey));
        return RoiDexInvokeReachability.partition(calls, starts, ends, reachable, true);
    }

    private static InvokeCollection collectInvokes(RoiDexFileReader.DexFile dex,
            RoiDexCodeReader.CodeItem code, int callerMethodIndex)
            throws EvidenceException {
        List<InvokeReference> invokes = new ArrayList<InvokeReference>();
        final RoiDexInstructionScanner.ScanResult scan;
        try {
            scan = RoiDexInstructionScanner.scan(code.instructions);
        } catch (RoiDexInstructionScanner.ScanException error) {
            return new InvokeCollection(Collections.<InvokeReference>emptyList(),
                    "invoke_scan_invalid:method=" + callerMethodIndex + ":" + error.getMessage());
        }
        for (RoiDexInstructionScanner.Instruction instruction : scan.instructions) {
            if (instruction.payload || !isMethodInvoke(instruction.opcode)) continue;
            final RoiDexInstructionDecoder.Decoded decoded;
            try {
                decoded = code.decodeInstructionAt(instruction.offset);
            } catch (RoiDexInstructionDecoder.DecodeException error) {
                int first = code.instructions[instruction.offset] & 0xffff;
                int opcode = first & 0xff;
                int argumentWords = (first >>> 12) & 0x0f;
                return new InvokeCollection(Collections.<InvokeReference>emptyList(),
                        "invoke_decode_invalid:method=" + callerMethodIndex
                                + ":offset=" + instruction.offset
                                + ":opcode=0x" + Integer.toHexString(opcode)
                                + ":argument_words=" + argumentWords
                                + ":" + error.getMessage());
            }
            if (decoded.referenceIndex < 0 || decoded.referenceIndex >= dex.methods.length)
                throw new EvidenceException("invoke_method_index_out_of_range:method="
                        + callerMethodIndex + ":offset=" + instruction.offset);
            invokes.add(new InvokeReference(instruction.offset, methodKey(dex, decoded.referenceIndex)));
        }
        return new InvokeCollection(invokes, "");
    }

    private static boolean isMethodInvoke(int opcode) {
        return (opcode >= 0x6e && opcode <= 0x72)
                || (opcode >= 0x74 && opcode <= 0x78);
    }

    private static String methodKey(RoiDexFileReader.DexFile dex, int index)
            throws EvidenceException {
        if (index < 0 || index >= dex.methods.length)
            throw new EvidenceException("method_index_out_of_range:" + index);
        RoiDexFileReader.Method method = dex.methods[index];
        if (method.classIndex < 0 || method.classIndex >= dex.typeDescriptors.length
                || method.nameIndex < 0 || method.nameIndex >= dex.strings.length
                || method.protoIndex < 0 || method.protoIndex >= dex.protos.length)
            throw new EvidenceException("method_id_reference_out_of_range:" + index);
        String owner = dex.typeDescriptors[method.classIndex];
        String name = dex.strings[method.nameIndex];
        String proto = prototypeDescriptor(dex, method.protoIndex);
        if (owner == null || owner.length() == 0 || name == null || name.length() == 0)
            throw new EvidenceException("method_identity_incomplete:" + index);
        return owner + "->" + name + proto;
    }

    private static String prototypeDescriptor(RoiDexFileReader.DexFile dex, int index)
            throws EvidenceException {
        RoiDexFileReader.Proto proto = dex.protos[index];
        if (proto.returnTypeIndex < 0 || proto.returnTypeIndex >= dex.typeDescriptors.length
                || index >= dex.protoParameterTypeIndices.length)
            throw new EvidenceException("prototype_reference_out_of_range:" + index);
        StringBuilder value = new StringBuilder("(");
        int[] parameters = dex.protoParameterTypeIndices[index];
        for (int type : parameters) {
            if (type < 0 || type >= dex.typeDescriptors.length)
                throw new EvidenceException("prototype_parameter_out_of_range:" + index);
            value.append(dex.typeDescriptors[type]);
        }
        value.append(')').append(dex.typeDescriptors[proto.returnTypeIndex]);
        return value.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
