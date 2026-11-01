package ds.dsh.DeepSeek

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Platform-neutral diagnostics aggregation shared by the Android presentation layer.
 * Raw event text is sanitized before it is retained; callers must not use this as a
 * substitute for avoiding prompts, message bodies, or raw hook arguments.
 */
class DshDiagnosticsRecorder @JvmOverloads constructor(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    private val lock = Any()
    private val events = ArrayDeque<String>()
    private var total = 0L
    private var errors = 0L
    private var hooks = 0L
    private var network = 0L
    private var agent = 0L
    private var timed = 0L
    private var totalCostMs = 0L
    private var maxCostMs = 0L
    private var slow = 0L

    fun record(raw: String?) {
        if (raw.isNullOrEmpty()) return
        val lower = raw.lowercase(Locale.US)
        val cost = parseCost(raw)
        val safeEvent = DshLog.sanitizeForStorage(raw).take(MAX_EVENT_CHARS)
        synchronized(lock) {
            total++
            if (lower.contains("fail") || lower.contains("error")
                || lower.contains("exception") || lower.contains("crash")) errors++
            if (lower.contains("hook") || lower.contains("inject")) hooks++
            if (lower.contains("server") || lower.contains("request")
                || lower.contains("response") || lower.contains("sse")) network++
            if (lower.contains("agent") || lower.contains("tool")) agent++
            if (cost >= 0L) {
                timed++
                totalCostMs += cost
                if (cost > maxCostMs) maxCostMs = cost
                if (cost >= SLOW_EVENT_MS) slow++
            }
            if (isUseful(lower)) {
                events.addLast(SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                    .format(Date()) + "  " + safeEvent)
                while (events.size > maxEvents.coerceAtLeast(1)) events.removeFirst()
            }
        }
    }

    fun performanceSummary(sessionUptimeMillis: Long): String = synchronized(lock) {
        val average = if (timed == 0L) 0L else totalCostMs / timed
        "session=${formatDuration(sessionUptimeMillis)}" +
            "\nevents=$total" +
            "\nhooks=$hooks" +
            "\nnetwork=$network" +
            "\nagent=$agent" +
            "\nerrors=$errors" +
            "\ntimedEvents=$timed" +
            "\naverageCost=${average}ms" +
            "\nmaxCost=${maxCostMs}ms" +
            "\nslowEvents(>=32ms)=$slow"
    }

    fun traceLines(): List<String> = synchronized(lock) { events.toList() }

    private fun parseCost(raw: String): Long {
        val matcher = COST.matcher(raw)
        if (!matcher.find()) return -1L
        return matcher.group(1).toLongOrNull() ?: -1L
    }

    private fun isUseful(lower: String): Boolean {
        if (lower.contains("progress=") || lower.contains("sensor sample")
            || lower.contains("recomposition") || lower.contains("frame=")) return false
        return lower.contains("hook") || lower.contains("inject") || lower.contains("request")
            || lower.contains("response") || lower.contains("server") || lower.contains("agent")
            || lower.contains("tool") || lower.contains("fail") || lower.contains("error")
            || lower.contains("activity") || lower.contains("adaptation")
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis.coerceAtLeast(0L) / 1000L
        return "${seconds / 3600L}h ${(seconds / 60L) % 60L}m ${seconds % 60L}s"
    }

    private companion object {
        const val DEFAULT_MAX_EVENTS = 320
        const val MAX_EVENT_CHARS = 420
        const val SLOW_EVENT_MS = 32L
        val COST = Pattern.compile(
            "(?:cost|elapsed|duration)=([0-9]{1,7})ms",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
