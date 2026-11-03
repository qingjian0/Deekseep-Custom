package ds.dsh.DeepSeek.agent

import java.util.Collections
import java.util.LinkedHashMap

/** Stable, host-independent contracts for Agent Core and its adapters. */
class AgentRequest(
    requestId: String,
    conversationScope: String,
    requestedModel: String,
    input: String,
    val stream: Boolean,
    val deadlineEpochMs: Long,
    metadata: Map<String, String> = emptyMap(),
) {
    val requestId: String = required(requestId, "requestId", 128)
    val conversationScope: String = required(conversationScope, "conversationScope", 256)
    val requestedModel: String = required(requestedModel, "requestedModel", 128)
    val input: String = input.also {
        require(it.length <= MAX_INPUT_CHARS) { "input exceeds $MAX_INPUT_CHARS characters" }
    }
    val metadata: Map<String, String> = Collections.unmodifiableMap(
        LinkedHashMap<String, String>().apply {
            require(metadata.size <= MAX_METADATA_ENTRIES) { "too many metadata entries" }
            metadata.forEach { (key, value) ->
                val safeKey = required(key, "metadata key", 64)
                val safeValue = value
                require(safeValue.length <= MAX_METADATA_VALUE_CHARS) {
                    "metadata value too long: $safeKey"
                }
                put(safeKey, safeValue)
            }
        },
    )

    init {
        require(deadlineEpochMs > 0L) { "deadlineEpochMs must be positive" }
    }

    companion object {
        const val MAX_INPUT_CHARS = 1_000_000
        const val MAX_METADATA_ENTRIES = 32
        const val MAX_METADATA_VALUE_CHARS = 2_048
    }
}

enum class AgentEventType {
    STARTED,
    TEXT_DELTA,
    REASONING_DELTA,
    COMPLETED,
    FAILED,
}

class AgentEvent(
    val type: AgentEventType,
    requestId: String,
    text: String = "",
    detail: String = "",
    attributes: Map<String, String> = emptyMap(),
) {
    val requestId: String = required(requestId, "requestId", 128)
    val text: String = text.also {
        require(it.length <= MAX_EVENT_TEXT_CHARS) { "event text exceeds limit" }
    }
    val detail: String = detail.also {
        require(it.length <= MAX_EVENT_DETAIL_CHARS) { "event detail exceeds limit" }
    }
    val attributes: Map<String, String> = Collections.unmodifiableMap(
        LinkedHashMap<String, String>().apply {
            require(attributes.size <= MAX_EVENT_ATTRIBUTES) { "too many event attributes" }
            attributes.forEach { (key, value) ->
                val safeKey = required(key, "attribute key", 64)
                require(value.length <= MAX_EVENT_ATTRIBUTE_VALUE_CHARS) {
                    "event attribute too long: $safeKey"
                }
                put(safeKey, value)
            }
        },
    )

    companion object {
        const val MAX_EVENT_TEXT_CHARS = 64 * 1024
        const val MAX_EVENT_DETAIL_CHARS = 2_048
        const val MAX_EVENT_ATTRIBUTES = 32
        const val MAX_EVENT_ATTRIBUTE_VALUE_CHARS = 2_048
    }
}

fun interface AgentEventSink {
    /** Return false when the downstream client no longer accepts events. */
    fun onEvent(event: AgentEvent): Boolean
}

interface AgentCancellation {
    fun cancel(): Boolean
    fun isCancelled(): Boolean
}

interface AgentModelAdapter {
    val adapterId: String
    fun isAvailable(): Boolean
    fun complete(
        request: AgentRequest,
        sink: AgentEventSink,
        cancellation: AgentCancellation,
    )
}

interface AgentRunRepository {
    /** Atomically claims a new request ID and initializes it in CREATED state. */
    fun create(request: AgentRequest): Boolean

    /** Performs one validated state transition and returns false on a race or invalid edge. */
    fun transition(requestId: String, next: AgentRunState, detail: String = ""): Boolean

    fun stateOf(requestId: String): AgentRunState?
}

private fun required(value: String, label: String, maxLength: Int): String {
    val clean = value.trim()
    require(clean.isNotEmpty() && clean.length <= maxLength) {
        "$label is empty or too long"
    }
    return clean
}
