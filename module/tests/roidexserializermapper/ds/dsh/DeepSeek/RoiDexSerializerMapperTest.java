package ds.dsh.DeepSeek;

public final class RoiDexSerializerMapperTest {
    public static void main(String[] args)throws Exception {
        requestConstructorShape();directElementIndexMapping();checkCastSerializableElementMapping();
        dynamicSwitchElementIndexMapping();dynamicSwitchDifferentDecoderReceiverRejected();
        multipleDecodeCallsForOneSwitchCaseRejected();
        duplicateSwitchTargetRejected();
        switchTargetWithAlternatePredecessorRejected();decoderReceiverMismatchRejected();incompleteElementCoverageRejected();
        incompleteElementRegistrationRejected();duplicateElementIdentityRejected();
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
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching,entryLayout(code));
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,null,
                flow,reaching,useDef,links);
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(result.complete);eq(1,result.constructors.length);eq(1,result.constructors[0].parameters.length);
        eq("chat_session_id",result.constructors[0].parameters[0].fieldName);
        eq(0,result.constructors[0].parameters[0].elementIndex);
        eq(0,result.constructors[0].parameters[0].constructorParameterIndex);
        yes(result.constructors[0].complete);
        eq(1,result.constructors[0].parameters.length);
    }

    static void checkCastSerializableElementMapping()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0112,          // const/4 v1, #0
                (short)0x026e,0,(short)0x0012, // invoke-virtual {v2,v1}, decodeSerializableElement(I)Object
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
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching,entryLayout(code));
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
                (short)0x016e,0,(short)0x0002, // invoke-virtual {v2}, decodeElementIndex()I
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
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching,entryLayout(code));
        RoiDexValueProvenance.Result provenance=RoiDexValueProvenance.analyze(code,null,
                flow,reaching,useDef,links);
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(result.complete);eq(1,result.constructors[0].parameters.length);
        eq(0,result.constructors[0].parameters[0].elementIndex);
    }

    static void dynamicSwitchDifferentDecoderReceiverRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x016e,0,(short)0x0002, // decodeElementIndex on receiver v2
                (short)0x000a,
                (short)0x002b,4,0,
                (short)0x000e,
                (short)0x0100,1,0,0,10,0,
                (short)0x036e,1,(short)0x0043, // decodeStringElement on receiver v3
                (short)0x010c,
                (short)0x0270,2,(short)0x0015, // <init> {v5,v1}
                (short)0x000e
        },6);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(!result.complete);
        yes(hasRejection(result,"dynamic_element_index_decoder_receiver_mismatch"));
    }

    static void multipleDecodeCallsForOneSwitchCaseRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0071,0,0,
                (short)0x000a,
                (short)0x002b,4,0,
                (short)0x000e,
                (short)0x0100,1,0,0,10,0,
                (short)0x036e,1,(short)0x0032,
                (short)0x010c,
                (short)0x036e,1,(short)0x0032,
                (short)0x040c,
                (short)0x0270,2,(short)0x0015,
                (short)0x000e
        },6);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),elements(),provenance);
        yes(!result.complete);
        yes(hasRejection(result,"dynamic_element_index_switch_mapping_ambiguous"));
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


    static void decoderReceiverMismatchRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0012,          // const/4 v0, #0
                (short)0x026e,1,(short)0x0002, // invoke-virtual {v2,v0}, decodeStringElement(I)String
                (short)0x040c,          // move-result-object v4
                (short)0x1112,          // const/4 v1, #1
                (short)0x026e,1,(short)0x0013, // invoke-virtual {v3,v1}, decodeStringElement(I)String
                (short)0x050c,          // move-result-object v5
                (short)0x0370,2,(short)0x0546, // <init> {v6,v4,v5}
                (short)0x000e
        },7);
        RoiDexValueProvenance.Result provenance=provenance(code,index->{
            if(index==0)return new RoiDexFileReader.ResolvedMethodId(0,"LDecoder;",
                    "decodeElementIndex","()I");
            if(index==1)return new RoiDexFileReader.ResolvedMethodId(1,"LDecoder;",
                    "decodeStringElement","(I)Ljava/lang/String;");
            return new RoiDexFileReader.ResolvedMethodId(index,"Lx;","<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V");
        });
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->index==0?new RoiDexFileReader.ResolvedMethodId(0,"LDecoder;",
                        "decodeElementIndex","()I")
                        :index==1?new RoiDexFileReader.ResolvedMethodId(1,"LDecoder;",
                        "decodeStringElement","(I)Ljava/lang/String;")
                        :new RoiDexFileReader.ResolvedMethodId(index,"Lx;","<init>",
                        "(Ljava/lang/String;Ljava/lang/String;)V"),
                twoElements(),provenance);
        yes(!result.complete);
        RoiDexValueProvenance.ReceiverTrace firstReceiver=provenance.traceReceiverUse(1,0);
        RoiDexValueProvenance.ReceiverTrace secondReceiver=provenance.traceReceiverUse(6,0);
        if(!firstReceiver.unique||!secondReceiver.unique
                ||firstReceiver.identity.equals(secondReceiver.identity)) {
            throw new AssertionError("decoder receiver fixture invalid: first="
                    +firstReceiver.rejectionReason+":"+firstReceiver.identity+" second="
                    +secondReceiver.rejectionReason+":"+secondReceiver.identity);
        }
        if(!hasRejection(result,"decoder_receiver_identity_mismatch")) {
            StringBuilder reasons=new StringBuilder();
            for(RoiDexSerializerMapper.Rejected item:result.rejected)
                reasons.append(item.reason).append(';');
            throw new AssertionError("decoder receiver mismatch not rejected; reasons="+reasons);
        }
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

    static void incompleteElementRegistrationRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0012,
                (short)0x036e,1,(short)0x0032,
                (short)0x010c,
                (short)0x0270,2,(short)0x0014,
                (short)0x000e
        },5);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        java.util.List<RoiDexSerializerElementIndex.Element> values=
                java.util.Collections.singletonList(new RoiDexSerializerElementIndex.Element(
                        0,0,1,"chat_session_id","LDescriptor;"));
        java.util.List<RoiDexSerializerElementIndex.Rejected> rejected=
                java.util.Collections.singletonList(new RoiDexSerializerElementIndex.Rejected(
                        1,4,"element_name_unresolved:test"));
        RoiDexSerializerElementIndex.Result incomplete=new RoiDexSerializerElementIndex.Result(
                values,rejected,true);
        yes(incomplete.registrationOrderProven);
        yes(!incomplete.complete);
        try {
            RoiDexSerializerMapper.analyze(code,"Lx;",index->method(index),incomplete,provenance);
        } catch(RoiDexSerializerMapper.AnalysisException expected) {
            yes(expected.getMessage().contains("registration_incomplete"));
            return;
        }
        throw new AssertionError("incomplete element registration must be rejected");
    }

    static void duplicateElementIdentityRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{
                (short)0x0012,
                (short)0x036e,1,(short)0x0032,
                (short)0x010c,
                (short)0x0270,2,(short)0x0014,
                (short)0x000e
        },5);
        RoiDexValueProvenance.Result provenance=provenance(code,index->method(index));
        java.util.List<RoiDexSerializerElementIndex.Element> values=
                new java.util.ArrayList<RoiDexSerializerElementIndex.Element>();
        values.add(new RoiDexSerializerElementIndex.Element(0,0,1,"duplicate","LDescriptor;"));
        values.add(new RoiDexSerializerElementIndex.Element(1,4,2,"duplicate","LDescriptor;"));
        RoiDexSerializerElementIndex.Result duplicateNames=new RoiDexSerializerElementIndex.Result(
                values,java.util.Collections.<RoiDexSerializerElementIndex.Rejected>emptyList(),true);
        RoiDexSerializerMapper.Result result=RoiDexSerializerMapper.analyze(code,"Lx;",
                index->method(index),duplicateNames,provenance);
        yes(!result.complete);
        yes(hasRejection(result,"serializer_element_identity_ambiguous"));
    }

    static RoiDexMethodEntryLayout.Result entryLayout(RoiDexCodeReader.CodeItem code)
            throws Exception {
        int incoming=code.registersSize-2;
        if(incoming<1)throw new AssertionError("fixture lacks receiver register");
        StringBuilder prototype=new StringBuilder("(");
        for(int i=1;i<incoming;i++)prototype.append("Ljava/lang/Object;");
        prototype.append(")V");
        return RoiDexMethodEntryLayout.analyze(code.registersSize,incoming,0,prototype.toString());
    }
    static RoiDexValueProvenance.Result provenance(RoiDexCodeReader.CodeItem code,
            RoiDexInvokeResultLinker.MethodResolver resolver)throws Exception {
        RoiDexInvokeResultLinker.Result links=RoiDexInvokeResultLinker.analyze(code,resolver);
        RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
        RoiDexUseDefChains.Result useDef=RoiDexUseDefChains.analyze(flow,reaching,entryLayout(code));
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
