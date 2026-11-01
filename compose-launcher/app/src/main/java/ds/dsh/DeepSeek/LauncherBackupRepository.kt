package ds.dsh.DeepSeek

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import android.system.Os
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class LauncherBackupPlan(
    val settingsJson: String?,
    val launcherFiles: Map<String, ByteArray>,
    val sharedPreferences: Map<String, ByteArray>,
)

/** Owns the launcher ZIP/JSON format and safe restoration of launcher-owned files. */
internal class LauncherBackupRepository {
    fun parse(input: InputStream): LauncherBackupPlan {
        val bytes = readLimited(input)
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) {
            val text = String(bytes, StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
            val root = JSONObject(JSONTokener(text))
            require(root.optString("format") == CONFIG_FORMAT) { "不是有效的 YunSeek / Deekseep 配置文件" }
            return LauncherBackupPlan(text, emptyMap(), emptyMap())
        }

        var settings: String? = null
        var totalBytes = 0
        var entryCount = 0
        val launcher = LinkedHashMap<String, ByteArray>()
        val preferences = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "备份文件包含过多条目" }
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    val target = when {
                        name == ZIP_SETTINGS -> "settings"
                        name.startsWith(ZIP_LAUNCHER_FILES) -> "files"
                        name.startsWith(ZIP_LAUNCHER_PREFS) -> "prefs"
                        else -> "ignore"
                    }
                    val relative = when (target) {
                        "files" -> safeRelativePath(name.removePrefix(ZIP_LAUNCHER_FILES))
                        "prefs" -> safeRelativePath(name.removePrefix(ZIP_LAUNCHER_PREFS))
                        else -> null
                    }
                    val data = readEntryLimited(zis, MAX_IMPORT_BYTES - totalBytes)
                    totalBytes += data.size
                    when (target) {
                        "settings" -> settings = String(data, StandardCharsets.UTF_8)
                        "files" -> if (data.size <= MAX_LAUNCHER_FILE) launcher[relative!!] = data
                        "prefs" -> if (data.size <= MAX_LAUNCHER_FILE) preferences[relative!!] = data
                    }
                }
                zis.closeEntry()
            }
        }
        if (settings != null) {
            val root = JSONObject(JSONTokener(settings))
            require(root.optString("format") == CONFIG_FORMAT) { "ZIP 中的模块配置格式无效" }
        }
        require(settings != null || launcher.isNotEmpty() || preferences.isNotEmpty()) {
            "ZIP 中没有可恢复的 YunSeek 数据"
        }
        return LauncherBackupPlan(settings, launcher, preferences)
    }

    fun writeZip(
        output: ZipOutputStream,
        settingsJson: String?,
        filesDirectory: File,
        sharedPreferencesDirectory: File,
        appName: String,
        appVersion: String,
        appVersionCode: Int,
        hostLabel: String,
    ): Int {
        var count = 0
        var totalBytes = 0L
        val contents = JSONArray()
        fun put(name: String, bytes: ByteArray) {
            require(totalBytes + bytes.size <= MAX_IMPORT_BYTES) { "备份内容超过 64 MB 限制" }
            output.putNextEntry(ZipEntry(name))
            output.write(bytes)
            output.closeEntry()
            contents.put(name)
            count++
            totalBytes += bytes.size
        }
        if (settingsJson != null) put(ZIP_SETTINGS, settingsJson.toByteArray(StandardCharsets.UTF_8))
        listOf(
            filesDirectory to ZIP_LAUNCHER_FILES,
            sharedPreferencesDirectory to ZIP_LAUNCHER_PREFS,
        ).forEach { (root, prefix) ->
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { it.isFile && it.length() <= MAX_LAUNCHER_FILE }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && bytes.size <= MAX_LAUNCHER_FILE) {
                    put(prefix + relative, bytes)
                }
            }
        }
        val metadata = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", 1)
            .put("app", appName)
            .put("app_version", appVersion)
            .put("app_version_code", appVersionCode)
            .put("deepseek", hostLabel)
            .put("created_at", System.currentTimeMillis())
            .put("contains_module_settings", settingsJson != null)
            .put("contents", contents)
        output.putNextEntry(ZipEntry(ZIP_META))
        output.write(metadata.toString(2).toByteArray(StandardCharsets.UTF_8))
        output.closeEntry()
        output.putNextEntry(ZipEntry("README.txt"))
        output.write(
            ("YunSeek 模块数据备份\n\n" +
                "settings/deekseep-config.json：通过配置桥导出的模块设置（与旧版“导出配置”格式相同）。\n" +
                "launcher/：YunSeek 启动器自身的数据文件。\n\n" +
                "在新安装的 YunSeek 中选择“导入恢复”并选择此 ZIP 即可恢复。\n" +
                "提示词库、置顶与会话记录保存在 DeepSeek 私有目录，不在此备份中。\n")
                .toByteArray(StandardCharsets.UTF_8),
        )
        output.closeEntry()
        return count
    }

    fun restore(plan: LauncherBackupPlan, filesDirectory: File, sharedPreferencesDirectory: File): Int =
        restoreFiles(plan.launcherFiles, filesDirectory) +
            restoreFiles(plan.sharedPreferences, sharedPreferencesDirectory)

    private fun restoreFiles(files: Map<String, ByteArray>, root: File): Int {
        if (files.isEmpty()) return 0
        val canonicalRoot = root.canonicalFile
        require(canonicalRoot.mkdirs() || canonicalRoot.isDirectory) { "无法创建启动器数据目录" }
        var restored = 0
        files.forEach { (relative, bytes) ->
            val target = File(canonicalRoot, safeRelativePath(relative)).canonicalFile
            require(target.path.startsWith(canonicalRoot.path + File.separator)) { "备份路径超出启动器数据目录" }
            val parent = target.parentFile ?: error("备份目标目录无效")
            require(parent.mkdirs() || parent.isDirectory) { "无法创建备份目录" }
            val temporary = File(parent, ".${target.name}.restore-${System.nanoTime()}")
            try {
                temporary.outputStream().use { it.write(bytes) }
                // POSIX rename is atomic on the same filesystem and is available from API 21,
                // preserving the app's minSdk 24 without using java.nio.file (API 26).
                Os.rename(temporary.absolutePath, target.absolutePath)
                restored++
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
        return restored
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_IMPORT_BYTES) { "文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun readEntryLimited(input: InputStream, remaining: Int): ByteArray {
        require(remaining >= 0) { "备份解压内容超过限制" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= remaining) { "备份解压内容超过限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun safeRelativePath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/')) { "备份路径无效" }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) { "备份路径无效" }
        return segments.joinToString(File.separator)
    }

    private companion object {
        const val CONFIG_FORMAT = "deekseep-config"
        const val BACKUP_FORMAT = "yunseek-backup"
        const val ZIP_META = "yunseek-backup.json"
        const val ZIP_SETTINGS = "settings/deekseep-config.json"
        const val ZIP_LAUNCHER_FILES = "launcher/files/"
        const val ZIP_LAUNCHER_PREFS = "launcher/shared_prefs/"
        const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
        const val MAX_LAUNCHER_FILE = 4 * 1024 * 1024
        const val MAX_ENTRIES = 4096
    }
}
