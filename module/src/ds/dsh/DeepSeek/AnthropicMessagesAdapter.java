package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strict Anthropic Messages request normalizer. It translates the public Anthropic wire format
 * into the same prompt/tool-plan contract used by OpenAI Chat; it never executes client tools.
 */
final class AnthropicMessagesAdapter {
    private static final int MAX_PROMPT_CHARS = 128 * 1024;

    static final class Request {
        final String model;
        final String prompt;
        final int maxTokens;
        final boolean stream;
        final boolean reasoning;
        final OpenAiToolBridge.Plan toolPlan;

        Request(String model, String prompt, int maxTokens, boolean stream,
                boolean reasoning, OpenAiToolBridge.Plan toolPlan) {
            this.model = model;
            this.prompt = prompt;
            this.maxTokens = maxTokens;
            this.stream = stream;
            this.reasoning = reasoning;
            this.toolPlan = toolPlan;
        }
    }

    private AnthropicMessagesAdapter() {}

    static Request parse(JSONObject body) throws z2.GatewayException {
        if (body == null) throw invalid("invalid_request", "Request body must be an object");
        if (body.has("model") && !(body.opt("model") instanceof String)) {
            throw invalid("invalid_model", "model must be a string");
        }
        String requestedModel = body.optString("model", "deepseek-chat").trim();
        String model = "deepseek-flash".equals(requestedModel)
                ? "deepseek-chat" : requestedModel;
        if (!"deepseek-chat".equals(model) && !"deepseek-reasoner".equals(model)) {
            throw invalid("model_not_found", "Unsupported model: " + model);
        }

        Object rawMax = body.opt("max_tokens");
        if (!(rawMax instanceof Number)) {
            throw invalid("invalid_max_tokens", "max_tokens is required and must be an integer from 1 to 8192");
        }
        double numericMax = ((Number) rawMax).doubleValue();
        if (numericMax < 1 || numericMax > 8192 || numericMax != Math.rint(numericMax)) {
            throw invalid("invalid_max_tokens", "max_tokens must be an integer from 1 to 8192");
        }
        int maxTokens = (int) numericMax;

        if (body.has("stream") && !(body.opt("stream") instanceof Boolean)) {
            throw invalid("invalid_stream", "stream must be a boolean");
        }
        boolean stream = body.optBoolean("stream", false);
        if (body.has("temperature") || body.has("top_p") || body.has("top_k")
                || body.has("stop_sequences") || body.has("metadata")) {
            throw invalid("unsupported_parameter",
                    "temperature, top_p, top_k, stop_sequences, and metadata are not supported");
        }

        boolean reasoning = "deepseek-reasoner".equals(model);
        if (body.has("thinking")) {
            JSONObject thinking = body.optJSONObject("thinking");
            if (thinking == null) throw invalid("invalid_thinking", "thinking must be an object");
            String type = thinking.optString("type", "disabled");
            if (!"enabled".equals(type) && !"disabled".equals(type)) {
                throw invalid("invalid_thinking", "thinking.type must be enabled or disabled");
            }
            reasoning = "enabled".equals(type);
            if (reasoning && thinking.has("budget_tokens")) {
                Object budget = thinking.opt("budget_tokens");
                if (!(budget instanceof Number)
                        || ((Number) budget).doubleValue() < 1
                        || ((Number) budget).doubleValue() > maxTokens
                        || ((Number) budget).doubleValue() != Math.rint(
                                ((Number) budget).doubleValue())) {
                    throw invalid("invalid_thinking", "thinking.budget_tokens must be an integer within max_tokens");
                }
            }
        }

        JSONArray rawTools = body.optJSONArray("tools");
        if (body.has("tools") && body.opt("tools") != JSONObject.NULL && rawTools == null) {
            throw invalid("invalid_tools", "tools must be an array");
        }
        JSONArray openAiTools = new JSONArray();
        if (rawTools != null) {
            for (int i = 0; i < rawTools.length(); i++) {
                JSONObject raw = rawTools.optJSONObject(i);
                if (raw == null) throw invalid("invalid_tool", "Every tool must be an object");
                String name = raw.optString("name", "").trim();
                if (name.length() == 0 || name.length() > 128) {
                    throw invalid("invalid_tool", "Every tool requires a valid name");
                }
                JSONObject schema = raw.optJSONObject("input_schema");
                if (schema == null) throw invalid("invalid_tool", "Tool input_schema must be an object");
                JSONObject function = new JSONObject()
                        .put("name", name)
                        .put("description", raw.optString("description", ""))
                        .put("parameters", schema);
                openAiTools.put(new JSONObject().put("type", "function").put("function", function));
            }
        }

        Object rawChoice = body.opt("tool_choice");
        Object choice = null;
        if (rawChoice != null && rawChoice != JSONObject.NULL) {
            JSONObject toolChoice = body.optJSONObject("tool_choice");
            if (toolChoice == null) throw invalid("invalid_tool_choice", "tool_choice must be an object");
            String type = toolChoice.optString("type", "");
            if ("auto".equals(type)) {
                choice = "auto";
            } else if ("any".equals(type)) {
                choice = "required";
            } else if ("tool".equals(type)) {
                String name = toolChoice.optString("name", "").trim();
                if (name.length() == 0) throw invalid("invalid_tool_choice", "tool_choice.name is required");
                choice = new JSONObject().put("type", "function")
                        .put("function", new JSONObject().put("name", name));
            } else {
                throw invalid("invalid_tool_choice", "tool_choice.type must be auto, any, or tool");
            }
        }
        OpenAiToolBridge.Plan plan = OpenAiToolBridge.chatPlan(openAiTools, choice, true);
        if (stream && plan.active()) {
            throw invalid("unsupported_streaming_tool_calls",
                    "Streaming tool_use is not yet supported; use stream=false");
        }

        StringBuilder prompt = new StringBuilder();
        Object rawSystem = body.opt("system");
        if (rawSystem != null && rawSystem != JSONObject.NULL) {
            String system = textContent(rawSystem, "system");
            append(prompt, "system", system);
        }
        JSONArray messages = body.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            throw invalid("invalid_messages", "messages must be a non-empty array");
        }
        Set<String> pendingToolIds = new HashSet<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) throw invalid("invalid_messages", "Every message must be an object");
            String role = message.optString("role", "");
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw invalid("invalid_messages", "Message role must be user or assistant");
            }
            Object rawContent = message.opt("content");
            if (rawContent == null || rawContent == JSONObject.NULL) {
                throw invalid("invalid_messages", "Message content is required");
            }
            if (rawContent instanceof String) {
                append(prompt, role, (String) rawContent);
                continue;
            }
            if (!(rawContent instanceof JSONArray)) {
                throw invalid("invalid_messages", "Message content must be text or content blocks");
            }
            JSONArray blocks = (JSONArray) rawContent;
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < blocks.length(); j++) {
                JSONObject block = blocks.optJSONObject(j);
                if (block == null) throw invalid("invalid_content_block", "Content block must be an object");
                String type = block.optString("type", "");
                if ("text".equals(type)) {
                    String value = block.optString("text", "");
                    if (value.length() > 0) {
                        if (text.length() > 0) text.append("\n");
                        text.append(value);
                    }
                } else if ("tool_use".equals(type)) {
                    if (!"assistant".equals(role) || !plan.active()) {
                        throw invalid("invalid_tool_use", "tool_use is allowed only in assistant messages with tools declared");
                    }
                    String id = block.optString("id", "").trim();
                    String name = block.optString("name", "").trim();
                    JSONObject input = block.optJSONObject("input");
                    if (id.length() == 0 || id.length() > 128 || name.length() == 0
                            || input == null || pendingToolIds.contains(id)
                            || !containsTool(plan, name)) {
                        throw invalid("invalid_tool_use", "Invalid or undeclared tool_use block");
                    }
                    pendingToolIds.add(id);
                    append(prompt, "assistant", "tool call id=" + id + " name=" + name
                            + " arguments=" + input.toString());
                } else if ("tool_result".equals(type)) {
                    if (!"user".equals(role)) {
                        throw invalid("invalid_tool_result", "tool_result is allowed only in user messages");
                    }
                    String id = block.optString("tool_use_id", "").trim();
                    if (id.length() == 0 || !pendingToolIds.remove(id)) {
                        throw invalid("invalid_tool_result", "tool_result does not match a preceding tool_use");
                    }
                    String result = textContent(block.opt("content"), "tool_result");
                    if (result.length() == 0) result = block.optBoolean("is_error", false)
                            ? "Tool execution failed" : "(empty tool result)";
                    append(prompt, "tool", "tool result for " + id + ": " + result);
                } else {
                    throw invalid("unsupported_content_block",
                            "Only text, tool_use, and tool_result content blocks are supported");
                }
            }
            if (text.length() > 0) append(prompt, role, text.toString());
        }
        if (prompt.length() == 0 || prompt.length() > MAX_PROMPT_CHARS) {
            throw invalid("invalid_prompt", "Prompt is empty or too large");
        }
        String requestId = "anthropic-" + java.util.UUID.randomUUID().toString();
        String normalized = OpenAiToolBridge.addInstructions(prompt.toString(), plan, requestId);
        if (normalized.length() > MAX_PROMPT_CHARS) {
            throw invalid("invalid_prompt", "Prompt with tool instructions is too large");
        }
        return new Request(model, normalized, maxTokens, stream, reasoning, plan);
    }

    private static boolean containsTool(OpenAiToolBridge.Plan plan, String name) {
        for (OpenAiToolBridge.Definition definition : plan.tools) {
            if (name.equals(definition.name)) return true;
        }
        return false;
    }

    private static String textContent(Object raw, String field) throws z2.GatewayException {
        if (raw instanceof String) return ((String) raw).trim();
        if (!(raw instanceof JSONArray)) {
            throw invalid("invalid_" + field, field + " must be text or text blocks");
        }
        StringBuilder out = new StringBuilder();
        JSONArray blocks = (JSONArray) raw;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null || !"text".equals(block.optString("type", ""))
                    || !(block.opt("text") instanceof String)) {
                throw invalid("unsupported_" + field, field + " supports text blocks only");
            }
            if (out.length() > 0) out.append("\n");
            out.append(block.optString("text", ""));
        }
        return out.toString().trim();
    }

    private static void append(StringBuilder prompt, String role, String value) {
        if (value == null || value.trim().length() == 0) return;
        if (prompt.length() > 0) prompt.append("\n");
        prompt.append(role).append(": ").append(value.trim());
    }

    private static z2.GatewayException invalid(String code, String message) {
        return new z2.GatewayException(400, code, "invalid_request_error", message);
    }
}
