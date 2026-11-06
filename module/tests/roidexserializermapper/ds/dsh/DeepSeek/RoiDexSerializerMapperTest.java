package ds.dsh.DeepSeek;

public final class RoiDexSerializerMapperTest {
    public static void main(String[] args)throws Exception {
        requestConstructorShape();directElementIndexMapping();checkCastSerializableElementMapping();
        dynamicSwitchElementIndexMapping();duplicateSwitchTargetRejected();
        switchTargetWithAlternatePredecessorRejected();incompleteElementCoverageRejected();
        System.out.println("RoiDexSerializerMapperTest: PASS");
    }

    static void requestConstructorShape()throws Exception {
        String valid="Lx;-><init>(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;"
                +"Ljava/lang/Object;ZZLjava/lang/Object;ZLjava/lang/String;Ljava/lang/String;I)V";
        yes(RoiDexSerializerPipeline.isRequestConstructorDescriptor(valid));
        yes(!RoiDexSerializerPipeline.isRequestConstructorDescriptor(
                "Lx;-><init>(Ljava/lang/String;)V"));
    }

    static void directElementIndexMapping()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0012,          // const/4 v0, #0
                (short)0x036e,1,(short)0x0032, // decodeStringElement {v2,v3,v0}
                (short)0x010c,          // move-result-object v1
                (short)0x0270,2,(short)0x0014, // <init> {v4,v1}
                (short)0x000e
        },5);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method(index));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,null,
                flow,reaching,useDef,links);
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(result.complete);eq(1,result.constructors.length);eq(1,result.constructors[0].parameters.length);
        eq("chat_session_id",result.constructors[0].parameters[0].fieldName);
        eq(0,result.constructors[0].parameters[0].elementIndex);
    }

    static void checkCastSerializableElementMapping()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0112,          // const/4 v1, #0
                (short)0x0171,0,1,      // invoke-static {v1}, decodeSerializableElement(I)Object
                (short)0x000c,          // move-result-object v0
                (short)0x001f,0,        // check-cast v0, type@0
                (short)0x0270,1,(short)0x0003, // <init> {v3,v0}
                (short)0x000e
        },4);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->{
            if(index==0)return new RoiDexFileReader.ResolvedMethodId(0,"LDecoder;",
                    "decodeSerializableElement","(I)Ljava/lang/Object;");
            return new RoiDexFileReader.ResolvedMethodId(1,"Lx;","<init>","(Lx;)V");
        });
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,null,
                index->"Lx;",flow,reaching,useDef,links);
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->index==0
                        ?new RoiDexFileReader.ResolvedMethodId(0,"LDecoder;",
                            "decodeSerializableElement","(I)Ljava/lang/Object;")
                        :new RoiDexFileReader.ResolvedMethodId(1,"Lx;","<init>","(Lx;)V"),
                elements(),provenance);
        yes(result.complete);eq("chat_session_id",result.constructors[0].parameters[0].fieldName);
    }

    static void dynamicSwitchElementIndexMapping()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,      // decodeElementIndex()I
                (short)0x000a,          // move-result v0
                (short)0x002b,4,0,      // packed-switch v0, payload at 8
                (short)0x000e,          // default path
                (short)0x0100,1,0,0,10,0, // one case: key 0 -> offset 14
                (short)0x036e,1,(short)0x0032, // decodeStringElement {v2,v3,v0}
                (short)0x010c,
                (short)0x0270,2,(short)0x0014,
                (short)0x000e
        },5);
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,index->method(index));
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,null,
                flow,reaching,useDef,links);
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(result.complete);eq(1,result.constructors[0].parameters.length);
        eq(0,result.constructors[0].parameters[0].elementIndex);
    }

    static void duplicateSwitchTargetRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,
                (short)0x000a,
                (short)0x002b,4,0,
                (short)0x000e,
                (short)0x0100,2,0,0,12,0,12,0,
                (short)0x036e,1,(short)0x0032,
                (short)0x010c,
                (short)0x0270,2,(short)0x0014,
                (short)0x000e
        },5);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(!result.complete);
        yes(hasRejection(result,"dynamic_element_index_switch_mapping_ambiguous"));
    }

    static void switchTargetWithAlternatePredecessorRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,
                (short)0x000a,
                (short)0x002b,6,0,
                (short)0x0000,
                (short)0x0029,8,
                (short)0x0100,1,0,0,12,0,
                (short)0x036e,1,(short)0x0032,
                (short)0x010c,
                (short)0x0270,2,(short)0x0014,
                (short)0x000e
        },5);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(!result.complete);
        yes(hasRejection(result,"dynamic_element_index_switch_mapping_ambiguous"));
    }

    static void incompleteElementCoverageRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0012,
                (short)0x036e,1,(short)0x0032,
                (short)0x010c,
                (short)0x0270,2,(short)0x0014,
                (short)0x000e
        },5);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),twoElements(),provenance);
        yes(!result.complete);
        yes(hasRejection(result,"constructor_parameter_element_count_mismatch"));
    }

    static RoiDexValueProvenance.Result provenance(RoiDexCodeReader.CodeItem code,
            RoiDexSerializerMapper.MethodResolver resolver)throws Exception {
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,resolver);
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching);
        return RoiDexValueProvenance.analyze(code,null,flow,reaching,useDef,links);
    }

    static boolean hasRejection(RoiDexSerializerMapper.Result result,String reason) {
        for(RoiDexSerializerMapper.Rejected rejected:result.rejected)
            if(reason.equals(rejected.reason))return true;
        return false;
    }

    static RoiDexFileReader.ResolvedMethodId method(int index) {
        if(index==0)return new RoiDexFileReader.ResolvedMethodId(0,"LDecoder;",
                "decodeElementIndex","()I");
        if(index==1)return new RoiDexFileReader.ResolvedMethodId(1,"LDecoder;",
                "decodeStringElement","(Ljava/lang/Object;I)Ljava/lang/String;");
        return new RoiDexFileReader.ResolvedMethodId(index,"Lx;","<init>","(Ljava/lang/String;)V");
    }
    static RoiDexSerializerElementIndex.Result elements() {
        return new RoiDexSerializerElementIndex.Result(
                java.util.Collections.singletonList(new RoiDexSerializerElementIndex.Element(
                        0,0,1,"chat_session_id","Lkotlinx/serialization/internal/PluginGeneratedSerialDescriptor;")),
                java.util.Collections.<RoiDexSerializerElementIndex.Rejected>emptyList(),true);
    }
    static RoiDexSerializerElementIndex.Result twoElements() {
        java.util.List<RoiDexSerializerElementIndex.Element> values=
                new java.util.ArrayList<RoiDexSerializerElementIndex.Element>();
        values.add(new RoiDexSerializerElementIndex.Element(0,0,1,"chat_session_id","LDescriptor;"));
        values.add(new RoiDexSerializerElementIndex.Element(1,4,2,"message","LDescriptor;"));
        return new RoiDexSerializerElementIndex.Result(values,
                java.util.Collections.<RoiDexSerializerElementIndex.Rejected>emptyList(),true);
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
