package ds.dsh.DeepSeek

import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone

/**
 * Pure request-compatibility policy for the host account-validation endpoint.
 *
 * Android remains responsible for obtaining host/device values and performing HTTP I/O.
 * Keep header names, defaults, and retry boundaries aligned with the host Ktor client.
 */
object AccountValidationPolicy {
    private const val DEFAULT_VERSION = "2.2.2"
    private const val DEFAULT_LOCALE = "zh_CN"
    private const val HOST_BUNDLE_ID = "com.deepseek.chat"
    private const val MIN_RETRY_INTERVAL_MS = 1_500L
    private const val DEFAULT_RETRY_MS = 2_500L
    private const val MAX_INLINE_RETRY_MS = 10_000L

    private fun javaTrim(value: String): String = value.trim { it <= ' ' }

    @JvmStatic
    fun userAgent(version: String?, sdkInt: Int): String {
        val value = version?.let(::javaTrim).orEmpty().ifEmpty { DEFAULT_VERSION }
        return "DeepSeek/$value Android/${maxOf(1, sdkInt)}"
    }

    @JvmStatic
    fun locale(locale: Locale?): String {
        if (locale == null) return DEFAULT_LOCALE
        val language = locale.language.takeIf { it.isNotEmpty() } ?: "zh"
        val country = locale.country
        return if (country.isEmpty()) language else "${language}_${country}"
    }

    @JvmStatic
    fun headers(
        token: String?,
        version: String?,
        sdkInt: Int,
        locale: String?,
        rangersId: String?,
        timezoneOffsetSeconds: Int
    ): Map<String, String> {
        val normalizedVersion = version?.let(::javaTrim).orEmpty().ifEmpty { DEFAULT_VERSION }
        val normalizedLocale = locale?.let(::javaTrim).orEmpty().ifEmpty { DEFAULT_LOCALE }
        return LinkedHashMap<String, String>().apply {
            put("Accept", "application/json")
            put("User-Agent", userAgent(version, sdkInt))
            put("Referer", "https://chat.deepseek.com")
            put("Authorization", "Bearer $token")
            put("x-client-platform", "android")
            put("x-client-bundle-id", HOST_BUNDLE_ID)
            put("x-client-version", normalizedVersion)
            put("x-client-locale", normalizedLocale)
            put("x-client-timezone-offset", timezoneOffsetSeconds.toString())
            rangersId?.let(::javaTrim)?.takeIf { it.isNotEmpty() }?.let { put("x-rangers-id", it) }
        }
    }

    @JvmStatic
    fun timezoneOffsetSeconds(timezone: TimeZone?, nowMs: Long): Int {
        val value = timezone ?: TimeZone.getDefault()
        return value.getOffset(nowMs) / 1_000
    }

    @JvmStatic
    fun retryDelayMillis(retryAfter: String?): Long {
        if (retryAfter == null || javaTrim(retryAfter).isEmpty()) return DEFAULT_RETRY_MS
        return try {
            val seconds = javaTrim(retryAfter).toLong()
            if (seconds < 0L || seconds > MAX_INLINE_RETRY_MS / 1_000L) {
                -1L
            } else {
                maxOf(MIN_RETRY_INTERVAL_MS, seconds * 1_000L)
            }
        } catch (_: Throwable) {
            // An unparseable server delay must not trigger an immediate retry.
            -1L
        }
    }
}
