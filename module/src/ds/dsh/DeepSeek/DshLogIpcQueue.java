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
    private static final int CAPACITY_PER_STORE = 512;
    private static final AtomicLong PRIVATE_WRITES = new AtomicLong();
    private static final AtomicLong PRIVATE_FAILURES = new AtomicLong();
    private static final AtomicLong PUBLIC_WRITES = new AtomicLong();
    private static final AtomicLong PUBLIC_FAILURES = new AtomicLong();
    private static final ThreadPoolExecutor PRIVATE_WRITER = newWriter("private");
    private static final ThreadPoolExecutor PUBLIC_WRITER = newWriter("public");

    private DshLogIpcQueue() {}

    private static ThreadPoolExecutor newWriter(final String store) {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(CAPACITY_PER_STORE),
                new ThreadFactory() {
                    @Override public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task, "DSH-Log-" + store + "-Writer");
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    static boolean enqueue(final Context context, final String category, final String line) {
        if (context == null || !isAllowedCategory(category) || line == null
                || line.length() > 4096) return false;
        final Context appContext = context.getApplicationContext();
        final Context safeContext = appContext == null ? context : appContext;

        // Schedule both stores independently: a slow MediaStore operation must not block
        // private-file persistence, and a private-file failure must not suppress public output.
        boolean privateQueued = submit(PRIVATE_WRITER, PRIVATE_FAILURES, new Runnable() {
            @Override public void run() {
                if (DshFileLogStore.appendPrivate(safeContext, category, line)) {
                    PRIVATE_WRITES.incrementAndGet();
                } else {
                    PRIVATE_FAILURES.incrementAndGet();
                }
            }
        });
        boolean publicQueued = submit(PUBLIC_WRITER, PUBLIC_FAILURES, new Runnable() {
            @Override public void run() {
                if (DshFileLogStore.appendPublic(safeContext, category, line)) {
                    PUBLIC_WRITES.incrementAndGet();
                } else {
                    PUBLIC_FAILURES.incrementAndGet();
                }
            }
        });
        return privateQueued && publicQueued;
    }

    private static boolean submit(ThreadPoolExecutor writer, AtomicLong failures, Runnable task) {
        try {
            writer.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException full) {
            failures.incrementAndGet();
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
        int privateDepth = PRIVATE_WRITER.getQueue().size();
        int publicDepth = PUBLIC_WRITER.getQueue().size();
        result.putInt("private_queue_depth", privateDepth);
        result.putInt("public_queue_depth", publicDepth);
        result.putInt("queue_depth", privateDepth + publicDepth);
        result.putInt("private_queue_capacity", CAPACITY_PER_STORE);
        result.putInt("public_queue_capacity", CAPACITY_PER_STORE);
        result.putInt("queue_capacity", CAPACITY_PER_STORE * 2);
    }

    static boolean isAllowedCategory(String category) {
        return "main".equals(category) || "server".equals(category)
                || "vision".equals(category) || "crash".equals(category);
    }
}
