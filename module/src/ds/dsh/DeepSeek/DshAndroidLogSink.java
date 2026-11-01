package ds.dsh.DeepSeek;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android adapter for the platform-neutral Kotlin runtime logger.
 *
 * <p>Install from the injected host process before feature-hook installation begins. The adapter
 * writes only to Logcat; file storage remains a separate, process-aware concern.</p>
 */
final class DshAndroidLogSink {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final int MAX_TAG_LENGTH = 23;

    private DshAndroidLogSink() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            DshLog.addSink(event -> {
                int priority;
                switch (event.getLevel()) {
                    case DEBUG:
                        priority = Log.DEBUG;
                        break;
                    case INFO:
                        priority = Log.INFO;
                        break;
                    case WARN:
                        priority = Log.WARN;
                        break;
                    case ERROR:
                        priority = Log.ERROR;
                        break;
                    default:
                        priority = Log.INFO;
                        break;
                }

                String tag = "DSH/" + event.getTag();
                if (tag.length() > MAX_TAG_LENGTH) {
                    tag = tag.substring(0, MAX_TAG_LENGTH);
                }
                StringBuilder message = new StringBuilder(160)
                        .append("at=").append(event.getTimestampMillis())
                        .append(" thread=").append(event.getThreadName())
                        .append(" message=").append(event.getMessage());
                if (event.getErrorType() != null) {
                    message.append(" errorType=").append(event.getErrorType());
                }
                Log.println(priority, tag, message.toString());
            });
        } catch (Throwable failure) {
            INSTALLED.set(false);
            Log.e("DSH/Logger", "Unable to install Android Logcat sink; errorType="
                    + failure.getClass().getName());
        }
    }
}
