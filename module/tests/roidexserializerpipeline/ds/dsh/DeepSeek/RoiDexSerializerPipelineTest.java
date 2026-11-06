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
        System.out.println("RoiDexSerializerPipelineTest: PASS");
    }
    static void eq(String expected,String actual) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" got "+actual);
    }
    static void yes(boolean value) {
        if(!value)throw new AssertionError("expected true");
    }
}
