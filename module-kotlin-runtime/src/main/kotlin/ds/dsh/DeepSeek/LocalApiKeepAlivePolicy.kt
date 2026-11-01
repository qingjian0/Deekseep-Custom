package ds.dsh.DeepSeek

/** Pure timing decisions for the local API foreground keep-alive bridge. */
object LocalApiKeepAlivePolicy {
    @JvmStatic
    fun heartbeatAlreadySatisfies(
        enabling: Boolean,
        heartbeatAgeMs: Long,
        apiKeepaliveRequested: Boolean
    ): Boolean {
        return enabling && heartbeatAgeMs >= 0L && heartbeatAgeMs <= 15_000L &&
            apiKeepaliveRequested
    }

    @JvmStatic
    fun throttleSameDirection(
        nowMs: Long,
        lastLaunchMs: Long,
        lastEnabled: Boolean,
        nextEnabled: Boolean
    ): Boolean {
        return lastLaunchMs > 0L && nowMs >= lastLaunchMs &&
            nowMs - lastLaunchMs < 3_000L && lastEnabled == nextEnabled
    }
}
