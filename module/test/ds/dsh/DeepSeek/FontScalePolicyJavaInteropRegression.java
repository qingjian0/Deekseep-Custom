package ds.dsh.DeepSeek;

/** Java ABI regression for the extracted font-scale parsing policy. */
public final class FontScalePolicyJavaInteropRegression {
    public static void main(String[] args) {
        require(Float.compare(1.25f, FontScalePolicy.parse(" 1.25 ")) == 0, "trimmed parse");
        require(Float.compare(0.80f, FontScalePolicy.parse("0.1")) == 0, "lower clamp");
        require(Float.compare(1.60f, FontScalePolicy.parse("9")) == 0, "upper clamp");
        require(Float.compare(1.0f, FontScalePolicy.parse(null)) == 0, "null fallback");
        require(Float.compare(1.0f, FontScalePolicy.parse("NaN")) == 0, "NaN fallback");
        require(Float.compare(1.0f, FontScalePolicy.parse("Infinity")) == 0, "infinity fallback");
        require(Float.isNaN(FontScalePolicy.clamp(Float.NaN, 0.80f, 1.60f)), "clamp NaN compatibility");
        System.out.println("FontScalePolicyJavaInteropRegression OK");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
