package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话转接 (conversation relay): when enabled, native DeepSeek chat messages are routed to a
 * user-configured OpenAI/Anthropic-compatible endpoint instead of DeepSeek's own backend.
 * Supports SSE streaming with reasoning extraction so the host thinking panel is fed correctly.
 */
final class z17 {
    private static final String DIR = "/data/data/com.deepseek.chat/files";
    private static final String ENABLED_FILE = DIR + "/deekseep_chat_proxy_enabled";
    private static final String CONFIG_FILE = DIR + "/deekseep_chat_proxy_config.json";
    static final String FORMAT_OPENAI = ConversationRelayEndpointPolicy.FORMAT_OPENAI;
    static final String FORMAT_ANTHROPIC = ConversationRelayEndpointPolicy.FORMAT_ANTHROPIC;

    private static volatile JSONObject cache;

    static final class RelayResult {
        final String reasoning;
        final String content;
        final String error;
        final boolean success;

        RelayResult(String reasoning, String content) {
            this.reasoning = reasoning == null ? "" : reasoning.trim();
            this.content = content == null ? "" : content.trim();
            this.error = null;
            this.success = true;
        }

        RelayResult(String error) {
            this.reasoning = "";
            this.content = "";
            this.error = error;
            this.success = false;
        }
    }

    private z17() {}

    static boolean isEnabled() {
        return new File(ENABLED_FILE).isFile();
    }

