package ds.dsh.DeepSeek

/**
 * Pure color and gradient-direction normalization for ThemeColorConfig.
 *
 * Preserve Java String.trim() semantics (characters <= U+0020), accepted hexadecimal lengths,
 * alpha insertion for RGB input, and caller-provided fallback values.
 */
object ThemeColorPolicy {
    @JvmStatic
    fun parseColor(text: String?, fallback: Int): Int {
        if (text == null) return fallback
        var value = text.trim { it <= ' ' }
        if (value.startsWith("#")) value = value.substring(1)
        return try {
            when (value.length) {
                6 -> (0xFF000000L or java.lang.Long.parseLong(value, 16)).toInt()
                8 -> java.lang.Long.parseLong(value, 16).toInt()
                else -> fallback
            }
        } catch (_: Throwable) {
            fallback
        }
    }

    @JvmStatic
    fun validDirection(
        direction: String?,
        horizontal: String,
        vertical: String,
        diagonalDown: String,
        diagonalUp: String
    ): String = when (direction) {
        vertical, diagonalDown, diagonalUp -> direction
        else -> horizontal
    }
}
