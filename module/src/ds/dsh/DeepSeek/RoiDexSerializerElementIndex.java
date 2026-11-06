package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves Kotlin-style descriptor element names from addElement registration call order.
 * It does not infer indices from string-pool order or authorize runtime hooks.
 */
public final class RoiDexSerializerElementIndex {
    private RoiDexSerializerElementIndex() {}

    public interface MethodResolver {
        RoiDexFileReader.ResolvedMethodId resolve(int methodIndex)
                throws RoiDexFileReader.DexException;
    }

    public static final class Element {
        public final int index,registrationOffset,stringOperandIndex;
        public final String name,descriptorOwner;
        Element(int i,int offset,int operand,String value,String owner) {
            index=i;registrationOffset=offset;stringOperandIndex=operand;
            name=value;descriptorOwner=owner;
        }
    }

    public static final class Rejected {
        public final int registrationIndex,invokeOffset;
        public final String reason;
        Rejected(int index,int offset,String why) {
            registrationIndex=index;invokeOffset=offset;reason=why;
        }
    }

    public static final class Result {
        public final Element[] elements;
        public final Rejected[] rejected;
        public final boolean registrationOrderProven;
        /** True only when every discovered registration produced one resolved element. */
        public final boolean complete;
        public final boolean candidateEvidenceOnly=true;
        public final boolean hookAuthorizationGranted=false;
        Result(List<Element> elements,List<Rejected> rejected,boolean orderProven) {
            this(elements,rejected,orderProven,orderProven);
        }
        Result(List<Element> elements,List<Rejected> rejected,boolean orderProven,
                boolean registrationsResolved) {
            this.elements=elements.toArray(new Element[0]);
            this.rejected=rejected.toArray(new Rejected[0]);
            registrationOrderProven=orderProven;
            complete=registrationsResolved&&orderProven
                    &&hasCompleteRegistrationSequence(this.elements,this.rejected);
        }
    }

