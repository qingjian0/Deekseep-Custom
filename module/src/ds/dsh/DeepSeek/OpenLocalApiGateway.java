package ds.dsh.DeepSeek;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
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
    private static volatile z2.Backend backend;
    private static volatile Context appContext;
    private static volatile String token;
    private static volatile int boundPort;
    private static volatile int configuredPort = DEFAULT_PORT;
    private static volatile boolean lanEnabled;
    private static volatile ConnectivityManager networkManager;
    private static volatile ConnectivityManager.NetworkCallback networkCallback;

    private OpenLocalApiGateway() {}

    static synchronized void start(Context context, z2.Backend modelBackend) {
        if (context == null || modelBackend == null || RUNNING.get()) return;
        try {
            Context app = context.getApplicationContext();
            appContext = app;
            token = loadOrCreateToken(app);
            backend = modelBackend;
            android.content.SharedPreferences prefs = app.getSharedPreferences(
                    "dsh_local_api", Context.MODE_PRIVATE);
            configuredPort = prefs.getInt("port", DEFAULT_PORT);
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
            Main.log("Open Local API listening on 127.0.0.1:" + boundPort
                    + (boundLanAddress.length() == 0 ? ""
                    : " and " + boundLanAddress + ":" + boundPort));
        } catch (Exception error) {
            RUNNING.set(false);
            closeServer();
            Main.log("Open Local API start failed: " + error.getClass().getSimpleName());
        }
    }

    static synchronized void stop() {
        RUNNING.set(false);
        unregisterLanNetworkCallback();
        closeServer();
        lanBindingStatus = lanEnabled ? "已停止" : "未启用";
        ExecutorService pool = clients;
        clients = null;
        if (pool != null) pool.shutdownNow();
        backend = null;
        boundPort = 0;
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
        try {
            app.getSharedPreferences("dsh_local_api", Context.MODE_PRIVATE)
                    .edit().putInt("port", requestedPort).apply();
            configuredPort = requestedPort;
            if (RUNNING.get() && current != null) {
                stop();
                start(app, current);
                if (!RUNNING.get()) return "端口已保存，但服务启动失败；请检查端口占用";
            }
            return null;
        } catch (Exception error) {
            return "端口设置失败";
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

    private static String firstLanAddress() {
        Context app = appContext;
        if (app == null) return "";
        try {
            ConnectivityManager manager = (ConnectivityManager) app.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "";
            Network active = manager.getActiveNetwork();
            String activeAddress = active == null ? ""
                    : lanAddress(manager.getLinkProperties(active));
            if (activeAddress.length() > 0) return activeAddress;

            // A VPN may be Android's active/default network while Wi-Fi still owns the
            // reachable 192.168.x.x interface. Search all live networks as a fallback.
            for (Network network : manager.getAllNetworks()) {
                if (network.equals(active)) continue;
                String address = lanAddress(manager.getLinkProperties(network));
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
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback);
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
        if (!value.matches("sk-[A-Za-z0-9_-]{40,80}")) {
            return "本地 API Key 必须以 sk- 开头，后接 40 到 80 位字母、数字、下划线或连字符";
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
        if (!RUNNING.get()) return "本地 API 服务尚未启动";
        return "本机地址：" + baseUrl() + "\n"
                + "聊天接口：" + endpoint() + "/v1/chat/completions\n"
                + "本地 API Key：" + apiKey() + "\n"
                + "可用模型：deepseek-chat、deepseek-reasoner\n"
                + (lanEnabled
                        ? (boundLanAddress.length() > 0
                                ? "局域网地址：" + lanEndpoint() + "\n" : "")
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
                + ":" + boundPort : "Stopped";
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

    private static void acceptLoop(ServerSocket socket) {
        while (RUNNING.get() && !socket.isClosed()) {
            try {
                Socket client = socket.accept();
                client.setSoTimeout(30_000);
                ExecutorService pool = clients;
                if (pool == null) {
                    client.close();
                } else {
                    try {
                        pool.execute(() -> handle(client));
                    } catch (java.util.concurrent.RejectedExecutionException saturated) {
                        try {
                            OutputStream rejected = client.getOutputStream();
                            sendError(rejected, 503, "server_busy", "Too many queued requests");
                            client.close();
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception error) {
                if (RUNNING.get()) Main.log("Open Local API accept failed: "
                        + error.getClass().getSimpleName());
            }
        }
    }

    private static void handle(Socket socket) {
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
            String supplied = bearer(request.headers.get("authorization"));
            if (token == null || !constantTimeEquals(token, supplied)) {
                sendError(out, 401, "invalid_api_key", "Invalid bearer token");
                return;
            }
            if ("GET".equals(request.method) && "/v1/models".equals(request.path)) {
                // Model discovery describes the configured API surface, not live engine
                // readiness. Keep it usable for client setup/diagnostics while completions
                // independently return 503 until the native adapter is genuinely ready.
                JSONObject model = new JSONObject().put("id", "deepseek-chat")
                        .put("object", "model").put("owned_by", "local");
                JSONObject reasoner = new JSONObject().put("id", "deepseek-reasoner")
                        .put("object", "model").put("owned_by", "local");
                sendJson(out, 200, new JSONObject().put("object", "list")
                        .put("data", new JSONArray().put(model).put(reasoner)));
                return;
            }
            if (!"POST".equals(request.method)
                    || !"/v1/chat/completions".equals(request.path)) {
                sendError(out, 404, "not_found", "Unknown API endpoint");
                return;
            }
            String contentType = request.headers.get("content-type");
            if (contentType != null && !contentType.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("application/json")) {
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
            String model = body.optString("model", "deepseek-chat").trim();
            if (!"deepseek-chat".equals(model) && !"deepseek-reasoner".equals(model)) {
                String reportedModel = model.replace('\r', ' ').replace('\n', ' ');
                if (reportedModel.length() > 128) {
                    reportedModel = reportedModel.substring(0, 128);
                }
                Main.log("Open Local API rejected model id: " + reportedModel);
                sendError(out, 400, "model_not_found",
                        "不支持的模型名称：" + reportedModel
                                + "；当前可用模型：deepseek-chat、deepseek-reasoner");
                return;
            }
            String prompt;
            try {
                prompt = toPrompt(body.optJSONArray("messages"));
            } catch (Exception invalid) {
                sendError(out, 400, "invalid_messages", "Messages must contain supported text roles");
                return;
            }
            if (prompt.length() == 0 || prompt.length() > MAX_PROMPT_CHARS) {
                sendError(out, 400, "invalid_prompt", "Prompt is empty or too large");
                return;
            }
            boolean stream = body.optBoolean("stream", false);
            String maxTokenField = body.has("max_completion_tokens")
                    ? "max_completion_tokens" : "max_tokens";
            int maxTokens = 4096;
            if (body.has(maxTokenField)) {
                Object rawMaxTokens = body.opt(maxTokenField);
                if (!(rawMaxTokens instanceof Number)) {
                    sendError(out, 400, "invalid_max_tokens",
                            maxTokenField + " must be an integer from 1 to 8192");
                    return;
                }
                double numericMaxTokens = ((Number) rawMaxTokens).doubleValue();
                if (numericMaxTokens < 1 || numericMaxTokens > 8192
                        || numericMaxTokens != Math.rint(numericMaxTokens)) {
                    sendError(out, 400, "invalid_max_tokens",
                            maxTokenField + " must be an integer from 1 to 8192");
                    return;
                }
                maxTokens = (int) numericMaxTokens;
            }
            String requestId = "api-" + UUID.randomUUID();
            z2.CompletionRequest completion = new z2.CompletionRequest(
                    requestId, model, "default", prompt, prompt,
                    "deepseek-reasoner".equals(model), false, maxTokens,
                    null, null, false);
            z2.Backend current = backend;
            if (current == null || !current.isReady()) {
                sendError(out, 503, "model_not_ready",
                        current == null ? "Model backend unavailable" : current.readinessDetail());
                return;
            }
            if (stream) {
                streamCompletion(out, current, completion, model, requestId);
            } else {
                z2.CompletionResult result;
                try {
                    result = current.complete(completion, null);
                } catch (Exception upstreamFailure) {
                    sendError(out, 502, "upstream_error", "Native model execution failed");
                    return;
                }
                JSONObject message = new JSONObject().put("role", "assistant")
                        .put("content", result.text);
                if ("deepseek-reasoner".equals(model) && result.reasoning.length() > 0) {
                    message.put("reasoning_content", result.reasoning);
                }
                JSONObject choice = new JSONObject().put("index", 0).put("message", message)
                        .put("finish_reason", result.finishReason);
                sendJson(out, 200, new JSONObject().put("id", requestId)
                        .put("object", "chat.completion").put("created", System.currentTimeMillis()/1000)
                        .put("model", model).put("choices", new JSONArray().put(choice)));
            }
        } catch (Exception error) {
            Main.log("Open Local API request failed: " + error.getClass().getSimpleName());
        }
    }

    private static void streamCompletion(OutputStream out, z2.Backend current,
            z2.CompletionRequest request, String model, String requestId) throws Exception {
        writeHeaders(out, 200, "text/event-stream; charset=utf-8");
        final AtomicBoolean disconnected = new AtomicBoolean(false);
        z2.DeltaSink sink = new z2.DeltaSink() {
            private void send(String field, String content) throws Exception {
                if (disconnected.get() || content == null || content.length() == 0) return;
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
                try { send("content", delta); return true; }
                catch (Exception failure) { disconnected.set(true); return false; }
            }
            @Override public boolean onReasoning(String delta) {
                try { send("reasoning_content", delta); return !disconnected.get(); }
                catch (Exception failure) { disconnected.set(true); return false; }
            }
            @Override public boolean isCancelled() { return disconnected.get(); }
        };
        try {
            z2.CompletionResult result = current.complete(request, sink);
            if (!disconnected.get()) {
                JSONObject choice = new JSONObject().put("index", 0)
                        .put("delta", new JSONObject()).put("finish_reason", result.finishReason);
                JSONObject finalEvent = new JSONObject().put("id", requestId)
                        .put("object", "chat.completion.chunk").put("created", System.currentTimeMillis()/1000)
                        .put("model", model).put("choices", new JSONArray().put(choice));
                out.write(("data: " + finalEvent.toString() + "\n\ndata: [DONE]\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception failure) {
            if (!disconnected.get()) {
                JSONObject error = new JSONObject().put("error",
                        new JSONObject().put("message", "Model execution failed")
                                .put("type", "server_error").put("code", "upstream_error"));
                out.write(("data: " + error.toString() + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private static String toPrompt(JSONArray messages) throws Exception {
        if (messages == null || messages.length() == 0) return "";
        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) throw new IllegalArgumentException("Invalid message");
            String role = message.optString("role", "");
            if (!"system".equals(role) && !"developer".equals(role)
                    && !"user".equals(role) && !"assistant".equals(role)) {
                throw new IllegalArgumentException("Unsupported message role");
            }
            Object content = message.opt("content");
            if (!(content instanceof String)) {
                throw new IllegalArgumentException("Only text message content is supported");
            }
            if (prompt.length() > 0) prompt.append("\n");
            prompt.append(role).append(": ").append((String) content);
            if (prompt.length() > MAX_PROMPT_CHARS) return prompt.toString();
        }
        return prompt.toString();
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
        String[] first = lines[0].split(" ");
        if (first.length < 2) throw new IllegalArgumentException("Invalid request line");
        java.util.Map<String,String> headers = new java.util.HashMap<>();
        for (int i=1;i<lines.length;i++) {
            int colon=lines[i].indexOf(':');
            if(colon>0) headers.put(lines[i].substring(0,colon).trim().toLowerCase(),
                    lines[i].substring(colon+1).trim());
        }
        if (headers.containsKey("transfer-encoding")) throw new IllegalArgumentException("Chunked requests unsupported");
        int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        if (length < 0 || length > MAX_BODY_BYTES) throw new IllegalArgumentException("Request too large");
        byte[] body = new byte[length];
        int offset=0;
        while(offset<length) {
            int count=in.read(body,offset,length-offset);
            if(count<0) throw new IllegalArgumentException("Truncated request body");
            offset+=count;
        }
        return new HttpRequest(first[0], first[1], headers, body);
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
            if (existing.matches("sk-[A-Za-z0-9_-]{40,80}")) return existing;
        }
        String value = generateToken();
        persistToken(context, value);
        return value;
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
    private static void sendError(OutputStream out, int status, String code, String message) throws Exception {
        sendJson(out, status, new JSONObject().put("error", new JSONObject()
                .put("message", message).put("type", "invalid_request_error").put("code", code)));
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

    private static final class HttpRequest {
        final String method, path;
        final java.util.Map<String,String> headers;
        final byte[] body;
        HttpRequest(String method, String path, java.util.Map<String,String> headers, byte[] body) {
            this.method=method; this.path=path; this.headers=headers; this.body=body;
        }
    }
}
