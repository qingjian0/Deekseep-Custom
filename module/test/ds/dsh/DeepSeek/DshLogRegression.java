package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.List;

public final class DshLogRegression {
    public static void main(String[] args) throws Exception {
        List<DshLog.Event> events = new ArrayList<>();
        AutoCloseable sink = DshLog.addSink(events::add);
        DshLog.setMinimumLevel(DshLog.Level.INFO);
        try {
            DshLog.debug("Regression", "must be filtered");
            DshLog.info(
                    "Security",
                    "Authorization: Bearer bearer-secret-123456 api_key=api-secret-123456 " +
                            "path=/data/user/0/example/files/private.txt"
            );
            DshLog.error(
                    "Crash",
                    "uncaught worker failure",
                    new IllegalStateException("private exception message")
            );

            assertEquals(2, events.size(), "minimum-level filtering");
            String security = events.get(0).getMessage();
            assertContains(security, "Authorization: [redacted]", "authorization redaction");
            assertContains(security, "api_key=[redacted]", "API key redaction");
            assertContains(security, "[private-path]", "private path redaction");
            assertNotContains(security, "bearer-secret-123456", "bearer credential leakage");
            assertNotContains(security, "api-secret-123456", "API key leakage");

            DshLog.Event crash = events.get(1);
            assertEquals("java.lang.IllegalStateException", crash.getErrorType(),
                    "exception type retention");
            assertNotContains(crash.getMessage(), "private exception message",
                    "exception message leakage");
            System.out.println("DshLog regression: PASS");
        } finally {
            sink.close();
            DshLog.setMinimumLevel(DshLog.Level.INFO);
        }
    }

    private static void assertContains(String actual, String expected, String label) {
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(label + ": expected to contain " + expected + ", actual=" + actual);
        }
    }

    private static void assertNotContains(String actual, String forbidden, String label) {
        if (actual != null && actual.contains(forbidden)) {
            throw new AssertionError(label + ": found " + forbidden + " in " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
