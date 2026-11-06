package ds.dsh.DeepSeek;

public final class RoiDexValueProvenanceTest {
    public static void main(String[] args)throws Exception {
        singleMoveChain();wideMoveChain();stringConstantThroughMove();integerConstantTrace();
        dynamicIndexInvokeTrace();checkCastPreservesSource();checkCastAfterMove();consecutiveCheckCasts();
        primitiveSourceCannotBeCast();primitiveTargetCannotBeCast();invalidCheckCastTypeRejected();
        branchMergeRejected();unsupportedDefinitionRejected();
        System.out.println("RoiDexValueProvenanceTest: PASS");
    }

    static void singleMoveChain()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,       // invoke-static {}, method 0: ()I
                (short)0x000a,            // move-result v0
                (short)0x0101,            // move v1, v0
                (short)0x0270,1,(short)0x0012, // invoke-direct {v2,v1}, method 1: (I)V
                (short)0x000e
        },3);
        RoiDexInvokeResultLinker.Result links=links(code,"()I","(I)V");
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(flow,reaching,useDef,links);
        RoiDexValueProvenance.Trace trace=provenance.traceUse(5,1,1);
        yes(trace.unique);eq(0,trace.source.invokeOffset);eq(3,trace.source.resultOffset);
        eq(2,trace.instructionOffsets.length);eq(4,trace.instructionOffsets[0]);eq(3,trace.instructionOffsets[1]);
    }

    static void wideMoveChain()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,       // invoke-static {}, method 0: ()J
                (short)0x000b,            // move-result-wide v0
                (short)0x0204,            // move-wide v2, v0
                (short)0x0370,1,(short)0x0324, // invoke-direct {v4,v2,v3}, method 1: (J)V
                (short)0x000e
        },5);
        RoiDexInvokeResultLinker.Result links=links(code,"()J","(J)V");
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(flow,reaching,useDef,links);
        RoiDexValueProvenance.Trace trace=provenance.traceUse(5,1,2);
        yes(trace.unique);eq(2,trace.source.resultWordCount);eq(0,trace.source.invokeOffset);
    }

    static void stringConstantThroughMove()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x001a,0,       // const-string v0, string@0
                (short)0x0107,          // move-object v1, v0
                (short)0x0171,1,1,      // invoke-static {v1}, method 1
                (short)0x000e
        },2);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;","consume","(Ljava/lang/String;)V"));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,
                index->index==0?"chat_session_id":null,flow,reaching,useDef,links);
        RoiDexValueProvenance.StringTrace trace=provenance.traceStringUse(3,0);
        yes(trace.unique);eq("chat_session_id",trace.value);
        eq(2,trace.instructionOffsets.length);eq(2,trace.instructionOffsets[0]);
        eq(0,trace.instructionOffsets[1]);
    }

    static void integerConstantTrace()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x3012,          // const/4 v0, #3
                (short)0x0101,          // move v1, v0
                (short)0x0171,1,1,      // invoke-static {v1}, method 1
                (short)0x000e
        },2);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;","consume","(I)V"));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(
                code,null,flow,reaching,useDef,links);
        RoiDexValueProvenance.IntTrace trace=provenance.traceIntUse(2,0);
        yes(trace.unique);yes(trace.hasConstant);eq(3,trace.constantValue);
    }

    static void dynamicIndexInvokeTrace()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,      // invoke-static {}, decodeElementIndex()I
                (short)0x000a,          // move-result v0
                (short)0x0101,          // move v1, v0
                (short)0x0171,1,1,      // invoke-static {v1}, consume(I)V
                (short)0x000e
        },2);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;",
                        index==0?"decodeElementIndex":"consume",index==0?"()I":"(I)V"));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(
                code,null,flow,reaching,useDef,links);
        RoiDexValueProvenance.IntTrace trace=provenance.traceIntUse(5,0);
        yes(trace.unique);yes(!trace.hasConstant);eq(0,trace.sourceInvoke.invokeOffset);
    }

    static void checkCastPreservesSource()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,      // decodeSerializableElement()Ljava/lang/Object;
                (short)0x000c,          // move-result-object v0
                (short)0x001f,0,        // check-cast v0, type@0
                (short)0x0270,1,(short)0x0002, // invoke-direct {v2,v0}, <init>(Lx;)V
                (short)0x000e
        },3);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;",
                        index==0?"decodeSerializableElement":"<init>",
                        index==0?"()Ljava/lang/Object;":"(Lx;)V"));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(
                code,null,index->"Lx;",flow,reaching,useDef,links);
        RoiDexValueProvenance.Trace trace=provenance.traceUse(6,1,1);
        yes(trace.unique);eq(0,trace.source.invokeOffset);
        eq("Lx;",trace.effectiveTypeDescriptor);
    }

    static void checkCastAfterMove()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,      // decodeSerializableElement()Ljava/lang/Object;
                (short)0x000c,          // move-result-object v0
                (short)0x001f,0,        // check-cast v0, type@0
                (short)0x0107,          // move-object v1, v0
                (short)0x0270,1,(short)0x0012,
                (short)0x000e
        },3);
        RoiDexValueProvenance.Trace trace=trace(code,"()Ljava/lang/Object;","(Lx;)V",i->"Lx;",7,1);
        yes(trace.unique);eq("Lx;",trace.effectiveTypeDescriptor);
        eq(3,trace.instructionOffsets.length);
    }

    static void consecutiveCheckCasts()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,      // decodeSerializableElement()Ljava/lang/Object;
                (short)0x000c,          // move-result-object v0
                (short)0x001f,0,        // check-cast v0, type@0
                (short)0x001f,1,        // check-cast v0, type@1
                (short)0x0270,1,(short)0x0002,
                (short)0x000e
        },3);
        RoiDexValueProvenance.Trace trace=trace(code,"()Ljava/lang/Object;","(Lx;)V",
                i->i==0?"Lx;":"Lx;",8,1);
        yes(trace.unique);eq("Lx;",trace.effectiveTypeDescriptor);
        eq(3,trace.instructionOffsets.length);
    }

    static void primitiveSourceCannotBeCast()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,      // malformed producer: ()I
                (short)0x000a,          // move-result v0
                (short)0x001f,0,        // invalid check-cast on primitive
                (short)0x0270,1,(short)0x0002,
                (short)0x000e
        },3);
        RoiDexValueProvenance.Trace trace=trace(code,"()I","(Lx;)V",i->"Lx;",6,1);
        yes(!trace.unique);eq("check_cast_source_type_not_reference",trace.rejectionReason);
    }

    static void primitiveTargetCannotBeCast()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,
                (short)0x000c,
                (short)0x001f,0,
                (short)0x0270,1,(short)0x0002,
                (short)0x000e
        },3);
        RoiDexValueProvenance.Trace trace=trace(code,"()Ljava/lang/Object;","(I)V",i->"I",6,1);
        yes(!trace.unique);eq("check_cast_target_type_not_reference",trace.rejectionReason);
    }

    static void invalidCheckCastTypeRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,
                (short)0x000c,
                (short)0x001f,0,
                (short)0x0270,1,(short)0x0002,
                (short)0x000e
        },3);
        RoiDexValueProvenance.Trace trace=trace(code,"()Ljava/lang/Object;","(Lx;)V",
                i->{throw new RoiDexValueProvenance.AnalysisException("type_index_out_of_range");},6,1);
        yes(!trace.unique);eq("type_index_out_of_range",trace.rejectionReason);
    }

    static RoiDexValueProvenance.Trace trace(RoiDexCodeReader.CodeItem code,String producer,
            String constructor,RoiDexValueProvenance.TypeResolver types,int invokeOffset,int operand)
            throws Exception {
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;",
                        index==0?"decodeSerializableElement":"<init>",
                        index==0?producer:constructor));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(
                code,null,types,flow,reaching,useDef,links);
        return provenance.traceUse(invokeOffset,operand,1);
    }

    static void branchMergeRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,(short)0x000a,
                (short)0x0038,5,          // if-eqz v0, target 9
                (short)0x0101,            // path A: move v1,v0
                (short)0x0029,3,          // goto constructor at 10
                (short)0x0112,            // path B: const/4 v1,#0
                (short)0x0270,1,(short)0x0012,
                (short)0x000e
        },3);
        RoiDexInvokeResultLinker.Result links=links(code,"()I","(I)V");
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(flow,reaching,useDef,links);
        RoiDexValueProvenance.Trace trace=provenance.traceUse(10,1,1);
        yes(!trace.unique);eq("multiple_reaching_definitions",trace.rejectionReason);
    }

    static void unsupportedDefinitionRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,(short)0x000a,
                (short)0x0112,           // const/4 v1, #0 overwrites copied value
                (short)0x0270,1,(short)0x0012,(short)0x000e
        },3);
        RoiDexInvokeResultLinker.Result links=links(code,"()I","(I)V");
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(flow,reaching,useDef,links);
        RoiDexValueProvenance.Trace trace=provenance.traceUse(5,1,1);
        yes(!trace.unique);eq("unsupported_value_definition_opcode",trace.rejectionReason);
    }

    static RoiDexInvokeResultLinker.Result links(RoiDexCodeReader.CodeItem code,String first,String second)
            throws Exception {
        return RoiDexInvokeResultLinker.analyze(code,index->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;",index==0?"decode":"<init>",
                        index==0?first:second));
    }
    static RoiDexCodeReader.CodeItem code(short[] instructions,int registers)throws Exception {
        byte[] b=new byte[16+instructions.length*2];
        put16(b,0,registers);put16(b,2,0);put16(b,4,0);put16(b,6,0);
        put32(b,8,0);put32(b,12,instructions.length);
        for(int i=0;i<instructions.length;i++)put16(b,16+i*2,instructions[i]&0xffff);
        return RoiDexCodeReader.readCodeItem(b,0,b.length,0,1);
    }
    static void put16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
    static void put32(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
    static void eq(String e,String a){if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
    static void yes(boolean value){if(!value)throw new AssertionError("expected true");}
}
