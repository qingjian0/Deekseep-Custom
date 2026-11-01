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
        ThreadPoolExecutor.AbortPolicy(),
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
            // Reserve prior ingress drops for this record. Restore them if this record
            // cannot be accepted by both provider-side queues.
            val dropped = DROPPED_EVENTS.getAndSet(0L)
            if (dropped > 0L) line.append(" droppedBefore=").append(dropped)
            val record = line.toString()
            if (category == "crash") {
                writeToProvider(resolver, category, record, dropped)
                return@addSink
            }
            try {
                EXECUTOR.execute { writeToProvider(resolver, category, record, dropped) }
            } catch (_: RejectedExecutionException) {
                DROPPED_EVENTS.addAndGet(dropped + 1L)
            } catch (_: Throwable) {
                DROPPED_EVENTS.addAndGet(dropped + 1L)
            }
        }
        installed = true
    }

    private fun writeToProvider(
        resolver: ContentResolver,
        category: String,
        line: String,
        carriedDropCount: Long,
    ) {
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
            val notAcceptedByBothQueuesOrStores = result == null ||
                !result.getBoolean("accepted", false) ||
                result.getBoolean("partial", false) ||
                crashPartial
            if (notAcceptedByBothQueuesOrStores) {
                // A normal record is accepted when both provider queues accept it;
                // a crash record is accepted only when both stores report a write.
                DROPPED_EVENTS.addAndGet(carriedDropCount + 1L)
            }
        } catch (_: Throwable) {
            DROPPED_EVENTS.addAndGet(carriedDropCount + 1L)
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
