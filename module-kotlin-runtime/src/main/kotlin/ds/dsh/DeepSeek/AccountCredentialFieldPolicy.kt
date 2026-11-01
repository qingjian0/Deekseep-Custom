package ds.dsh.DeepSeek

import java.util.Locale

/**
 * Pure field-level compatibility rules for account credential import/export.
 *
 * JSONObject parsing, unknown-field preservation, and document framing remain in the
 * Android-facing Java codec. Keep these limits and normalization rules byte-for-byte
 * compatible with its historical Java String.trim()/Locale.US behavior.
 */
object AccountCredentialFieldPolicy {
    const val MIN_ID_LENGTH = 8
    const val MAX_ID_LENGTH = 128
    const val MIN_TOKEN_LENGTH = 16
    const val MAX_TOKEN_LENGTH = 16_384

    private fun javaTrim(value: String): String = value.trim { it <= ' ' }

    @JvmStatic
    fun trim(value: String?): String = value?.let(::javaTrim).orEmpty()

    @JvmStatic
    fun isValidIdLength(length: Int): Boolean =
        length in MIN_ID_LENGTH..MAX_ID_LENGTH

    @JvmStatic
    fun isValidTokenLength(length: Int): Boolean =
        length in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH

    @JvmStatic
    fun isIntegralNumber(value: Double): Boolean =
        !value.isNaN() && !value.isInfinite() && value == Math.rint(value)

    /** Java-compatible filename component sanitization used by account export. */
    @JvmStatic
    fun safeFileName(raw: String?): String {
        if (raw == null) return ""
        var clean = javaTrim(raw).replace(Regex("""[\\/:*?"<>|\\p{Cntrl}]"""), "_")
        clean = clean.replace(Regex("""[. ]+$"""), "")
        if (clean.length > 40) clean = clean.substring(0, 40)
        return clean
    }

    /** Builds the export filename from at most three non-empty sanitized account labels. */
    @JvmStatic
    fun suggestFileName(rawNames: List<String?>?, totalCount: Int): String {
        val names = ArrayList<String>(3)
        if (rawNames != null) {
            for (raw in rawNames) {
                if (names.size >= 3) break
                val clean = safeFileName(raw)
                if (clean.isNotEmpty()) names.add(clean)
            }
        }
        val base = StringBuilder()
        for (name in names) {
            if (base.isNotEmpty()) base.append('_')
            base.append(name)
        }
        if (base.isEmpty()) base.append("DeepSeek账号")
        if (totalCount > names.size) base.append("_等").append(totalCount).append("个账号")
        if (base.length > 96) base.setLength(96)
        return base.append("_DeepSeek账号.txt").toString()
    }

    @JvmStatic
    fun shortId(id: String?): String {
        if (id == null) return "未知账号"
        val value = javaTrim(id).lowercase(Locale.US)
        return if (value.length > 8) value.substring(0, 8) else value
    }
}
