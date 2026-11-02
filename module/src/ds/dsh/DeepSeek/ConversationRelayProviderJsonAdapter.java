package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Android-side org.json decoding for provider stream events.
 *
 * Provider semantics and accumulation remain in ConversationRelayProviderStreamAdapter;
 * malformed event payloads retain the legacy fail-soft behavior of z17's stream loops.
 */
final class ConversationRelayProviderJsonAdapter {
    private ConversationRelayProviderJsonAdapter() {}

    static void appendOpenAiData(
            String data, ConversationRelayProviderStreamAdapter accumulator) {
        try {
            JSONObject chunk = new JSONObject(data);
            JSONArray choices = chunk.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject delta = choices.optJSONObject(0).optJSONObject("delta");
                if (delta != null) {
                    java.util.Iterator<String> fields = delta.keys();
                    while (fields.hasNext()) {
                        String field = fields.next();
                        String value = delta.optString(field, "");
                        if (value.length() == 0) continue;
                        accumulator.appendOpenAiDelta(field, value);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    static void appendAnthropicData(
            String data, ConversationRelayProviderStreamAdapter accumulator) {
        try {
            JSONObject event = new JSONObject(data);
            String type = event.optString("type", "");
            if ("content_block_delta".equals(type)) {
                JSONObject delta = event.optJSONObject("delta");
                if (delta != null) {
                    String deltaType = delta.optString("type", "");
                    String thinking = delta.optString("thinking", "");
                    String text = delta.optString("text", "");
                    accumulator.appendAnthropicDelta(type, deltaType, thinking, text);
                }
            } else if ("content_block_start".equals(type)) {
                JSONObject block = event.optJSONObject("content_block");
                if (block != null) {
                    String blockType = block.optString("type", "");
                    String thinking = block.optString("thinking", "");
                    String text = block.optString("text", "");
                    accumulator.appendAnthropicStart(type, blockType, thinking, text);
                }
            }
        } catch (Exception ignored) {}
    }
}
