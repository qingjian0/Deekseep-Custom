package ds.dsh.DeepSeek;

public final class RoiDexSerializerPipelineTest {
    public static void main(String[] args) {
        eq("serializer_element_registration_candidate_discovery_incomplete",
                RoiDexSerializerPipeline.registrationCandidateDiscoveryFailureReason(true));
        yes(RoiDexSerializerPipeline.registrationCandidateDiscoveryFailureReason(false)==null);
        eq("serializer_element_registration_not_found",
                RoiDexSerializerPipeline.registrationCandidateFailureReason(0,false));
        eq("serializer_element_registration_ambiguous",
                RoiDexSerializerPipeline.registrationCandidateFailureReason(2,false));
        eq("serializer_element_registration_ambiguous",
                RoiDexSerializerPipeline.registrationCandidateFailureReason(2,true));
        eq("serializer_element_registration_analysis_incomplete",
                RoiDexSerializerPipeline.registrationCandidateFailureReason(1,true));
        yes(RoiDexSerializerPipeline.registrationCandidateFailureReason(1,false)==null);
        pipelineMappingBoundary();
        registrationCandidateScanMustInspectAllInvokes();
        System.out.println("RoiDexSerializerPipelineTest: PASS");
    }

    static void registrationCandidateScanMustInspectAllInvokes() {
        eq("1", String.valueOf(RoiDexSerializerPipeline.registrationCandidateScanResult(false,true)));
        eq("0", String.valueOf(RoiDexSerializerPipeline.registrationCandidateScanResult(false,false)));
        // Even if an addElement was found, a later unresolved invoke means another
        // registration candidate may have been missed. Uniqueness is therefore unproven.
        eq("-1", String.valueOf(RoiDexSerializerPipeline.registrationCandidateScanResult(true,true)));
        eq("-1", String.valueOf(RoiDexSerializerPipeline.registrationCandidateScanResult(true,false)));
    }

    static void pipelineMappingBoundary() {
        java.util.List<RoiDexSerializerElementIndex.Element> values=
                java.util.Collections.singletonList(new RoiDexSerializerElementIndex.Element(
                        0,0,1,"field","LDescriptor;"));
        RoiDexSerializerElementIndex.Result elements=new RoiDexSerializerElementIndex.Result(
                values,java.util.Collections.<RoiDexSerializerElementIndex.Rejected>emptyList(),true);
        java.util.List<RoiDexSerializerMapper.ParameterMapping> parameters=
                java.util.Collections.singletonList(new RoiDexSerializerMapper.ParameterMapping(
                        0,0,1,2,"Ljava/lang/String;","field","LDecoder;",
                        "decodeStringElement","(I)Ljava/lang/String;"));
        RoiDexSerializerMapper.ConstructorMapping constructor=
                new RoiDexSerializerMapper.ConstructorMapping(1,3,"LRequest;","(Ljava/lang/String;)V",
                        parameters,true);
        RoiDexSerializerMapper.Result complete=new RoiDexSerializerMapper.Result(
                java.util.Collections.singletonList(constructor),
                java.util.Collections.<RoiDexSerializerMapper.Rejected>emptyList(),true);
        yes(RoiDexSerializerPipeline.isCompleteMapping(complete,elements));
        RoiDexSerializerMapper.ParameterMapping wrongElement=
                new RoiDexSerializerMapper.ParameterMapping(0,7,1,2,"Ljava/lang/String;",
                        "field","LDecoder;","decodeStringElement","(I)Ljava/lang/String;");
        RoiDexSerializerMapper.ConstructorMapping partialConstructor=
                new RoiDexSerializerMapper.ConstructorMapping(1,3,"LRequest;","(Ljava/lang/String;)V",
                        java.util.Collections.singletonList(wrongElement),true);
        RoiDexSerializerMapper.Result partial=new RoiDexSerializerMapper.Result(
                java.util.Collections.singletonList(partialConstructor),
                java.util.Collections.<RoiDexSerializerMapper.Rejected>emptyList(),true);
        yes(!RoiDexSerializerPipeline.isCompleteMapping(partial,elements));
    }

    static void eq(String expected,String actual) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" got "+actual);
    }
    static void yes(boolean value) {
        if(!value)throw new AssertionError("expected true");
    }
}
