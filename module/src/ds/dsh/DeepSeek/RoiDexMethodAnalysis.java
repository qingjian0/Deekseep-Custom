package ds.dsh.DeepSeek;

/**
 * End-to-end static evidence assembly for one already-resolved DEX method.
 *
 * This is an analysis pipeline only: it never installs hooks, grants runtime
 * permissions, or treats incomplete exception-flow evidence as exact.
 */
public final class RoiDexMethodAnalysis {
    private RoiDexMethodAnalysis() {}

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }

    public static final class Result {
        public final int methodIndex;
        public final int accessFlags;
        public final int codeOffset;
        public final String declaringClassDescriptor;
        public final String methodName;
        public final String prototypeDescriptor;
        public final boolean isStatic;
        public final boolean isDirect;
        public final RoiDexMethodEntryLayout.Result entryLayout;
        public final RoiDexRegisterFlowAnalyzer.Result registerFlow;
        public final RoiDexReachingDefinitions.Result reachingDefinitions;
        public final RoiDexUseDefChains.Result useDefChains;
        public final boolean exceptionEdgesIncluded;
        public final boolean completeForExceptionFlow;
        /** Static evidence is a candidate input, never runtime Hook authorization. */
        public final boolean candidateEvidenceOnly;
        /** Always false: authorization is an independent runtime policy decision. */
        public final boolean hookAuthorizationGranted;

        Result(RoiDexFileReader.ResolvedMethod method,
                RoiDexMethodEntryLayout.Result layout,
                RoiDexRegisterFlowAnalyzer.Result flow,
                RoiDexReachingDefinitions.Result reaching,
                RoiDexUseDefChains.Result chains) {
            methodIndex = method.methodIndex;
            accessFlags = method.accessFlags;
            codeOffset = method.codeOffset;
            declaringClassDescriptor = method.declaringClassDescriptor;
            methodName = method.name;
            prototypeDescriptor = method.prototypeDescriptor;
            isStatic = method.isStatic;
            isDirect = method.isDirect;
            entryLayout = layout;
            registerFlow = flow;
            reachingDefinitions = reaching;
            useDefChains = chains;
            exceptionEdgesIncluded = flow.exceptionEdgesIncluded
                    && reaching.exceptionEdgesIncluded && chains.exceptionEdgesIncluded;
            completeForExceptionFlow = flow.completeForExceptionFlow
                    && reaching.completeForExceptionFlow && chains.completeForExceptionFlow;
            candidateEvidenceOnly = true;
            hookAuthorizationGranted = false;
        }
    }

    /**
     * Assemble method identity, incoming-register layout, CFG/register flow,
     * reaching definitions, and use/def provenance from a ResolvedMethod.
     */
    public static Result analyze(RoiDexFileReader.ResolvedMethod method)
            throws AnalysisException {
        if (method == null) throw new AnalysisException("null_resolved_method");
        if (method.codeItem == null || method.codeOffset == 0)
            throw new AnalysisException("method_code_item_missing");
        try {
            RoiDexMethodEntryLayout.Result layout = method.methodEntryLayout();
            RoiDexRegisterFlowAnalyzer.Result flow =
                    RoiDexRegisterFlowAnalyzer.analyze(method.codeItem);
            RoiDexReachingDefinitions.Result reaching =
                    RoiDexReachingDefinitions.analyze(flow);
            RoiDexUseDefChains.Result chains =
                    RoiDexUseDefChains.analyze(flow, reaching, layout);
            return new Result(method, layout, flow, reaching, chains);
        } catch (RoiDexMethodEntryLayout.AnalysisException error) {
            throw new AnalysisException("method_entry_layout_invalid:" + error.getMessage());
        } catch (RoiDexRegisterFlowAnalyzer.AnalysisException error) {
            throw new AnalysisException("register_flow_invalid:" + error.getMessage());
        } catch (RoiDexReachingDefinitions.AnalysisException error) {
            throw new AnalysisException("reaching_definitions_invalid:" + error.getMessage());
        } catch (RoiDexUseDefChains.AnalysisException error) {
            throw new AnalysisException("use_def_chains_invalid:" + error.getMessage());
        }
    }
}
