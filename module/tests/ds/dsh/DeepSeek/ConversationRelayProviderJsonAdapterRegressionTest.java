package ds.dsh.DeepSeek;

/** Exercises the real org.json decoding boundary used by z17 before Kotlin event routing. */
public final class ConversationRelayProviderJsonAdapterRegressionTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(String expected, String actual, String message) {
        require(expected.equals(actual),
                message + " expected=<" + expected + "> actual=<" + actual + ">");
    }

    public static void main(String[] args) {
        ConversationRelayProviderStreamAdapter openAi =
                new ConversationRelayProviderStreamAdapter();
        ConversationRelayProviderJsonAdapter.appendOpenAiData(
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"reason-1\","
                        + "\"content\":\"content-1\",\"tool_calls\":[{\"id\":\"ignored\"}],"
                        + "\"empty\":\"\"}}]}",
                openAi);
        ConversationRelayProviderJsonAdapter.appendOpenAiData(
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"reason-2\","
                        + "\"content\":\"content-2\"}}]}",
                openAi);
        equal("reason-1reason-2", openAi.reasoningText(), "OpenAI reasoning accumulation");
        equal("content-1content-2", openAi.contentText(), "OpenAI content accumulation");

        // Malformed JSON and malformed nested shapes are isolated to the individual event.
        ConversationRelayProviderJsonAdapter.appendOpenAiData("{broken", openAi);
        ConversationRelayProviderJsonAdapter.appendOpenAiData(
                "{\"choices\":\"not-an-array\"}", openAi);
        ConversationRelayProviderJsonAdapter.appendOpenAiData(
                "{\"choices\":[{\"delta\":\"not-an-object\"}]}", openAi);
        ConversationRelayProviderJsonAdapter.appendOpenAiData(
                "{\"choices\":[null]}", openAi);
        equal("reason-1reason-2", openAi.reasoningText(), "OpenAI malformed-event isolation");
        equal("content-1content-2", openAi.contentText(), "OpenAI malformed-event isolation");

        ConversationRelayProviderStreamAdapter anthropic =
                new ConversationRelayProviderStreamAdapter();
        ConversationRelayProviderJsonAdapter.appendAnthropicData(
                "{\"type\":\"content_block_start\",\"content_block\":"
                        + "{\"type\":\"thinking\",\"thinking\":\"think-1\"}}",
                anthropic);
        ConversationRelayProviderJsonAdapter.appendAnthropicData(
                "{\"type\":\"content_block_delta\",\"delta\":"
                        + "{\"type\":\"thinking_delta\",\"thinking\":\"think-2\"}}",
                anthropic);
        ConversationRelayProviderJsonAdapter.appendAnthropicData(
                "{\"type\":\"content_block_start\",\"content_block\":"
                        + "{\"type\":\"text\",\"text\":\"answer-1\"}}",
                anthropic);
        ConversationRelayProviderJsonAdapter.appendAnthropicData(
                "{\"type\":\"content_block_delta\",\"delta\":"
                        + "{\"type\":\"text_delta\",\"text\":\"answer-2\"}}",
                anthropic);
        ConversationRelayProviderJsonAdapter.appendAnthropicData(
                "{\"type\":\"message_delta\",\"delta\":{\"type\":\"text_delta\","
                        + "\"text\":\"must-ignore\"}}",
                anthropic);
        ConversationRelayProviderJsonAdapter.appendAnthropicData(
                "{\"type\":\"content_block_delta\",\"delta\":\"wrong-shape\"}",
                anthropic);
        equal("think-1think-2", anthropic.reasoningText(),
                "Anthropic thinking start/delta routing");
        equal("answer-1answer-2", anthropic.contentText(),
                "Anthropic text start/delta routing");

        ConversationRelayProviderStreamAdapter isolated =
                new ConversationRelayProviderStreamAdapter();
        ConversationRelayProviderJsonAdapter.appendOpenAiData(
                "{\"choices\":[{\"delta\":{\"content\":\"separate\"}}]}",
                isolated);
        equal("", isolated.reasoningText(), "new response reasoning isolation");
        equal("separate", isolated.contentText(), "new response content isolation");

        System.out.println("PASS: provider org.json decoding preserves legacy event routing");
    }
}
