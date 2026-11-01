package ds.dsh.DeepSeek

import java.util.Locale

/**
 * Pure locale and override policy shared by every maintained UI entry point.
 */
object UiLanguagePolicy {
    const val MODE_AUTO = "auto"
    const val MODE_CHINESE = "chinese"
    const val MODE_ENGLISH = "english"

    @JvmStatic
    fun normalizeMode(value: String?): String = when {
        MODE_CHINESE.equals(value, ignoreCase = true) -> MODE_CHINESE
        MODE_ENGLISH.equals(value, ignoreCase = true) -> MODE_ENGLISH
        else -> MODE_AUTO
    }

    @JvmStatic
    fun isChineseLanguage(language: String?): Boolean {
        if (language == null) return false
        val normalized = javaTrim(language).lowercase(Locale.US).replace('_', '-')
        return normalized == "zh" || normalized.startsWith("zh-")
    }

    @JvmStatic
    fun effectiveChinese(requestedMode: String?, detected: String?): Boolean {
        val normalized = normalizeMode(requestedMode)
        return MODE_CHINESE == normalized ||
            (MODE_AUTO == normalized && isChineseLanguage(detected))
    }

    @JvmStatic
    fun languageFromTag(tag: String?): String {
        if (tag == null) return "en"
        val normalized = javaTrim(tag).replace('_', '-')
        if (normalized.isEmpty()) return "en"
        return try {
            val language = Locale.forLanguageTag(normalized).language
            if (language.isNotEmpty()) language.lowercase(Locale.US) else fallbackLanguage(normalized)
        } catch (_: Throwable) {
            fallbackLanguage(normalized)
        }
    }

    private fun fallbackLanguage(normalized: String): String {
        val separator = normalized.indexOf('-')
        val language = if (separator < 0) normalized else normalized.substring(0, separator)
        return if (language.isEmpty()) "en" else language.lowercase(Locale.US)
    }

    /** Match java.lang.String.trim(): remove only UTF-16 chars <= U+0020. */
    private fun javaTrim(value: String): String = value.trim { it <= '\u0020' }
}
