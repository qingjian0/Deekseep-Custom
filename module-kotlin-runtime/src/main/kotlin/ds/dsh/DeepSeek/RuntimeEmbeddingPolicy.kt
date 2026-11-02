package ds.dsh.DeepSeek

/**
 * Pure exact-version gate for passive host embedding.
 *
 * Keep the supported identities and failure text aligned with the Java compatibility facade:
 * host component routing must remain disabled for every unrecognized build.
 */
object RuntimeEmbeddingPolicy {
    const val HOST_PACKAGE = "com.deepseek.chat"

    private const val V236_VERSION_NAME = "2.3.6"
    private const val V236_VERSION_CODE = 249L
    private const val V241_VERSION_NAME = "2.4.1"
    private const val V241_VERSION_CODE = 257L

    /**
     * Returns null only for an exact supported host identity; otherwise returns the
     * diagnostic string historically exposed by RuntimeEmbeddingMode.
     */
    @JvmStatic
    fun failureReason(
        versionName: String?,
        versionCode: Long,
        isV236: Boolean,
        isV241: Boolean,
        isGooglePlay: Boolean,
        isV250: Boolean
    ): String? {
        if (isV236) {
            return if (versionName == V236_VERSION_NAME && versionCode == V236_VERSION_CODE) {
                null
            } else {
                "v236_mismatch:$versionName/$versionCode"
            }
        }
        if (isV241) {
            return if (versionName == V241_VERSION_NAME && versionCode == V241_VERSION_CODE) {
                null
            } else {
                "v241_mismatch:$versionName/$versionCode"
            }
        }
        return "hostcompat_not_v236_nor_v241(isV241=$isV241,isV236=$isV236,isGP=$isGooglePlay,isV250=$isV250)"
    }
}
