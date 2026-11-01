package ds.dsh.DeepSeek

/** Pure key and polarity mapping for DeepSeek remote feature overrides. */
object RemoteFeatureFlagsPolicy {
    private const val REMOTE_PREFIX = "kv_remote_settings_"
    private const val LOCAL_PREFIX = "kv_settings_"

    @JvmStatic
    fun localKey(remoteKey: String?): String? {
        if (remoteKey == null || !remoteKey.startsWith(REMOTE_PREFIX)) return remoteKey
        return LOCAL_PREFIX + remoteKey.substring(REMOTE_PREFIX.length)
    }

    @JvmStatic
    fun hostValue(inverted: Boolean, userValue: Boolean): Boolean =
        if (inverted) !userValue else userValue

    @JvmStatic
    fun userModeFromHost(inverted: Boolean, hostValue: Boolean): Int =
        if (hostValue(inverted, hostValue)) FORCE_ON else FORCE_OFF

    const val FORCE_OFF = -1
    const val FORCE_ON = 1
}
