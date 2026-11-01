package ds.dsh.DeepSeek;

/** Java ABI guard for Kotlin-owned Agent configuration value normalization. */
public final class AgentToolConfigPolicyJavaInteropRegression {
    public static void main(String[] args) {
        require("in_app".equals(AgentToolConfigPolicy.backend(null, "in_app", "root", "shizuku")),
                "null backend fallback");
        require("root".equals(AgentToolConfigPolicy.backend("root", "in_app", "root", "shizuku")),
                "root backend");
        require("shizuku".equals(AgentToolConfigPolicy.backend("shizuku", "in_app", "root", "shizuku")),
                "Shizuku backend");
        require("in_app".equals(AgentToolConfigPolicy.backend("ROOT", "in_app", "root", "shizuku")),
                "backend matching must remain case-sensitive");
        require("execute".equals(AgentToolConfigPolicy.permission("execute", "execute", "all")),
                "execute permission");
        require("all".equals(AgentToolConfigPolicy.permission("unknown", "execute", "all")),
                "unknown permission fallback");
        require(AgentToolConfigPolicy.promptStrength(0, 1, 2, 3) == 1,
                "lower prompt clamp");
        require(AgentToolConfigPolicy.promptStrength(1, 1, 2, 3) == 1,
                "basic prompt level");
        require(AgentToolConfigPolicy.promptStrength(2, 1, 2, 3) == 2,
                "enhanced prompt level");
        require(AgentToolConfigPolicy.promptStrength(3, 1, 2, 3) == 3,
                "immersive prompt level");
        require(AgentToolConfigPolicy.promptStrength(99, 1, 2, 3) == 3,
                "upper prompt clamp");
        System.out.println("AgentToolConfigPolicyJavaInteropRegression OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
