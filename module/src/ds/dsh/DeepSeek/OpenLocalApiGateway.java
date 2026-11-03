package ds.dsh.DeepSeek;

import android.content.Context;

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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Open-build loopback API gateway. It deliberately uses only Android/JDK APIs and the stable
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
    private static volatile Thread acceptThread;
    private static volatile ExecutorService clients;
    private static volatile z2.Backend backend;
    private static volatile Context appContext;
    private static volatile String token;
    private static volatile int boundPort;

    private OpenLocalApiGateway() {}

    static synchronized void start(Context context, z2.Backend modelBackend) {
        if (context == null || modelBackend == null || RUNNING.get()) return;
        try {
            Context app = context.getApplicationContext();
            appContext = app;
            token = loadOrCreateToken(app);
            backend = modelBackend;
            ServerSocket socket = new ServerSocket(DEFAULT_PORT, 16,
                    InetAddress.getByName("127.0.0.1"));
            server = socket;
            boundPort = socket.getLocalPort();
            clients = Executors.newFixedThreadPool(2);
            RUNNING.set(true);
            acceptThread = new Thread(() -> acceptLoop(socket), "dsh-local-api-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            Main.log("Open Local API listening on 127.0.0.1:" + boundPort);
        } catch (Exception error) {
            RUNNING.set(false);
            closeServer();
            Main.log("Open Local API start failed: " + error.getClass().getSimpleName());
        }
    }

    static synchronized void stop() {
        RUNNING.set(false);
        closeServer();
        ExecutorService pool = clients;
        clients = null;
        if (pool != null) pool.shutdownNow();
        backend = null;
        boundPort = 0;
    }

    static boolean isRunning() { return RUNNING.get(); }
    static int port() { return boundPort; }
    static String endpoint() {
        return RUNNING.get() ? "http://127.0.0.1:" + boundPort : "";
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
    static String connectionInfo() {
        if (!RUNNING.get()) return "Open Local API is stopped";
        return "Base URL: " + endpoint() + "/v1\\n"
                + "Chat Completions: " + endpoint() + "/v1/chat/completions\\n"
                + "API Key: " + apiKey() + "\\n"
                + "Models: deepseek-chat, deepseek-reasoner\\n"
                + "Binding: 127.0.0.1 only";
    }
    static String runtimeStatus() {
        return RUNNING.get() ? "Running on 127.0.0.1:" + boundPort
                : "Stopped";
    }

    private static void closeServer() {
        ServerSocket socket = server;
        server = null;
        if (socket != null) try { socket.close(); } catch (Exception ignored) {}
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
                    pool.execute(() -> handle(client));
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
            HttpRequest request = readRequest(in);
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
            JSONObject body;
            try {
                body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
            } catch (Exception invalid) {
                sendError(out, 400, "invalid_json", "Request body must be valid JSON");
                return;
            }
            String model = body.optString("model", "deepseek-chat");
            if (!"deepseek-chat".equals(model) && !"deepseek-reasoner".equals(model)) {
                sendError(out, 400, "model_not_found", "Unsupported model");
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
            int maxTokens = body.optInt("max_tokens", 4096);
            maxTokens = Math.max(128, Math.min(8192, maxTokens));
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
                z2.CompletionResult result = current.complete(completion, null);
                JSONObject message = new JSONObject().put("role", "assistant")
                        .put("content", result.text);
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
            private void send(String content) throws Exception {
                if (disconnected.get()) return;
                JSONObject delta = new JSONObject().put("content", content);
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
                try { send(delta); return true; }
                catch (Exception failure) { disconnected.set(true); return false; }
            }
            @Override public boolean onReasoning(String delta) throws Exception { return onText(delta); }
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
            try (InputStream input = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[128];
                int count;
                while ((count = input.read(buffer)) >= 0 && stored.size() < 256) {
                    stored.write(buffer, 0, count);
                }
            }
            String existing = new String(stored.toByteArray(), StandardCharsets.US_ASCII).trim();
            if (existing.matches("[A-Za-z0-9_-]{40,80}")) return existing;
        }
        String value = generateToken();
        persistToken(context, value);
        return value;
    }
    private static String generateToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return android.util.Base64.encodeToString(random,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP
                        | android.util.Base64.NO_PADDING);
    }
    private static void persistToken(Context context, String value) throws Exception {
        File file = new File(context.getFilesDir(), TOKEN_FILE);
        File temp = new File(context.getFilesDir(), TOKEN_FILE + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temp)) {
            stream.write(value.getBytes(StandardCharsets.US_ASCII));
            stream.getFD().sync();
        }
        if (file.exists() && !file.delete()) throw new IllegalStateException("Cannot replace API token");
        if (!temp.renameTo(file)) throw new IllegalStateException("Cannot persist API token");
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
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized"
                : status == 400 ? "Bad Request" : status == 404 ? "Not Found"
                : status == 405 ? "Method Not Allowed" : "Service Unavailable";
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
