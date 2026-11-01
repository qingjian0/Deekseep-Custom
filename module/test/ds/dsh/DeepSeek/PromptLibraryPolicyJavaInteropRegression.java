package ds.dsh.DeepSeek;

/** Java ABI regression for Kotlin-owned prompt-library pure policies. */
public final class PromptLibraryPolicyJavaInteropRegression {
    public static void main(String[] args) {
        String[] categories = {"通用", "代码审查", "写作", "翻译"};
        require("通用".equals(PromptLibraryPolicy.normalizeCategory(null, categories)), "null fallback");
        require("写作".equals(PromptLibraryPolicy.normalizeCategory(" 写作 ", categories)), "known category");
        require("通用".equals(PromptLibraryPolicy.normalizeCategory("未知", categories)), "unknown fallback");
        String first = PromptLibraryPolicy.newId();
        String second = PromptLibraryPolicy.newId();
        require(first.matches("custom:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"), "UUID v4 format");
        require(!first.equals(second), "unique ids");
        System.out.println("PromptLibraryPolicyJavaInteropRegression OK");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
