package ds.dsh.DeepSeek

import java.util.Locale

/** Pure filename and account-label text rules shared by account import/export. */
object AccountCredentialTextPolicy {
    @JvmStatic
    fun safeFileNamePart(raw: String?): String {
        if (raw == null) return ""
        var clean = raw.trim().replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        clean = clean.replace(Regex("""[. ]+$"""), "")
        if (clean.length > 40) clean = clean.substring(0, 40)
        return clean
    }

    @JvmStatic
    fun shortId(id: String?): String {
        if (id == null) return "未知账号"
        val value = id.trim().lowercase(Locale.US)
        return if (value.length > 8) value.substring(0, 8) else value
    }
}
