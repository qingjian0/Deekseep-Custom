package ds.dsh.DeepSeek;

/**
 * Evidence catalog for mainland DeepSeek 2.7.5 / code295.
 *
 * This is a STATIC_VERIFIED profile, not an executable host adapter. Its APK identity and
 * static call chain are recorded for diagnostics; no request construction, transport invocation,
 * event decoding, reducer mapping, or completion hook is authorized by this class.
 */
final class HostV275 {
    static final String VERSION_NAME = "2.7.5";
    static final int VERSION_CODE = 295;
    static final String PACKAGE_NAME = "com.deepseek.chat";
    static final String DISTRIBUTION = "mainland";
    static final String APK_SHA256 =
            "ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70";
    static final String SIGNING_CERT_SHA256 =
            "c6893c5368f7b5122fecdafc6c4ecf49e0b3dc4e42bd166cdb646907788c7ffa";
    static final String DEX1_SHA256 =
            "74eeb079c2ce731f91e02ebd8dadb890364af15a8413970c8bed3c00e32f6aeb";
    static final String DEX2_SHA256 =
            "17685809b5391b98519420a92f6fed973c8520aca35a8bc99457c14b5c407b7d";
    static final String DEX3_SHA256 =
            "1d1cdbf997236abfc01e9ffa284dee46d8f49e139c4852059cac3f4548b53d78";

    // Independently audited static roles. These are not HookSpecs.
    static final String REQUEST_ENTRY_OWNER = "nw3";
    static final String REQUEST_MODEL = "yu1";
    static final String FLOW_WRAPPER = "b70";
    static final String HTTP_RESPONSE_HANDLER = "g0";
    static final String SSE_PARSER = "vs1";
    static final String LINE_READER = "qp0";
    static final String SSE_ENVELOPE = "bia";
    static final String EVENT_ADAPTER = "t5";
    static final String EVENT_WRAPPER = "ev1";
    static final String DELTA_DISPATCHER = "r3";
    static final String AUTO_RESUME_OWNER = "rr2";
    static final String UI_DELTA_OWNER = "pv1";
    static final String FLOW_COLLECTOR = "ot1";
    static final String LIFECYCLE_OWNER = "iv1";

    enum EvidenceState { DISCOVERED, STATIC_VERIFIED, RUNTIME_VERIFIED, E2E_VERIFIED }

    private HostV275() {}

    static EvidenceState evidenceState() {
        return EvidenceState.STATIC_VERIFIED;
    }

    static boolean supportsRuntimeHooks() {
        return false;
    }

    static boolean supportsNativeCompletion() {
        return false;
    }

    static String diagnosticLabel() {
        return VERSION_NAME + "/code" + VERSION_CODE + "-static-verified-native-disabled";
    }

    static boolean hasStaticMarkers(ClassLoader loader) {
        return loader != null
                && classExists(loader, "qy1")
                && classExists(loader, REQUEST_ENTRY_OWNER)
                && classExists(loader, UI_DELTA_OWNER)
                && classExists(loader, FLOW_WRAPPER);
    }

    private static boolean classExists(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
