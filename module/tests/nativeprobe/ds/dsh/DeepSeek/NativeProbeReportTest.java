package ds.dsh.DeepSeek;

/** Executable boundary tests for the bounded native probe report writer. */
public final class NativeProbeReportTest {
    private NativeProbeReportTest() {}

    public static void main(String[] args) {
        StringBuilder report = new StringBuilder();
        String payload = repeat('x', 512);
        for (int i = 0; i < 100; i++) {
            NativeProbeReport.line(report, "candidate." + i, payload);
        }
        NativeProbeReport.line(report, "probe.elapsedMs", "123");

        String value = report.toString();
        check(value.length() <= NativeProbeReport.MAX_REPORT_CHARS,
                "report exceeded maximum character count");
        check(value.contains("probe.truncated=true\n"),
                "truncation marker missing after overflow");
        check(value.endsWith("probe.elapsedMs=123\n"),
                "terminal elapsed field missing or not terminal");
        check(value.indexOf("probe.truncated=true\n")
                        == value.lastIndexOf("probe.truncated=true\n"),
                "truncation marker must appear once");

        StringBuilder exact = new StringBuilder();
        String marker = "probe.truncated=true\n";
        int contentLimit = NativeProbeReport.MAX_REPORT_CHARS
                - NativeProbeReport.REPORT_TERMINAL_RESERVE - marker.length();
        NativeProbeReport.line(exact, "boundary", repeat('b',
                contentLimit - "boundary=\n".length()));
        check(exact.length() == contentLimit,
                "exact content boundary was not accepted");
        NativeProbeReport.line(exact, "overflow", "x");
        check(exact.toString().endsWith(marker),
                "overflow at exact content boundary did not append marker");
        NativeProbeReport.line(exact, "probe.elapsedMs", "7");
        check(exact.length() <= NativeProbeReport.MAX_REPORT_CHARS,
                "exact-boundary report exceeded maximum");
        check(exact.toString().endsWith("probe.elapsedMs=7\n"),
                "exact-boundary report lost terminal timing");

        StringBuilder elapsedOnly = new StringBuilder();
        NativeProbeReport.line(elapsedOnly, "probe.elapsedMs", "0");
        check("probe.elapsedMs=0\n".contentEquals(elapsedOnly),
                "elapsed field should be retained in an empty report");

        System.out.println("NativeProbeReport executable boundary tests passed");
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
