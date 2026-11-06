package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backward value provenance through only explicit DEX move instructions.
 * Ambiguous, merged, cyclic, unsupported, or exception-uncertain values fail closed.
 */
public final class RoiDexValueProvenance {
    private RoiDexValueProvenance() {}

    public interface StringResolver {
        String resolve(int stringIndex) throws AnalysisException;
    }

    public static final class IntTrace {
        public final boolean unique,hasConstant;
        public final int constantValue;
        public final RoiDexInvokeResultLinker.Link sourceInvoke;
        public final String rejectionReason;
        public final int[] definitionIds,instructionOffsets;
        IntTrace(boolean ok,boolean constant,int value,RoiDexInvokeResultLinker.Link source,
                String reason,List<Integer> definitions,List<Integer> offsets) {
            unique=ok;hasConstant=constant;constantValue=value;sourceInvoke=source;
            rejectionReason=reason;definitionIds=toArray(definitions);instructionOffsets=toArray(offsets);
        }
    }

    public static final class StringTrace {
        public final boolean unique;
        public final String value,rejectionReason;
        public final int[] definitionIds,instructionOffsets;
        StringTrace(boolean ok,String text,String reason,List<Integer> definitions,List<Integer> offsets) {
            unique=ok;value=text;rejectionReason=reason;
            definitionIds=toArray(definitions);instructionOffsets=toArray(offsets);
        }
    }

