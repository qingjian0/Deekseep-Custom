package ds.dsh.DeepSeek;

import android.content.Context;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded asynchronous ingress for log records received by the module process.
 *
 * <p>ContentProvider.call() runs on a Binder thread. It must enqueue only; disk I/O is performed
 * by this single writer thread so a hooked host thread is never made to wait for file access.</p>
 */
final class DshLogIpcQueue {
    private static final int CAPACITY = 512;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
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
                || line.length() > 4096) return false;
        final Context appContext = context.getApplicationContext();
        final Context safeContext = appContext == null ? context : appContext;
        try {
            WRITER.execute(new Runnable() {
                @Override public void run() {
                    DshFileLogStore.appendPrivate(safeContext, category, line);
                }
            });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException full) {
            return false;
        }
    }

    static boolean isAllowedCategory(String category) {
        return "main".equals(category) || "server".equals(category)
                || "vision".equals(category) || "crash".equals(category);
    }
}
