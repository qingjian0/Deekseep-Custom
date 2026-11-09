package ds.dsh.DeepSeek;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Open-build local/LAN API gateway. It deliberately uses only Android/JDK APIs and the stable
 * z2.Backend contract; no protected payload or obfuscated host type is referenced here.
 */
final class OpenLocalApiGateway {
    private static final int DEFAULT_PORT = 8765;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_PROMPT_CHARS = 128 * 1024;
    private static final String TOKEN_FILE = "dsh-local-api-token";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile ServerSocket server;
    private static volatile ServerSocket lanServer;
    private static volatile Thread acceptThread;
    private static volatile Thread lanAcceptThread;
    private static volatile String boundLanAddress = "";
    private static volatile String lanBindingStatus = "未启用";
    private static volatile String lanNetworkMonitorStatus = "未注册";
    private static volatile ExecutorService clients;
    // Registration and stop-time closure share one lifecycle lock.
    private static final Set<Socket> activeClients = Collections.newSetFromMap(
            new ConcurrentHashMap<Socket, Boolean>());
    private static volatile z2.Backend backend;
    private static volatile Context appContext;
    private static volatile String token;
    private static volatile int boundPort;
    private static volatile int configuredPort = DEFAULT_PORT;
    private static volatile String lastStartError = "";
    private static volatile boolean lanEnabled;
    private static volatile ConnectivityManager networkManager;
    private static volatile ConnectivityManager.NetworkCallback networkCallback;
    private static final int MAX_RESPONSE_STATES = 64;
    private static final long RESPONSE_STATE_TTL_MS = 30L * 60L * 1000L;
    private static final int MAX_RESPONSE_STATE_CHARS = 256 * 1024;
    private static final java.util.LinkedHashMap<String, ResponseState> RESPONSE_STATES =
            new java.util.LinkedHashMap<String, ResponseState>(16, 0.75f, true);

    private OpenLocalApiGateway() {}

