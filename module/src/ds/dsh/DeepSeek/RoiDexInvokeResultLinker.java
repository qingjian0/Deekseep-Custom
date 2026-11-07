package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Links ordinary DEX invoke instructions to their immediately following move-result. */
public final class RoiDexInvokeResultLinker {
    private RoiDexInvokeResultLinker() {}
    public interface MethodResolver {
        RoiDexFileReader.ResolvedMethodId resolve(int methodIndex) throws RoiDexFileReader.DexException;
    }
    public static final class Link {
        public final int invokeOffset,resultOffset,methodIndex,resultOpcode,destinationRegister,resultWordCount,invokeOpcode;
        public final boolean isStaticInvoke;
        public final int[] invokeRegisters;
        public final String ownerDescriptor,methodName,prototypeDescriptor;
        Link(int invoke,int result,int method,int invokeOp,boolean staticInvoke,int opcode,int destination,
                int words,int[] registers,String owner,String name,String prototype) {
            invokeOffset=invoke;resultOffset=result;methodIndex=method;invokeOpcode=invokeOp;
            isStaticInvoke=staticInvoke;resultOpcode=opcode;
            destinationRegister=destination;resultWordCount=words;invokeRegisters=registers.clone();
            ownerDescriptor=owner;methodName=name;prototypeDescriptor=prototype;
        }
    }
    public static final class Rejected {
        public final int invokeOffset,resultOffset,methodIndex;
        public final String reason;
        Rejected(int invoke,int result,int method,String why) {
            invokeOffset=invoke;resultOffset=result;methodIndex=method;reason=why;
        }
    }
    public static final class Result {
        public final Link[] links;
        public final Rejected[] rejected;
        public final boolean completeForExceptionFlow;
        Result(List<Link> links,List<Rejected> rejected,boolean complete) {
            this.links=links.toArray(new Link[0]);this.rejected=rejected.toArray(new Rejected[0]);
            completeForExceptionFlow=complete;
        }
    }
    public static final class AnalysisException extends Exception {
        public AnalysisException(String message){super(message);}
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code,RoiDexFileReader.DexFile dex)
            throws AnalysisException {
        if(dex==null)throw new AnalysisException("null_dex_file");
        return analyze(code,index->dex.resolveMethodId(index));
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code,MethodResolver resolver)
            throws AnalysisException {
        if(code==null||resolver==null)throw new AnalysisException("null_analysis_input");
        final RoiDexInstructionScanner.ControlFlowGraph graph;
        final RoiDexExceptionControlFlow.Result exceptional;
        try {
            if(code.triesSize>0) {
                exceptional=RoiDexExceptionControlFlow.analyze(code);
                graph=exceptional.normalGraph;
            } else {
                exceptional=null;
                graph=RoiDexInstructionScanner.buildControlFlowGraph(code.instructions);
            }
        } catch(RoiDexExceptionControlFlow.AnalysisException
                |RoiDexInstructionScanner.ScanException error) {
            throw new AnalysisException("control_flow_invalid:"+error.getMessage());
        }
        RoiDexInstructionScanner.ScanResult scan=graph.scan;
        Map<Integer,Integer> blockAt=new HashMap<Integer,Integer>();
        for(int b=0;b<graph.blocks.length;b++)
            for(RoiDexInstructionScanner.Instruction ins:graph.blocks[b].instructions)
                blockAt.put(ins.offset,b);

        List<Link> links=new ArrayList<Link>();
        List<Rejected> rejected=new ArrayList<Rejected>();
        for(RoiDexInstructionScanner.Instruction instruction:scan.instructions) {
            if(instruction.payload||!isOrdinaryInvoke(instruction.opcode))continue;
            final RoiDexInstructionDecoder.Decoded invoke;
            try {
                invoke=RoiDexInstructionDecoder.decode(code.instructions,instruction.offset,code.registersSize);
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                throw new AnalysisException("invoke_decode_failed:"+instruction.offset+":"+error.getMessage());
            }
            if(invoke.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.METHOD)
                throw new AnalysisException("invoke_reference_kind_invalid:"+instruction.offset);
            final RoiDexFileReader.ResolvedMethodId method;
            try {
                method=resolver.resolve(invoke.referenceIndex);
            } catch(RoiDexFileReader.DexException error) {
                throw new AnalysisException("method_id_resolution_failed:"+invoke.referenceIndex);
            }
            if(method==null)throw new AnalysisException("method_id_resolution_null");
            boolean isStatic=instruction.opcode==0x71||instruction.opcode==0x77;
            final int expectedWords;
            try {
                expectedWords=RoiDexInstructionDecoder.registerWordCountFor(method.prototypeDescriptor,isStatic);
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                throw new AnalysisException("method_prototype_invalid:"+error.getMessage());
            }
            if(invoke.registers.length!=expectedWords) {
                rejected.add(new Rejected(instruction.offset,-1,invoke.referenceIndex,"invoke_register_word_count_mismatch"));
                continue;
            }
            if(!wideRegisterPairsValid(method.prototypeDescriptor,isStatic,invoke.registers)) {
                rejected.add(new Rejected(instruction.offset,-1,invoke.referenceIndex,"wide_argument_register_pair_invalid"));
                continue;
            }
            int resultOffset=instruction.offset+instruction.width;
            RoiDexInstructionScanner.Instruction next=scan.at(resultOffset);
            if(next==null||next.payload||!isMoveResult(next.opcode))continue;
            if(!resultMatches(method.prototypeDescriptor,next.opcode)) {
                rejected.add(new Rejected(instruction.offset,resultOffset,invoke.referenceIndex,"invoke_result_type_mismatch"));
                continue;
            }
            Integer invokeBlock=blockAt.get(instruction.offset),resultBlock=blockAt.get(resultOffset);
            if(invokeBlock==null||resultBlock==null) {
                rejected.add(new Rejected(instruction.offset,resultOffset,invoke.referenceIndex,"invoke_result_block_missing"));
                continue;
            }
            if(!validNormalAdjacency(graph,invokeBlock,resultBlock,resultOffset)) {
                rejected.add(new Rejected(instruction.offset,resultOffset,invoke.referenceIndex,"move_result_not_unique_fallthrough"));
                continue;
            }
            if(exceptional!=null&&hasExceptionEntry(exceptional.exceptionEdges,resultBlock)) {
                rejected.add(new Rejected(instruction.offset,resultOffset,invoke.referenceIndex,"move_result_is_exception_handler_entry"));
                continue;
            }
            final RoiDexInstructionDecoder.Decoded result;
            try {
                result=RoiDexInstructionDecoder.decode(code.instructions,resultOffset,code.registersSize);
            } catch(RoiDexInstructionDecoder.DecodeException error) {
                throw new AnalysisException("move_result_decode_failed:"+resultOffset);
            }
            links.add(new Link(instruction.offset,resultOffset,invoke.referenceIndex,instruction.opcode,isStatic,
                    next.opcode,result.registers[0],result.resultWordCount,invoke.registers,method.ownerDescriptor,
                    method.name,method.prototypeDescriptor));
        }
        return new Result(links,rejected,code.triesSize==0);
    }

