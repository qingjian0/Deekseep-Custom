package ds.dsh.DeepSeek

/** Pure parsing and range policy; Android lifecycle, configuration and I/O stay in Java. */
object FontScalePolicy {
    @JvmStatic
    fun parse(text: String?): Float = try {
        val value = java.lang.Float.parseFloat(text?.trim() ?: "")
        if (value.isNaN() || value.isInfinite()) 1.0f else clamp(value, 0.80f, 1.60f)
    } catch (_: Throwable) {
        1.0f
    }

    /** Retains Java's comparison-based clamp semantics, including NaN passthrough. */
    @JvmStatic
    fun clamp(value: Float, minimum: Float, maximum: Float): Float =
        if (value < minimum) minimum else if (value > maximum) maximum else value
}
