package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Joins constructor arguments to serializer element indices through decode-element calls.
 * Only unique, statically proven paths are emitted; all output remains candidate evidence.
 */
public final class RoiDexSerializerMapper {
    private RoiDexSerializerMapper() {}

    public interface MethodResolver {
        RoiDexFileReader.ResolvedMethodId resolve(int methodIndex)
                throws RoiDexFileReader.DexException;
    }

    public static final class ParameterMapping {
        public final int constructorParameterIndex,elementIndex,constructorInvokeOffset,decodeInvokeOffset;
        public final String parameterDescriptor,fieldName,decodeOwner,decodeMethod,decodePrototype;
        ParameterMapping(int parameter,int element,int constructorOffset,int decodeOffset,
                String type,String field,String owner,String method,String prototype) {
            constructorParameterIndex=parameter;elementIndex=element;
            constructorInvokeOffset=constructorOffset;decodeInvokeOffset=decodeOffset;
            parameterDescriptor=type;fieldName=field;decodeOwner=owner;
            decodeMethod=method;decodePrototype=prototype;
        }
    }

    public static final class ConstructorMapping {
        public final int constructorInvokeOffset,methodIndex;
        public final String ownerDescriptor,prototypeDescriptor;
        public final ParameterMapping[] parameters;
        public final boolean complete;
        ConstructorMapping(int offset,int method,String owner,String prototype,
                List<ParameterMapping> mappings,boolean complete) {
            constructorInvokeOffset=offset;methodIndex=method;ownerDescriptor=owner;
            prototypeDescriptor=prototype;parameters=mappings.toArray(new ParameterMapping[0]);
            this.complete=complete;
        }
    }

    public static final class Rejected {
        public final int constructorInvokeOffset,parameterIndex;
        public final String reason;
        Rejected(int offset,int parameter,String why) {
            constructorInvokeOffset=offset;parameterIndex=parameter;reason=why;
        }
    }

