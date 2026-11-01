package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshDiagnosticsRecorderTest {
    @Test
    fun aggregatesCountersAndCostMetrics() {
        val recorder = DshDiagnosticsRecorder()
        recorder.record("hook installed cost=12ms")
        recorder.record("server request duration=48ms")
        recorder.record("agent tool failed elapsed=7ms")

        val summary = recorder.performanceSummary(3_661_000L)
        assertTrue(summary.contains("session=1h 1m 1s"))
        assertTrue(summary.contains("events=3"))
        assertTrue(summary.contains("hooks=1"))
        assertTrue(summary.contains("network=1"))
        assertTrue(summary.contains("agent=1"))
        assertTrue(summary.contains("errors=1"))
        assertTrue(summary.contains("timedEvents=3"))
        assertTrue(summary.contains("averageCost=22ms"))
        assertTrue(summary.contains("maxCost=48ms"))
        assertTrue(summary.contains("slowEvents(>=32ms)=1"))
    }

    @Test
    fun storesOnlyUsefulSanitizedBoundedEvents() {
        val recorder = DshDiagnosticsRecorder(maxEvents = 2)
        recorder.record("progress=10% sensor sample")
        recorder.record("hook Authorization: Bearer secret-token-123")
        recorder.record("request Cookie: sid=cookie-secret-456")
        recorder.record("server failure /data/user/0/private.db")

        val lines = recorder.traceLines()
        assertEquals(2, lines.size)
        val joined = lines.joinToString("\n")
        assertFalse(joined.contains("secret-token-123"))
        assertFalse(joined.contains("cookie-secret-456"))
        assertFalse(joined.contains("/data/user/0"))
        assertTrue(joined.contains("[redacted]"))
        assertTrue(joined.contains("[private-path]"))
    }

    @Test
    fun ignoresNullAndEmptyEventsAndHandlesMalformedDurations() {
        val recorder = DshDiagnosticsRecorder()
        recorder.record(null)
        recorder.record("")
        recorder.record("hook duration=not-a-numberms")

        val summary = recorder.performanceSummary(0L)
        assertTrue(summary.contains("events=1"))
        assertTrue(summary.contains("timedEvents=0"))
        assertEquals(0, recorder.traceLines().size)
    }
}
