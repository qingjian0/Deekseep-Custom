package ds.dsh.DeepSeek

import java.util.Locale

/** Pure ZIP entry path and account-file classification rules. */
object AccountImportArchivePolicy {
    @JvmStatic
    fun normalizePath(raw: String?): String = raw?.replace('\\', '/') ?: ""

    @JvmStatic
    fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith('/') || path.indexOf('\u0000') >= 0
            || Regex("^[A-Za-z]:.*").matches(path)
        ) return false
        return path.split('/').none { it == ".." }
    }

    @JvmStatic
    fun isSupportedAccountFile(path: String?): Boolean {
        val lower = path?.lowercase(Locale.US) ?: return false
        return lower.endsWith(".json") || lower.endsWith(".txt")
            || lower.endsWith(".ds-account")
    }
}
