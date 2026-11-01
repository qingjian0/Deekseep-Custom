package ds.dsh.DeepSeek

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Android Logcat adapter for the platform-neutral Kotlin runtime logger. */
object DshAndroidLogSink {
    private val INSTALLED = AtomicBoolean(false)
    private const val MAX_TAG_LENGTH = 23

    @JvmStatic
    fun install() {
        if (!INSTALLED.compareAndSet(false, true)) return
        try {
            DshLog.addSink { event ->
                val priority = when (event.level) {
                    DshLog.Level.DEBUG -> Log.DEBUG
                    DshLog.Level.INFO -> Log.INFO
                    DshLog.Level.WARN -> Log.WARN
                    DshLog.Level.ERROR -> Log.ERROR
                }
                val tag = ("DSH/" + event.tag).take(MAX_TAG_LENGTH)
                val message = buildString(160) {
                    append("at=").append(event.timestampMillis)
                    append(" thread=").append(event.threadName)
                    append(" message=").append(event.message)
                    event.errorType?.let { append(" errorType=").append(it) }
                }
                Log.println(priority, tag, message)
            }
            DshLog.setConsoleSinkEnabled(false)
        } catch (failure: Throwable) {
            INSTALLED.set(false)
            Log.e(
                "DSH/Logger",
                "Unable to install Android Logcat sink; errorType=" +
                    failure.javaClass.name,
            )
        }
    }
}
