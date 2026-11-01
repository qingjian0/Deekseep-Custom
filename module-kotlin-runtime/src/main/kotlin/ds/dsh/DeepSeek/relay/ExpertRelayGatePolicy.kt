package ds.dsh.DeepSeek.relay

/** Pure model-resolution rules for the expert image relay. */
object ExpertRelayGatePolicy {
    @JvmStatic
    fun resolveModel(explicitModel: Any?, capturedModel: String?): String? {
        return if (explicitModel is String) explicitModel else capturedModel
    }

    @JvmStatic
    fun matches(explicitModel: Any?, capturedModel: String?, hasFiles: Boolean): Boolean {
        return hasFiles && resolveModel(explicitModel, capturedModel) == "expert"
    }
}