    public static final class Result {
        public final ConstructorMapping[] constructors;
        public final Rejected[] rejected;
        public final boolean candidateEvidenceOnly=true;
        public final boolean hookAuthorizationGranted=false;
        public final boolean complete;
        Result(List<ConstructorMapping> constructors,List<Rejected> rejected,boolean complete) {
            this.constructors=constructors.toArray(new ConstructorMapping[0]);
            this.rejected=rejected.toArray(new Rejected[0]);this.complete=complete;
        }
    }

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message){super(message);}
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code,String expectedConstructorOwner,
            MethodResolver resolver,RoiDexSerializerElementIndex.Result elementIndices,
            RoiDexValueProvenance.Result provenance)throws AnalysisException {
        return analyze(code,expectedConstructorOwner,null,resolver,elementIndices,provenance);
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code,String expectedConstructorOwner,
            String expectedConstructorPrototype,MethodResolver resolver,
            RoiDexSerializerElementIndex.Result elementIndices,
            RoiDexValueProvenance.Result provenance)throws AnalysisException {
        if(code==null||expectedConstructorOwner==null||expectedConstructorOwner.length()==0
                ||resolver==null||elementIndices==null||provenance==null)
            throw new AnalysisException("null_or_incomplete_analysis_input");
        if(!elementIndices.registrationOrderProven)
            throw new AnalysisException("serializer_element_order_not_proven");
        if(!provenance.completeForExceptionFlow)
            throw new AnalysisException("serializer_value_provenance_exception_flow_incomplete");
        final RoiDexInstructionScanner.ControlFlowGraph graph;
        try {
            graph=RoiDexInstructionScanner.buildControlFlowGraph(code.instructions);
        } catch(RoiDexInstructionScanner.ScanException error) {
            throw new AnalysisException("serializer_method_cfg_invalid:"+error.getMessage());
        }
        Map<Integer,Integer> blockAt=new HashMap<Integer,Integer>();
        for(int b=0;b<graph.blocks.length;b++)
            for(RoiDexInstructionScanner.Instruction ins:graph.blocks[b].instructions)
                blockAt.put(ins.offset,b);

        List<ConstructorMapping> constructors=new ArrayList<ConstructorMapping>();
        List<Rejected> rejected=new ArrayList<Rejected>();
        boolean allComplete=true;
        for(RoiDexInstructionScanner.Instruction instruction:graph.scan.instructions) {
            if(instruction.payload||!isInvokeDirect(instruction.opcode))continue;
            RoiDexInstructionDecoder.Decoded invoke=decode(code,instruction.offset);
            if(invoke.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.METHOD)
                throw new AnalysisException("constructor_reference_kind_invalid");
            RoiDexFileReader.ResolvedMethodId constructor=resolve(resolver,invoke.referenceIndex);
            if(!"<init>".equals(constructor.name)
                    ||!expectedConstructorOwner.equals(constructor.ownerDescriptor)
                    ||(expectedConstructorPrototype!=null
                        &&!expectedConstructorPrototype.equals(constructor.prototypeDescriptor)))continue;
            final int expectedWords;
            final int[] parameterWidths;
            try {
                expectedWords=RoiDexInstructionDecoder.registerWordCountFor(
                        constructor.prototypeDescriptor,false);
                parameterWidths=RoiDexInstructionDecoder.parameterWordWidthsFor(
                        constructor.prototypeDescriptor);
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                throw new AnalysisException("constructor_prototype_invalid");
            }
            if(invoke.registers.length!=expectedWords) {
                rejected.add(new Rejected(instruction.offset,-1,"constructor_register_word_count_mismatch"));
                allComplete=false;continue;
            }
            if(!hasUniqueElementIdentity(elementIndices.elements)) {
                rejected.add(new Rejected(instruction.offset,-1,"serializer_element_identity_ambiguous"));
                constructors.add(new ConstructorMapping(instruction.offset,invoke.referenceIndex,
                        constructor.ownerDescriptor,constructor.prototypeDescriptor,
                        new ArrayList<ParameterMapping>(),false));
                allComplete=false;continue;
            }
            if(parameterWidths.length!=elementIndices.elements.length) {
                rejected.add(new Rejected(instruction.offset,-1,
                        "constructor_parameter_element_count_mismatch"));
                constructors.add(new ConstructorMapping(instruction.offset,invoke.referenceIndex,
                        constructor.ownerDescriptor,constructor.prototypeDescriptor,
                        new ArrayList<ParameterMapping>(),false));
                allComplete=false;continue;
            }
            List<ParameterMapping> mappings=new ArrayList<ParameterMapping>();
            Set<Integer> usedElementIndices=new HashSet<Integer>();
            boolean constructorComplete=true;
            for(int parameter=0;parameter<parameterWidths.length;parameter++) {
                int words=parameterWidths[parameter];
                final int wordPosition;
                final String parameterType;
                try {
                    wordPosition=RoiDexInstructionDecoder.parameterWordPositionFor(
                            constructor.prototypeDescriptor,false,parameter);
                    parameterType=RoiDexInstructionDecoder.parameterDescriptorAt(
                            constructor.prototypeDescriptor,parameter);
                } catch(RoiDexInstructionDecoder.DecodeException error) {
                    rejected.add(new Rejected(instruction.offset,parameter,"constructor_parameter_layout_invalid"));
                    constructorComplete=false;continue;
                }
                RoiDexValueProvenance.Trace value=provenance.traceUse(
                        instruction.offset,wordPosition,words);
                if(!value.unique||value.source==null) {
                    rejected.add(new Rejected(instruction.offset,parameter,
                            "constructor_argument_source_unresolved:"+value.rejectionReason));
                    constructorComplete=false;continue;
                }
                RoiDexInvokeResultLinker.Link decodeLink=value.source;
                if(!isDecodeElementMethod(decodeLink.methodName)) {
                    rejected.add(new Rejected(instruction.offset,parameter,"source_is_not_decode_element"));
                    constructorComplete=false;continue;
                }
                String effectiveType=value.effectiveTypeDescriptor;
                if(!parameterType.equals(effectiveType)) {
                    rejected.add(new Rejected(instruction.offset,parameter,"decode_return_type_mismatch"));
                    constructorComplete=false;continue;
                }
                final int indexWordPosition;
                try {
                    int indexParameter=findUniqueIntParameter(decodeLink.prototypeDescriptor);
                    if(indexParameter<0)throw new RoiDexInstructionDecoder.DecodeException(
                            "decode_element_index_parameter_missing_or_ambiguous");
                    indexWordPosition=RoiDexInstructionDecoder.parameterWordPositionFor(
                            decodeLink.prototypeDescriptor,decodeLink.isStaticInvoke,indexParameter);
                } catch(RoiDexInstructionDecoder.DecodeException error) {
                    rejected.add(new Rejected(instruction.offset,parameter,"decode_element_index_parameter_invalid"));
                    constructorComplete=false;continue;
                }
                RoiDexValueProvenance.IntTrace indexTrace=provenance.traceIntUse(
                        decodeLink.invokeOffset,indexWordPosition);
                int elementIndex;
                if(indexTrace.unique&&indexTrace.hasConstant) {
                    elementIndex=indexTrace.constantValue;
                } else if(indexTrace.unique&&indexTrace.sourceInvoke!=null
                        &&"decodeElementIndex".equals(indexTrace.sourceInvoke.methodName)) {
                    Integer switchKey=resolveSwitchCase(graph,code,provenance,
                            indexTrace.sourceInvoke,decodeLink.invokeOffset,blockAt);
                    if(switchKey==null) {
                        rejected.add(new Rejected(instruction.offset,parameter,
                                "dynamic_element_index_switch_mapping_ambiguous"));
                        constructorComplete=false;continue;
                    }
                    elementIndex=switchKey;
                } else {
                    rejected.add(new Rejected(instruction.offset,parameter,
                            "decode_element_index_unresolved:"+indexTrace.rejectionReason));
                    constructorComplete=false;continue;
                }
                RoiDexSerializerElementIndex.Element element=findElement(elementIndices,elementIndex);
                if(element==null) {
                    rejected.add(new Rejected(instruction.offset,parameter,"serializer_element_index_missing"));
                    constructorComplete=false;continue;
                }
                if(!usedElementIndices.add(elementIndex)) {
                    rejected.add(new Rejected(instruction.offset,parameter,"duplicate_constructor_element_mapping"));
                    constructorComplete=false;continue;
                }
                mappings.add(new ParameterMapping(parameter,elementIndex,instruction.offset,
                        decodeLink.invokeOffset,parameterType,element.name,decodeLink.ownerDescriptor,
                        decodeLink.methodName,decodeLink.prototypeDescriptor));
            }
            constructors.add(new ConstructorMapping(instruction.offset,invoke.referenceIndex,
                    constructor.ownerDescriptor,constructor.prototypeDescriptor,mappings,
                    constructorComplete));
            if(!constructorComplete)allComplete=false;
        }
        if(constructors.size()!=1) {
            allComplete=false;
            if(constructors.size()>1)rejected.add(new Rejected(-1,-1,"constructor_candidate_ambiguous"));
        }
        if(constructors.isEmpty())rejected.add(new Rejected(-1,-1,"constructor_candidate_missing"));
        return new Result(constructors,rejected,allComplete&&constructors.size()==1
                &&constructors.get(0).complete);
    }

    private static RoiDexInstructionDecoder.Decoded decode(RoiDexCodeReader.CodeItem code,int offset)
            throws AnalysisException {
        try { return code.decodeInstructionAt(offset); }
        catch(RoiDexInstructionDecoder.DecodeException error) {
            throw new AnalysisException("instruction_decode_failed:"+offset);
        }
    }
    private static RoiDexFileReader.ResolvedMethodId resolve(MethodResolver resolver,int index)
            throws AnalysisException {
        try {
            RoiDexFileReader.ResolvedMethodId method=resolver.resolve(index);
            if(method==null)throw new AnalysisException("method_id_resolution_null");
            return method;
        } catch(RoiDexFileReader.DexException error) {
            throw new AnalysisException("method_id_resolution_failed:"+index);
        }
    }
    private static boolean isInvokeDirect(int opcode){return opcode==0x70||opcode==0x76;}
    private static boolean isDecodeElementMethod(String name) {
        return name!=null&&name.startsWith("decode")&&name.endsWith("Element")
                &&!"decodeElement".equals(name);
    }
    private static String returnDescriptor(String prototype) {
        int close=prototype==null?-1:prototype.indexOf(')');
        return close<0?"":prototype.substring(close+1);
    }
    private static int findUniqueIntParameter(String prototype)
            throws RoiDexInstructionDecoder.DecodeException {
        int[] widths=RoiDexInstructionDecoder.parameterWordWidthsFor(prototype);
        int found=-1;
        for(int i=0;i<widths.length;i++) {
            if("I".equals(RoiDexInstructionDecoder.parameterDescriptorAt(prototype,i))) {
                if(found>=0)return -1;
                found=i;
            }
        }
        return found;
    }
    private static boolean hasUniqueElementIdentity(
            RoiDexSerializerElementIndex.Element[] elements) {
        if(elements==null)return false;
        Set<Integer> indices=new HashSet<Integer>();
        Set<String> names=new HashSet<String>();
        for(RoiDexSerializerElementIndex.Element element:elements) {
            if(element==null||element.index<0||element.name==null||element.name.length()==0
                    ||!indices.add(element.index)||!names.add(element.name))return false;
        }
        return true;
    }

    private static RoiDexSerializerElementIndex.Element findElement(
            RoiDexSerializerElementIndex.Result elements,int index) {
        for(RoiDexSerializerElementIndex.Element element:elements.elements)
            if(element.index==index)return element;
        return null;
    }

    private static Integer resolveSwitchCase(RoiDexInstructionScanner.ControlFlowGraph graph,
            RoiDexCodeReader.CodeItem code,RoiDexValueProvenance.Result provenance,
            RoiDexInvokeResultLinker.Link indexSource,int decodeOffset,Map<Integer,Integer> blockAt) {
        Integer decodeBlock=blockAt.get(decodeOffset);
        if(decodeBlock==null)return null;
        RoiDexInstructionScanner.Instruction matchingSwitch=null;
        Integer matchingKey=null;
        for(RoiDexInstructionScanner.Instruction instruction:graph.scan.instructions) {
            if(instruction.payload||(instruction.opcode!=0x2b&&instruction.opcode!=0x2c))continue;
            RoiDexValueProvenance.Trace switchValue=provenance.traceUse(instruction.offset,0,1);
            if(!switchValue.unique||switchValue.source==null
                    ||switchValue.source.invokeOffset!=indexSource.invokeOffset)continue;
            RoiDexInstructionScanner.SwitchCase[] cases;
            try { cases=graph.scan.switchCasesAt(instruction.offset); }
            catch(RoiDexInstructionScanner.ScanException error){return null;}
            int matchingKeys=0;
            for(RoiDexInstructionScanner.SwitchCase item:cases) {
                Integer targetBlock=blockAt.get(item.targetOffset);
                if(targetBlock!=null&&targetBlock.equals(decodeBlock)) {
                    matchingKeys++;matchingKey=item.key;
                }
            }
            if(matchingKeys!=1)return null;
            if(matchingSwitch!=null)return null;
            matchingSwitch=instruction;
        }
        if(matchingSwitch==null)return null;
        Integer switchBlock=blockAt.get(matchingSwitch.offset);
        if(switchBlock==null)return null;
        int incoming=0;
        for(RoiDexInstructionScanner.Edge edge:graph.edges) {
            if(edge.toBlock!=decodeBlock)continue;
            incoming++;
            if(edge.fromBlock!=switchBlock||!"SWITCH".equals(edge.kind))return null;
        }
        return incoming==1?matchingKey:null;
    }
}
