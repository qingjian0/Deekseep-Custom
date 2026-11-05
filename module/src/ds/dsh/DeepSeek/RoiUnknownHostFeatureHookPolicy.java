package ds.dsh.DeepSeek;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Constructor;

/**
 * Explicit authorization for the single unknown-host ChatFullCompletionRequest prompt adapter.
 * This policy does not authorize transport, authentication, PoW, response, or Agent hooks.
 */
final class RoiUnknownHostFeatureHookPolicy {
    private static final String ENABLE_TOKEN = "ENABLE";

    private RoiUnknownHostFeatureHookPolicy() {}

    static boolean isExplicitlyEnabled(File switchFile) {
        if (switchFile == null || !switchFile.isFile()) return false;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(switchFile));
            String first = reader.readLine();
            String second = reader.readLine();
            return ENABLE_TOKEN.equals(first == null ? "" : first.trim()) && second == null;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Throwable ignored) {}
            }
        }
    }

    static boolean allowsChatRequestPromptHook(boolean maintainedHost, File switchFile,
            RoiUnknownHostChatRequestResolver.Candidate candidate, Constructor<?> constructor,
            ClassLoader hostRoot) {
        if (maintainedHost || !isExplicitlyEnabled(switchFile) || candidate == null
                || constructor == null || hostRoot == null
                || candidate.fieldAnchors == null
                || !candidate.fieldAnchors.contains("chat_session_id")
                || !candidate.fieldAnchors.contains("thinking_enabled")
                || !candidate.fieldAnchors.contains("search_enabled")
                || !(candidate.fieldAnchors.contains("parent_message_id")
                    || candidate.fieldAnchors.contains("preempt"))
                || candidate.fieldAnchors.size()
                        < RoiUnknownHostChatRequestResolver.MIN_FIELD_ANCHORS
                || candidate.serializerMethods == null || candidate.serializerMethods.isEmpty()
                || !RoiUnknownHostChatRequestResolver.hasExpectedConstructorShape(
                        constructor.getParameterTypes())) return false;
        String owner = "L" + constructor.getDeclaringClass().getName().replace('.', '/') + ";";
        if (!owner.equals(candidate.ownerDescriptor)
                || !candidate.constructorDescriptor.equals(
                        RoiUnknownHostChatRequestResolver.constructorDescriptorForPolicy(constructor))
                || !candidate.sourceScope.equals(
                        RoiCandidateIdentity.forClassLoader(constructor.getDeclaringClass().getClassLoader()))) {
            return false;
        }
        ClassLoader cursor = constructor.getDeclaringClass().getClassLoader();
        for (int depth = 0; cursor != null && depth < 64; depth++, cursor = cursor.getParent()) {
            if (cursor == hostRoot) return true;
        }
        return false;
    }
}
