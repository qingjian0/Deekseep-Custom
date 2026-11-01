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

    @JvmStatic
    fun shortId(id: String?): String {
        if (id == null) return "未知账号"
        val value = javaTrim(id).lowercase(Locale.US)
        return if (value.length > 8) value.substring(0, 8) else value
    }
}
