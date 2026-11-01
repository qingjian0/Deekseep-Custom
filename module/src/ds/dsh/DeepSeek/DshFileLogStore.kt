package ds.dsh.DeepSeek

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

object DshFileLogStore {
    private const val PUBLIC_DIRECTORY = "ds"
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    private const val BACKUP_COUNT = 3
    private const val MAX_LINE_CHARS = 2048
    private val PRIVATE_LOCK = Any()
    private val PUBLIC_LOCK = Any()

    @JvmStatic
    fun appendPrivate(context: Context?, category: String?, line: String?): Boolean =
        synchronized(PRIVATE_LOCK) {
            if (context == null || !isAllowedCategory(category) || line == null) false
            else appendFile(context.filesDir, "$category.log", line)
        }

    @JvmStatic
    fun readPrivateCrashTail(context: Context?, requestedMaxBytes: Int): String =
        synchronized(PRIVATE_LOCK) {
            if (context == null) return@synchronized ""
            val maxBytes = requestedMaxBytes.coerceIn(1, 512 * 1024)
            val files = arrayOf("crash.log.3", "crash.log.2", "crash.log.1", "crash.log")
                .map { File(context.filesDir, it) }
            val combined = ByteArrayOutputStream(maxBytes)
            for (file in files) {
                if (!file.isFile || file.length() <= 0L) continue
                val chunk = readFileTail(file, maxBytes)
                if (chunk.isEmpty()) continue
                try {
                    combined.write(chunk)
                    val all = combined.toByteArray()
                    if (all.size > maxBytes) {
                        var offset = all.size - maxBytes
                        while (offset < all.size && all[offset] != 10.toByte()) offset++
                        if (offset < all.size) offset++
                        combined.reset()
                        combined.write(all, offset, all.size - offset)
                    }
                } catch (_: Throwable) {
                    return@synchronized String(combined.toByteArray(), StandardCharsets.UTF_8)
                }
            }
            String(combined.toByteArray(), StandardCharsets.UTF_8)
        }

    private fun readFileTail(file: File, maxBytes: Int): ByteArray = try {
        RandomAccessFile(file, "r").use { input ->
            val start = (input.length() - maxBytes).coerceAtLeast(0L)
            input.seek(start)
            if (start > 0L) {
                while (input.filePointer < input.length() && input.read() != 10) { }
            }
            val count = minOf(maxBytes.toLong(), input.length() - input.filePointer).toInt()
            ByteArray(count).also { input.readFully(it) }
        }
    } catch (_: Throwable) {
        ByteArray(0)
    }

    @JvmStatic
    fun appendPublic(context: Context?, category: String?, line: String?): Boolean =
        synchronized(PUBLIC_LOCK) {
            if (context == null || !isAllowedCategory(category) || line == null) {
                return@synchronized false
            }
            val safe = sanitize(line) ?: return@synchronized false
            val bytes = (safe + "\n").toByteArray(StandardCharsets.UTF_8)
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    appendMediaStore(context.contentResolver, category!!, bytes)
                } else {
                    if (Build.VERSION.SDK_INT >= 23 &&
                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                    ) return@synchronized false
                    val downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS)
                    appendFile(File(downloads, PUBLIC_DIRECTORY), "$category.log", safe)
                }
            } catch (_: Throwable) { false }
        }

    private fun appendFile(directory: File, name: String, line: String): Boolean = try {
        if ((!directory.isDirectory && !directory.mkdirs()) || !directory.isDirectory) return false
        val target = File(directory, name)
        val safe = sanitize(line) ?: return false
        val bytes = (safe + "\n").toByteArray(StandardCharsets.UTF_8)
        if (target.exists() && target.length() + bytes.size > MAX_FILE_BYTES &&
            !rotateFile(target)) return false
        FileOutputStream(target, true).use { it.write(bytes); it.flush() }
        true
    } catch (_: Throwable) { false }

    private fun appendMediaStore(
        resolver: ContentResolver, category: String, bytes: ByteArray,
    ): Boolean {
        val relative = Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIRECTORY + "/"
        val name = "$category.log"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var current = findDownload(resolver, collection, name, relative)
        if (current != null) {
            val pending = queryPending(resolver, current)
            if (pending < 0) return false
            if (pending == 1) {
                if (resolver.delete(current, null, null) <= 0) return false
                current = null
            }
        }
        if (current != null) {
            val size = querySize(resolver, current)
            if (size < 0L) return false
            if (size + bytes.size > MAX_FILE_BYTES) {
                if (!rotateMediaStore(resolver, collection, category, relative)) return false
                current = null
            }
        }
        val created = current == null
        if (created) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            current = resolver.insert(collection, values) ?: return false
        }
        val uri = current ?: return false
        resolver.openOutputStream(uri, if (created) "w" else "wa")?.use {
            it.write(bytes); it.flush()
        } ?: return false
        if (created && !publishMediaStore(resolver, uri)) return false
        return true
    }

    private fun queryPending(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null)
            ?.use { if (it.moveToFirst()) it.getInt(0) else -1 } ?: -1
    } catch (_: Throwable) { -1 }

    private fun publishMediaStore(resolver: ContentResolver, uri: Uri): Boolean {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        return resolver.update(uri, values, null, null) > 0
    }

    private fun findDownload(
        resolver: ContentResolver, collection: Uri, name: String, relative: String,
    ): Uri? {
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
            MediaStore.MediaColumns.RELATIVE_PATH + "=?"
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection,
            arrayOf(name, relative), null)?.use {
            if (it.moveToFirst()) Uri.withAppendedPath(collection, it.getLong(0).toString())
            else null
        } ?: throw IllegalStateException("MediaStore query returned no cursor")
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
    } catch (_: Throwable) { -1L }

    private fun rotateMediaStore(
        resolver: ContentResolver, collection: Uri, category: String, relative: String,
    ): Boolean = try {
        val oldest = findDownload(resolver, collection, "$category.log.$BACKUP_COUNT", relative)
        if (oldest != null && resolver.delete(oldest, null, null) <= 0) return false
        for (i in BACKUP_COUNT - 1 downTo 1) {
            val source = findDownload(resolver, collection, "$category.log.$i", relative)
            if (source != null && !renameMediaStore(resolver, source, "$category.log.${i + 1}"))
                return false
        }
        val active = findDownload(resolver, collection, "$category.log", relative)
        active == null || renameMediaStore(resolver, active, "$category.log.1")
    } catch (_: Throwable) { false }

    private fun renameMediaStore(resolver: ContentResolver, uri: Uri, name: String): Boolean {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
        return resolver.update(uri, values, null, null) > 0
    }

    private fun rotateFile(target: File): Boolean {
        val parent = target.parentFile
        val oldest = File(parent, target.name + "." + BACKUP_COUNT)
        if (oldest.exists() && !oldest.delete()) return false
        for (i in BACKUP_COUNT - 1 downTo 1) {
            val source = File(parent, target.name + "." + i)
            if (source.exists() && !source.renameTo(File(parent, target.name + "." + (i + 1))))
                return false
        }
        val first = File(parent, target.name + ".1")
        return !target.exists() || target.renameTo(first)
    }

    private fun sanitize(value: String?): String? {
        if (value == null) return null
        val safe = DshLog.sanitizeForStorage(value.replace('\n', ' ').replace('\r', ' '))
        return if (safe.length > MAX_LINE_CHARS) safe.substring(0, MAX_LINE_CHARS) else safe
    }

    private fun isAllowedCategory(category: String?): Boolean =
        category == "main" || category == "server" || category == "vision" || category == "crash"
}