    private static boolean hasCompleteRegistrationSequence(Element[] elements,Rejected[] rejected) {
        if(elements==null||elements.length==0||rejected==null||rejected.length!=0)return false;
        for(int i=0;i<elements.length;i++) {
            Element element=elements[i];
            if(element==null||element.index!=i||element.name==null||element.name.length()==0)
                return false;
        }
        return true;
    }

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message){super(message);}
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code,MethodResolver resolver,
            RoiDexValueProvenance.Result provenance)throws AnalysisException {
        if(code==null||resolver==null||provenance==null)
            throw new AnalysisException("null_analysis_input");
        if(code.triesSize!=0||!provenance.completeForExceptionFlow)
            throw new AnalysisException("descriptor_registration_exception_flow_incomplete");
        final RoiDexInstructionScanner.ControlFlowGraph graph;
        try {
            graph=RoiDexInstructionScanner.buildControlFlowGraph(code.instructions);
        } catch(RoiDexInstructionScanner.ScanException error) {
            throw new AnalysisException("descriptor_registration_cfg_invalid:"+error.getMessage());
        }
        java.util.Map<Integer,Integer> blockAt=new java.util.HashMap<Integer,Integer>();
        for(int b=0;b<graph.blocks.length;b++)
            for(RoiDexInstructionScanner.Instruction ins:graph.blocks[b].instructions)
                blockAt.put(ins.offset,b);

        List<Element> elements=new ArrayList<Element>();
        List<Rejected> rejected=new ArrayList<Rejected>();
        Set<String> names=new HashSet<String>();
        int registrationIndex=0;
        int registrationBlock=-1;
        String registrationOwner=null;
        boolean complete=true;
        boolean registrationOrderProven=true;
        for(RoiDexInstructionScanner.Instruction instruction:graph.scan.instructions) {
            if(instruction.payload||!isInvoke(instruction.opcode))continue;
            final RoiDexInstructionDecoder.Decoded invoke;
            try {
                invoke=RoiDexInstructionDecoder.decode(code.instructions,instruction.offset,
                        code.registersSize);
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                throw new AnalysisException("descriptor_invoke_decode_failed:"+instruction.offset);
            }
            if(invoke.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.METHOD)
                throw new AnalysisException("descriptor_invoke_reference_kind_invalid");
            final RoiDexFileReader.ResolvedMethodId method;
            try {
                method=resolver.resolve(invoke.referenceIndex);
            } catch(RoiDexFileReader.DexException error) {
                throw new AnalysisException("descriptor_method_id_unresolved:"+invoke.referenceIndex);
            }
            if(method==null)throw new AnalysisException("descriptor_method_id_null");
            if(!"addElement".equals(method.name))continue;
            if(registrationOwner==null)registrationOwner=method.ownerDescriptor;
            else if(!registrationOwner.equals(method.ownerDescriptor))
                throw new AnalysisException("descriptor_registration_owner_mismatch");
            Integer callBlock=blockAt.get(instruction.offset);
            if(callBlock==null)throw new AnalysisException("descriptor_registration_block_missing");
            if(registrationBlock<0)registrationBlock=callBlock;
            else if(registrationBlock!=callBlock)
                throw new AnalysisException("descriptor_registration_not_linear");
            int currentIndex=registrationIndex++;
            boolean isStatic=instruction.opcode==0x71||instruction.opcode==0x77;
            if(isStatic||!"(Ljava/lang/String;Z)V".equals(method.prototypeDescriptor)) {
                rejected.add(new Rejected(currentIndex,instruction.offset,"add_element_signature_unsupported"));
                complete=false;continue;
            }
            final int expectedWords;
            try {
                expectedWords=RoiDexInstructionDecoder.registerWordCountFor(method.prototypeDescriptor,false);
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                rejected.add(new Rejected(currentIndex,instruction.offset,"add_element_descriptor_invalid"));
                complete=false;continue;
            }
            if(invoke.registers.length!=expectedWords) {
                rejected.add(new Rejected(currentIndex,instruction.offset,"add_element_register_word_count_mismatch"));
                complete=false;continue;
            }
            int stringOperand=-1;
            try {
                for(int word=0;word<invoke.registers.length;word++) {
                    RoiDexInstructionDecoder.RegisterPosition position=
                            RoiDexInstructionDecoder.registerPositionFor(method.prototypeDescriptor,false,word);
                    if(position.kind==RoiDexInstructionDecoder.RegisterPositionKind.PARAMETER
                            &&position.parameterIndex==0){stringOperand=word;break;}
                }
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                rejected.add(new Rejected(currentIndex,instruction.offset,"add_element_parameter_mapping_invalid"));
                complete=false;continue;
            }
            if(stringOperand<0) {
                rejected.add(new Rejected(currentIndex,instruction.offset,"add_element_string_parameter_missing"));
                complete=false;continue;
            }
            RoiDexValueProvenance.StringTrace name=provenance.traceStringUse(
                    instruction.offset,stringOperand);
            if(!name.unique) {
                rejected.add(new Rejected(currentIndex,instruction.offset,
                        "element_name_unresolved:"+name.rejectionReason));
                complete=false;continue;
            }
            if(!names.add(name.value)) {
                rejected.add(new Rejected(currentIndex,instruction.offset,"duplicate_element_name"));
                complete=false;continue;
            }
            elements.add(new Element(currentIndex,instruction.offset,stringOperand,
                    name.value,method.ownerDescriptor));
        }
        if(registrationIndex==0) {
            complete=false;
            registrationOrderProven=false;
        }
        if(registrationBlock>=0&&!isReachableFromEntry(graph,registrationBlock))
            throw new AnalysisException("descriptor_registration_block_unreachable");
        if(registrationBlock>=0&&hasCycleThroughBlock(graph,registrationBlock))
            throw new AnalysisException("descriptor_registration_loop_or_back_edge");
        return new Result(elements,rejected,registrationOrderProven,complete);
    }

    private static boolean isReachableFromEntry(
            RoiDexInstructionScanner.ControlFlowGraph graph,int targetBlock) {
        if(graph.blocks.length==0||targetBlock<0||targetBlock>=graph.blocks.length)return false;
        boolean[] visited=new boolean[graph.blocks.length];
        java.util.ArrayDeque<Integer> queue=new java.util.ArrayDeque<Integer>();
        visited[0]=true;queue.add(0);
        while(!queue.isEmpty()) {
            int block=queue.removeFirst();
            if(block==targetBlock)return true;
            for(RoiDexInstructionScanner.Edge edge:graph.edges) {
                if(edge.fromBlock==block&&!visited[edge.toBlock]) {
                    visited[edge.toBlock]=true;queue.add(edge.toBlock);
                }
            }
        }
        return false;
    }

    private static boolean hasCycleThroughBlock(
            RoiDexInstructionScanner.ControlFlowGraph graph,int startBlock) {
        boolean[] visited=new boolean[graph.blocks.length];
        java.util.ArrayDeque<Integer> queue=new java.util.ArrayDeque<Integer>();
        for(RoiDexInstructionScanner.Edge edge:graph.edges)
            if(edge.fromBlock==startBlock) {
                if(edge.toBlock==startBlock)return true;
                if(!visited[edge.toBlock]){visited[edge.toBlock]=true;queue.add(edge.toBlock);}
            }
        while(!queue.isEmpty()) {
            int block=queue.removeFirst();
            for(RoiDexInstructionScanner.Edge edge:graph.edges) {
                if(edge.fromBlock!=block)continue;
                if(edge.toBlock==startBlock)return true;
                if(!visited[edge.toBlock]){visited[edge.toBlock]=true;queue.add(edge.toBlock);}
            }
        }
        return false;
    }

    private static boolean isInvoke(int opcode) {
        return (opcode>=0x6e&&opcode<=0x72)||(opcode>=0x74&&opcode<=0x78);
    }
}
