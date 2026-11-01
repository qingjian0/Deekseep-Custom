package ds.dsh.DeepSeek

import android.content.Context
import android.os.Bundle
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Bounded asynchronous ingress and single-writer for authenticated host log records. */
object DshLogIpcQueue {
    private const val CAPACITY_PER_STORE = 512
    private val PRIVATE_WRITES = AtomicLong()
    private val PRIVATE_FAILURES = AtomicLong()
    private val PUBLIC_WRITES = AtomicLong()
    private val PUBLIC_FAILURES = AtomicLong()
    private val INGRESS_REJECTIONS = AtomicLong()
    private val PRIVATE_WRITER = newWriter("private")
    private val PUBLIC_WRITER = newWriter("public")

    private fun newWriter(store: String): ThreadPoolExecutor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(CAPACITY_PER_STORE),
            ThreadFactory { task ->
                Thread(task, "DSH-Log-$store-Writer").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )

    @JvmStatic
    fun enqueue(context: Context?, category: String?, line: String?): Bundle {
        val result = Bundle()
        if (context == null || !isAllowedCategory(category) || line == null || line.length > 4096) {
            INGRESS_REJECTIONS.incrementAndGet()
            result.putBoolean("accepted", false)
            result.putBoolean("queued", false)
            result.putBoolean("private_queued", false)
            result.putBoolean("public_queued", false)
            result.putString("error", "invalid log payload")
            return result
        }
        val safeContext = context.applicationContext ?: context

        // Keep the two stores independent: neither writer can block the other's queue.
        val privateQueued = submit(PRIVATE_WRITER, PRIVATE_FAILURES) {
            if (DshFileLogStore.appendPrivate(safeContext, category, line)) {
                PRIVATE_WRITES.incrementAndGet()
            } else {
                PRIVATE_FAILURES.incrementAndGet()
            }
        }
        val publicQueued = submit(PUBLIC_WRITER, PUBLIC_FAILURES) {
            if (DshFileLogStore.appendPublic(safeContext, category, line)) {
                PUBLIC_WRITES.incrementAndGet()
            } else {
                PUBLIC_FAILURES.incrementAndGet()
            }
        }
        if (!privateQueued || !publicQueued) INGRESS_REJECTIONS.incrementAndGet()
        result.putBoolean("accepted", privateQueued || publicQueued)
        result.putBoolean("queued", privateQueued || publicQueued)
        result.putBoolean("private_queued", privateQueued)
        result.putBoolean("public_queued", publicQueued)
        result.putBoolean("partial", privateQueued != publicQueued)
        if (!privateQueued && !publicQueued) result.putString("error", "log queues full")
        return result
    }

    private inline fun submit(
        writer: ThreadPoolExecutor,
        failures: AtomicLong,
        crossinline task: () -> Unit,
    ): Boolean {
        return try {
            writer.execute { task() }
            true
        } catch (_: RejectedExecutionException) {
            failures.incrementAndGet()
            false
        }
    }

    /** Crash records bypass the normal queue so process teardown cannot discard them. */
    @JvmStatic
    fun writeCrashNow(context: Context?, line: String?): Bundle {
        val result = Bundle()
        val privateWritten = DshFileLogStore.appendPrivate(context, "crash", line)
        val publicWritten = DshFileLogStore.appendPublic(context, "crash", line)
        if (privateWritten) PRIVATE_WRITES.incrementAndGet()
        else PRIVATE_FAILURES.incrementAndGet()
        if (publicWritten) PUBLIC_WRITES.incrementAndGet()
        else PUBLIC_FAILURES.incrementAndGet()
        result.putBoolean("private_written", privateWritten)
        result.putBoolean("public_written", publicWritten)
        result.putBoolean("accepted", privateWritten || publicWritten)
        result.putBoolean("queued", false)
        return result
    }

    @JvmStatic
    fun putStatus(result: Bundle) {
        result.putLong("private_writes", PRIVATE_WRITES.get())
        result.putLong("private_failures", PRIVATE_FAILURES.get())
        result.putLong("public_writes", PUBLIC_WRITES.get())
        result.putLong("public_failures", PUBLIC_FAILURES.get())
        result.putLong("ingress_rejections", INGRESS_REJECTIONS.get())
        val privateDepth = PRIVATE_WRITER.queue.size
        val publicDepth = PUBLIC_WRITER.queue.size
        result.putInt("private_queue_depth", privateDepth)
        result.putInt("public_queue_depth", publicDepth)
        result.putInt("queue_depth", privateDepth + publicDepth)
        result.putInt("private_queue_capacity", CAPACITY_PER_STORE)
        result.putInt("public_queue_capacity", CAPACITY_PER_STORE)
        result.putInt("queue_capacity", CAPACITY_PER_STORE * 2)
    }

    @JvmStatic
    fun isAllowedCategory(category: String?): Boolean =
        category == "main" || category == "server" || category == "vision" || category == "crash"
}
