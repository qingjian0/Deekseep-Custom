package ds.dsh.DeepSeek

/**
 * Pure validation rules for portable chat-backup record identifiers.
 *
 * Keep the accepted alphabet ASCII-only: these values are also used to derive SQLite table
 * identifiers. SQL, JSON, cursor conversion, and transaction handling remain Android-side.
 */
object ChatBackupRecordPolicy {
    const val MAX_SESSION_ID_LENGTH = 128

    @JvmStatic
    fun isValidSessionId(sid: String?): Boolean {
        if (sid.isNullOrEmpty() || sid.length > MAX_SESSION_ID_LENGTH) return false
        for (c in sid) {
            if (!((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')
                        || c == '-' || c == '_')) return false
        }
        return true
    }
}
