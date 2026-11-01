package ds.dsh.DeepSeek;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModelDisplayNamePolicyRegressionTest {
    public static void main(String[] args) {
        require("".equals(ModelDisplayNamePolicy.clean(null)), "null cleans to empty");
        require("A B C".equals(ModelDisplayNamePolicy.clean("A\rB\nC\t")),
                "CR/LF/TAB normalize to spaces");
        require("A".equals(ModelDisplayNamePolicy.clean("  A  ")),
                "ASCII edge whitespace is trimmed");
        require(ModelDisplayNamePolicy.clean(repeat("x", 45)).length() == 40,
                "cleaned labels are capped at forty characters");

        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("快速", "闪电");
        mappings.put("Deep Think", "Reasoning");
        require("闪电".equals(ModelDisplayNamePolicy.mapDisplay("快速", true, mappings)),
                "exact display label is mapped");
        require("闪电".equals(ModelDisplayNamePolicy.mapDisplay(" 快速 ", true, mappings)),
                "Java-trimmed exact display label is mapped");
        require(ModelDisplayNamePolicy.mapDisplay("普通聊天内容快速", true, mappings) == null,
                "substring matches are not rewritten");
        require(ModelDisplayNamePolicy.mapDisplay("快速", false, mappings) == null,
                "disabled mapping is inert");
        require(ModelDisplayNamePolicy.mapDisplay("快速", true, null) == null,
                "missing mapping is inert");
        require(ModelDisplayNamePolicy.mapDisplay("", true, mappings) == null,
                "empty labels are ignored");
        require(ModelDisplayNamePolicy.mapDisplay(repeat("x", 41), true, mappings) == null,
                "oversized labels are ignored");
        require(ModelDisplayNamePolicy.mapDisplay("Deep Think", true,
                Collections.singletonMap("Deep Think", "Deep Think")) == null,
                "identity mappings are treated as unchanged");

        String nbsp = String.valueOf((char) 0x00A0) + "快速" + (char) 0x00A0;
        require(ModelDisplayNamePolicy.mapDisplay(nbsp, true, mappings) == null,
                "non-ASCII whitespace must not be trimmed like Java String.trim");

        System.out.println("Model display-name policy regression passed.");
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
