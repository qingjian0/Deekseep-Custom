package ds.dsh.DeepSeek.relay

/** Pure model-resolution rules for the expert image relay. */
object ExpertRelayGate {
    /**
     * DeepSeek includes model_type on the first request in a session, but omits it on
     * later requests. An explicit request value always wins over the model captured
     * from the send-point session object.
     */
    @JvmStatic
    fun resolveModel(explicitModel: Any?, capturedModel: String?): String? =
        (explicitModel as? String) ?: capturedModel

    @JvmStatic
    fun matches(explicitModel: Any?, capturedModel: String?, hasFiles: Boolean): Boolean =
        hasFiles && resolveModel(explicitModel, capturedModel) == "expert"
}
