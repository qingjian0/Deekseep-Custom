package ds.dsh.DeepSeek;

public final class RoiDexSerializerElementIndexTest {
    public static void main(String[] args)throws Exception {
        registrationOrderNotStringPoolOrder();conditionalPreambleAccepted();
        branchingRegistrationRejected();unreachableRegistrationRejected();incompleteRegistrationRejected();
        System.out.println("RoiDexSerializerElementIndexTest: PASS");
    }

    static void registrationOrderNotStringPoolOrder()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x001a,1,          // const-string v0, string@1 ("second")
                (short)0x011a,0,          // const-string v1, string@0 ("first")
                (short)0x036e,0,(short)0x0312, // addElement {v2,v1,v3}
                (short)0x036e,0,(short)0x0302, // addElement {v2,v0,v3}
                (short)0x000e
        },4);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method());
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,
                index->index==0?"first":index==1?"second":null,flow,reaching,useDef,links);
        RoiDexSerializerElementIndex.Result result=RoiDexSerializerElementIndex.analyze(
                code,index->method(),provenance);
        eq(2,result.elements.length);eq(0,result.rejected.length);
        eq(0,result.elements[0].index);eq("first",result.elements[0].name);
        eq(1,result.elements[1].index);eq("second",result.elements[1].name);
        yes(result.registrationOrderProven);yes(result.complete);yes(result.candidateEvidenceOnly);
        yes(!result.hookAuthorizationGranted);
    }

    static void conditionalPreambleAccepted()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0138,3,         // if-eqz v1, target 3
                (short)0x000e,           // branch bypasses descriptor initialization
                (short)0x001a,0,         // const-string v0, string@0
                (short)0x036e,0,(short)0x0302, // addElement {v2,v0,v3}
                (short)0x000e
        },4);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method());
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,
                index->"chat_session_id",flow,reaching,useDef,links);
        RoiDexSerializerElementIndex.Result result=RoiDexSerializerElementIndex.analyze(
                code,index->method(),provenance);
        yes(result.registrationOrderProven);eq(1,result.elements.length);
        eq("chat_session_id",result.elements[0].name);
    }

    static void branchingRegistrationRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x001a,0,
                (short)0x036e,0,(short)0x0312,
                (short)0x0038,7,
                (short)0x036e,0,(short)0x0312,
                (short)0x0029,5,
                (short)0x036e,0,(short)0x0312,
                (short)0x000e
        },4);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method());
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,
                index->"field",flow,reaching,useDef,links);
        try {
            RoiDexSerializerElementIndex.analyze(code,index->method(),provenance);
        } catch(RoiDexSerializerElementIndex.AnalysisException expected) {
            if(expected.getMessage().contains("not_linear"))return;
            throw expected;
        }
        throw new AssertionError("branching descriptor registration must be rejected");
    }

    static void unreachableRegistrationRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0029,2,         // goto/16 -> return at offset 2
                (short)0x000e,           // return; registration block below is unreachable
                (short)0x001a,0,         // const-string v0, string@0
                (short)0x036e,0,(short)0x0312, // addElement {v2,v0,v3}
                (short)0x000e
        },4);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method());
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,
                index->"field",flow,reaching,useDef,links);
        try {
            RoiDexSerializerElementIndex.analyze(code,index->method(),provenance);
        } catch(RoiDexSerializerElementIndex.AnalysisException expected) {
            if(expected.getMessage().contains("block_unreachable"))return;
            throw expected;
        }
        throw new AssertionError("unreachable descriptor registration must be rejected");
    }

    static void incompleteRegistrationRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x001a,0,
                (short)0x036e,0,(short)0x0312,
                (short)0x000e
        },4);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method());
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,
                index->null,flow,reaching,useDef,links);
        RoiDexSerializerElementIndex.Result result=RoiDexSerializerElementIndex.analyze(
                code,index->method(),provenance);
        yes(result.registrationOrderProven);
        yes(!result.complete);
        eq(0,result.elements.length);
        eq(1,result.rejected.length);
    }

    static RoiDexFileReader.ResolvedMethodId method() {
        return new RoiDexFileReader.ResolvedMethodId(0,
                "Lkotlinx/serialization/internal/PluginGeneratedSerialDescriptor;",
                "addElement","(Ljava/lang/String;Z)V");
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
