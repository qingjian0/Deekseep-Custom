package ds.dsh.DeepSeek;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded asynchronous bridge from the injected host process to the module-owned log provider.
 */
final class DshRemoteFileLogSink {
    private static final Uri PROVIDER_URI =
            Uri.parse("content://" + XposedActivationProvider.AUTHORITY);
    private static final int QUEUE_CAPACITY = 256;
    private static final AtomicLong DROPPED_EVENTS = new AtomicLong();
    private static volatile boolean installed;

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "dsh-log-ipc");
                thread.setDaemon(true);
                return thread;
            },
            (runnable, executor) -> DROPPED_EVENTS.incrementAndGet());

    private DshRemoteFileLogSink() {}

    static synchronized void install(Context source) {
        if (installed || source == null) return;
        Context context = source.getApplicationContext() == null
                ? source : source.getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        DshLog.addSink(event -> {
            String category = categoryFor(event.getTag());
            StringBuilder line = new StringBuilder(256)
                    .append("at=").append(event.getTimestampMillis())
                    .append(" level=").append(event.getLevel().name())
                    .append(" tag=").append(event.getTag())
                    .append(" thread=").append(event.getThreadName())
                    .append(" message=").append(event.getMessage());
            if (event.getErrorType() != null) {
                line.append(" errorType=").append(event.getErrorType());
            }
            long dropped = DROPPED_EVENTS.getAndSet(0L);
            if (dropped > 0L) line.append(" droppedBefore=").append(dropped);
            // Crash records cross the process boundary synchronously before the uncaught-
            // exception handler delegates to the previous handler, which normally terminates
            // this host process. The provider writes the bounded crash record immediately.
            if ("crash".equals(category)) {
                writeToProvider(resolver, category, line.toString());
                return;
            }
            try {
                EXECUTOR.execute(() -> writeToProvider(resolver, category, line.toString()));
            } catch (RejectedExecutionException rejected) {
                DROPPED_EVENTS.incrementAndGet();
            } catch (Throwable failure) {
                DROPPED_EVENTS.incrementAndGet();
            }
        });
        installed = true;
    }

    private static void writeToProvider(ContentResolver resolver, String category, String line) {
        Bundle payload = new Bundle();
        payload.putString("category", category);
        payload.putString("line", line);
        try {
            Bundle result = resolver.call(PROVIDER_URI,
                    XposedActivationProvider.METHOD_WRITE_DSH_LOG, null, payload);
            if (result == null || !result.getBoolean("accepted", false)) {
                DROPPED_EVENTS.incrementAndGet();
            }
        } catch (Throwable failure) {
            DROPPED_EVENTS.incrementAndGet();
        }
    }

    private static String categoryFor(String tag) {
        String normalized = tag == null ? "" : tag.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("crash")) return "crash";
        if (normalized.contains("vision")) return "vision";
        if (normalized.contains("server") || normalized.contains("relay")) return "server";
        return "main";
    }
}
