package ds.dsh.DeepSeek;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Process-local file store for structured DSH diagnostics.
 *
 * <p>This class must only be called in the module application's process. Hook processes must
 * forward events through the authenticated provider instead of opening the module private path.</p>
 */
final class DshFileLogStore {
    private static final String DIRECTORY_NAME = "dsh-logs";
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int BACKUP_COUNT = 3;
    private static final int MAX_LINE_CHARS = 2048;

    private DshFileLogStore() {}

    static synchronized boolean appendPrivate(Context context, String category, String line) {
        if (context == null || !isAllowedCategory(category) || line == null) return false;
        try {
            File directory = new File(context.getFilesDir(), DIRECTORY_NAME);
            if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
                return false;
            }
            File target = new File(directory, category + ".log");
            String boundedLine = line.length() > MAX_LINE_CHARS
                    ? line.substring(0, MAX_LINE_CHARS) : line;
            byte[] bytes = (boundedLine + "\n").getBytes(StandardCharsets.UTF_8);
            if (target.exists() && target.length() + bytes.length > MAX_FILE_BYTES
                    && !rotate(target)) {
                return false;
            }
            try (FileOutputStream output = new FileOutputStream(target, true)) {
                output.write(bytes);
                output.flush();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean rotate(File target) {
        File parent = target.getParentFile();
        File oldest = new File(parent, target.getName() + "." + BACKUP_COUNT);
        if (oldest.exists() && !oldest.delete()) return false;
        for (int index = BACKUP_COUNT - 1; index >= 1; index--) {
            File source = new File(parent, target.getName() + "." + index);
            if (source.exists()) {
                File destination = new File(parent, target.getName() + "." + (index + 1));
                if (!source.renameTo(destination)) return false;
            }
        }
        File firstBackup = new File(parent, target.getName() + ".1");
        return !target.exists() || target.renameTo(firstBackup);
    }

    private static boolean isAllowedCategory(String category) {
        return "main".equals(category) || "server".equals(category)
                || "vision".equals(category) || "crash".equals(category);
    }
}
