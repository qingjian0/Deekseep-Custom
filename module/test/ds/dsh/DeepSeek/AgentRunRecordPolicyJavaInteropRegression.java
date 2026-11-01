package ds.dsh.DeepSeek;

public final class AgentRunRecordPolicyJavaInteropRegression {
    public static void main(String[] args) {
        check("scope|call".equals(AgentRunRecordPolicy.callKey(" scope ", " call ")),
                "call key normalization");
        check("abc".equals(AgentRunRecordPolicy.clean("  abcdef  ", 3)),
                "trim then truncate");
        String nbsp = String.valueOf((char) 160);
        check((nbsp + "x" + nbsp).equals(
                AgentRunRecordPolicy.clean(nbsp + "x" + nbsp, 10)),
                "Java trim compatibility");
        check("".equals(AgentRunRecordPolicy.clean(null, 10)), "null fallback");
        System.out.println("Agent run record policy Java interop regression passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
