package ds.dsh.DeepSeek

import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone

/**
 * Pure account-server validation request policy.
 *
 * Kept independent of Android so Legacy and Universal builds share identical request
 * headers, locale formatting, timezone offsets, and bounded retry behavior.
 */
object AccountValidationPolicy {
    private const val HOST_PACKAGE = "com.deepseek.chat"
    private const val DEFAULT_VERSION = "2.2.2"
    private const val DEFAULT_RETRY_MS = 2_500L
    private const val MIN_RETRY_MS = 1_500L
    private const val MAX_INLINE_RETRY_MS = 10_000L

    @JvmStatic
    fun validationUserAgent(version: String?, sdkInt: Int): String {
        var value = javaTrim(version ?: "")
        if (value.isEmpty()) value = DEFAULT_VERSION
        return "DeepSeek/$value Android/${maxOf(1, sdkInt)}"
    }

    @JvmStatic
    fun validationLocale(locale: Locale?): String {
        if (locale == null) return "zh_CN"
        val rawLanguage = locale.language
        val language = if (rawLanguage.isNullOrEmpty()) "zh" else rawLanguage
        val country = locale.country
        return if (country.isNullOrEmpty()) language else "${language}_$country"
    }

    @JvmStatic
    fun validationHeaders(
        token: String?,
        version: String?,
        sdkInt: Int,
        locale: String?,
        rangersId: String?,
        timezoneOffsetSeconds: Int
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        val cleanVersion = javaTrim(version ?: "")
        val cleanLocale = javaTrim(locale ?: "")
        val cleanRangersId = javaTrim(rangersId ?: "")
        headers["Accept"] = "application/json"
        headers["User-Agent"] = validationUserAgent(version, sdkInt)
        headers["Referer"] = "https://chat.deepseek.com"
        headers["Authorization"] = "Bearer $token"
        headers["x-client-platform"] = "android"
        headers["x-client-bundle-id"] = HOST_PACKAGE
        headers["x-client-version"] = if (cleanVersion.isEmpty()) DEFAULT_VERSION else cleanVersion
        headers["x-client-locale"] = if (cleanLocale.isEmpty()) "zh_CN" else cleanLocale
        headers["x-client-timezone-offset"] = timezoneOffsetSeconds.toString()
        if (cleanRangersId.isNotEmpty()) headers["x-rangers-id"] = cleanRangersId
        return headers
    }

    @JvmStatic
    fun validationTimezoneOffsetSeconds(timezone: TimeZone?, nowMs: Long): Int {
        val value = timezone ?: TimeZone.getDefault()
        return value.getOffset(nowMs) / 1000
    }

    @JvmStatic
    fun retryDelayMillis(retryAfter: String?): Long {
        val value = javaTrim(retryAfter ?: "")
        if (value.isEmpty()) return DEFAULT_RETRY_MS
        return try {
            val seconds = java.lang.Long.parseLong(value)
            if (seconds < 0L || seconds > MAX_INLINE_RETRY_MS / 1000L) {
                -1L
            } else {
                maxOf(MIN_RETRY_MS, seconds * 1000L)
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    /** Match java.lang.String.trim(): remove only UTF-16 chars <= U+0020. */
    private fun javaTrim(value: String): String = value.trim { it <= '\u0020' }
}
