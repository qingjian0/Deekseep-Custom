package ds.dsh.DeepSeek;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

final class DshFileLogStore {
    private static final String PUBLIC_DIRECTORY = "ds";
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int BACKUP_COUNT = 3;
    private static final int MAX_LINE_CHARS = 2048;

    private DshFileLogStore() {}

    /** Must run in the module application's process so filesDir belongs to ds.dsh.DeepSeek. */
    static synchronized boolean appendPrivate(Context context, String category, String line) {
        if (context == null || !isAllowedCategory(category) || line == null) return false;
        return appendFile(context.getFilesDir(), category + ".log", line);
    }

    /** Reads only the bounded tail of the module-private crash log for the authenticated host UI. */
    static synchronized String readPrivateCrashTail(Context context, int requestedMaxBytes) {
        if (context == null) return "";
        int maxBytes = Math.max(1, Math.min(requestedMaxBytes, 512 * 1024));
        File target = new File(context.getFilesDir(), "crash.log");
        if (!target.isFile() || target.length() <= 0L) return "";
        try (RandomAccessFile input = new RandomAccessFile(target, "r")) {
            long start = Math.max(0L, input.length() - maxBytes);
            input.seek(start);
            if (start > 0L) {
                // Drop a partial first line; the remainder is valid UTF-8 log content.
                while (input.getFilePointer() < input.length() && input.read() != '\n') { }
            }
            int remaining = (int) Math.min(maxBytes, input.length() - input.getFilePointer());
            byte[] bytes = new byte[remaining];
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Writes Download/ds through MediaStore on Q+, with the legacy permission path on P-. */
    static synchronized boolean appendPublic(Context context, String category, String line) {
        if (context == null || !isAllowedCategory(category) || line == null) return false;
        String safeLine = sanitize(line);
        if (safeLine == null) return false;
        byte[] bytes = (safeLine + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return appendMediaStore(context.getContentResolver(), category, bytes);
            }
            if (Build.VERSION.SDK_INT >= 23
                    && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            return appendFile(new File(downloads, PUBLIC_DIRECTORY), category + ".log", safeLine);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean appendFile(File directory, String fileName, String line) {
        try {
            if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
                return false;
            }
            File target = new File(directory, fileName);
            String boundedLine = sanitize(line);
            if (boundedLine == null) return false;
            byte[] bytes = (boundedLine + "\n").getBytes(StandardCharsets.UTF_8);
            if (target.exists() && target.length() + bytes.length > MAX_FILE_BYTES
                    && !rotateFile(target)) {
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

    private static boolean appendMediaStore(ContentResolver resolver, String category,
                                            byte[] bytes) throws Exception {
        final String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIRECTORY + "/";
        final String fileName = category + ".log";
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri current = findDownload(resolver, collection, fileName, relativePath);
        long currentSize = 0L;
        if (current != null) {
            int pendingState = queryPending(resolver, current);
            if (pendingState < 0) return false;
            // Recover a row left pending by a process death or a failed earlier append.
            // Publish it before rotation so a later append cannot leave it permanently hidden.
            if (pendingState == 1 && !publishMediaStore(resolver, current)) return false;
            currentSize = querySize(resolver, current);
            if (currentSize < 0L) return false;
            if (currentSize + bytes.length > MAX_FILE_BYTES) {
                if (!rotateMediaStore(resolver, collection, category, relativePath)) return false;
                current = null;
            }
        }
        boolean created = current == null;
        if (created) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            current = resolver.insert(collection, values);
            if (current == null) return false;
        }
        try (OutputStream output = resolver.openOutputStream(current, created ? "w" : "wa")) {
            if (output == null) return false;
            output.write(bytes);
            output.flush();
        }
        if (created && !publishMediaStore(resolver, current)) return false;
        return true;
    }

    /** Returns 1 for pending, 0 for published, and -1 when the state cannot be read. */
    private static int queryPending(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[] { MediaStore.MediaColumns.IS_PENDING }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getInt(0);
        } catch (Throwable ignored) {}
        return -1;
    }

    private static boolean publishMediaStore(ContentResolver resolver, Uri uri) {
        ContentValues publish = new ContentValues();
        publish.put(MediaStore.MediaColumns.IS_PENDING, 0);
        return resolver.update(uri, publish, null, null) > 0;
    }

    private static Uri findDownload(ContentResolver resolver, Uri collection,
                                    String name, String relativePath) {
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        try (Cursor cursor = resolver.query(collection, projection, selection,
                new String[] { name, relativePath }, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return Uri.withAppendedPath(collection, Long.toString(cursor.getLong(0)));
            }
        }
        return null;
    }

    private static long querySize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[] { MediaStore.MediaColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        } catch (Throwable ignored) {}
        return -1L;
    }

    private static boolean rotateMediaStore(ContentResolver resolver, Uri collection,
                                            String category, String relativePath) {
        try {
            Uri oldest = findDownload(resolver, collection,
                    category + ".log." + BACKUP_COUNT, relativePath);
            if (oldest != null && resolver.delete(oldest, null, null) <= 0) return false;
            for (int index = BACKUP_COUNT - 1; index >= 1; index--) {
                Uri source = findDownload(resolver, collection,
                        category + ".log." + index, relativePath);
                if (source != null && !renameMediaStore(resolver, source,
                        category + ".log." + (index + 1))) return false;
            }
            Uri current = findDownload(resolver, collection, category + ".log", relativePath);
            return current == null || renameMediaStore(resolver, current, category + ".log.1");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean renameMediaStore(ContentResolver resolver, Uri uri, String newName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, newName);
        return resolver.update(uri, values, null, null) > 0;
    }

    private static boolean rotateFile(File target) {
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

    private static String sanitize(String value) {
        if (value == null) return null;
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        String safe = DshLog.sanitizeForStorage(normalized);
        return safe.length() > MAX_LINE_CHARS ? safe.substring(0, MAX_LINE_CHARS) : safe;
    }

    private static boolean isAllowedCategory(String category) {
        return "main".equals(category) || "server".equals(category)
                || "vision".equals(category) || "crash".equals(category);
    }
}