    private static boolean isOrdinaryInvoke(int op) {
        return (op>=0x6e&&op<=0x72)||(op>=0x74&&op<=0x78);
    }
    private static boolean isMoveResult(int op){return op>=0x0a&&op<=0x0c;}
    private static boolean resultMatches(String descriptor,int opcode) {
        int close=descriptor.indexOf(')');
        if(close<0||close+1>=descriptor.length())return false;
        String result=descriptor.substring(close+1);
        if(opcode==0x0a)return result.length()==1&&"ZBCSIF".indexOf(result.charAt(0))>=0;
        if(opcode==0x0b)return "J".equals(result)||"D".equals(result);
        if(opcode==0x0c)return result.startsWith("L")||result.startsWith("[");
        return false;
    }
    private static boolean wideRegisterPairsValid(String descriptor,boolean isStatic,int[] registers) {
        try {
            for(int i=1;i<registers.length;i++) {
                RoiDexInstructionDecoder.RegisterPosition previous=
                        RoiDexInstructionDecoder.registerPositionFor(descriptor,isStatic,i-1);
                RoiDexInstructionDecoder.RegisterPosition current=
                        RoiDexInstructionDecoder.registerPositionFor(descriptor,isStatic,i);
                if(previous.kind==RoiDexInstructionDecoder.RegisterPositionKind.PARAMETER
                        &&current.kind==RoiDexInstructionDecoder.RegisterPositionKind.PARAMETER
                        &&previous.parameterIndex==current.parameterIndex
                        &&registers[i]!=registers[i-1]+1)return false;
            }
            return true;
        } catch(RoiDexInstructionDecoder.DecodeException error) { return false; }
    }
    private static boolean validNormalAdjacency(RoiDexInstructionScanner.ControlFlowGraph graph,
            int invokeBlock,int resultBlock,int resultOffset) {
        if(invokeBlock==resultBlock)return true;
        if(graph.blocks[resultBlock].startOffset!=resultOffset)return false;
        int incoming=0;
        for(RoiDexInstructionScanner.Edge edge:graph.edges) {
            if(edge.toBlock!=resultBlock)continue;
            incoming++;
            if(edge.fromBlock!=invokeBlock||!"FALLTHROUGH".equals(edge.kind))return false;
        }
        return incoming==1;
    }
    private static boolean hasExceptionEntry(RoiDexExceptionControlFlow.ExceptionEdge[] edges,int block) {
        for(RoiDexExceptionControlFlow.ExceptionEdge edge:edges)if(edge.toBlock==block)return true;
        return false;
    }
}
