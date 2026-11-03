package ds.dsh.DeepSeek;

/** Bounded, deterministic report writer shared by the native host probe. */
final class NativeProbeReport {
    static final int MAX_REPORT_CHARS = 12_000;
    static final int REPORT_TERMINAL_RESERVE = 64;

    private NativeProbeReport() {}

    static void line(StringBuilder out, String key, String value) {
        String entry = key + '=' + (value == null ? "" : value) + '\n';
        // Keep the terminal timing field even when a large candidate inventory is truncated.
        if ("probe.elapsedMs".equals(key)) {
            if (out.length() + entry.length() <= MAX_REPORT_CHARS) out.append(entry);
            return;
        }
        String marker = "probe.truncated=true\n";
        int contentLimit = MAX_REPORT_CHARS - REPORT_TERMINAL_RESERVE - marker.length();
        if (out.length() + entry.length() > contentLimit) {
            if (out.indexOf("probe.truncated=") < 0
                    && out.length() + marker.length() + REPORT_TERMINAL_RESERVE <= MAX_REPORT_CHARS) {
                out.append(marker);
            }
            return;
        }
        out.append(entry);
    }
}
