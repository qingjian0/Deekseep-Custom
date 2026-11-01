package ds.dsh.DeepSeek;

import android.content.Context;
import android.os.Bundle;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded asynchronous ingress and single-writer for authenticated host log records. */
final class DshLogIpcQueue {
    private static final int CAPACITY = 512;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final AtomicLong PRIVATE_WRITES = new AtomicLong();
    private static final AtomicLong PRIVATE_FAILURES = new AtomicLong();
    private static final AtomicLong PUBLIC_WRITES = new AtomicLong();
    private static final AtomicLong PUBLIC_FAILURES = new AtomicLong();
    private static final AtomicLong INGRESS_REJECTIONS = new AtomicLong();
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(CAPACITY),
            new ThreadFactory() {
                @Override public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task,
                            "DSH-Log-Writer-" + THREAD_IDS.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy());

    private DshLogIpcQueue() {}

    static boolean enqueue(final Context context, final String category, final String line) {
        if (context == null || !isAllowedCategory(category) || line == null
                || line.length() > 4096) {
            INGRESS_REJECTIONS.incrementAndGet();
            return false;
        }
        final Context appContext = context.getApplicationContext();
        final Context safeContext = appContext == null ? context : appContext;
        try {
            WRITER.execute(new Runnable() {
                @Override public void run() {
                    if (DshFileLogStore.appendPrivate(safeContext, category, line)) {
                        PRIVATE_WRITES.incrementAndGet();
                    } else {
                        PRIVATE_FAILURES.incrementAndGet();
                    }
                    if (DshFileLogStore.appendPublic(safeContext, category, line)) {
                        PUBLIC_WRITES.incrementAndGet();
                    } else {
                        PUBLIC_FAILURES.incrementAndGet();
                    }
                }
            });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException full) {
            INGRESS_REJECTIONS.incrementAndGet();
            return false;
        }
    }

    /** Crash records bypass the normal queue so process teardown cannot discard them. */
    static Bundle writeCrashNow(Context context, String line) {
        Bundle result = new Bundle();
        boolean privateWritten = DshFileLogStore.appendPrivate(context, "crash", line);
        boolean publicWritten = DshFileLogStore.appendPublic(context, "crash", line);
        if (privateWritten) PRIVATE_WRITES.incrementAndGet();
        else PRIVATE_FAILURES.incrementAndGet();
        if (publicWritten) PUBLIC_WRITES.incrementAndGet();
        else PUBLIC_FAILURES.incrementAndGet();
        result.putBoolean("private_written", privateWritten);
        result.putBoolean("public_written", publicWritten);
        result.putBoolean("accepted", privateWritten || publicWritten);
        result.putBoolean("queued", false);
        return result;
    }

    static void putStatus(Bundle result) {
        result.putLong("private_writes", PRIVATE_WRITES.get());
        result.putLong("private_failures", PRIVATE_FAILURES.get());
        result.putLong("public_writes", PUBLIC_WRITES.get());
        result.putLong("public_failures", PUBLIC_FAILURES.get());
        result.putLong("ingress_rejections", INGRESS_REJECTIONS.get());
        result.putInt("queue_depth", WRITER.getQueue().size());
        result.putInt("queue_capacity", CAPACITY);
    }

    static boolean isAllowedCategory(String category) {
        return "main".equals(category) || "server".equals(category)
                || "vision".equals(category) || "crash".equals(category);
    }
}