    static synchronized void start(Context context, z2.Backend modelBackend) {
        if (RUNNING.get()) return;
        if (context == null || modelBackend == null) {
            lastStartError = context == null ? "DeepSeek context unavailable"
                    : "Model backend contract unavailable";
            return;
        }
        try {
            Context app = context.getApplicationContext();
            appContext = app;
            token = loadOrCreateToken(app);
            backend = modelBackend;
            android.content.SharedPreferences prefs = app.getSharedPreferences(
                    "dsh_local_api", Context.MODE_PRIVATE);
            configuredPort = prefs.getInt("port", DEFAULT_PORT);
            if (configuredPort < 1024 || configuredPort > 65535) {
                configuredPort = DEFAULT_PORT;
                prefs.edit().putInt("port", DEFAULT_PORT).commit();
            }
            lanEnabled = prefs.getBoolean("lan_enabled", false);
            ServerSocket socket = new ServerSocket(configuredPort, 16,
                    InetAddress.getByName("127.0.0.1"));
            server = socket;
            boundPort = socket.getLocalPort();
            clients = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<Runnable>(8));
            RUNNING.set(true);
            acceptThread = new Thread(() -> acceptLoop(socket), "dsh-local-api-loopback");
            acceptThread.setDaemon(true);
            acceptThread.start();
            boundLanAddress = "";
            lanBindingStatus = lanEnabled ? "等待绑定" : "未启用";
            lanNetworkMonitorStatus = lanEnabled ? "等待注册" : "未启用";
            if (lanEnabled) {
                refreshLanBinding();
                registerLanNetworkCallback(app);
            }
            lastStartError = "";
            Main.log("Open Local API listening on 127.0.0.1:" + boundPort
                    + (boundLanAddress.length() == 0 ? ""
                    : " and " + boundLanAddress + ":" + boundPort));
        } catch (Exception error) {
            RUNNING.set(false);
            unregisterLanNetworkCallback();
            closeServer();
            closeActiveClients();
            ExecutorService pool = clients;
            clients = null;
            if (pool != null) pool.shutdownNow();
            backend = null;
            boundPort = 0;
            lastStartError = error.getClass().getSimpleName();
            Main.log("Open Local API start failed: " + lastStartError);
        }
    }

    static synchronized void stop() {
        RUNNING.set(false);
        unregisterLanNetworkCallback();
        closeServer();
        closeActiveClients();
        lanBindingStatus = lanEnabled ? "已停止" : "未启用";
        ExecutorService pool = clients;
        clients = null;
        if (pool != null) pool.shutdownNow();
        backend = null;
        RESPONSE_STATES.clear();
        boundPort = 0;
        lastStartError = "";
    }

    static boolean isRunning() { return RUNNING.get(); }
    static int port() { return RUNNING.get() ? boundPort : configuredPort; }
    static int preferredPort() { return configuredPort; }
    static int preferredPort(Context context) {
        return context == null ? configuredPort : context.getApplicationContext()
                .getSharedPreferences("dsh_local_api", Context.MODE_PRIVATE)
                .getInt("port", DEFAULT_PORT);
    }
    static synchronized String setPreferredPort(Context context, int requestedPort) {
        if (requestedPort < 1024 || requestedPort > 65535) {
            return "端口必须在 1024 到 65535 之间";
        }
        Context app = context == null ? appContext : context.getApplicationContext();
        if (app == null) return "DeepSeek 上下文尚未就绪";
        z2.Backend current = backend;
        int previousPort = preferredPort(app);
        boolean wasRunning = RUNNING.get();
        android.content.SharedPreferences prefs = app.getSharedPreferences(
                "dsh_local_api", Context.MODE_PRIVATE);
        try {
            if (!prefs.edit().putInt("port", requestedPort).commit()) {
                return "端口配置写入失败，原配置未改变";
            }
            configuredPort = requestedPort;
            if (wasRunning && current != null) {
                stop();
                start(app, current);
                if (!RUNNING.get()) {
                    prefs.edit().putInt("port", previousPort).commit();
                    configuredPort = previousPort;
                    start(app, current);
                    return RUNNING.get()
                            ? "新端口启动失败，已恢复原端口 " + previousPort
                            : "新端口启动失败；原端口恢复也失败，请重启 DeepSeek 后检查端口占用";
                }
            }
            return null;
        } catch (Exception error) {
            try {
                prefs.edit().putInt("port", previousPort).commit();
                configuredPort = previousPort;
                if (wasRunning && current != null && !RUNNING.get()) start(app, current);
            } catch (Throwable ignored) {}
            return "端口设置失败，已尝试恢复原配置";
        }
    }
    static String endpoint() {
        return RUNNING.get() ? "http://127.0.0.1:" + boundPort : "";
    }
    static String baseUrl() {
        String root = endpoint();
        return root.length() == 0 ? "" : root + "/v1";
    }
    static boolean isLanEnabled() { return lanEnabled; }
    static boolean isLanEnabled(Context context) {
        Context app = context == null ? appContext : context.getApplicationContext();
        return app == null ? lanEnabled : app.getSharedPreferences(
                "dsh_local_api", Context.MODE_PRIVATE).getBoolean("lan_enabled", lanEnabled);
    }
    static synchronized String setLanEnabled(Context context, boolean enabled) {
        Context app = context == null ? appContext : context.getApplicationContext();
        if (app == null) return "DeepSeek 上下文尚未就绪";
        z2.Backend current = backend;
        boolean previous = isLanEnabled(app);
        try {
            app.getSharedPreferences("dsh_local_api", Context.MODE_PRIVATE)
                    .edit().putBoolean("lan_enabled", enabled).commit();
            lanEnabled = enabled;
            if (RUNNING.get() && current != null) {
                stop();
                start(app, current);
                if (!RUNNING.get()) {
                    app.getSharedPreferences("dsh_local_api", Context.MODE_PRIVATE)
                            .edit().putBoolean("lan_enabled", previous).commit();
                    lanEnabled = previous;
                    if (!RUNNING.get()) start(app, current);
                    else {
                        stop();
                        start(app, current);
                    }
                    return enabled
                            ? "未能绑定 192.168.x.x 局域网地址，已恢复原监听模式"
                            : "局域网监听切换失败，已尝试恢复原监听模式";
                }
            }
            return null;
        } catch (Exception error) {
            app.getSharedPreferences("dsh_local_api", Context.MODE_PRIVATE)
                    .edit().putBoolean("lan_enabled", previous).commit();
            lanEnabled = previous;
            if (!RUNNING.get() && current != null) start(app, current);
            return "局域网设置失败，已恢复原配置";
        }
    }
    static String lanEndpoint() {
        if (!RUNNING.get() || !lanEnabled) return "";
        return boundLanAddress.length() == 0 ? ""
                : "http://" + boundLanAddress + ":" + boundPort + "/v1";
    }
    private static String lanAddress(LinkProperties properties) {
        if (properties == null) return "";
        for (LinkAddress linkAddress : properties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()) continue;
            byte[] bytes = address.getAddress();
            if ((bytes[0] & 255) == 192 && (bytes[1] & 255) == 168) {
                return address.getHostAddress();
            }
        }
        return "";
    }

    private static String lanAddress(ConnectivityManager manager, Network network) {
        if (manager == null || network == null) return "";
        try {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            // A VPN's virtual 192.168.x.x address is not necessarily reachable from the LAN.
            // Bind only to a physical/non-VPN interface and keep VPN changes out of address choice.
            if (capabilities == null
                    || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                return "";
            }
            return lanAddress(manager.getLinkProperties(network));
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static String firstLanAddress() {
        Context app = appContext;
        if (app == null) return "";
        try {
            ConnectivityManager manager = (ConnectivityManager) app.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "";
            Network active = manager.getActiveNetwork();
            String activeAddress = lanAddress(manager, active);
            if (activeAddress.length() > 0) return activeAddress;

            // A VPN may be Android's active/default network while Wi-Fi still owns the
            // reachable 192.168.x.x interface. Prefer any live non-VPN network.
            for (Network network : manager.getAllNetworks()) {
                if (network.equals(active)) continue;
                String address = lanAddress(manager, network);
                if (address.length() > 0) return address;
            }
        } catch (Exception error) {
            Main.log("Open Local API network address lookup failed: "
                    + error.getClass().getSimpleName());
        }
        return "";
    }

    private static void registerLanNetworkCallback(Context app) {
        if (app == null || !lanEnabled || networkCallback != null) return;
        try {
            ConnectivityManager manager = (ConnectivityManager) app.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                lanNetworkMonitorStatus = "无法获取 Android 网络服务";
                return;
            }
            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { refreshLanBinding(); }
                @Override public void onLost(Network network) { refreshLanBinding(); }
                @Override public void onLinkPropertiesChanged(
                        Network network, LinkProperties properties) { refreshLanBinding(); }
            };
            manager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback);
            networkManager = manager;
            networkCallback = callback;
            lanNetworkMonitorStatus = "已注册";
        } catch (Exception error) {
            lanNetworkMonitorStatus = "注册失败：" + error.getClass().getSimpleName();
            Main.log("Open Local API network callback registration failed: "
                    + error.getClass().getSimpleName());
        }
    }

    private static void unregisterLanNetworkCallback() {
        ConnectivityManager manager = networkManager;
        ConnectivityManager.NetworkCallback callback = networkCallback;
        networkManager = null;
        networkCallback = null;
        if (manager != null && callback != null) {
            try { manager.unregisterNetworkCallback(callback); } catch (Exception ignored) {}
        }
        lanNetworkMonitorStatus = RUNNING.get() && lanEnabled ? "未注册" : "已停止";
    }

    private static synchronized void refreshLanBinding() {
        if (!RUNNING.get() || !lanEnabled) return;
        String address = firstLanAddress();
        ServerSocket current = lanServer;
        if (address.length() == 0) {
            lanServer = null;
            boundLanAddress = "";
            lanBindingStatus = "未找到可用的 192.168.x.x 局域网 IPv4 地址";
            if (current != null) try { current.close(); } catch (Exception ignored) {}
            Main.log("Open Local API LAN unavailable: no active 192.168.x.x address");
            return;
        }
        if (address.equals(boundLanAddress) && current != null && !current.isClosed()) return;
        try {
            ServerSocket replacement = new ServerSocket(configuredPort, 16,
                    InetAddress.getByName(address));
            lanServer = replacement;
            boundLanAddress = address;
            Thread thread = new Thread(() -> acceptLoop(replacement), "dsh-local-api-lan");
            thread.setDaemon(true);
            lanAcceptThread = thread;
            thread.start();
            if (current != null) try { current.close(); } catch (Exception ignored) {}
            lanBindingStatus = "已绑定 " + address + ":" + boundPort;
            Main.log("Open Local API LAN rebound to " + address + ":" + boundPort);
        } catch (Exception error) {
            lanBindingStatus = "绑定失败：" + error.getClass().getSimpleName();
            Main.log("Open Local API LAN rebind failed: "
                    + error.getClass().getSimpleName());
            if (current == null || current.isClosed() || !address.equals(boundLanAddress)) {
                if (current != null && !current.isClosed()) {
                    try { current.close(); } catch (Exception ignored) {}
                }
                lanServer = null;
                boundLanAddress = "";
            }
        }
    }
    private static boolean isAllowedRemote(InetAddress address) {
        if (address == null) return false;
        if (address.isLoopbackAddress()) return true;
        if (!lanEnabled || !(address instanceof Inet4Address)) return false;
        byte[] bytes = address.getAddress();
        return (bytes[0] & 255) == 192 && (bytes[1] & 255) == 168;
    }
    static String apiKey() { return token == null ? "" : token; }
    static synchronized String rotateKey(Context context) {
        try {
            Context app = context == null ? appContext : context.getApplicationContext();
            if (app == null) return null;
            String replacement = generateToken();
            persistToken(app, replacement);
            appContext = app;
            token = replacement;
            return replacement;
        } catch (Exception error) {
            Main.log("Open Local API token rotation failed: " + error.getClass().getSimpleName());
            return null;
        }
    }
    static synchronized String setCustomKey(Context context, String candidate) {
        String value = candidate == null ? "" : candidate.trim();
        if (!isValidApiKey(value)) {
            return "本地 API Key 必须为 8 到 256 位可见 ASCII 字符，不能包含空格或控制字符";
        }
        Context app = context == null ? appContext : context.getApplicationContext();
        if (app == null) return "DeepSeek 上下文尚未就绪";
        try {
            persistToken(app, value);
            appContext = app;
            token = value;
            return null;
        } catch (Exception error) {
            Main.log("Open Local API custom key update failed: "
                    + error.getClass().getSimpleName());
            return "API Key 保存失败";
        }
    }
    static String connectionInfo() {
        if (!RUNNING.get()) return "本地 API 服务尚未启动"
                + (lastStartError.length() == 0 ? "" : "\n最近启动错误：" + lastStartError);
        return "本机地址：" + baseUrl() + "\n"
                + "Anthropic Base URL：" + endpoint() + "\n"
                + "聊天接口：" + endpoint() + "/v1/chat/completions\n"
                + "Anthropic Messages：" + endpoint() + "/v1/messages\n"
                + "本地 API Key：" + apiKey() + "\n"
                + "可用模型：deepseek-chat、deepseek-reasoner；兼容别名：deepseek-flash\n"
                + (lanEnabled
                        ? (boundLanAddress.length() > 0
                                ? "局域网地址：" + lanEndpoint() + "\n"
                                        + "局域网 Anthropic Base URL：http://" + boundLanAddress
                                                + ":" + boundPort + "\n"
                                : "")
                                + "局域网绑定状态：" + lanBindingStatus + "\n"
                                + "网络变化监听：" + lanNetworkMonitorStatus + "\n"
                        : "局域网状态：未启用\n")
                + "允许的局域网范围：192.168.0.0/16\n"
                + "后端状态：" + (backend != null && backend.isReady()
                        ? "就绪" : (backend == null ? "不可用" : backend.readinessDetail()));
    }
    static String runtimeStatus() {
        return RUNNING.get() ? "Running on 127.0.0.1"
                + (boundLanAddress.length() > 0 ? " + " + boundLanAddress
                        : (lanEnabled ? " (LAN unavailable)" : ""))
                + ":" + boundPort
                : (lastStartError.length() == 0 ? "Stopped"
                        : "Stopped (startup failed: " + lastStartError + ")");
    }

    private static void closeServer() {
        ServerSocket socket = server;
        ServerSocket lanSocket = lanServer;
        server = null;
        lanServer = null;
        boundLanAddress = "";
        if (socket != null) try { socket.close(); } catch (Exception ignored) {}
        if (lanSocket != null) try { lanSocket.close(); } catch (Exception ignored) {}
    }

    private static void closeActiveClients() {
        synchronized (activeClients) {
            for (Socket client : activeClients) {
                try { client.close(); } catch (Exception ignored) {}
            }
            activeClients.clear();
        }
    }

    private static boolean registerClient(Socket client) {
        synchronized (activeClients) {
            if (!RUNNING.get() || client == null || client.isClosed()) return false;
            activeClients.add(client);
            return true;
        }
    }

    private static void closeTrackedClient(Socket client) {
        if (client == null) return;
        synchronized (activeClients) { activeClients.remove(client); }
        try { client.close(); } catch (Exception ignored) {}
    }

    private static void acceptLoop(ServerSocket socket) {
        while (RUNNING.get() && !socket.isClosed()) {
            try {
                Socket client = socket.accept();
                client.setSoTimeout(30_000);
                // Serialize registration with stop so an accepted socket cannot escape shutdown.
                if (!registerClient(client)) {
                    closeTrackedClient(client);
                    continue;
                }
                ExecutorService pool = clients;
                if (pool == null) {
                    closeTrackedClient(client);
                } else {
                    try {
                        pool.execute(() -> handle(client));
                    } catch (java.util.concurrent.RejectedExecutionException saturated) {
                        try {
                            OutputStream rejected = client.getOutputStream();
                            sendError(rejected, 503, "server_busy", "Too many queued requests");
                        } catch (Exception ignored) {
                        } finally {
                            closeTrackedClient(client);
                        }
                    }
                }
            } catch (Exception error) {
                if (RUNNING.get()) Main.log("Open Local API accept failed: "
                        + error.getClass().getSimpleName());
            }
        }
    }

    private static void handle(Socket socket) {
        final long requestStartedNanos = System.nanoTime();
        try (Socket client = socket;
             InputStream in = new BufferedInputStream(client.getInputStream());
             OutputStream out = new BufferedOutputStream(client.getOutputStream())) {
            if (!isAllowedRemote(client.getInetAddress())) {
                sendError(out, 403, "source_not_allowed",
                        "Only loopback and 192.168.0.0/16 clients are allowed");
                return;
            }
            HttpRequest request;
            try {
                request = readRequest(in);
            } catch (Exception malformed) {
                String detail = malformed.getMessage() == null ? "" : malformed.getMessage();
                int status = detail.toLowerCase(java.util.Locale.ROOT).contains("too large")
                        ? 413 : 400;
                sendError(out, status, status == 413 ? "request_too_large"
                        : "invalid_http_request", "Invalid HTTP request");
                return;
            }
            if (request == null) return;
            if (!"GET".equals(request.method) && !"POST".equals(request.method)) {
                sendError(out, 405, "method_not_allowed", "Only GET and POST are supported");
                return;
            }
            boolean anthropicRoute = "POST".equals(request.method)
                    && "/v1/messages".equals(request.path);
            String supplied = anthropicRoute
                    ? request.headers.get("x-api-key") : bearer(request.headers.get("authorization"));
            if (anthropicRoute && (supplied == null || supplied.length() == 0)) {
                supplied = bearer(request.headers.get("authorization"));
            }
            if (token == null || !constantTimeEquals(token, supplied)) {
                if (anthropicRoute) sendAnthropicError(out, 401, "authentication_error",
                        "Invalid API key");
                else sendError(out, 401, "invalid_api_key", "Invalid bearer token");
                return;
            }
            OpenNativeCompletionProbe.apiTrace("http-authenticated method=" + request.method
                    + " path=" + request.path);
            if ("GET".equals(request.method)
                    && "/v1/diagnostics/requests".equals(request.path)) {
                sendJson(out, 200, new JSONObject().put("object", "list")
                        .put("data", LocalApiDiagnostics.recent(24)));
                return;
            }
            final String diagnosticsPrefix = "/v1/diagnostics/requests/";
            if ("GET".equals(request.method) && request.path.startsWith(diagnosticsPrefix)) {
                String diagnosticId = request.path.substring(diagnosticsPrefix.length());
                if (diagnosticId.length() == 0 || diagnosticId.indexOf('/') >= 0
                        || diagnosticId.length() > 96) {
                    sendError(out, 400, "invalid_request_id", "Invalid diagnostic request ID");
                    return;
                }
                JSONObject snapshot = LocalApiDiagnostics.snapshot(diagnosticId);
                if (snapshot == null) {
                    sendError(out, 404, "diagnostic_not_found", "Diagnostic request was not found");
                } else {
                    sendJson(out, 200, snapshot);
                }
                return;
            }
            if ("GET".equals(request.method) && "/v1/models".equals(request.path)) {
                // Model discovery describes the configured API surface, not live engine
                // readiness. Keep it usable for client setup/diagnostics while completions
                // independently return 503 until the native adapter is genuinely ready.
                JSONObject model = new JSONObject().put("id", "deepseek-chat")
                        .put("object", "model").put("owned_by", "local");
                // Compatibility alias retained for clients using the pre-Open model catalog.
                JSONObject flashAlias = new JSONObject().put("id", "deepseek-flash")
                        .put("object", "model").put("owned_by", "local");
                JSONObject reasoner = new JSONObject().put("id", "deepseek-reasoner")
                        .put("object", "model").put("owned_by", "local");
                sendJson(out, 200, new JSONObject().put("object", "list")
                        .put("data", new JSONArray().put(model).put(reasoner).put(flashAlias)));
                OpenNativeCompletionProbe.apiTrace("models-list status=200 durationMs="
                        + ((System.nanoTime() - requestStartedNanos) / 1000000L));
                return;
            }
            if ("POST".equals(request.method) && "/v1/responses".equals(request.path)) {
                handleResponses(out, request);
                return;
            }
            if (anthropicRoute) {
                handleAnthropicMessages(out, request);
                return;
            }
            if (!"POST".equals(request.method)
                    || !"/v1/chat/completions".equals(request.path)) {
                sendError(out, 404, "not_found", "Unknown API endpoint");
                return;
            }
            String contentType = request.headers.get("content-type");
            String mediaType = contentType == null ? ""
                    : contentType.split(";", 2)[0].trim();
            if (contentType != null
                    && !"application/json".equalsIgnoreCase(mediaType)) {
                sendError(out, 415, "unsupported_media_type",
                        "Content-Type must be application/json");
                return;
            }
            JSONObject body;
            try {
                body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
            } catch (Exception invalid) {
                sendError(out, 400, "invalid_json", "Request body must be valid JSON");
                return;
            }
            String unsupportedOption = unsupportedChatOption(body);
            if (unsupportedOption.length() > 0) {
                sendError(out, 400, "unsupported_parameter",
                        "This Open gateway does not support the requested parameter: "
                                + unsupportedOption);
                return;
            }
            String unsupportedFeature = unsupportedFeature(body);
            if (unsupportedFeature.length() > 0) {
                OpenNativeCompletionProbe.apiTrace("request-rejected stage=feature-validation"
                        + " parameter=" + unsupportedFeature + " status=400 code=unsupported_feature");
                sendError(out, 400, "unsupported_feature",
                        "This Open gateway does not support the requested feature: "
                                + unsupportedFeature);
                return;
            }
            if (body.has("model") && !(body.opt("model") instanceof String)) {
                sendError(out, 400, "invalid_model", "model must be a string");
                return;
            }
            String requestedModel = body.optString("model", "deepseek-chat").trim();
            // The earlier model catalog used deepseek-flash for the default native route.
            // Canonicalize it to deepseek-chat rather than forwarding an unsupported ID.
            String model = "deepseek-flash".equals(requestedModel)
                    ? "deepseek-chat" : requestedModel;
            if (!"deepseek-chat".equals(model) && !"deepseek-reasoner".equals(model)) {
                String reportedModel = model.replace('\r', ' ').replace('\n', ' ');
                if (reportedModel.length() > 128) {
                    reportedModel = reportedModel.substring(0, 128);
                }
                Main.log("Open Local API rejected model id: " + reportedModel);
                OpenNativeCompletionProbe.apiTrace("request-rejected stage=model-validation"
                        + " model=" + reportedModel + " status=400 code=model_not_found");
                sendError(out, 400, "model_not_found",
                        "不支持的模型名称：" + reportedModel
                                + "；当前可用模型：deepseek-chat、deepseek-reasoner（兼容别名：deepseek-flash）");
                return;
            }
            String conversationPrompt;
            try {
                conversationPrompt = toPrompt(body.optJSONArray("messages"));
            } catch (Exception invalid) {
                sendError(out, 400, "invalid_messages", "Messages must contain supported text roles");
                return;
            }
            if (conversationPrompt.length() == 0 || conversationPrompt.length() > MAX_PROMPT_CHARS) {
                sendError(out, 400, "invalid_prompt", "Prompt is empty or too large");
                return;
            }
            if (body.has("stream") && !(body.opt("stream") instanceof Boolean)) {
                sendError(out, 400, "invalid_stream", "stream must be a boolean");
                return;
            }
            boolean stream = body.optBoolean("stream", false);
            String maxTokenField = body.has("max_completion_tokens")
                    && body.opt("max_completion_tokens") != JSONObject.NULL
                    ? "max_completion_tokens" : "max_tokens";
            int maxTokens = 4096;
            if (body.has(maxTokenField)) {
                Object rawMaxTokens = body.opt(maxTokenField);
                if (rawMaxTokens == JSONObject.NULL) {
                    OpenNativeCompletionProbe.apiTrace("token-budget-defaulted parameter="
                            + maxTokenField + " reason=null default=4096");
                } else {
                    int normalizedMaxTokens = normalizeMaxTokens(rawMaxTokens);
                    if (normalizedMaxTokens < 1) {
                        String rawType = rawMaxTokens == null ? "null"
                                : rawMaxTokens.getClass().getSimpleName();
                        OpenNativeCompletionProbe.apiTrace("request-rejected stage=parameter-validation"
                                + " parameter=" + maxTokenField + " rawType=" + rawType
                                + " status=400 code=invalid_max_tokens");
                        sendError(out, 400, "invalid_max_tokens",
                                maxTokenField + " must be an integer from 1 to 8192");
                        return;
                    }
                    maxTokens = normalizedMaxTokens;
                    String rawType = rawMaxTokens.getClass().getSimpleName();
                    OpenNativeCompletionProbe.apiTrace("token-budget-normalized parameter="
                            + maxTokenField + " inputType=" + rawType
                            + " applied=" + maxTokens + " limit=8192");
                }
            }
            JSONArray rawTools = body.optJSONArray("tools");
            if (body.has("tools") && body.opt("tools") != JSONObject.NULL && rawTools == null) {
                sendError(out, 400, "invalid_tools", "tools must be an array");
                return;
            }
            if (body.has("parallel_tool_calls")
                    && !(body.opt("parallel_tool_calls") instanceof Boolean)) {
                sendError(out, 400, "invalid_parallel_tool_calls",
                        "parallel_tool_calls must be a boolean");
                return;
            }
            boolean parallelTools = body.optBoolean("parallel_tool_calls", true);
            OpenAiToolBridge.Plan toolPlan;
            try {
                toolPlan = OpenAiToolBridge.chatPlan(
                        rawTools, body.opt("tool_choice"), parallelTools);
            } catch (z2.GatewayException invalidTools) {
                sendError(out, invalidTools.status, invalidTools.code, invalidTools.type,
                        invalidTools.getMessage());
                return;
            }
            String requestId = "api-" + UUID.randomUUID();
            String prompt = OpenAiToolBridge.addInstructions(
                    conversationPrompt, toolPlan, requestId);
            if (prompt.length() > MAX_PROMPT_CHARS) {
                sendError(out, 400, "invalid_prompt", "Prompt with tool instructions is too large");
                return;
            }
            if (stream && toolPlan.active()) {
                sendError(out, 400, "unsupported_streaming_tool_calls",
                        "Tool calls are currently supported only with stream=false");
                return;
            }
            z2.CompletionRequest completion = new z2.CompletionRequest(
                    requestId, model, "default", conversationPrompt, prompt,
                    "deepseek-reasoner".equals(model), false, maxTokens,
                    toolPlan, null, false);
            LocalApiDiagnostics.begin(requestId, "openai", stream ? "stream" : "non_stream");
            JSONArray diagnosticMessages = body.optJSONArray("messages");
            LocalApiDiagnostics.inputShape(requestId, prompt.length(),
                    diagnosticMessages == null ? 1 : diagnosticMessages.length());
            LocalApiDiagnostics.stage(requestId, "http_validation", "succeeded", "",
                    "HTTP request and authentication accepted");
            LocalApiDiagnostics.stage(requestId, "parameter_validation", "succeeded", "",
                    "Model, messages and generation options validated");
            OpenNativeCompletionProbe.apiTrace("request-accepted requestId=" + requestId
                    + " requestedModel=" + requestedModel + " model=" + model
                    + " stream=" + stream + " promptChars=" + prompt.length()
                    + " maxTokens=" + maxTokens);
            z2.Backend current = backend;
            OpenNativeCompletionProbe.apiTrace("model-routing requestId=" + requestId
                    + " model=" + model + " backendPresent=" + (current != null));
            boolean ready = current != null && current.isReady();
            LocalApiDiagnostics.stage(requestId, "backend_readiness",
                    ready ? "succeeded" : "failed", ready ? "" : "model_not_ready",
                    "backend_present=" + (current != null) + " ready=" + ready);
            OpenNativeCompletionProbe.apiTrace("backend-readiness requestId=" + requestId
                    + " ready=" + ready);
            if (current == null) {
                sendError(out, 503, "model_not_ready", "server_error",
                        "Model backend unavailable");
                LocalApiDiagnostics.finish(requestId, "failed", "model_not_ready",
                        "Model backend unavailable");
                OpenNativeCompletionProbe.apiTrace("request-rejected requestId=" + requestId
                        + " stage=backend-missing status=503");
                return;
            }
            if (!ready) {
                // Readiness is diagnostic, not a dispatch gate. The backend execution facade
                // owns the per-request failure code (for Open this is engine_missing). This
                // keeps the protocol route honest and prevents an edition-level preflight from
                // bypassing the native adapter boundary. Streaming failures are emitted as SSE
                // errors by streamCompletion; non-streaming failures retain their HTTP status.
                OpenNativeCompletionProbe.apiTrace("backend-not-ready-dispatching requestId="
                        + requestId + " code=" + current.readinessCode());
            }
            boolean includeUsage = body.optJSONObject("stream_options") != null
                    && body.optJSONObject("stream_options").optBoolean("include_usage", false);
            if (stream) {
                streamCompletion(out, current, completion, model, requestId, includeUsage);
            } else {
                z2.CompletionResult result;
                long completionStartedNanos = System.nanoTime();
                OpenNativeCompletionProbe.apiTrace("completion-start requestId=" + requestId
                        + " mode=non-stream");
                try {
                    result = current.complete(completion, null);
                } catch (z2.GatewayException rejected) {
                    sendError(out, rejected.status, rejected.code, rejected.type,
                            rejected.getMessage());
                    LocalApiDiagnostics.finish(requestId, "failed", rejected.code,
                            rejected.getClass().getSimpleName());
                    OpenNativeCompletionProbe.apiTrace("completion-failed requestId=" + requestId
                            + " status=" + rejected.status + " code=" + rejected.code
                            + " durationMs=" + ((System.nanoTime() - completionStartedNanos) / 1000000L));
                    return;
                } catch (Exception upstreamFailure) {
                    sendError(out, 502, "upstream_error", "Native model execution failed");
                    LocalApiDiagnostics.finish(requestId, "failed", "upstream_error",
                            upstreamFailure.getClass().getSimpleName());
                    OpenNativeCompletionProbe.apiTrace("completion-failed requestId=" + requestId
                            + " status=502 exception=" + upstreamFailure.getClass().getName()
                            + " durationMs=" + ((System.nanoTime() - completionStartedNanos) / 1000000L));
                    return;
                }
                if (result == null) {
                    sendError(out, 502, "empty_upstream_response", "Native model returned no result");
                    LocalApiDiagnostics.finish(requestId, "failed", "empty_upstream_response",
                            "Native model returned no result");
                    OpenNativeCompletionProbe.apiTrace("completion-empty requestId=" + requestId
                            + " durationMs=" + ((System.nanoTime() - completionStartedNanos) / 1000000L));
                    return;
                }
                OpenNativeCompletionProbe.apiTrace("completion-complete requestId=" + requestId
                        + " durationMs=" + ((System.nanoTime() - completionStartedNanos) / 1000000L)
                        + " textChars=" + result.text.length()
                        + " reasoningChars=" + result.reasoning.length()
                        + " finishReason=" + result.finishReason);
                List<OpenAiToolBridge.Call> parsedToolCalls =
                        OpenAiToolBridge.parseCalls(result.text, toolPlan);
                if (toolPlan.active() && parsedToolCalls.isEmpty()
                        && (toolPlan.choice.requiresCall()
                        || OpenAiToolBridge.resemblesCallSyntax(result.text, toolPlan))) {
                    sendError(out, 502, "invalid_tool_response",
                            "The model did not return a valid tool call matching the request schema");
                    LocalApiDiagnostics.finish(requestId, "failed", "invalid_tool_response",
                            "Native output did not match the requested tool schema");
                    return;
                }
                JSONObject message = new JSONObject().put("role", "assistant")
                        .put("content", parsedToolCalls.isEmpty()
                                ? result.text : JSONObject.NULL);
                if (!parsedToolCalls.isEmpty()) {
                    JSONArray wireCalls = new JSONArray();
                    for (OpenAiToolBridge.Call call : parsedToolCalls) {
                        JSONObject function = new JSONObject()
                                .put("name", call.name)
                                .put("arguments", call.arguments);
                        wireCalls.put(new JSONObject().put("id", call.callId)
                                .put("type", "function").put("function", function));
                    }
                    message.put("tool_calls", wireCalls);
                }
                if ("deepseek-reasoner".equals(model) && result.reasoning.length() > 0) {
                    message.put("reasoning_content", result.reasoning);
                }
                JSONObject choice = new JSONObject().put("index", 0).put("message", message)
                        .put("finish_reason", parsedToolCalls.isEmpty()
                                ? result.finishReason : "tool_calls");
                int promptTokens = estimateTokens(completion.prompt);
                int completionTokens = estimateTokens(result.text)
                        + estimateTokens(result.reasoning);
                JSONObject usage = new JSONObject().put("prompt_tokens", promptTokens)
                        .put("completion_tokens", completionTokens)
                        .put("total_tokens", promptTokens + completionTokens)
                        .put("dsh_usage_estimated", true);
                LocalApiDiagnostics.stage(requestId, "response_delivery", "running", "",
                        "Writing JSON completion response");
                sendJson(out, 200, new JSONObject().put("id", requestId)
                        .put("object", "chat.completion").put("created", System.currentTimeMillis()/1000)
                        .put("model", model).put("choices", new JSONArray().put(choice))
                        .put("usage", usage));
                LocalApiDiagnostics.stage(requestId, "response_delivery", "succeeded", "",
                        "JSON completion response delivered");
                LocalApiDiagnostics.finish(requestId, "succeeded", "",
                        "Completion response finished");
            }
        } catch (Exception error) {
            Main.log("Open Local API request failed: " + error.getClass().getSimpleName());
            OpenNativeCompletionProbe.apiTrace("http-handler-failed exception="
                    + error.getClass().getName()
                    + " durationMs=" + ((System.nanoTime() - requestStartedNanos) / 1000000L));
        } finally {
            closeTrackedClient(socket);
        }
    }

    private static void handleAnthropicMessages(OutputStream out, HttpRequest request)
            throws Exception {
        String version = request.headers.get("anthropic-version");
        if (version == null || version.trim().length() == 0 || version.length() > 32) {
            sendAnthropicError(out, 400, "invalid_request_error",
                    "anthropic-version header is required");
            return;
        }
        String contentType = request.headers.get("content-type");
        String mediaType = contentType == null ? ""
                : contentType.split(";", 2)[0].trim();
        if (contentType != null && !"application/json".equalsIgnoreCase(mediaType)) {
            sendAnthropicError(out, 415, "invalid_request_error",
                    "Content-Type must be application/json");
            return;
        }
        JSONObject body;
        try {
            body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
        } catch (Exception invalid) {
            sendAnthropicError(out, 400, "invalid_request_error",
                    "Request body must be valid JSON");
            return;
        }
        final AnthropicMessagesAdapter.Request normalized;
        try {
            normalized = AnthropicMessagesAdapter.parse(body);
        } catch (z2.GatewayException invalid) {
            sendAnthropicError(out, invalid.status, "invalid_request_error",
                    invalid.getMessage());
            return;
        } catch (Exception invalid) {
            sendAnthropicError(out, 400, "invalid_request_error",
                    "Invalid Anthropic Messages request");
            return;
        }
        String requestId = normalized.requestId;
        if (normalized.stream && normalized.toolPlan.active()) {
            sendAnthropicError(out, 400, "invalid_request_error",
                    "Streaming tool_use is not implemented yet; use stream=false");
            return;
        }
        z2.CompletionRequest completion = new z2.CompletionRequest(
                requestId, normalized.model, "default", normalized.prompt, normalized.prompt,
                normalized.reasoning, false, normalized.maxTokens,
                normalized.toolPlan, null, false);
        LocalApiDiagnostics.begin(requestId, "anthropic_messages",
                normalized.stream ? "stream" : "non_stream");
        JSONArray diagnosticMessages = body.optJSONArray("messages");
        LocalApiDiagnostics.inputShape(requestId, normalized.prompt.length(),
                diagnosticMessages == null ? 1 : diagnosticMessages.length());
        LocalApiDiagnostics.stage(requestId, "http_validation", "succeeded", "",
                "HTTP request and authentication accepted");
        LocalApiDiagnostics.stage(requestId, "parameter_validation", "succeeded", "",
                "Anthropic Messages input and generation options validated");
        z2.Backend current = backend;
        boolean ready = current != null && current.isReady();
        LocalApiDiagnostics.stage(requestId, "backend_readiness", ready ? "succeeded" : "failed",
                ready ? "" : "model_not_ready",
                "backend_present=" + (current != null) + " ready=" + ready);
        if (!ready) {
            sendAnthropicError(out, 503, "api_error",
                    current == null ? "Model backend unavailable" : current.readinessDetail());
            LocalApiDiagnostics.finish(requestId, "failed", "model_not_ready",
                    "Model backend unavailable");
            return;
        }
        if (normalized.stream) {
            streamAnthropicMessages(out, current, completion, normalized, requestId);
            return;
        }
        long started = System.nanoTime();
        final z2.CompletionResult result;
        try {
            result = current.complete(completion, null);
        } catch (z2.GatewayException rejected) {
            sendAnthropicError(out, rejected.status, "api_error", rejected.getMessage());
            LocalApiDiagnostics.finish(requestId, "failed", rejected.code,
                    rejected.getClass().getSimpleName());
            return;
        } catch (Exception failure) {
            sendAnthropicError(out, 502, "api_error", "Native model execution failed");
            LocalApiDiagnostics.finish(requestId, "failed", "upstream_error",
                    failure.getClass().getSimpleName());
            OpenNativeCompletionProbe.apiTrace("anthropic-completion-failed requestId="
                    + requestId + " exception=" + failure.getClass().getName()
                    + " durationMs=" + ((System.nanoTime() - started) / 1000000L));
            return;
        }
        if (result == null) {
            sendAnthropicError(out, 502, "api_error", "Native model returned no result");
            LocalApiDiagnostics.finish(requestId, "failed", "empty_upstream_response",
                    "Native model returned no result");
            return;
        }
        List<OpenAiToolBridge.Call> calls =
                OpenAiToolBridge.parseCalls(result.text, normalized.toolPlan);
        if (normalized.toolPlan.active() && calls.isEmpty()
                && (normalized.toolPlan.choice.requiresCall()
                || OpenAiToolBridge.resemblesCallSyntax(result.text, normalized.toolPlan))) {
            sendAnthropicError(out, 502, "api_error",
                    "The model did not return a valid tool call matching the request schema");
            LocalApiDiagnostics.finish(requestId, "failed", "invalid_tool_response",
                    "Native output did not match the requested tool schema");
            return;
        }
        JSONArray content = anthropicContent(result, calls);
        String stopReason = !calls.isEmpty() ? "tool_use"
                : "length".equals(result.finishReason) ? "max_tokens" : "end_turn";
        JSONObject message = new JSONObject().put("id", requestId)
                .put("type", "message").put("role", "assistant")
                .put("model", normalized.model).put("content", content)
                .put("stop_reason", stopReason).put("stop_sequence", JSONObject.NULL)
                .put("usage", new JSONObject()
                        .put("input_tokens", estimateTokens(completion.prompt))
                        .put("output_tokens", estimateTokens(result.text))
                        .put("dsh_usage_estimated", true));
        // z2.Backend does not expose tokenizer counters. Return rough estimates and mark them
        // explicitly so clients do not mistake them for exact DeepSeek tokenizer usage.
        LocalApiDiagnostics.stage(requestId, "response_delivery", "running", "",
                "Writing Anthropic Messages JSON response");
        sendJson(out, 200, message);
        LocalApiDiagnostics.stage(requestId, "response_delivery", "succeeded", "",
                "Anthropic Messages JSON response delivered");
        LocalApiDiagnostics.finish(requestId, "succeeded", "", "Anthropic request finished");
        OpenNativeCompletionProbe.apiTrace("anthropic-completion-complete requestId="
                + requestId + " durationMs=" + ((System.nanoTime() - started) / 1000000L)
                + " textChars=" + result.text.length()
                + " reasoningChars=" + result.reasoning.length()
                + " toolCalls=" + calls.size());
    }

    private static JSONArray anthropicContent(z2.CompletionResult result,
            List<OpenAiToolBridge.Call> calls) throws Exception {
        JSONArray content = new JSONArray();
        if (result.text.length() > 0 && calls.isEmpty()) {
            content.put(new JSONObject().put("type", "text").put("text", result.text));
        }
        for (OpenAiToolBridge.Call call : calls) {
            JSONObject input;
            try { input = new JSONObject(call.arguments); }
            catch (Exception invalid) { input = new JSONObject(); }
            content.put(new JSONObject().put("type", "tool_use")
                    .put("id", call.callId).put("name", call.name).put("input", input));
        }
        if (content.length() == 0) {
            content.put(new JSONObject().put("type", "text").put("text", ""));
        }
        return content;
    }

    private static void streamAnthropicMessages(OutputStream out, z2.Backend current,
            z2.CompletionRequest completion, AnthropicMessagesAdapter.Request normalized,
            String requestId) throws Exception {
        writeHeaders(out, 200, "text/event-stream; charset=utf-8");
        final AtomicBoolean disconnected = new AtomicBoolean(false);
        final StringBuilder streamedText = new StringBuilder();
        final int[] nextBlock = {0};
        final int[] textBlock = {-1};
        try {
            sendAnthropicEvent(out, "message_start", new JSONObject().put("type", "message_start")
                    .put("message", new JSONObject().put("id", requestId)
                            .put("type", "message").put("role", "assistant")
                            .put("model", normalized.model).put("content", new JSONArray())
                            .put("stop_reason", JSONObject.NULL).put("stop_sequence", JSONObject.NULL)
                            .put("usage", new JSONObject()
                                    .put("input_tokens", estimateTokens(completion.prompt))
                                    .put("output_tokens", 0).put("dsh_usage_estimated", true))));
            sendAnthropicEvent(out, "ping", new JSONObject().put("type", "ping"));
        } catch (Exception disconnectedDuringStart) {
            LocalApiDiagnostics.finish(requestId, "cancelled", "client_disconnected",
                    "Client disconnected during Responses stream setup");
            return;
        }
        z2.DeltaSink sink = new z2.DeltaSink() {
            private boolean emitText(String delta) throws Exception {
                if (disconnected.get() || delta == null || delta.length() == 0) return !disconnected.get();
                if (textBlock[0] < 0) {
                    textBlock[0] = nextBlock[0]++;
                    sendAnthropicEvent(out, "content_block_start",
                            new JSONObject().put("type", "content_block_start")
                                    .put("index", textBlock[0])
                                    .put("content_block", new JSONObject()
                                            .put("type", "text").put("text", "")));
                }
                streamedText.append(delta);
                sendAnthropicEvent(out, "content_block_delta",
                        new JSONObject().put("type", "content_block_delta")
                                .put("index", textBlock[0])
                                .put("delta", new JSONObject().put("type", "text_delta")
                                        .put("text", delta)));
                return true;
            }
            @Override public boolean onText(String delta) throws Exception {
                try { return emitText(delta); }
                catch (Exception failure) { disconnected.set(true); return false; }
            }
            @Override public boolean onReasoning(String delta) { return !disconnected.get(); }
            @Override public boolean isCancelled() { return disconnected.get(); }
        };
        try {
            z2.CompletionResult result = current.complete(completion, sink);
            if (result == null) throw new z2.GatewayException(502,
                    "empty_upstream_response", "Native model returned no result");
            if (disconnected.get()) {
                LocalApiDiagnostics.finish(requestId, "cancelled", "client_disconnected",
                        "Client disconnected while receiving Anthropic stream");
                return;
            }
            if (streamedText.length() == 0 && result.text.length() > 0) {
                sink.onText(result.text);
                if (disconnected.get()) return;
            }
            if (textBlock[0] >= 0) {
                sendAnthropicEvent(out, "content_block_stop",
                        new JSONObject().put("type", "content_block_stop")
                                .put("index", textBlock[0]));
            }
            String stopReason = "length".equals(result.finishReason) ? "max_tokens" : "end_turn";
            sendAnthropicEvent(out, "message_delta",
                    new JSONObject().put("type", "message_delta")
                            .put("delta", new JSONObject().put("stop_reason", stopReason)
                                    .put("stop_sequence", JSONObject.NULL))
                            .put("usage", new JSONObject().put("output_tokens",
                                    estimateTokens(streamedText.length() > 0
                                            ? streamedText.toString() : result.text))
                                    .put("dsh_usage_estimated", true)));
            sendAnthropicEvent(out, "message_stop", new JSONObject().put("type", "message_stop"));
            LocalApiDiagnostics.stage(requestId, "response_delivery", "succeeded", "",
                    "Anthropic SSE stream completed");
            LocalApiDiagnostics.finish(requestId, "succeeded", "", "Anthropic stream finished");
        } catch (Exception failure) {
            LocalApiDiagnostics.finish(requestId, disconnected.get() ? "cancelled" : "failed",
                    disconnected.get() ? "client_disconnected" : "upstream_error",
                    failure.getClass().getSimpleName());
            if (!disconnected.get()) {
                sendAnthropicEvent(out, "error", new JSONObject().put("type", "error")
                        .put("error", new JSONObject().put("type", "api_error")
                                .put("message", "Model execution failed")));
            }
        }
        if (!disconnected.get()) out.flush();
    }

    /**
     * Rough protocol-compatible usage estimate only. DeepSeek's tokenizer is not exposed by
     * z2.Backend; callers can distinguish these values through dsh_usage_estimated.
     */
    /**
     * Accepts numeric values and numeric strings emitted by some OpenAI-compatible clients.
     * Null means "use the gateway default"; positive values above the supported ceiling are
     * bounded to 8192. Fractional, zero, negative and non-numeric values remain invalid.
     * Never log the raw value or request body.
     */
    private static int normalizeMaxTokens(Object raw) {
        if (!(raw instanceof Number) && !(raw instanceof String)) return -1;
        String value = raw.toString().trim();
        if (value.length() == 0) return -1;
        final java.math.BigDecimal requested;
        try {
            requested = new java.math.BigDecimal(value);
        } catch (NumberFormatException invalid) {
            return -1;
        }
        if (requested.signum() <= 0 || requested.stripTrailingZeros().scale() > 0) {
            return -1;
        }
        if (requested.compareTo(java.math.BigDecimal.valueOf(8192L)) > 0) {
            return -1;
        }
        return requested.intValue();
    }

    private static int estimateTokens(String value) {
        if (value == null || value.length() == 0) return 0;
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint <= 0x7f && Character.isLetterOrDigit(codePoint)) {
                asciiRun++;
                continue;
            }
            if (asciiRun > 0) {
                tokens += (asciiRun + 3) / 4;
                asciiRun = 0;
            }
            if (!Character.isWhitespace(codePoint)) tokens++;
        }
        if (asciiRun > 0) tokens += (asciiRun + 3) / 4;
        return tokens;
    }

    private static void sendAnthropicEvent(OutputStream out, String event,
            JSONObject payload) throws Exception {
        byte[] bytes = ("event: " + event + "\ndata: " + payload.toString() + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        synchronized (out) { out.write(bytes); out.flush(); }
    }

    private static void sendAnthropicError(OutputStream out, int status, String type,
            String message) throws Exception {
        sendJson(out, status, new JSONObject().put("type", "error")
                .put("error", new JSONObject().put("type", type == null ? "api_error" : type)
                        .put("message", message == null ? "Request failed" : message)));
    }

    private static void handleResponses(OutputStream out, HttpRequest request)
            throws Exception {
        String contentType = request.headers.get("content-type");
        String mediaType = contentType == null ? ""
                : contentType.split(";", 2)[0].trim();
        if (contentType != null && !"application/json".equalsIgnoreCase(mediaType)) {
            sendError(out, 415, "unsupported_media_type",
                    "Content-Type must be application/json");
            return;
        }
        JSONObject body;
        try {
            body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
        } catch (Exception invalid) {
            sendError(out, 400, "invalid_json", "Request body must be valid JSON");
            return;
        }
        String unsupported = unsupportedChatOption(body);
        if (unsupported.length() > 0) {
            sendError(out, 400, "unsupported_parameter",
                    "This Responses endpoint does not support: " + unsupported);
            return;
        }
        String unsupportedFeatureName = unsupportedFeature(body);
        if (unsupportedFeatureName.length() > 0) {
            sendError(out, 400, "unsupported_feature",
                    "This Responses endpoint does not support: " + unsupportedFeatureName);
            return;
        }
        if (body.has("previous_response_id")
                && !(body.opt("previous_response_id") instanceof String)) {
            sendError(out, 400, "invalid_previous_response_id",
                    "previous_response_id must be a string");
            return;
        }
        String previousId = body.optString("previous_response_id", "").trim();
        if (previousId.length() > 128) {
            sendError(out, 400, "invalid_previous_response_id",
                    "previous_response_id is too long");
            return;
        }
        ResponseState previousState = previousId.length() == 0
                ? null : loadResponseState(previousId);
        if (previousId.length() > 0 && previousState == null) {
            sendError(out, 400, "previous_response_not_found",
                    "previous_response_id is unknown, expired, or was cleared by a service restart");
            return;
        }
        if (body.has("model") && !(body.opt("model") instanceof String)) {
            sendError(out, 400, "invalid_model", "model must be a string");
            return;
        }
        String requestedModel = body.optString("model", "deepseek-chat").trim();
        String model = "deepseek-flash".equals(requestedModel)
                ? "deepseek-chat" : requestedModel;
        if (!"deepseek-chat".equals(model) && !"deepseek-reasoner".equals(model)) {
            sendError(out, 400, "model_not_found", "Unsupported model: " + model);
            return;
        }
        if (body.has("stream") && !(body.opt("stream") instanceof Boolean)) {
            sendError(out, 400, "invalid_stream", "stream must be a boolean");
            return;
        }
        boolean stream = body.optBoolean("stream", false);
        Object rawInput = body.opt("input");
        if (!(rawInput instanceof String) && !(rawInput instanceof JSONArray)) {
            sendError(out, 400, "invalid_input", "input must be a string or an array");
            return;
        }
        String instructions = body.optString("instructions", "").trim();
        if (body.has("instructions") && !(body.opt("instructions") instanceof String)) {
            sendError(out, 400, "invalid_instructions", "instructions must be a string");
            return;
        }
        java.util.HashSet<String> pendingToolCallIds = previousState == null
                ? new java.util.HashSet<String>()
                : new java.util.HashSet<String>(previousState.pendingToolCallIds);
        String inputPrompt;
        try {
            inputPrompt = responsesInputToPrompt(rawInput, pendingToolCallIds);
        } catch (Exception invalid) {
            sendError(out, 400, "invalid_input", "Responses input contains unsupported or invalid items");
            return;
        }
        String basePrompt = previousState == null ? inputPrompt
                : previousState.transcript + (inputPrompt.length() == 0 ? "" : "\n" + inputPrompt);
        // Responses instructions are request-level system context. Keep them ahead of the
        // accumulated transcript so a follow-up request cannot demote them to the final turn.
        if (instructions.length() > 0) {
            basePrompt = "system: " + instructions
                    + (basePrompt.length() == 0 ? "" : "\n" + basePrompt);
        }
        if (basePrompt.trim().length() == 0 || basePrompt.length() > MAX_PROMPT_CHARS) {
            sendError(out, 400, "invalid_input", "input is empty or too large");
            return;
        }
        JSONObject textOptions = body.optJSONObject("text");
        if (body.has("text") && textOptions == null) {
            sendError(out, 400, "invalid_text_options", "text must be an object");
            return;
        }
        if (textOptions != null && textOptions.has("format")
                && textOptions.optJSONObject("format") == null) {
            sendError(out, 400, "invalid_response_format", "text.format must be an object");
            return;
        }
        JSONObject format = textOptions == null ? null : textOptions.optJSONObject("format");
        if (format != null && !"text".equals(format.optString("type", "text"))) {
            sendError(out, 400, "unsupported_response_format",
                    "Only text output format is currently supported");
            return;
        }
        JSONArray rawTools = body.optJSONArray("tools");
        if (body.has("tools") && body.opt("tools") != JSONObject.NULL && rawTools == null) {
            sendError(out, 400, "invalid_tools", "tools must be an array");
            return;
        }
        if (body.has("parallel_tool_calls")
                && !(body.opt("parallel_tool_calls") instanceof Boolean)) {
            sendError(out, 400, "invalid_parallel_tool_calls",
                    "parallel_tool_calls must be a boolean");
            return;
        }
        boolean searchRequested = false;
        if (rawTools != null) {
            for (int i = 0; i < rawTools.length(); i++) {
                JSONObject rawTool = rawTools.optJSONObject(i);
                if (rawTool == null) {
                    sendError(out, 400, "invalid_tool", "Every tools entry must be an object");
                    return;
                }
                String type = rawTool.optString("type", "function").toLowerCase(java.util.Locale.US);
                if (type.startsWith("web_search_") || "web_search".equals(type)) {
                    searchRequested = true;
                } else if (!"function".equals(type) && !"custom".equals(type)
                        && !"namespace".equals(type) && !"shell".equals(type)
                        && !"local_shell".equals(type) && !"apply_patch".equals(type)) {
                    sendError(out, 400, "unsupported_tool_type",
                            "Unsupported Responses tool type: " + type);
                    return;
                }
            }
        }
        OpenAiToolBridge.Plan toolPlan;
        try {
            toolPlan = OpenAiToolBridge.responsesPlan(
                    rawTools, body.opt("tool_choice"), body.optBoolean("parallel_tool_calls", true));
        } catch (z2.GatewayException invalidTools) {
            sendError(out, invalidTools.status, invalidTools.code, invalidTools.type,
                    invalidTools.getMessage());
            return;
        }
        for (OpenAiToolBridge.Definition definition : toolPlan.tools) {
            if (!OpenAiToolBridge.FUNCTION.equals(definition.kind)
                    && !OpenAiToolBridge.CUSTOM.equals(definition.kind)) {
                sendError(out, 400, "unsupported_tool_type",
                        "This Responses endpoint currently supports function and custom tools only");
                return;
            }
        }
        if (stream && toolPlan.active()) {
            sendError(out, 400, "unsupported_streaming_tool_calls",
                    "Responses tool calls are currently supported only with stream=false");
            return;
        }
        String requestId = "resp_" + UUID.randomUUID().toString().replace("-", "");
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        String prompt = OpenAiToolBridge.addInstructions(basePrompt, toolPlan, requestId);
        if (prompt.length() > MAX_PROMPT_CHARS) {
            sendError(out, 400, "invalid_input", "input with tool instructions is too large");
            return;
        }
        boolean reasoningRequested = "deepseek-reasoner".equals(model);
        if (body.has("reasoning")) {
            JSONObject reasoningOptions = body.optJSONObject("reasoning");
            if (reasoningOptions == null) {
                sendError(out, 400, "invalid_reasoning", "reasoning must be an object");
                return;
            }
            java.util.Iterator<String> reasoningKeys = reasoningOptions.keys();
            while (reasoningKeys.hasNext()) {
                if (!"effort".equals(reasoningKeys.next())) {
                    sendError(out, 400, "unsupported_reasoning_option",
                            "Only reasoning.effort is currently supported");
                    return;
                }
            }
            String effort = reasoningOptions.optString("effort", "medium")
                    .toLowerCase(java.util.Locale.US);
            if (!"none".equals(effort) && !"minimal".equals(effort)
                    && !"low".equals(effort) && !"medium".equals(effort)
                    && !"high".equals(effort) && !"xhigh".equals(effort)) {
                sendError(out, 400, "invalid_reasoning_effort",
                        "reasoning.effort must be none, minimal, low, medium, high, or xhigh");
                return;
            }
            reasoningRequested = reasoningRequested || !"none".equals(effort);
        }
        int maxTokens = 4096;
        if (body.has("max_output_tokens")) {
            Object raw = body.opt("max_output_tokens");
            if (!(raw instanceof Number)) {
                sendError(out, 400, "invalid_max_output_tokens",
                        "max_output_tokens must be an integer from 1 to 8192");
                return;
            }
            double value = ((Number) raw).doubleValue();
            if (value < 1 || value > 8192 || value != Math.rint(value)) {
                sendError(out, 400, "invalid_max_output_tokens",
                        "max_output_tokens must be an integer from 1 to 8192");
                return;
            }
            maxTokens = (int) value;
        }
        if (stream && toolPlan.active()) {
            sendError(out, 400, "unsupported_streaming_tool_calls",
                    "Responses streaming tool calls are not implemented yet; use stream=false");
            return;
        }
        z2.CompletionRequest completion = new z2.CompletionRequest(
                requestId, model, "default", basePrompt, prompt,
                reasoningRequested, searchRequested, maxTokens,
                toolPlan, previousId.length() == 0 ? null : previousId, true)
                .withOutputTextFormat(format);
        LocalApiDiagnostics.begin(requestId, "openai_responses", stream ? "stream" : "non_stream");
        LocalApiDiagnostics.inputShape(requestId, prompt.length(),
                rawInput instanceof JSONArray ? ((JSONArray) rawInput).length() : 1);
        LocalApiDiagnostics.stage(requestId, "http_validation", "succeeded", "",
                "HTTP request and authentication accepted");
        LocalApiDiagnostics.stage(requestId, "parameter_validation", "succeeded", "",
                "Responses input and generation options validated");
        z2.Backend current = backend;
        boolean ready = current != null && current.isReady();
        LocalApiDiagnostics.stage(requestId, "backend_readiness", ready ? "succeeded" : "failed",
                ready ? "" : "model_not_ready",
                "backend_present=" + (current != null) + " ready=" + ready);
        if (!ready) {
            sendError(out, 503, "model_not_ready",
                    current == null ? "Model backend unavailable" : current.readinessDetail());
            LocalApiDiagnostics.finish(requestId, "failed", "model_not_ready",
                    "Model backend unavailable");
            return;
        }
        if (stream) {
            streamResponsesText(out, current, completion, model, requestId, messageId, pendingToolCallIds);
            return;
        }
        long started = System.nanoTime();
        z2.CompletionResult result;
        try {
            result = current.complete(completion, null);
        } catch (z2.GatewayException rejected) {
            sendError(out, rejected.status, rejected.code, rejected.type, rejected.getMessage());
            LocalApiDiagnostics.finish(requestId, "failed", rejected.code,
                    rejected.getClass().getSimpleName());
            return;
        } catch (Exception failure) {
            sendError(out, 502, "upstream_error", "Native model execution failed");
            LocalApiDiagnostics.finish(requestId, "failed", "upstream_error",
                    failure.getClass().getSimpleName());
            return;
        }
        if (result == null) {
            sendError(out, 502, "empty_upstream_response", "Native model returned no result");
            LocalApiDiagnostics.finish(requestId, "failed", "empty_upstream_response",
                    "Native model returned no result");
            return;
        }
        List<OpenAiToolBridge.Call> calls = OpenAiToolBridge.parseCalls(result.text, toolPlan);
        if (toolPlan.active() && calls.isEmpty()
                && (toolPlan.choice.requiresCall()
                || OpenAiToolBridge.resemblesCallSyntax(result.text, toolPlan))) {
            sendError(out, 502, "invalid_tool_response",
                    "The model did not return a valid tool call matching the request schema");
            LocalApiDiagnostics.finish(requestId, "failed", "invalid_tool_response",
                    "Native output did not match the requested tool schema");
            return;
        }
        JSONArray output = new JSONArray();
        String outputText = calls.isEmpty() ? result.text : "";
        if (calls.isEmpty()) {
            JSONArray content = new JSONArray().put(new JSONObject()
                    .put("type", "output_text").put("text", result.text)
                    .put("annotations", new JSONArray()));
            output.put(new JSONObject().put("id", messageId).put("type", "message")
                    .put("status", "completed").put("role", "assistant")
                    .put("content", content));
        } else {
            for (OpenAiToolBridge.Call call : calls) {
                JSONObject item;
                if (OpenAiToolBridge.CUSTOM.equals(call.kind)) {
                    item = new JSONObject().put("id", call.itemId)
                            .put("type", "custom_tool_call").put("status", "completed")
                            .put("call_id", call.callId).put("name", call.name)
                            .put("input", call.customInput());
                } else {
                    item = new JSONObject().put("id", call.itemId)
                            .put("type", "function_call").put("status", "completed")
                            .put("call_id", call.callId).put("name", call.name)
                            .put("arguments", call.arguments);
                    if (call.namespace != null) item.put("namespace", call.namespace);
                }
                output.put(item);
            }
        }
        StringBuilder savedTranscript = new StringBuilder(basePrompt);
        java.util.HashSet<String> savedPendingCalls =
                new java.util.HashSet<String>(pendingToolCallIds);
        if (calls.isEmpty()) {
            if (result.text.length() > 0) savedTranscript.append("\nassistant: ").append(result.text);
        } else {
            for (OpenAiToolBridge.Call call : calls) {
                savedTranscript.append("\nassistant tool call id=").append(call.callId)
                        .append(" name=").append(call.name)
                        .append(" arguments=").append(call.arguments);
                savedPendingCalls.add(call.callId);
            }
        }
        rememberResponseState(requestId, savedTranscript.toString(), savedPendingCalls);
        JSONObject response = new JSONObject().put("id", requestId)
                .put("object", "response").put("created_at", System.currentTimeMillis() / 1000L)
                .put("status", "completed").put("error", JSONObject.NULL)
                .put("incomplete_details", JSONObject.NULL).put("model", model)
                .put("output", output).put("output_text", outputText);
        LocalApiDiagnostics.stage(requestId, "response_delivery", "running", "",
                "Writing Responses JSON response");
        sendJson(out, 200, response);
        LocalApiDiagnostics.stage(requestId, "response_delivery", "succeeded", "",
                "Responses JSON response delivered");
        LocalApiDiagnostics.finish(requestId, "succeeded", "", "Responses request finished");
        OpenNativeCompletionProbe.apiTrace("responses-complete requestId=" + requestId
                + " durationMs=" + ((System.nanoTime() - started) / 1000000L)
                + " outputItems=" + output.length());
    }

    private static String responsesInputToPrompt(Object input,
            java.util.Set<String> callIds) throws Exception {
        if (input instanceof String) return "user: " + (String) input;
        JSONArray items = (JSONArray) input;
        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) throw new IllegalArgumentException("Invalid input item");
            Object rawType = item.opt("type");
            if (rawType != null && !(rawType instanceof String)) {
                throw new IllegalArgumentException("Input item type must be a string");
            }
            String type = rawType == null ? "message" : (String) rawType;
            if ("message".equals(type)) {
                Object rawRole = item.opt("role");
                if (rawRole != null && !(rawRole instanceof String)) {
                    throw new IllegalArgumentException("Message role must be a string");
                }
                String role = rawRole == null ? "user" : (String) rawRole;
                if (!"system".equals(role) && !"developer".equals(role)
                        && !"user".equals(role) && !"assistant".equals(role)) {
                    throw new IllegalArgumentException("Invalid message role");
                }
                String value = messageText(item.opt("content"));
                if (value.trim().length() == 0) {
                    throw new IllegalArgumentException("Empty input message");
                }
                if (prompt.length() > 0) prompt.append("\n");
                prompt.append(role).append(": ").append(value);
            } else if ("function_call".equals(type) || "custom_tool_call".equals(type)) {
                Object rawCallId = item.opt("call_id");
                Object rawName = item.opt("name");
                Object rawArguments = "custom_tool_call".equals(type)
                        ? item.opt("input") : item.opt("arguments");
                if (!(rawCallId instanceof String) || !(rawName instanceof String)
                        || !(rawArguments instanceof String)) {
                    throw new IllegalArgumentException("Tool-call id, name, and arguments must be strings");
                }
                String callId = ((String) rawCallId).trim();
                String name = ((String) rawName).trim();
                String arguments = (String) rawArguments;
                if (callId.length() == 0 || name.length() == 0) {
                    throw new IllegalArgumentException("Invalid tool-call history");
                }
                if (!callIds.add(callId)) {
                    throw new IllegalArgumentException("Duplicate tool-call id");
                }
                if ("function_call".equals(type)) new JSONObject(arguments);
                if (prompt.length() > 0) prompt.append("\n");
                prompt.append("assistant tool call id=").append(callId)
                        .append(" name=").append(name).append(" arguments=").append(arguments);
            } else if ("function_call_output".equals(type)
                    || "custom_tool_call_output".equals(type)) {
                Object rawCallId = item.opt("call_id");
                if (!(rawCallId instanceof String)) {
                    throw new IllegalArgumentException("Tool-call output id must be a string");
                }
                String callId = ((String) rawCallId).trim();
                if (callId.length() == 0 || !callIds.remove(callId)) {
                    throw new IllegalArgumentException("Unmatched tool-call output");
                }
                Object rawOutput = item.opt("output");
                String value = rawOutput instanceof String ? (String) rawOutput
                        : rawOutput == null || rawOutput == JSONObject.NULL
                                ? "" : String.valueOf(rawOutput);
                if (prompt.length() > 0) prompt.append("\n");
                prompt.append("tool result for ").append(callId).append(": ").append(value);
            } else {
                throw new IllegalArgumentException("Unsupported Responses input item type");
            }
            if (prompt.length() > MAX_PROMPT_CHARS) {
                throw new IllegalArgumentException("Responses input exceeds the prompt size limit");
            }
        }
        return prompt.toString();
    }

    private static void streamResponsesText(OutputStream out, z2.Backend current,
            z2.CompletionRequest request, String model, String requestId, String messageId,
            java.util.Set<String> pendingToolCallIds) throws Exception {
        writeHeaders(out, 200, "text/event-stream; charset=utf-8");
        final AtomicBoolean disconnected = new AtomicBoolean(false);
        final StringBuilder streamed = new StringBuilder();
        final int[] sequence = {0};
        JSONObject item;
        JSONObject part;
        try {
            sendResponsesEvent(out, "response.created", ++sequence[0],
                    new JSONObject().put("response", responsesEnvelope(requestId, model,
                            "in_progress", new JSONArray(), "")));
            sendResponsesEvent(out, "response.in_progress", ++sequence[0],
                    new JSONObject().put("response", responsesEnvelope(requestId, model,
                            "in_progress", new JSONArray(), "")));
            item = new JSONObject().put("id", messageId).put("type", "message")
                    .put("status", "in_progress").put("role", "assistant")
                    .put("content", new JSONArray());
            sendResponsesEvent(out, "response.output_item.added", ++sequence[0],
                    new JSONObject().put("output_index", 0).put("item", item));
            part = new JSONObject().put("type", "output_text")
                    .put("text", "").put("annotations", new JSONArray());
            sendResponsesEvent(out, "response.content_part.added", ++sequence[0],
                    new JSONObject().put("item_id", messageId).put("output_index", 0)
                            .put("content_index", 0).put("part", part));
        } catch (Exception disconnectedDuringStart) {
            LocalApiDiagnostics.finish(requestId, "cancelled", "client_disconnected",
                    "Client disconnected during Anthropic stream setup");
            return;
        }
        z2.DeltaSink sink = new z2.DeltaSink() {
            @Override public boolean onText(String delta) throws Exception {
                if (disconnected.get()) return false;
                try {
                    streamed.append(delta);
                    sendResponsesEvent(out, "response.output_text.delta", ++sequence[0],
                            new JSONObject().put("item_id", messageId).put("output_index", 0)
                                    .put("content_index", 0).put("delta", delta));
                    return true;
                } catch (Exception failure) {
                    disconnected.set(true);
                    return false;
                }
            }
            @Override public boolean onReasoning(String delta) { return !disconnected.get(); }
            @Override public boolean isCancelled() { return disconnected.get(); }
        };
        try {
            z2.CompletionResult result = current.complete(request, sink);
            if (result == null) throw new z2.GatewayException(502,
                    "empty_upstream_response", "Native model returned no result");
            if (disconnected.get()) {
                LocalApiDiagnostics.finish(requestId, "cancelled", "client_disconnected",
                        "Client disconnected while receiving Responses stream");
                return;
            }
            String finalText = streamed.length() > 0 ? streamed.toString() : result.text;
            sendResponsesEvent(out, "response.output_text.done", ++sequence[0],
                    new JSONObject().put("item_id", messageId).put("output_index", 0)
                            .put("content_index", 0).put("text", finalText));
            JSONObject finalPart = new JSONObject().put("type", "output_text")
                    .put("text", finalText).put("annotations", new JSONArray());
            sendResponsesEvent(out, "response.content_part.done", ++sequence[0],
                    new JSONObject().put("item_id", messageId).put("output_index", 0)
                            .put("content_index", 0).put("part", finalPart));
            JSONObject finalItem = new JSONObject().put("id", messageId).put("type", "message")
                    .put("status", "completed").put("role", "assistant")
                    .put("content", new JSONArray().put(finalPart));
            sendResponsesEvent(out, "response.output_item.done", ++sequence[0],
                    new JSONObject().put("output_index", 0).put("item", finalItem));
            sendResponsesEvent(out, "response.completed", ++sequence[0],
                    new JSONObject().put("response", responsesEnvelope(requestId, model,
                            "completed", new JSONArray().put(finalItem), finalText)));
            rememberResponseState(requestId,
                    request.basePrompt + (finalText.length() == 0 ? "" : "\nassistant: " + finalText),
                    pendingToolCallIds);
            LocalApiDiagnostics.stage(requestId, "response_delivery", "succeeded", "",
                    "Responses SSE stream completed");
            LocalApiDiagnostics.finish(requestId, "succeeded", "", "Responses stream finished");
        } catch (Exception failure) {
            LocalApiDiagnostics.finish(requestId, disconnected.get() ? "cancelled" : "failed",
                    disconnected.get() ? "client_disconnected" : "upstream_error",
                    failure.getClass().getSimpleName());
            if (!disconnected.get()) {
                JSONObject error = new JSONObject().put("message", "Model execution failed")
                        .put("type", "server_error").put("code", "upstream_error");
                sendResponsesEvent(out, "response.failed", ++sequence[0],
                        new JSONObject().put("response", responsesEnvelope(requestId, model,
                                "failed", new JSONArray(), "")
                                .put("error", error)));
            }
        }
        if (!disconnected.get()) out.flush();
    }

    private static JSONObject responsesEnvelope(String id, String model, String status,
            JSONArray output, String outputText) throws Exception {
        return new JSONObject().put("id", id).put("object", "response")
                .put("created_at", System.currentTimeMillis() / 1000L)
                .put("status", status).put("error", JSONObject.NULL)
                .put("incomplete_details", JSONObject.NULL).put("model", model)
                .put("output", output).put("output_text", outputText);
    }

    private static void sendResponsesEvent(OutputStream out, String type, int sequence,
            JSONObject payload) throws Exception {
        payload.put("type", type).put("sequence_number", sequence);
        byte[] bytes = ("event: " + type + "\ndata: " + payload.toString() + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        synchronized (out) { out.write(bytes); out.flush(); }
    }

    private static void streamCompletion(OutputStream out, z2.Backend current,
            z2.CompletionRequest request, String model, String requestId,
            boolean includeUsage) throws Exception {
        writeHeaders(out, 200, "text/event-stream; charset=utf-8");
        final long streamStartedNanos = System.nanoTime();
        final AtomicBoolean disconnected = new AtomicBoolean(false);
        final AtomicBoolean firstDelta = new AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicInteger eventCount =
                new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger textChars =
                new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger reasoningChars =
                new java.util.concurrent.atomic.AtomicInteger();
        LocalApiDiagnostics.stage(requestId, "response_delivery", "running", "",
                "Streaming response to client");
        OpenNativeCompletionProbe.apiTrace("completion-start requestId=" + requestId
                + " mode=stream");
        z2.DeltaSink sink = new z2.DeltaSink() {
            private void send(String field, String content) throws Exception {
                if (disconnected.get() || content == null || content.length() == 0) return;
                eventCount.incrementAndGet();
                if ("content".equals(field)) textChars.addAndGet(content.length());
                else if ("reasoning_content".equals(field)) reasoningChars.addAndGet(content.length());
                if (firstDelta.compareAndSet(false, true)) {
                    OpenNativeCompletionProbe.apiTrace("first-delta requestId=" + requestId
                            + " field=" + field + " elapsedMs="
                            + ((System.nanoTime() - streamStartedNanos) / 1000000L));
                }
                JSONObject delta = new JSONObject().put(field, content);
                JSONObject choice = new JSONObject().put("index", 0).put("delta", delta)
                        .put("finish_reason", JSONObject.NULL);
                JSONObject event = new JSONObject().put("id", requestId)
                        .put("object", "chat.completion.chunk").put("created", System.currentTimeMillis()/1000)
                        .put("model", model).put("choices", new JSONArray().put(choice));
                byte[] bytes = ("data: " + event.toString() + "\n\n")
                        .getBytes(StandardCharsets.UTF_8);
                synchronized (out) { out.write(bytes); out.flush(); }
            }
            @Override public boolean onText(String delta) throws Exception {
                try { send("content", delta); return !disconnected.get(); }
                catch (Exception failure) {
                    disconnected.set(true);
                    OpenNativeCompletionProbe.apiTrace("stream-write-failed requestId=" + requestId
                            + " field=content exception=" + failure.getClass().getName());
                    return false;
                }
            }
            @Override public boolean onReasoning(String delta) {
                try { send("reasoning_content", delta); return !disconnected.get(); }
                catch (Exception failure) {
                    disconnected.set(true);
                    OpenNativeCompletionProbe.apiTrace("stream-write-failed requestId=" + requestId
                            + " field=reasoning_content exception=" + failure.getClass().getName());
                    return false;
                }
            }
            @Override public boolean isCancelled() { return disconnected.get(); }
        };
        try {
            z2.CompletionResult result = current.complete(request, sink);
            if (result == null) {
                OpenNativeCompletionProbe.apiTrace("stream-empty requestId=" + requestId
                        + " durationMs=" + ((System.nanoTime() - streamStartedNanos) / 1000000L));
                throw new z2.GatewayException(502, "empty_upstream_response",
                        "server_error", "Native model returned no result");
            }
            if (disconnected.get()) {
                OpenNativeCompletionProbe.apiTrace("stream-disconnected requestId=" + requestId
                        + " events=" + eventCount.get()
                        + " durationMs=" + ((System.nanoTime() - streamStartedNanos) / 1000000L));
            } else {
                JSONObject choice = new JSONObject().put("index", 0)
                        .put("delta", new JSONObject()).put("finish_reason", result.finishReason);
                JSONObject finalEvent = new JSONObject().put("id", requestId)
                        .put("object", "chat.completion.chunk").put("created", System.currentTimeMillis()/1000)
                        .put("model", model).put("choices", new JSONArray().put(choice));
                out.write(("data: " + finalEvent.toString() + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                if (includeUsage) {
                    int promptTokens = estimateTokens(request.prompt);
                    int completionTokens = estimateTokens(result.text)
                            + estimateTokens(result.reasoning);
                    JSONObject usage = new JSONObject().put("prompt_tokens", promptTokens)
                            .put("completion_tokens", completionTokens)
                            .put("total_tokens", promptTokens + completionTokens)
                            .put("dsh_usage_estimated", true);
                    JSONObject usageChunk = new JSONObject().put("id", requestId)
                            .put("object", "chat.completion.chunk")
                            .put("created", System.currentTimeMillis() / 1000L)
                            .put("model", model).put("choices", new JSONArray())
                            .put("usage", usage);
                    out.write(("data: " + usageChunk.toString() + "\n\n")
                            .getBytes(StandardCharsets.UTF_8));
                }
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                LocalApiDiagnostics.stage(requestId, "response_delivery", "succeeded", "",
                        "SSE stream completed");
                LocalApiDiagnostics.finish(requestId, "succeeded", "", "Completion stream finished");
                OpenNativeCompletionProbe.apiTrace("stream-complete requestId=" + requestId
                        + " events=" + eventCount.get()
                        + " textChars=" + textChars.get()
                        + " reasoningChars=" + reasoningChars.get()
                        + " durationMs=" + ((System.nanoTime() - streamStartedNanos) / 1000000L)
                        + " finishReason=" + result.finishReason);
            }
        } catch (Exception failure) {
            boolean gatewayFailure = failure instanceof z2.GatewayException;
            z2.GatewayException traceRejected = gatewayFailure
                    ? (z2.GatewayException) failure : null;
            LocalApiDiagnostics.finish(requestId,
                    failure instanceof java.util.concurrent.CancellationException
                            ? "cancelled" : "failed",
                    gatewayFailure ? traceRejected.code : "upstream_error",
                    failure.getClass().getSimpleName());
            OpenNativeCompletionProbe.apiTrace("stream-failed requestId=" + requestId
                    + " status=" + (gatewayFailure ? traceRejected.status : 502)
                    + " code=" + (gatewayFailure ? traceRejected.code : "upstream_error")
                    + " exception=" + failure.getClass().getName()
                    + " events=" + eventCount.get()
                    + " textChars=" + textChars.get()
                    + " reasoningChars=" + reasoningChars.get()
                    + " durationMs=" + ((System.nanoTime() - streamStartedNanos) / 1000000L));
            if (!disconnected.get()) {
                z2.GatewayException rejected = gatewayFailure
                        ? (z2.GatewayException) failure : null;
                JSONObject error = new JSONObject().put("error",
                        new JSONObject()
                                .put("message", gatewayFailure && rejected.getMessage() != null
                                        ? rejected.getMessage() : "Model execution failed")
                                .put("type", gatewayFailure ? rejected.type : "server_error")
                                .put("code", gatewayFailure ? rejected.code : "upstream_error"));
                out.write(("data: " + error.toString() + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private static String toPrompt(JSONArray messages) throws Exception {
        if (messages == null || messages.length() == 0) return "";
        StringBuilder prompt = new StringBuilder();
        java.util.HashSet<String> pendingToolCallIds = new java.util.HashSet<String>();
        java.util.HashSet<String> seenToolCallIds = new java.util.HashSet<String>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) throw new IllegalArgumentException("Invalid message");
            Object rawRole = message.opt("role");
            if (!(rawRole instanceof String)) {
                throw new IllegalArgumentException("Message role must be a string");
            }
            String role = (String) rawRole;
            if (!"system".equals(role) && !"developer".equals(role)
                    && !"user".equals(role) && !"assistant".equals(role)
                    && !"tool".equals(role)) {
                throw new IllegalArgumentException("Unsupported message role");
            }
            if (message.has("name")) {
                throw new IllegalArgumentException("Named messages are not supported");
            }
            if ("tool".equals(role)) {
                Object rawToolCallId = message.opt("tool_call_id");
                if (!(rawToolCallId instanceof String)) {
                    throw new IllegalArgumentException("Tool result call id must be a string");
                }
                String callId = ((String) rawToolCallId).trim();
                if (callId.length() == 0 || !pendingToolCallIds.remove(callId)) {
                    throw new IllegalArgumentException("Tool result does not match a preceding assistant tool call");
                }
                String toolResult = messageText(message.opt("content"));
                if (toolResult.trim().length() == 0) {
                    throw new IllegalArgumentException("Tool result content is empty");
                }
                if (prompt.length() > 0) prompt.append("\n");
                prompt.append("tool result for ").append(callId).append(": ").append(toolResult);
                continue;
            }
            if (message.has("tool_call_id") || message.has("function_call")) {
                throw new IllegalArgumentException("Invalid tool-call history role or legacy function_call");
            }
            Object content = message.opt("content");
            String text = content == null || content == JSONObject.NULL
                    ? "" : messageText(content);
            JSONArray calls = message.optJSONArray("tool_calls");
            if (message.has("tool_calls") && calls == null) {
                throw new IllegalArgumentException("tool_calls must be an array");
            }
            if (message.has("tool_calls") && !"assistant".equals(role)) {
                throw new IllegalArgumentException("Only assistant messages may contain tool_calls");
            }
            if (text.trim().length() == 0 && (calls == null || calls.length() == 0)) {
                throw new IllegalArgumentException("Message content is empty");
            }
            if (text.length() > 0) {
                if (prompt.length() > 0) prompt.append("\n");
                prompt.append(role).append(": ").append(text);
            }
            if (calls != null) {
                if (calls.length() > 32) {
                    throw new IllegalArgumentException("Too many historical tool calls");
                }
                for (int callIndex = 0; callIndex < calls.length(); callIndex++) {
                    JSONObject call = calls.optJSONObject(callIndex);
                    JSONObject function = call == null ? null : call.optJSONObject("function");
                    Object rawId = call == null ? null : call.opt("id");
                    Object rawName = function == null ? null : function.opt("name");
                    Object rawType = call == null ? null : call.opt("type");
                    String id = rawId instanceof String ? ((String) rawId).trim() : "";
                    String name = rawName instanceof String ? ((String) rawName).trim() : "";
                    Object rawArguments = function == null ? null : function.opt("arguments");
                    if (call == null || function == null || !(rawId instanceof String)
                            || !(rawName instanceof String) || id.length() == 0 || id.length() > 128
                            || name.length() == 0 || name.length() > 128
                            || (rawType != null && (!(rawType instanceof String)
                            || !"function".equals(rawType)))
                            || !(rawArguments instanceof String) || !seenToolCallIds.add(id)) {
                        throw new IllegalArgumentException("Invalid assistant tool-call history");
                    }
                    String arguments = (String) rawArguments;
                    new JSONObject(arguments);
                    pendingToolCallIds.add(id);
                    if (prompt.length() > 0) prompt.append("\n");
                    prompt.append("assistant tool call id=").append(id)
                            .append(" name=").append(name)
                            .append(" arguments=").append(arguments);
                }
            }
            if (prompt.length() > MAX_PROMPT_CHARS) {
                throw new IllegalArgumentException("Chat messages exceed the prompt size limit");
            }
        }
        return prompt.toString();
    }

    /** Returns the first unsupported non-default Chat Completions option, or empty. */
    private static String unsupportedChatOption(JSONObject body) {
        String[] unsupported = new String[] {
                "temperature", "top_p", "stop", "presence_penalty",
                "frequency_penalty", "logit_bias", "seed", "user",
                "logprobs", "top_logprobs", "modalities", "audio",
                "prediction", "service_tier", "store", "metadata"
        };
        for (String key : unsupported) {
            if (!body.has(key)) continue;
            Object value = body.opt(key);
            if (("temperature".equals(key) && value instanceof Number
                    && ((Number) value).doubleValue() == 1.0)
                    || ("top_p".equals(key) && value instanceof Number
                    && ((Number) value).doubleValue() == 1.0)
                    || (("presence_penalty".equals(key) || "frequency_penalty".equals(key))
                    && value instanceof Number && ((Number) value).doubleValue() == 0.0)
                    || ("stop".equals(key) && value == JSONObject.NULL)
                    || ("logprobs".equals(key) && Boolean.FALSE.equals(value))
                    || ("top_logprobs".equals(key) && value instanceof Number
                    && ((Number) value).intValue() == 0)) {
                continue;
            }
            return key;
        }
        if (body.has("n")) {
            Object n = body.opt("n");
            if (!(n instanceof Number) || ((Number) n).doubleValue() != 1.0) return "n";
        }
        return "";
    }

    /** Returns the first actually requested unsupported feature, ignoring harmless defaults. */
    private static String unsupportedFeature(JSONObject body) {
        Object functions = body.opt("functions");
        if (functions != null && functions != JSONObject.NULL) {
            if (!(functions instanceof JSONArray) || ((JSONArray) functions).length() > 0) {
                return "functions";
            }
        }
        Object functionCall = body.opt("function_call");
        if (functionCall != null && functionCall != JSONObject.NULL
                && !(functionCall instanceof String && "none".equals(functionCall))) {
            return "function_call";
        }
        Object responseFormat = body.opt("response_format");
        if (responseFormat != null && responseFormat != JSONObject.NULL) {
            JSONObject format = body.optJSONObject("response_format");
            if (format == null || !"text".equals(format.optString("type", ""))) {
                return "response_format";
            }
        }
        Object streamOptions = body.opt("stream_options");
        if (streamOptions != null && streamOptions != JSONObject.NULL) {
            JSONObject options = body.optJSONObject("stream_options");
            if (options == null) return "stream_options";
            Object includeUsage = options.opt("include_usage");
            // include_usage is implemented by streamCompletion(): accept either boolean
            // value and reject only malformed types.
            if (includeUsage != null && includeUsage != JSONObject.NULL
                    && !(includeUsage instanceof Boolean)) {
                return "stream_options.include_usage";
            }
            java.util.Iterator<String> keys = options.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!"include_usage".equals(key)) return "stream_options." + key;
            }
        }
        return "";
    }

    private static String messageText(Object content) throws Exception {
        if (content instanceof String) return (String) content;
        if (!(content instanceof JSONArray)) {
            throw new IllegalArgumentException("Message content must be text or text parts");
        }
        JSONArray parts = (JSONArray) content;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) throw new IllegalArgumentException("Invalid message content part");
            Object rawType = part.opt("type");
            if (!(rawType instanceof String)) {
                throw new IllegalArgumentException("Content part type must be a string");
            }
            String type = (String) rawType;
            if (!"text".equals(type) && !"input_text".equals(type) && !"output_text".equals(type)) {
                throw new IllegalArgumentException("Only text content parts are supported");
            }
            Object value = part.has("text") ? part.opt("text") : part.opt("input_text");
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Text content part is missing text");
            }
            if (text.length() > 0) text.append("\n");
            text.append((String) value);
            if (text.length() > MAX_PROMPT_CHARS) {
                throw new IllegalArgumentException("Message content exceeds the prompt size limit");
            }
        }
        return text.toString();
    }

    private static boolean isHttpToken(String value) {
        return value != null && value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    }

    private static boolean isHttpFieldValue(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7f) return false;
        }
        return true;
    }

    private static boolean isRequestTarget(String value) {
        if (value == null || value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c >= 0x7f) return false;
        }
        return value.startsWith("/");
    }

    private static boolean isDecimalDigits(String value) {
        if (value == null || value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }

    private static boolean isHexDigits(String value) {
        if (value == null || value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static HttpRequest readRequest(InputStream in) throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (header.size() < MAX_HEADER_BYTES) {
            int value = in.read();
            if (value < 0) return null;
            header.write(value);
            if ((matched == 0 && value == '\r') || (matched == 2 && value == '\r')) matched++;
            else if ((matched == 1 && value == '\n') || (matched == 3 && value == '\n')) {
                matched++;
                if (matched == 4) break;
            } else matched = value == '\r' ? 1 : 0;
        }
        if (matched != 4) throw new IllegalArgumentException("Invalid HTTP headers");
        String[] lines = new String(header.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] first = lines[0].split(" ", -1);
        if (first.length != 3 || !isHttpToken(first[0]) || !isRequestTarget(first[1])
                || (!"HTTP/1.0".equals(first[2]) && !"HTTP/1.1".equals(first[2]))) {
            throw new IllegalArgumentException("Invalid request line");
        }
        java.util.Map<String,String> headers = new java.util.HashMap<>();
        for (int i=1;i<lines.length;i++) {
            if (lines[i].length() == 0) continue;
            int colon=lines[i].indexOf(':');
            if(colon<=0) throw new IllegalArgumentException("Invalid HTTP header");
            String name = lines[i].substring(0,colon)
                    .toLowerCase(java.util.Locale.ROOT);
            String rawValue = lines[i].substring(colon+1);
            if (!isHttpToken(name) || !isHttpFieldValue(rawValue) || headers.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate or empty HTTP header");
            }
            headers.put(name, rawValue.trim());
        }
        String transferEncoding = headers.get("transfer-encoding");
        String contentLength = headers.get("content-length");
        if (transferEncoding != null && contentLength != null) {
            throw new IllegalArgumentException("Ambiguous request framing");
        }
        byte[] body;
        if (transferEncoding != null) {
            if (!"HTTP/1.1".equals(first[2])) {
                throw new IllegalArgumentException("Transfer encoding requires HTTP/1.1");
            }
            if (!"chunked".equalsIgnoreCase(transferEncoding.trim())) {
                throw new IllegalArgumentException("Unsupported transfer encoding");
            }
            body = readChunkedBody(in);
        } else {
            int length;
            if (contentLength != null && !isDecimalDigits(contentLength)) {
                throw new IllegalArgumentException("Invalid content length");
            }
            try {
                length = Integer.parseInt(contentLength == null ? "0" : contentLength);
            } catch (NumberFormatException invalidLength) {
                throw new IllegalArgumentException("Invalid content length");
            }
            if (length < 0 || length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Request too large");
            }
            body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = in.read(body, offset, length - offset);
                if (count < 0) throw new IllegalArgumentException("Truncated request body");
                offset += count;
            }
        }
        String path = first[1];
        int queryStart = path.indexOf('?');
        if (queryStart >= 0) path = path.substring(0, queryStart);
        if (!path.startsWith("/")) throw new IllegalArgumentException("Invalid request target");
        return new HttpRequest(first[0], path, headers, body);
    }

    /**
     * Reads HTTP/1.1 chunked transfer bodies with strict CRLF framing and a hard decoded-size
     * limit. Chunk extensions are ignored; trailers are consumed but never trusted as headers.
     */
    private static byte[] readChunkedBody(InputStream in) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int trailerBytes = 0;
        while (true) {
            String sizeLine = readCrlfLine(in, 1024);
            int extension = sizeLine.indexOf(';');
            String sizeText = extension >= 0 ? sizeLine.substring(0, extension) : sizeLine;
            if (sizeText.length() > 16 || !isHexDigits(sizeText)) {
                throw new IllegalArgumentException("Invalid chunk size");
            }
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeText, 16);
            } catch (NumberFormatException invalidSize) {
                throw new IllegalArgumentException("Invalid chunk size");
            }
            if (chunkSize < 0 || chunkSize > MAX_BODY_BYTES - body.size()) {
                throw new IllegalArgumentException("Request too large");
            }
            if (chunkSize == 0) {
                while (true) {
                    String trailer = readCrlfLine(in, 4096);
                    trailerBytes += trailer.length() + 2;
                    if (trailerBytes > MAX_HEADER_BYTES) {
                        throw new IllegalArgumentException("Request trailers too large");
                    }
                    if (trailer.length() == 0) break;
                    int colon = trailer.indexOf(':');
                    if (colon <= 0) throw new IllegalArgumentException("Invalid request trailer");
                    String name = trailer.substring(0, colon)
                            .toLowerCase(java.util.Locale.ROOT);
                    String trailerValue = trailer.substring(colon + 1);
                    if (!isHttpToken(name) || !isHttpFieldValue(trailerValue)) {
                        throw new IllegalArgumentException("Invalid request trailer");
                    }
                    if ("content-length".equals(name) || "transfer-encoding".equals(name)
                            || "host".equals(name) || "authorization".equals(name)) {
                        throw new IllegalArgumentException("Forbidden request trailer");
                    }
                }
                return body.toByteArray();
            }
            byte[] chunk = new byte[(int) chunkSize];
            int offset = 0;
            while (offset < chunk.length) {
                int count = in.read(chunk, offset, chunk.length - offset);
                if (count < 0) throw new IllegalArgumentException("Truncated chunk data");
                offset += count;
            }
            body.write(chunk);
            if (in.read() != '\r' || in.read() != '\n') {
                throw new IllegalArgumentException("Invalid chunk terminator");
            }
        }
    }

    private static String readCrlfLine(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() <= maxBytes) {
            int value = in.read();
            if (value < 0) throw new IllegalArgumentException("Truncated chunk framing");
            if (value == '\r') {
                if (in.read() != '\n') throw new IllegalArgumentException("Invalid chunk line ending");
                return new String(line.toByteArray(), StandardCharsets.US_ASCII);
            }
            if (value == '\n' || value < 0x20 && value != '\t') {
                throw new IllegalArgumentException("Invalid chunk line");
            }
            line.write(value);
        }
        throw new IllegalArgumentException("Chunk line too large");
    }

    private static String bearer(String value) {
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) return "";
        return value.substring(7).trim();
    }
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        int n = Math.max(a.length(), b.length());
        for (int i=0;i<n;i++) diff |= (i<a.length()?a.charAt(i):0) ^ (i<b.length()?b.charAt(i):0);
        return diff == 0;
    }
    private static String loadOrCreateToken(Context context) throws Exception {
        File file = new File(context.getFilesDir(), TOKEN_FILE);
        if (file.isFile()) {
            ByteArrayOutputStream stored = new ByteArrayOutputStream();
            try (InputStream input = new AtomicFile(file).openRead()) {
                byte[] buffer = new byte[128];
                int count;
                while ((count = input.read(buffer)) >= 0 && stored.size() < 256) {
                    stored.write(buffer, 0, count);
                }
            }
            String existing = new String(stored.toByteArray(), StandardCharsets.US_ASCII).trim();
            if (isValidApiKey(existing)) return existing;
        }
        String value = generateToken();
        persistToken(context, value);
        return value;
    }
    private static boolean isValidApiKey(String value) {
        if (value == null || value.length() < 8 || value.length() > 256) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x21 || c > 0x7e) return false;
        }
        return true;
    }

    private static String generateToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return "sk-" + android.util.Base64.encodeToString(random,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP
                        | android.util.Base64.NO_PADDING);
    }
    private static void persistToken(Context context, String value) throws Exception {
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), TOKEN_FILE));
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(value.getBytes(StandardCharsets.US_ASCII));
            stream.getFD().sync();
            atomic.finishWrite(stream);
        } catch (Exception failure) {
            atomic.failWrite(stream);
            throw failure;
        }
    }

    private static void sendJson(OutputStream out, int status, JSONObject body) throws Exception {
        writeHeaders(out, status, "application/json; charset=utf-8");
        out.write(body.toString().getBytes(StandardCharsets.UTF_8)); out.flush();
    }
    private static void sendError(OutputStream out, int status, String code, String message)
            throws Exception {
        String type = status >= 500 ? "server_error"
                : status == 401 ? "authentication_error"
                : status == 403 ? "permission_error" : "invalid_request_error";
        sendError(out, status, code, type, message);
    }

    private static void sendError(OutputStream out, int status, String code, String type,
                                  String message) throws Exception {
        sendJson(out, status, new JSONObject().put("error", new JSONObject()
                .put("message", message == null ? "Request failed" : message)
                .put("type", type == null ? "server_error" : type)
                .put("code", code == null ? "request_failed" : code)));
    }
    private static void writeHeaders(OutputStream out, int status, String type) throws Exception {
        String reason = status == 200 ? "OK" : status == 400 ? "Bad Request"
                : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden"
                : status == 404 ? "Not Found" : status == 405 ? "Method Not Allowed"
                : status == 413 ? "Payload Too Large" : status == 415 ? "Unsupported Media Type"
                : status == 429 ? "Too Many Requests"
                : status == 502 ? "Bad Gateway" : status == 503 ? "Service Unavailable"
                : "Internal Server Error";
        out.write(("HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Cache-Control: no-cache, no-store\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static synchronized ResponseState loadResponseState(String id) {
        long now = SystemClock.elapsedRealtime();
        java.util.Iterator<java.util.Map.Entry<String, ResponseState>> iterator =
                RESPONSE_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            ResponseState state = iterator.next().getValue();
            if (now - state.updatedAtElapsedMs > RESPONSE_STATE_TTL_MS) iterator.remove();
        }
        return RESPONSE_STATES.get(id);
    }

    private static synchronized void rememberResponseState(String id, String transcript,
            java.util.Set<String> pendingToolCallIds) {
        if (id == null || transcript == null || transcript.length() > MAX_RESPONSE_STATE_CHARS) {
            return;
        }
        RESPONSE_STATES.put(id, new ResponseState(transcript, pendingToolCallIds));
        while (RESPONSE_STATES.size() > MAX_RESPONSE_STATES) {
            java.util.Iterator<String> iterator = RESPONSE_STATES.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private static final class ResponseState {
        final String transcript;
        final java.util.HashSet<String> pendingToolCallIds;
        final long updatedAtElapsedMs;

        ResponseState(String transcript, java.util.Set<String> pendingToolCallIds) {
            this.transcript = transcript;
            this.pendingToolCallIds = pendingToolCallIds == null
                    ? new java.util.HashSet<String>()
                    : new java.util.HashSet<String>(pendingToolCallIds);
            this.updatedAtElapsedMs = SystemClock.elapsedRealtime();
        }
    }

    private static final class HttpRequest {
        final String method, path;
        final java.util.Map<String,String> headers;
        final byte[] body;
        HttpRequest(String method, String path, java.util.Map<String,String> headers, byte[] body) {
            this.method=method; this.path=path; this.headers=headers; this.body=body;
        }
    }
}
