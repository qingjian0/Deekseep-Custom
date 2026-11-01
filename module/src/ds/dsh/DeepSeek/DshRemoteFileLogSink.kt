package ds.dsh.DeepSeek

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Bounded asynchronous bridge from the injected host process to the module-owned log provider. */
object DshRemoteFileLogSink {
    private val PROVIDER_URI: Uri =
        Uri.parse("content://" + XposedActivationProvider.AUTHORITY)
    private const val QUEUE_CAPACITY = 256
    private val DROPPED_EVENTS = AtomicLong()
    @Volatile private var installed = false

    private val EXECUTOR = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "dsh-log-ipc").apply { isDaemon = true }
        },
        { _, _ -> DROPPED_EVENTS.incrementAndGet() },
    )

    @JvmStatic
    @Synchronized
    fun install(source: Context?) {
        if (installed || source == null) return
        val context = source.applicationContext ?: source
        val resolver = context.contentResolver
        DshLog.addSink { event ->
            val category = categoryFor(event.tag)
            val line = StringBuilder(256)
                .append("at=").append(event.timestampMillis)
                .append(" level=").append(event.level.name)
                .append(" tag=").append(event.tag)
                .append(" thread=").append(event.threadName)
                .append(" message=").append(event.message)
            event.errorType?.let { line.append(" errorType=").append(it) }
            val dropped = DROPPED_EVENTS.getAndSet(0L)
            if (dropped > 0L) line.append(" droppedBefore=").append(dropped)
            if (category == "crash") {
                writeToProvider(resolver, category, line.toString())
                return@addSink
            }
            try {
                EXECUTOR.execute { writeToProvider(resolver, category, line.toString()) }
            } catch (_: RejectedExecutionException) {
                DROPPED_EVENTS.incrementAndGet()
            } catch (_: Throwable) {
                DROPPED_EVENTS.incrementAndGet()
            }
        }
        installed = true
    }

    private fun writeToProvider(resolver: ContentResolver, category: String, line: String) {
        val payload = Bundle().apply {
            putString("category", category)
            putString("line", line)
        }
        try {
            val result = resolver.call(
                PROVIDER_URI,
                XposedActivationProvider.METHOD_WRITE_DSH_LOG,
                null,
                payload,
            )
            val crashPartial = result != null &&
                result.containsKey("private_written") &&
                result.containsKey("public_written") &&
                result.getBoolean("private_written") != result.getBoolean("public_written")
            if (result == null || !result.getBoolean("accepted", false) ||
                result.getBoolean("partial", false) || crashPartial
            ) {
                DROPPED_EVENTS.incrementAndGet()
            }
        } catch (_: Throwable) {
            DROPPED_EVENTS.incrementAndGet()
        }
    }

    private fun categoryFor(tag: String?): String {
        val normalized = tag?.lowercase(Locale.ROOT) ?: ""
        return when {
            normalized.contains("crash") -> "crash"
            normalized.contains("vision") -> "vision"
            normalized.contains("server") || normalized.contains("relay") -> "server"
            else -> "main"
        }
    }
}
