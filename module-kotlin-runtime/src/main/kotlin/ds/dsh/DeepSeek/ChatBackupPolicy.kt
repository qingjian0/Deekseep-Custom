package ds.dsh.DeepSeek

/** Pure schema and entry-name validation for portable chat backups. */
object ChatBackupPolicy {
    @JvmStatic
    fun validManifest(format: String?, version: Int): Boolean =
        format == "deekseep-chat-backup" && version == 1

    @JvmStatic
    fun portableSessionEntry(name: String?): Boolean {
        if (name == null || !name.startsWith("sessions/") || !name.endsWith(".json")
            || name.contains("..") || name.indexOf('\\') >= 0
        ) return false
        val leaf = name.substring("sessions/".length, name.length - 5)
        return leaf.length > 4 && leaf.indexOf('/') < 0
    }
}
