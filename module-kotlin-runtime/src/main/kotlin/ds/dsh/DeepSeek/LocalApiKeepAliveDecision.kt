package ds.dsh.DeepSeek

/**
 * Pure state decisions shared by the foreground-keeper bridge and its Java/Xposed callers.
 *
 * Keep the thresholds and boundary semantics aligned with the legacy Java implementation:
 * heartbeat freshness is inclusive at 15 seconds; launch throttling is exclusive at 3 seconds.
 */
class LocalApiKeepAliveDecision private constructor() {
    companion object {
        @JvmStatic
        fun heartbeatAlreadySatisfies(
            enabling: Boolean,
            heartbeatAgeMs: Long,
            apiKeepaliveRequested: Boolean,
        ): Boolean =
            enabling &&
                heartbeatAgeMs >= 0L &&
                heartbeatAgeMs <= HEARTBEAT_FRESHNESS_MS &&
                apiKeepaliveRequested

        @JvmStatic
        fun throttleSameDirection(
            nowMs: Long,
            lastLaunchMs: Long,
            lastEnabled: Boolean,
            nextEnabled: Boolean,
        ): Boolean =
            lastLaunchMs > 0L &&
                nowMs >= lastLaunchMs &&
                nowMs - lastLaunchMs < LAUNCH_THROTTLE_MS &&
                lastEnabled == nextEnabled

        private const val HEARTBEAT_FRESHNESS_MS = 15_000L
        private const val LAUNCH_THROTTLE_MS = 3_000L
    }
}
