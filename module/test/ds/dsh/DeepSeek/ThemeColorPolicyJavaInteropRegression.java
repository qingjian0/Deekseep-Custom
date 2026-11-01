package ds.dsh.DeepSeek;

public final class ThemeColorPolicyJavaInteropRegression {
    public static void main(String[] args) {
        check(ThemeColorPolicy.parseColor("#123456", 0) == 0xFF123456, "RGB alpha default");
        check(ThemeColorPolicy.parseColor("#80123456", 0) == 0x80123456, "ARGB preserved");
        check(ThemeColorPolicy.parseColor("invalid", 42) == 42, "fallback preserved");
        check("vertical".equals(ThemeColorPolicy.validDirection(
                "vertical", "horizontal", "vertical", "diagonal_down", "diagonal_up")),
                "valid direction");
        check("horizontal".equals(ThemeColorPolicy.validDirection(
                null, "horizontal", "vertical", "diagonal_down", "diagonal_up")),
                "null direction fallback");
        System.out.println("Theme color policy Java interop regression passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