    public static final class Trace {
        public final boolean unique;
        public final RoiDexInvokeResultLinker.Link source;
        public final String rejectionReason;
        public final int[] definitionIds,instructionOffsets;
        Trace(boolean ok,RoiDexInvokeResultLinker.Link link,String reason,
                List<Integer> definitions,List<Integer> offsets) {
            unique=ok;source=link;rejectionReason=reason;
            definitionIds=toArray(definitions);instructionOffsets=toArray(offsets);
        }
    }

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message){super(message);}
    }

    public static final class Result {
        private final Map<Long,RoiDexUseDefChains.UseSite> uses;
        private final Map<Integer,RoiDexRegisterFlowAnalyzer.InstructionFlow> instructions;
        private final Map<Integer,RoiDexInvokeResultLinker.Link> linksByResultOffset;
        private final Map<Integer,RoiDexReachingDefinitions.Definition> definitions;
        private final RoiDexCodeReader.CodeItem code;
        private final StringResolver stringResolver;
        /** The current exception CFG is conservative, never claimed complete. */
        public final boolean completeForExceptionFlow;

        Result(Map<Long,RoiDexUseDefChains.UseSite> useMap,
                Map<Integer,RoiDexRegisterFlowAnalyzer.InstructionFlow> instructionMap,
                Map<Integer,RoiDexInvokeResultLinker.Link> linkMap,
                Map<Integer,RoiDexReachingDefinitions.Definition> definitionMap,
                RoiDexCodeReader.CodeItem codeItem,StringResolver strings) {
            uses=useMap;instructions=instructionMap;linksByResultOffset=linkMap;
            definitions=definitionMap;code=codeItem;stringResolver=strings;
            completeForExceptionFlow=codeItem!=null&&codeItem.triesSize==0;
        }

        /** Trace one constructor/invoke argument beginning at its register-word operand index. */
        public Trace traceUse(int instructionOffset,int operandIndex,int valueWords) {
            if(operandIndex<0||(valueWords!=1&&valueWords!=2))
                return rejected("invalid_operand_or_value_width");
            RoiDexUseDefChains.UseSite[] sites=new RoiDexUseDefChains.UseSite[valueWords];
            for(int i=0;i<valueWords;i++) {
                sites[i]=uses.get(useKey(instructionOffset,operandIndex+i));
                if(sites[i]==null)return rejected("use_site_missing");
            }
            return trace(sites,valueWords,new HashSet<Integer>());
        }

        /** Resolve an integer use to a literal constant or a uniquely linked invoke result. */
        public IntTrace traceIntUse(int instructionOffset,int operandIndex) {
            if(code==null)return rejectedInt("code_item_unavailable");
            RoiDexUseDefChains.UseSite use=uses.get(useKey(instructionOffset,operandIndex));
            if(use==null)return rejectedInt("use_site_missing");
            return traceInt(use,new HashSet<Integer>());
        }

        private IntTrace traceInt(RoiDexUseDefChains.UseSite use,Set<Integer> active) {
            if(use.mayBeExceptionFlowUnknown)return rejectedInt("exception_flow_unknown");
            if(use.reachingDefinitions.length==0)return rejectedInt("entry_or_unknown_value");
            if(use.reachingDefinitions.length!=1)return rejectedInt("multiple_reaching_definitions");
            RoiDexReachingDefinitions.Definition definition=use.reachingDefinitions[0];
            if(definition==null||definitions.get(definition.id)==null)
                return rejectedInt("definition_identity_unknown");
            if(!active.add(definition.id))return rejectedInt("cyclic_value_provenance");
            int opcode=definition.opcode,offset=definition.instructionOffset;
            if(opcode>=0x12&&opcode<=0x15) {
                try {
                    RoiDexInstructionDecoder.Decoded decoded=code.decodeInstructionAt(offset);
                    if(!decoded.hasLiteral||decoded.literalValue<Integer.MIN_VALUE
                            ||decoded.literalValue>Integer.MAX_VALUE)
                        return rejectedInt("integer_literal_unavailable");
                    List<Integer> ids=new ArrayList<Integer>();ids.add(definition.id);
                    return new IntTrace(true,true,(int)decoded.literalValue,null,null,ids,singleOffset(offset));
                } catch(RoiDexInstructionDecoder.DecodeException error) {
                    return rejectedInt("integer_constant_decode_failed");
                }
            }
            if(opcode==0x0a) {
                RoiDexInvokeResultLinker.Link link=linksByResultOffset.get(offset);
                if(link==null||link.resultWordCount!=1||!link.prototypeDescriptor.endsWith(")I"))
                    return rejectedInt("integer_invoke_result_link_missing");
                List<Integer> ids=new ArrayList<Integer>();ids.add(definition.id);
                return new IntTrace(true,false,0,link,null,ids,singleOffset(offset));
            }
            if(opcode>=0x01&&opcode<=0x03) {
                RoiDexRegisterFlowAnalyzer.InstructionFlow move=instructions.get(offset);
                if(move==null||move.opcode!=opcode||move.uses.length!=1||move.definitions.length!=1
                        ||move.definitions[0]!=definition.register)
                    return rejectedInt("integer_move_semantics_unavailable");
                RoiDexUseDefChains.UseSite source=uses.get(useKey(offset,0));
                if(source==null||source.register!=move.uses[0])
                    return rejectedInt("integer_move_source_missing");
                IntTrace child=traceInt(source,active);
                if(!child.unique)return child;
                List<Integer> ids=new ArrayList<Integer>();ids.add(definition.id);append(ids,child.definitionIds);
                List<Integer> offsets=new ArrayList<Integer>();offsets.add(offset);append(offsets,child.instructionOffsets);
                return new IntTrace(true,child.hasConstant,child.constantValue,child.sourceInvoke,
                        null,ids,offsets);
            }
            return rejectedInt("unsupported_integer_value_definition");
        }

        private static IntTrace rejectedInt(String reason) {
            return new IntTrace(false,false,0,null,reason,new ArrayList<Integer>(),new ArrayList<Integer>());
        }

        /** Resolve a string-valued use through explicit move/move-object copies to const-string. */
        public StringTrace traceStringUse(int instructionOffset,int operandIndex) {
            if(code==null||stringResolver==null)return rejectedString("string_resolution_context_missing");
            RoiDexUseDefChains.UseSite use=uses.get(useKey(instructionOffset,operandIndex));
            if(use==null)return rejectedString("use_site_missing");
            return traceString(use,new HashSet<Integer>());
        }

        private StringTrace traceString(RoiDexUseDefChains.UseSite use,Set<Integer> active) {
            if(use.mayBeExceptionFlowUnknown)return rejectedString("exception_flow_unknown");
            if(use.reachingDefinitions.length==0)return rejectedString("entry_or_unknown_value");
            if(use.reachingDefinitions.length!=1)return rejectedString("multiple_reaching_definitions");
            RoiDexReachingDefinitions.Definition definition=use.reachingDefinitions[0];
            if(definition==null||definitions.get(definition.id)==null)
                return rejectedString("definition_identity_unknown");
            if(!active.add(definition.id))return rejectedString("cyclic_value_provenance");
            int opcode=definition.opcode,offset=definition.instructionOffset;
            if(opcode==0x1a||opcode==0x1b) {
                try {
                    RoiDexInstructionDecoder.Decoded decoded=code.decodeInstructionAt(offset);
                    if(decoded.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.STRING)
                        return rejectedString("constant_reference_kind_mismatch");
                    String value=stringResolver.resolve(decoded.referenceIndex);
                    if(value==null)return rejectedString("string_index_unresolved");
                    List<Integer> ids=new ArrayList<Integer>();ids.add(definition.id);
                    return new StringTrace(true,value,null,ids,singleOffset(offset));
                } catch(RoiDexInstructionDecoder.DecodeException error) {
                    return rejectedString("const_string_decode_failed");
                } catch(AnalysisException error) {
                    return rejectedString(error.getMessage());
                }
            }
            if(!((opcode>=0x01&&opcode<=0x03)||(opcode>=0x07&&opcode<=0x09)))
                return rejectedString("unsupported_string_value_definition");
            RoiDexRegisterFlowAnalyzer.InstructionFlow move=instructions.get(offset);
            if(move==null||move.opcode!=opcode||move.uses.length!=1||move.definitions.length!=1
                    ||move.definitions[0]!=definition.register)
                return rejectedString("string_move_semantics_unavailable");
            RoiDexUseDefChains.UseSite source=uses.get(useKey(offset,0));
            if(source==null||source.register!=move.uses[0])
                return rejectedString("string_move_source_missing");
            StringTrace child=traceString(source,active);
            if(!child.unique)return child;
            List<Integer> ids=new ArrayList<Integer>();ids.add(definition.id);append(ids,child.definitionIds);
            List<Integer> offsets=new ArrayList<Integer>();offsets.add(offset);append(offsets,child.instructionOffsets);
            return new StringTrace(true,child.value,null,ids,offsets);
        }

        private Trace trace(RoiDexUseDefChains.UseSite[] sites,int words,Set<Integer> active) {
            RoiDexReachingDefinitions.Definition[] defs=
                    new RoiDexReachingDefinitions.Definition[words];
            for(int i=0;i<words;i++) {
                RoiDexUseDefChains.UseSite use=sites[i];
                if(use.mayBeExceptionFlowUnknown)return rejected("exception_flow_unknown");
                if(use.reachingDefinitions.length==0)return rejected("entry_or_unknown_value");
                if(use.reachingDefinitions.length!=1)return rejected("multiple_reaching_definitions");
                defs[i]=use.reachingDefinitions[0];
                if(defs[i]==null||definitions.get(defs[i].id)==null)
                    return rejected("definition_identity_unknown");
                if(active.contains(defs[i].id))return rejected("cyclic_value_provenance");
            }
            int offset=defs[0].instructionOffset,opcode=defs[0].opcode;
            for(int i=0;i<words;i++) {
                if(defs[i].instructionOffset!=offset||defs[i].opcode!=opcode
                        ||(i>0&&defs[i].register!=defs[0].register+i))
                    return rejected("value_word_definitions_do_not_match");
            }
            if(opcode>=0x0a&&opcode<=0x0c) {
                RoiDexInvokeResultLinker.Link link=linksByResultOffset.get(offset);
                int expected=opcode==0x0b?2:1;
                if(expected!=words||link==null||link.resultOpcode!=opcode
                        ||link.resultWordCount!=words||link.destinationRegister!=defs[0].register)
                    return rejected("invoke_result_link_missing_or_width_mismatch");
                return new Trace(true,link,null,definitionIds(defs),singleOffset(offset));
            }
            if(opcode<0x01||opcode>0x09)return rejected("unsupported_value_definition_opcode");
            int moveWords=(opcode>=0x04&&opcode<=0x06)?2:1;
            if(moveWords!=words)return rejected("move_width_mismatch");
            RoiDexRegisterFlowAnalyzer.InstructionFlow move=instructions.get(offset);
            if(move==null||move.opcode!=opcode||move.uses.length!=words
                    ||move.definitions.length!=words)return rejected("move_semantics_unavailable");
            for(int i=0;i<words;i++) {
                if(move.definitions[i]!=defs[i].register
                        ||(i>0&&move.uses[i]!=move.uses[0]+i))
                    return rejected("move_register_pair_mismatch");
            }
            RoiDexUseDefChains.UseSite[] sourceUses=new RoiDexUseDefChains.UseSite[words];
            for(int i=0;i<words;i++) {
                sourceUses[i]=uses.get(useKey(offset,i));
                if(sourceUses[i]==null||sourceUses[i].register!=move.uses[i])
                    return rejected("move_source_use_missing");
            }
            for(RoiDexReachingDefinitions.Definition def:defs)active.add(def.id);
            Trace child=trace(sourceUses,words,active);
            for(RoiDexReachingDefinitions.Definition def:defs)active.remove(def.id);
            if(!child.unique)return child;
            List<Integer> pathDefinitions=new ArrayList<Integer>();
            for(RoiDexReachingDefinitions.Definition def:defs)pathDefinitions.add(def.id);
            append(pathDefinitions,child.definitionIds);
            List<Integer> pathOffsets=new ArrayList<Integer>();pathOffsets.add(offset);
            append(pathOffsets,child.instructionOffsets);
            return new Trace(true,child.source,null,pathDefinitions,pathOffsets);
        }

        private static Trace rejected(String reason) {
            return new Trace(false,null,reason,new ArrayList<Integer>(),new ArrayList<Integer>());
        }
        private static StringTrace rejectedString(String reason) {
            return new StringTrace(false,null,reason,new ArrayList<Integer>(),new ArrayList<Integer>());
        }
    }

    public static Result analyze(RoiDexRegisterFlowAnalyzer.Result flow,
            RoiDexReachingDefinitions.Result reaching,RoiDexUseDefChains.Result useDef,
            RoiDexInvokeResultLinker.Result invokeLinks)throws AnalysisException {
        return analyze(null,null,flow,reaching,useDef,invokeLinks);
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code,StringResolver strings,
            RoiDexRegisterFlowAnalyzer.Result flow,RoiDexReachingDefinitions.Result reaching,
            RoiDexUseDefChains.Result useDef,RoiDexInvokeResultLinker.Result invokeLinks)
            throws AnalysisException {
        if(flow==null||reaching==null||useDef==null||invokeLinks==null)
            throw new AnalysisException("null_analysis_input");
        if(flow.blocks.length!=reaching.blocks.length)
            throw new AnalysisException("block_count_mismatch");
        Map<Long,RoiDexUseDefChains.UseSite> uses=new HashMap<Long,RoiDexUseDefChains.UseSite>();
        for(RoiDexUseDefChains.UseSite use:useDef.uses) {
            long key=useKey(use.instructionOffset,use.operandIndex);
            if(uses.put(key,use)!=null)throw new AnalysisException("duplicate_use_site");
        }
        Map<Integer,RoiDexRegisterFlowAnalyzer.InstructionFlow> instructions=
                new HashMap<Integer,RoiDexRegisterFlowAnalyzer.InstructionFlow>();
        for(RoiDexRegisterFlowAnalyzer.BasicBlockFlow block:flow.blocks)
            for(RoiDexRegisterFlowAnalyzer.InstructionFlow instruction:block.instructions)
                if(instructions.put(instruction.offset,instruction)!=null)
                    throw new AnalysisException("duplicate_instruction_offset");
        Map<Integer,RoiDexInvokeResultLinker.Link> links=
                new HashMap<Integer,RoiDexInvokeResultLinker.Link>();
        for(RoiDexInvokeResultLinker.Link link:invokeLinks.links)
            if(links.put(link.resultOffset,link)!=null)
                throw new AnalysisException("duplicate_invoke_result_link");
        Map<Integer,RoiDexReachingDefinitions.Definition> definitions=
                new HashMap<Integer,RoiDexReachingDefinitions.Definition>();
        for(RoiDexReachingDefinitions.Definition definition:reaching.definitions)
            if(definitions.put(definition.id,definition)!=null)
                throw new AnalysisException("duplicate_definition_id");
        return new Result(uses,instructions,links,definitions,code,strings);
    }

    private static long useKey(int offset,int operand) {
        return (((long)offset)<<32)|(operand&0xffffffffL);
    }
    private static List<Integer> definitionIds(RoiDexReachingDefinitions.Definition[] defs) {
        List<Integer> out=new ArrayList<Integer>();
        for(RoiDexReachingDefinitions.Definition def:defs)out.add(def.id);
        return out;
    }
    private static List<Integer> singleOffset(int offset) {
        List<Integer> out=new ArrayList<Integer>();out.add(offset);return out;
    }
    private static void append(List<Integer> target,int[] values) {
        for(int value:values)target.add(value);
    }
    private static int[] toArray(List<Integer> values) {
        int[] out=new int[values.size()];
        for(int i=0;i<out.length;i++)out[i]=values.get(i);
        return out;
    }
}
