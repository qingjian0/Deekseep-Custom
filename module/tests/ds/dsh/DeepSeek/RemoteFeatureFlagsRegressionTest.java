package ds.dsh.DeepSeek;

import java.util.HashSet;

public final class RemoteFeatureFlagsRegressionTest {
    public static void main(String[] args) {
        HashSet<String> keys = new HashSet<>();
        checkPolicyHelpers();

        for (RemoteFeatureFlags.Feature feature : RemoteFeatureFlags.FEATURES) {
            if (feature.nativeBoolean) {
                require(feature.key.startsWith("kv_remote_settings_"),
                        "non-DeepSeek remote key " + feature.key);
                require(RemoteFeatureFlags.localKey(feature.key).startsWith("kv_settings_"),
                        "non-DeepSeek local override key " + feature.key);
            }
            require(keys.add(feature.key), "duplicate key " + feature.key);
            require(feature.zh.length() > 0 && feature.en.length() > 0,
                    "missing label " + feature.key);
        }
        require(keys.size() == 29, "unexpected feature count " + keys.size());
        require(keys.contains("kv_remote_settings_conversation_search_enabled"),
                "conversation search missing");
        require(keys.contains("kv_remote_settings_voice_input_enabled"),
                "voice input missing");
        require(keys.contains("kv_remote_settings_allow_file_with_search"),
                "file + search flag missing");
        require(keys.contains("kv_remote_settings_show_new_chat_button_above_input"),
                "new-chat flag missing");
        require(keys.contains("kv_remote_settings_hide_assistant_avatar"),
                "assistant-avatar flag missing");
        require(keys.contains("kv_remote_settings_sse_auto_scroll_one_screen"),
                "SSE scroll flag missing");
        require(keys.contains(RemoteFeatureFlags.ATTACHMENT_GUIDE_PROMPTS),
                "attachment guide-prompt rollout missing");
        RemoteFeatureFlags.Feature guidePrompts = RemoteFeatureFlags.featureForKey(
                RemoteFeatureFlags.ATTACHMENT_GUIDE_PROMPTS);
        require(guidePrompts != null && !guidePrompts.nativeBoolean,
                "attachment prompts must stay model-config managed");
        RemoteFeatureFlags.Feature avatar = RemoteFeatureFlags.featureForKey(
                "kv_remote_settings_hide_assistant_avatar");
        require(avatar != null && avatar.inverted, "avatar flag must expose positive semantics");
        require("显示助手头像".equals(avatar.zh), "avatar label must remain positive");
        require(!RemoteFeatureFlags.hostValue(avatar, true),
                "show-avatar ON must write hide-avatar=false");
        require(RemoteFeatureFlags.hostValue(avatar, false),
                "show-avatar OFF must write hide-avatar=true");
        require(RemoteFeatureFlags.userModeFromHost(avatar, false)
                        == RemoteFeatureFlags.FORCE_ON,
                "hide-avatar=false must display show-avatar ON");
        System.out.println("Remote feature flag regression passed");
    }

    private static void checkPolicyHelpers() {
        require("kv_settings_voice_input_enabled".equals(
                        RemoteFeatureFlagsPolicy.localKey("kv_remote_settings_voice_input_enabled")),
                "remote key prefix mapping");
        require(RemoteFeatureFlagsPolicy.localKey("custom_key").equals("custom_key"),
                "non-remote key must remain unchanged");
        require(RemoteFeatureFlagsPolicy.localKey(null) == null, "null key");
        require(RemoteFeatureFlagsPolicy.hostValue(false, true),
                "non-inverted feature preserves user value");
        require(!RemoteFeatureFlagsPolicy.hostValue(true, true),
                "inverted feature negates enabled user value");
        require(RemoteFeatureFlagsPolicy.userModeFromHost(true, false)
                        == RemoteFeatureFlags.FORCE_ON,
                "host false maps to user enabled for inverted feature");
        require(RemoteFeatureFlagsPolicy.userModeFromHost(false, false)
                        == RemoteFeatureFlags.FORCE_OFF,
                "host false maps to user disabled for normal feature");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
