package ds.dsh.DeepSeek

import java.util.Locale

/** Pure ZIP entry path and account-file classification rules. */
object AccountImportArchivePolicy {
    @JvmStatic
    @Throws(AccountCredentialCodec.FormatException::class)
    fun safePath(raw: String?): String {
        val path = raw?.replace('\\', '/') ?: ""
        if (path.isEmpty() || path.startsWith('/') || path.indexOf('\u0000') >= 0
            || Regex("^[A-Za-z]:.*").matches(path)
        ) throw AccountCredentialCodec.FormatException("ZIP 含不安全路径：$path")
        if (path.split('/').any { it == ".." })
            throw AccountCredentialCodec.FormatException("ZIP 含不安全路径：$path")
        return path
    }

    @JvmStatic
    fun isSupportedAccountFile(path: String?): Boolean {
        val lower = path?.toLowerCase(Locale.US) ?: return false
        return lower.endsWith(".json") || lower.endsWith(".txt") || lower.endsWith(".ds-account")
    }
}