    static void setEnabled(boolean on) {
        File flag = new File(ENABLED_FILE);
        try {
            if (on) {
                if (!flag.exists()) {
                    FileOutputStream out = new FileOutputStream(flag, false);
                    try { out.write("1\n".getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
                    finally { out.close(); }
                }
            } else if (flag.exists()) {
                flag.delete();
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized JSONObject config() {
        if (cache != null) return cache;
        try {
            File f = new File(CONFIG_FILE);
            if (!f.isFile()) { cache = new JSONObject(); return cache; }
            byte[] bytes = new byte[(int) f.length()];
            InputStream in = new FileInputStream(f);
            try {
                int off = 0;
                while (off < bytes.length) {
                    int n = in.read(bytes, off, bytes.length - off);
                    if (n < 0) break;
                    off += n;
                }
            } finally { in.close(); }
            cache = new JSONObject(new String(bytes, 0, bytes.length, StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            cache = new JSONObject();
        }
        return cache;
    }

    private static String get(String key) {
        return config().optString(key, "").trim();
    }

    static String apiKey() { return get("api_key"); }
    static String baseUrl() { return get("base_url"); }
    static String model() { return get("model"); }

    static String format() {
        return ConversationRelayEndpointPolicy.normalizeFormat(get("format"));
    }

    static synchronized void save(String apiKey, String format, String baseUrl, String model) {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("api_key", apiKey == null ? "" : apiKey.trim());
            cfg.put("format", FORMAT_ANTHROPIC.equals(format) ? FORMAT_ANTHROPIC : FORMAT_OPENAI);
            cfg.put("base_url", baseUrl == null ? "" : baseUrl.trim());
            cfg.put("model", model == null ? "" : model.trim());
            File f = new File(CONFIG_FILE);
            File tmp = new File(CONFIG_FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp, false);
            try { out.write(cfg.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
            finally { out.close(); }
            if (f.exists()) f.delete();
            tmp.renameTo(f);
            cache = cfg;
        } catch (Exception ignored) {}
    }

    /** Fetches the model id list from {@code baseUrl}/v1/models using the configured API key. */
    static List<String> fetchModels(String apiKey, String format, String baseUrl)
            throws Exception {
        String endpoint = ConversationRelayEndpointPolicy.modelsEndpoint(baseUrl);
        HttpURLConnection conn = ConversationRelayHttpTransport.openRequest(
                endpoint,
                "GET",
                ConversationRelayEndpointPolicy.MODEL_CONNECT_TIMEOUT_MILLIS,
                ConversationRelayEndpointPolicy.MODEL_READ_TIMEOUT_MILLIS,
                false,
                ConversationRelayEndpointPolicy.modelDiscoveryHeaders(format, apiKey),
                null);
        try {
            int code = conn.getResponseCode();
            if (!ConversationRelayHttpPolicy.isSuccessfulStatus(code)) {
                String body = readStream(conn.getErrorStream());
                throw new java.io.IOException(ConversationRelayHttpPolicy.formatHttpError(code, body));
            }
            String body = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(body);
            JSONArray data = json.optJSONArray("data");
            ArrayList<String> modelIds = new ArrayList<String>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    modelIds.add(data.optJSONObject(i).optString("id", ""));
                }
            }
            return ConversationRelayModelListPolicy.normalizeAndDeduplicate(modelIds);
        } finally {
            conn.disconnect();
        }
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null) return "";
        return ConversationRelayEndpointPolicy.normalizeBaseUrl(baseUrl);
    }

    /**
     * Sends one user message to the configured endpoint via SSE streaming and returns both
     * reasoning and text content. Throws IOException when the endpoint rejects the request.
     */
    static RelayResult relay(String prompt) {
        if (prompt == null || prompt.length() == 0) return new RelayResult("");
        String baseUrl = normalize(baseUrl());
        String key = apiKey();
        String model = model();
        boolean anthropic = FORMAT_ANTHROPIC.equals(format());
        String endpoint = ConversationRelayEndpointPolicy.chatEndpoint(baseUrl,
                anthropic ? FORMAT_ANTHROPIC : FORMAT_OPENAI);
        HttpURLConnection conn = null;
        try {
            JSONObject root = new JSONObject();
            root.put("model", ConversationRelayEndpointPolicy.effectiveModel(model));
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            root.put("messages", messages);
            root.put("stream", true);
            if (anthropic) root.put("max_tokens", 8192);
            String body = root.toString();

            conn = ConversationRelayHttpTransport.openRequest(
                    endpoint,
                    "POST",
                    ConversationRelayEndpointPolicy.CONNECT_TIMEOUT_MILLIS,
                    ConversationRelayEndpointPolicy.STREAM_READ_TIMEOUT_MILLIS,
                    true,
                    ConversationRelayEndpointPolicy.requestHeaders(
                            anthropic ? FORMAT_ANTHROPIC : FORMAT_OPENAI, key),
                    body.getBytes(StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            if (!ConversationRelayHttpPolicy.isSuccessfulStatus(code)) {
                String errBody = readStream(conn.getErrorStream());
                conn.disconnect();
                return new RelayResult(ConversationRelayHttpPolicy.formatHttpError(code, errBody));
            }
            return anthropic ? parseAnthropicStream(conn) : parseOpenAIStream(conn);
        } catch (Exception t) {
            String msg = t.getMessage();
            return new RelayResult(msg != null ? msg : HostRemap.simpleName(t.getClass()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static RelayResult parseOpenAIStream(HttpURLConnection conn) throws Exception {
        ConversationRelayProviderStreamAdapter accumulator = new ConversationRelayProviderStreamAdapter();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        try {
            ConversationRelaySseLineReader.readLines(reader, line -> {
                String data = ConversationRelaySsePolicy.dataPayload(line);
                if (data == null) return true;
                if (ConversationRelaySsePolicy.isOpenAiDone(data)) return false;
                try {
                    JSONObject chunk = new JSONObject(data);
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject delta = choices.optJSONObject(0).optJSONObject("delta");
                        if (delta != null) {
                            java.util.Iterator<String> fields = delta.keys();
                            while (fields.hasNext()) {
                                String field = fields.next();
                                ConversationRelayProviderEventPolicy.OutputChannel channel =
                                        accumulator.openAiChannel(field);
                                String value = delta.optString(field, "");
                                if (value.length() == 0) continue;
                                accumulator.append(channel, value);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                return true;
            });
        } finally {
            try { reader.close(); } catch (java.io.IOException ignored) {}
            conn.disconnect();
        }
        return new RelayResult(accumulator.reasoningText(), accumulator.contentText());
    }

    private static RelayResult parseAnthropicStream(HttpURLConnection conn) throws Exception {
        ConversationRelayProviderStreamAdapter accumulator = new ConversationRelayProviderStreamAdapter();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        try {
            ConversationRelaySseLineReader.readLines(reader, line -> {
                String data = ConversationRelaySsePolicy.dataPayload(line);
                if (data == null) return true;
                try {
                    JSONObject event = new JSONObject(data);
                    String type = event.optString("type", "");
                    if ("content_block_delta".equals(type)) {
                        JSONObject delta = event.optJSONObject("delta");
                        if (delta != null) {
                            String dt = delta.optString("type", "");
                            ConversationRelayProviderEventPolicy.OutputChannel channel =
                                    accumulator.anthropicDeltaChannel(type, dt);
                            if (channel == ConversationRelayProviderEventPolicy.OutputChannel.REASONING) {
                                String think = delta.optString("thinking", "");
                                accumulator.append(channel, think);
                            } else if (channel == ConversationRelayProviderEventPolicy.OutputChannel.CONTENT) {
                                String text = delta.optString("text", "");
                                accumulator.append(channel, text);
                            }
                        }
                    } else if ("content_block_start".equals(type)) {
                        JSONObject block = event.optJSONObject("content_block");
                        if (block != null) {
                            String bt = block.optString("type", "");
                            ConversationRelayProviderEventPolicy.OutputChannel channel =
                                    accumulator.anthropicStartChannel(type, bt);
                            if (channel == ConversationRelayProviderEventPolicy.OutputChannel.REASONING) {
                                String think = block.optString("thinking", "");
                                accumulator.append(channel, think);
                            } else if (channel == ConversationRelayProviderEventPolicy.OutputChannel.CONTENT) {
                                String text = block.optString("text", "");
                                accumulator.append(channel, text);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                return true;
            });
        } finally {
            try { reader.close(); } catch (java.io.IOException ignored) {}
            conn.disconnect();
        }
        return new RelayResult(accumulator.reasoningText(), accumulator.contentText());
    }

    private static String readStream(InputStream in) throws Exception {
        return ConversationRelayInputStreamReader.readUtf8(in);
    }
}