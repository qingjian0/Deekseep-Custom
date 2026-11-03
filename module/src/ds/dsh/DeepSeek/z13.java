package ds.dsh.DeepSeek;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thin reflective facade over the encrypted-payload gateway.  The main DEX only knows
 * this facade + {@link z2}; the entire HTTP server and protocol conversion live
 * inside the decrypted payload.  Every call is optional and degrades to a safe default so the
 * host can never be brought down by a missing payload.
 */
public final class z13 {
    static final String INFO_FILE = "/data/data/com.deepseek.chat/files/dq0.txt";
    static final String LOG_FILE = "/data/data/com.deepseek.chat/files/deekseep_api.log";
    static final String STATUS_FILE = "/data/data/com.deepseek.chat/files/deekseep_api_status.json";
    static final String PUBLIC_INFO_FILE = "/storage/emulated/0/Deekseep_API.txt";

    private static final String GATEWAY = "ds.dsh.DeepSeek.z1";
    private static volatile Context appContext;
    private static final Map<String, Method> METHODS = new HashMap<String, Method>();

    private z13() {}

    private static Method method(String key, Class<?> gateway, String name, Class<?>... types) {
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        if (gateway == null) return null;
        try {
            Method m = HostRemap.declaredMethod(gateway, name, types);
            m.setAccessible(true);
            synchronized (METHODS) { METHODS.put(key, m); }
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> gateway() {
        return z14.payloadClass(appContext, GATEWAY);
    }

    private static String callString(String name) {
        try {
            Class<?> g = gateway();
            Method m = g == null ? null : method(name, g, name);
            return m == null ? null : (String) m.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean callBool(String name) {
        try {
            Class<?> g = gateway();
            Method m = g == null ? null : method(name, g, name);
            return m != null && (Boolean) m.invoke(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int callInt(String name) {
        try {
            Class<?> g = gateway();
            Method m = g == null ? null : method(name, g, name);
            return m == null ? 0 : (Integer) m.invoke(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void callVoid(String name) {
        try {
            Class<?> g = gateway();
            Method m = g == null ? null : method(name, g, name);
            if (m != null) m.invoke(null);
        } catch (Throwable ignored) {}
    }

    static void start(Context context, z2.Backend backend) {
        appContext = context == null ? null : context.getApplicationContext();
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            OpenLocalApiGateway.start(appContext, backend);
            return;
        }
        try {
            Class<?> g = gateway();
            if (g == null) return;
            Method m = method("start", g, "start", Context.class, z2.Backend.class);
            if (m != null) m.invoke(null, appContext, backend);
        } catch (Throwable ignored) {}
    }

    static void stop() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            OpenLocalApiGateway.stop();
            return;
        }
        callVoid("stop");
    }

    static boolean isRunning() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.isRunning();
        }
        return callBool("isRunning");
    }

    static String endpoint() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.endpoint();
        }
        return callString("endpoint");
    }

    static String rootEndpoint() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.endpoint();
        }
        return callString("rootEndpoint");
    }

    static String lanRootEndpoint() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) return "";
        return callString("lanRootEndpoint");
    }

    static String lanEndpoint() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.lanEndpoint();
        }
        return callString("lanEndpoint");
    }

    static boolean isLanEnabled() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.isLanEnabled();
        }
        return callBool("isLanEnabled");
    }

    static String setLanEnabled(Context context, boolean enabled) {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.setLanEnabled(context, enabled);
        }
        try {
            Class<?> g = gateway();
            if (g == null) return "Gateway unavailable";
            Method m = method("setLanEnabled", g, "setLanEnabled", Context.class, boolean.class);
            return m == null ? "LAN setting unavailable"
                    : (String) m.invoke(null, context, enabled);
        } catch (Throwable ignored) {
            return "LAN setting failed";
        }
    }

    static String openAiEndpoint() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.endpoint() + "/v1/chat/completions";
        }
        return callString("openAiEndpoint");
    }

    static int port() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.port();
        }
        return callInt("port");
    }

    static int tlsPort() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) return 0;
        return callInt("tlsPort");
    }

    static boolean isHttpsRunning() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) return false;
        return callBool("isHttpsRunning");
    }

    static String protocolMode() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) return "openai";
        return callString("protocolMode");
    }

    static String apiKey() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.apiKey();
        }
        return callString("apiKey");
    }

    static String connectionInfo() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.connectionInfo();
        }
        return callString("connectionInfo");
    }

    static String runtimeStatus() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.runtimeStatus();
        }
        return callString("runtimeStatus");
    }

    static int preferredPort(Context context) {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.preferredPort(context);
        }
        try {
            Class<?> g = gateway();
            if (g == null) return 0;
            Method m = method("preferredPortCtx", g, "preferredPort", Context.class);
            if (m == null) return 0;
            Object v = m.invoke(null, context);
            return v == null ? 0 : (Integer) v;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static int preferredPort() {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.preferredPort();
        }
        return callInt("preferredPort");
    }

    static void setProtocolMode(Context context, String requested) {
        try {
            Class<?> g = gateway();
            if (g == null) return;
            Method m = method("setProtocolMode", g, "setProtocolMode",
                    Context.class, String.class);
            if (m != null) m.invoke(null, context, requested);
        } catch (Throwable ignored) {}
    }

    static String setPreferredPort(Context context, int requestedPort) {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.setPreferredPort(context, requestedPort);
        }
        try {
            Class<?> g = gateway();
            if (g == null) return null;
            Method m = method("setPreferredPort", g, "setPreferredPort",
                    Context.class, int.class);
            if (m == null) return null;
            return (String) m.invoke(null, context, requestedPort);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String rotateKey(Context context) {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.rotateKey(context);
        }
        try {
            Class<?> g = gateway();
            if (g == null) return null;
            Method m = method("rotateKey", g, "rotateKey", Context.class);
            if (m == null) return null;
            return (String) m.invoke(null, context);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String setCustomKey(Context context, String candidate) {
        if (!BuildInfo.LOCAL_API_INCLUDED && "Open".equals(BuildInfo.BUILD_EDITION)) {
            return OpenLocalApiGateway.setCustomKey(context, candidate);
        }
        try {
            Class<?> g = gateway();
            if (g == null) return null;
            Method m = method("setCustomKey", g, "setCustomKey", Context.class, String.class);
            if (m == null) return null;
            return (String) m.invoke(null, context, candidate);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void diagnostic(String message) {
        try {
            Class<?> g = gateway();
            if (g == null) return;
            Method m = method("diagnostic", g, "diagnostic", String.class);
            if (m != null) m.invoke(null, message);
        } catch (Throwable ignored) {}
    }

    public static void ioDiagnostic(String requestId, String tag, String content) {
        try {
            Class<?> g = gateway();
            if (g == null) return;
            Method m = method("ioDiagnostic", g, "ioDiagnostic",
                    String.class, String.class, String.class);
            if (m != null) m.invoke(null, requestId, tag, content);
        } catch (Throwable ignored) {}
    }

    public static z2.DeltaSink nextStreamGenerationForRetry(
            z2.DeltaSink sink) {
        try {
            Class<?> g = gateway();
            if (g == null) return sink;
            Method m = method("nextStreamGenerationForRetry", g,
                    "nextStreamGenerationForRetry", z2.DeltaSink.class);
            if (m == null) return sink;
            Object v = m.invoke(null, sink);
            return v instanceof z2.DeltaSink
                    ? (z2.DeltaSink) v : sink;
        } catch (Throwable ignored) {
            return sink;
        }
    }

    public static z2.DeltaSink newGenerationGuard(AtomicLong epoch, long generation,
            z2.DeltaSink sink, String requestId) {
        try {
            Class<?> g = gateway();
            if (g == null) return sink;
            Method m = method("newGenerationGuard", g, "newGenerationGuard",
                    AtomicLong.class, long.class,
                    z2.DeltaSink.class, String.class);
            if (m == null) return sink;
            Object v = m.invoke(null, epoch, generation, sink, requestId);
            return v instanceof z2.DeltaSink
                    ? (z2.DeltaSink) v : sink;
        } catch (Throwable ignored) {
            return sink;
        }
    }

    static boolean looksStructurallyTruncatedText(String text) {
        try {
            Class<?> g = gateway();
            if (g == null) return false;
            Method m = method("looksStructurallyTruncatedText", g,
                    "looksStructurallyTruncatedText", String.class);
            if (m == null) return false;
            Object v = m.invoke(null, text);
            return v != null && (Boolean) v;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
