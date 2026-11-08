package ds.dsh.DeepSeek;

/** Regression checks for the 2.7.5 evidence catalog and fail-closed capability gate. */
public final class HostV275ProfileTest {
    public static void main(String[] args) {
        check("2.7.5".equals(HostV275.VERSION_NAME), "version name");
        check(HostV275.VERSION_CODE == 295, "version code");
        check("com.deepseek.chat".equals(HostV275.PACKAGE_NAME), "package");
        check("mainland".equals(HostV275.DISTRIBUTION), "distribution");
        check(HostV275.APK_SHA256.length() == 64, "APK digest");
        check(HostV275.SIGNING_CERT_SHA256.length() == 64, "signer digest");
        check(HostV275.DEX1_SHA256.length() == 64
                && HostV275.DEX2_SHA256.length() == 64
                && HostV275.DEX3_SHA256.length() == 64, "all DEX digests");
        check(HostV275.evidenceState() == HostV275.EvidenceState.STATIC_VERIFIED,
                "static evidence state");
        check(!HostV275.supportsRuntimeHooks(), "static profile must not authorize hooks");
        check(!HostV275.supportsNativeCompletion(), "static profile must not authorize completion");
        check("vs1".equals(HostV275.SSE_PARSER), "2.7.5 parser identity");
        check("ot1".equals(HostV275.FLOW_COLLECTOR), "2.7.5 collector identity");
        check("r3".equals(HostV275.DELTA_DISPATCHER), "2.7.5 delta dispatch identity");
        check("wt3".equals(HostV275.DELTA_COMMAND_MODEL), "DeltaCommand model");
        check("ut3".equals(HostV275.DELTA_COMMAND_SERIALIZER), "DeltaCommand serializer");
        check("f48".equals(HostV275.OPERATION_VALUE), "Operation value object");
        check("w28".equals(HostV275.OPERATION_SERIALIZER), "Operation serializer");
        check("h26".equals(HostV275.JSON_ELEMENT_MODEL), "JsonElement value model");
        check("l83".equals(HostV275.DELTA_REDUCER), "host delta reducer");
        check("coc".equals(HostV275.DELTA_STATE_OWNER), "host carry-forward state owner");
        check("ida".equals(HostV275.DELTA_ROOT_MODEL), "SSE message root");
        check("gda".equals(HostV275.DELTA_ROOT_SERIALIZER), "SSE message root serializer");
        check("ky".equals(HostV275.CHAT_MESSAGE_MODEL), "chat message model");
        check("jy1".equals(HostV275.FRAGMENT_LIST_MODEL), "fragment list model");
        check(HostV275.diagnosticLabel().contains("native-disabled"),
                "diagnostic label must expose disabled state");
        System.out.println("2.7.5 static profile regression checks passed");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
