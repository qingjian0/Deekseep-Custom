package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects the existing source-scoped DEX candidate evidence to serializer element discovery
 * and constructor-parameter provenance. This pipeline remains static evidence only.
 */
public final class RoiDexSerializerPipeline {
    private RoiDexSerializerPipeline() {}

    public static final class Result {
        public final String registrationMethodKey,rejectionReason;
        public final RoiDexSerializerElementIndex.Result elementIndices;
        public final RoiDexSerializerMapper.Result mapping;
        public final boolean complete;
        public final boolean candidateEvidenceOnly=true;
        public final boolean hookAuthorizationGranted=false;
        Result(String registration,String reason,RoiDexSerializerElementIndex.Result indices,
                RoiDexSerializerMapper.Result mapping,boolean complete) {
            registrationMethodKey=registration;rejectionReason=reason;
            elementIndices=indices;this.mapping=mapping;this.complete=complete;
        }
    }

    public static Result analyze(RoiDexFileReader.DexFile dex,
            RoiDexFileReader.ResolvedMethod serializerMethod,String constructorDescriptor) {
        if(dex==null||serializerMethod==null||serializerMethod.codeItem==null
                ||!isRequestConstructorDescriptor(constructorDescriptor))
            return rejected("serializer_pipeline_input_invalid");
        int arrow=constructorDescriptor.indexOf("->");
        int open=constructorDescriptor.indexOf('(',arrow+2);
        if(arrow<=0||open<0)return rejected("constructor_descriptor_invalid");
        String constructorOwner=constructorDescriptor.substring(0,arrow);
        String constructorPrototype=constructorDescriptor.substring(open);
        RoiDexSerializerElementIndex.Result selectedElements=null;
        String registrationKey="";
        int registrationCandidates=0;
        boolean registrationCandidateAnalysisFailed=false;

        for(RoiDexFileReader.ResolvedMethod candidate:dex.definedMethods) {
            if(candidate.codeItem==null
                    ||!serializerMethod.declaringClassDescriptor.equals(candidate.declaringClassDescriptor))
                continue;
            if(!hasAddElementCall(dex,candidate.codeItem))continue;
            // Count every plausible registration method before attempting analysis. A candidate
            // that fails parsing must still participate in ambiguity decisions.
            registrationCandidates++;
            try {
                RoiDexValueProvenance.Result provenance=analyzeValues(dex,candidate.codeItem);
                RoiDexSerializerElementIndex.Result elements=RoiDexSerializerElementIndex.analyze(
                        candidate.codeItem,dex::resolveMethodId,provenance);
                if(elements.registrationOrderProven) {
                    selectedElements=elements;
                    registrationKey=candidate.declaringClassDescriptor+"->"+candidate.name
                            +candidate.prototypeDescriptor;
                } else {
                    registrationCandidateAnalysisFailed=true;
                }
            } catch(Exception ignored) {
                registrationCandidateAnalysisFailed=true;
                // Failed plausible candidates remain relevant to uniqueness and fail closed.
            }
        }
        String candidateFailure=registrationCandidateFailureReason(
                registrationCandidates,registrationCandidateAnalysisFailed);
        if(candidateFailure!=null||selectedElements==null)
            return rejected(candidateFailure==null
                    ?"serializer_element_registration_analysis_incomplete":candidateFailure);

        final RoiDexSerializerMapper.Result mapping;
        try {
            RoiDexValueProvenance.Result provenance=analyzeValues(dex,serializerMethod.codeItem);
            mapping=RoiDexSerializerMapper.analyze(serializerMethod.codeItem,constructorOwner,
                    constructorPrototype,dex::resolveMethodId,selectedElements,provenance);
        } catch(Exception error) {
            return new Result(registrationKey,"serializer_parameter_mapping_failed:"
                    +safe(error.getMessage()),selectedElements,null,false);
        }
        return new Result(registrationKey,mapping.complete?"":"serializer_parameter_mapping_incomplete",
                selectedElements,mapping,mapping.complete);
    }

    static String registrationCandidateFailureReason(int candidateCount,boolean analysisFailed) {
        if(candidateCount<0)return "serializer_element_registration_candidate_count_invalid";
        if(candidateCount==0)return "serializer_element_registration_not_found";
        if(candidateCount>1)return "serializer_element_registration_ambiguous";
        if(analysisFailed)return "serializer_element_registration_analysis_incomplete";
        return null;
    }

    public static boolean isRequestConstructorDescriptor(String descriptor) {
        if(descriptor==null||descriptor.length()>4096)return false;
        int arrow=descriptor.indexOf("->");
        int open=descriptor.indexOf('(',arrow+2);
        int close=descriptor.indexOf(')',open+1);
        if(arrow<=1||open<=arrow+2||close<0
                ||!"<init>".equals(descriptor.substring(arrow+2,open))
                ||close!=descriptor.length()-2||!"V".equals(descriptor.substring(close+1)))
            return false;
        try {
            int[] widths=RoiDexInstructionDecoder.parameterWordWidthsFor(descriptor.substring(open));
            if(widths.length!=11)return false;
            String[] expected={"Ljava/lang/String;","Ljava/lang/String;","Z","Z","Z",
                    "Ljava/lang/String;","Ljava/lang/String;","I"};
            int[] positions={0,2,4,5,7,8,9,10};
            for(int i=0;i<positions.length;i++)
                if(!expected[i].equals(RoiDexInstructionDecoder.parameterDescriptorAt(
                        descriptor.substring(open),positions[i])))return false;
            return true;
        } catch(RoiDexInstructionDecoder.DecodeException error) {
            return false;
        }
    }

    private static RoiDexValueProvenance.Result analyzeValues(RoiDexFileReader.DexFile dex,
            RoiDexCodeReader.CodeItem code)throws Exception {
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,dex);
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        return RoiDexValueProvenance.analyze(code,index->{
            if(index<0||index>=dex.strings.length)
                throw new RoiDexValueProvenance.AnalysisException("string_index_out_of_range");
            return dex.strings[index];
        },index->{
            if(index<0||index>=dex.typeDescriptors.length)
                throw new RoiDexValueProvenance.AnalysisException("type_index_out_of_range");
            return dex.typeDescriptors[index];
        },flow,reaching,useDef,links);
    }

    private static boolean hasAddElementCall(RoiDexFileReader.DexFile dex,
            RoiDexCodeReader.CodeItem code) {
        final RoiDexInstructionScanner.ScanResult scan;
        try { scan=RoiDexInstructionScanner.scan(code.instructions); }
        catch(RoiDexInstructionScanner.ScanException error){return false;}
        for(RoiDexInstructionScanner.Instruction instruction:scan.instructions) {
            if(instruction.payload||!isInvoke(instruction.opcode))continue;
            try {
                RoiDexInstructionDecoder.Decoded decoded=code.decodeInstructionAt(instruction.offset);
                if(decoded.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.METHOD)continue;
                RoiDexFileReader.ResolvedMethodId method=dex.resolveMethodId(decoded.referenceIndex);
                if("addElement".equals(method.name)
                        &&"(Ljava/lang/String;Z)V".equals(method.prototypeDescriptor))return true;
            } catch(Exception ignored) {
                // An unresolved unrelated method does not create a descriptor registration match.
            }
        }
        return false;
    }

    private static boolean isInvoke(int opcode) {
        return (opcode>=0x6e&&opcode<=0x72)||(opcode>=0x74&&opcode<=0x78);
    }
    private static Result rejected(String reason) {
        return new Result("",reason,null,null,false);
    }
    private static String safe(String value) {
        if(value==null)return "unknown";
        return value.length()>160?value.substring(0,160):value;
    }
}
