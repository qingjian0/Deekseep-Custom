package ds.dsh.DeepSeek

import java.util.Collections
import java.util.HashSet

/**
 * Process-local decision bookkeeping for the manual Agent delivery gate.
 * Android UI, clipboard and persistence remain in the Java adapter.
 * Each operation retains the legacy synchronized-set semantics and decision order.
 */
object AgentManualGateState {
    private val pending = Collections.synchronizedSet(HashSet<String>())
    private val approved = Collections.synchronizedSet(HashSet<String>())
    private val refused = Collections.synchronizedSet(HashSet<String>())

    @JvmStatic fun consumeApproval(key: String): Boolean = approved.remove(key)
    @JvmStatic fun consumeRefusal(key: String): Boolean = refused.remove(key)
    @JvmStatic fun isPending(key: String): Boolean = pending.contains(key)
    @JvmStatic fun markPending(key: String): Boolean = pending.add(key)
    @JvmStatic fun clearPending(key: String): Boolean = pending.remove(key)
    @JvmStatic fun approve(key: String) {
        approved.add(key)
        pending.remove(key)
    }
    @JvmStatic fun refuse(key: String) {
        refused.add(key)
        pending.remove(key)
    }

    @JvmStatic fun clearForTest() {
        pending.clear()
        approved.clear()
        refused.clear()
    }
}
