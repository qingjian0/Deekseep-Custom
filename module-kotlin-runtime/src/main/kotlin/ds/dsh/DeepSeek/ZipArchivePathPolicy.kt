package ds.dsh.DeepSeek

/** Pure ZIP entry path traversal policy shared by account archive adapters. */
object ZipArchivePathPolicy {
    @JvmStatic
    fun isSafePath(path: String?): Boolean {
        if (path.isNullOrEmpty() || path.startsWith("/") || path.indexOf('\u0000') >= 0) {
            return false
        }
        if (path.matches(Regex("^[A-Za-z]:.*"))) return false
        return path.split('/').none { it == ".." }
    }
}
