package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationRelayEndpointPolicyTest {
    @Test
    fun normalizesOnlyFormatAndTrailingSlashes() {
        assertEquals("openai", ConversationRelayEndpointPolicy.normalizeFormat(null))
        assertEquals("openai", ConversationRelayEndpointPolicy.normalizeFormat("unknown"))
        assertEquals("anthropic", ConversationRelayEndpointPolicy.normalizeFormat("anthropic"))
        assertEquals("https://relay.example/prefix", ConversationRelayEndpointPolicy.normalizeBaseUrl(
            "  https://relay.example/prefix///  "
        ))
    }

    @Test
    fun selectsExistingProviderEndpoints() {
        assertEquals(
            "https://relay.example/v1/chat/completions",
            ConversationRelayEndpointPolicy.chatEndpoint("https://relay.example/", "openai"),
        )
        assertEquals(
            "https://relay.example/custom/v1/messages",
            ConversationRelayEndpointPolicy.chatEndpoint("https://relay.example/custom/", "anthropic"),
        )
        assertEquals(
            "https://relay.example/v1/models",
            ConversationRelayEndpointPolicy.modelsEndpoint("https://relay.example/"),
        )
    }

    @Test
    fun preservesProviderAuthenticationContracts() {
        val openAi = ConversationRelayEndpointPolicy.requestHeaders("openai", "secret")
        assertEquals("Bearer secret", openAi["Authorization"])
        assertEquals("text/event-stream", openAi["Accept"])
        assertFalse(openAi.containsKey("x-api-key"))

        val anthropic = ConversationRelayEndpointPolicy.requestHeaders("anthropic", "secret")
        assertEquals("secret", anthropic["x-api-key"])
        assertEquals("2023-06-01", anthropic["anthropic-version"])
        assertFalse(anthropic.containsKey("Authorization"))

        assertEquals(
            "Bearer secret",
            ConversationRelayEndpointPolicy.modelDiscoveryHeaders("openai", "secret")["Authorization"],
        )
        assertEquals(
            "application/json",
            ConversationRelayEndpointPolicy.modelDiscoveryHeaders("anthropic", "secret")["Accept"],
        )
    }

    @Test
    fun emptyModelUsesLegacyDefaultAndConfigRedactsSecret() {
        assertEquals("default", ConversationRelayEndpointPolicy.effectiveModel(null))
        assertEquals("default", ConversationRelayEndpointPolicy.effectiveModel("  "))
        assertEquals("claude", ConversationRelayEndpointPolicy.effectiveModel(" claude "))

        val config = ConversationRelayConfig("  secret  ", "anthropic", " https://relay/ ", " model ")
        assertEquals("secret", config.apiKey)
        assertEquals("anthropic", config.format)
        assertEquals("https://relay/", config.baseUrl)
        assertEquals("model", config.model)
        assertTrue(config.toString().contains("<redacted>"))
        assertFalse(config.toString().contains("secret"))
    }
}
