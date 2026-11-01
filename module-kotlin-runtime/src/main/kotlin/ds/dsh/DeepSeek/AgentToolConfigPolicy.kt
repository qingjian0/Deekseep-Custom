package ds.dsh.DeepSeek

/**
 * Pure normalization rules for the persisted Agent settings model.
 *
 * JSON decoding/encoding, build gating, host-version checks, and file persistence stay in
 * AgentToolConfig. These functions preserve its exact fallback and clamping behavior.
 */
object AgentToolConfigPolicy {
    @JvmStatic
    fun backend(value: String?, inApp: String, root: String, shizuku: String): String =
        value?.takeIf { it == root || it == shizuku } ?: inApp

    @JvmStatic
    fun permission(value: String?, execute: String, all: String): String =
        if (value == execute) execute else all

    @JvmStatic
    fun promptStrength(value: Int, basic: Int, enhanced: Int, immersive: Int): Int =
        when {
            value <= basic -> basic
            value >= immersive -> immersive
            else -> enhanced
        }
}
