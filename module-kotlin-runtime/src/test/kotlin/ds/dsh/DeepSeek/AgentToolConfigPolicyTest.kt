package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentToolConfigPolicyTest {
    @Test
    fun backendAcceptsOnlyExactPrivilegedValues() {
        assertEquals("in_app", AgentToolConfigPolicy.backend(null, "in_app", "root", "shizuku"))
        assertEquals("in_app", AgentToolConfigPolicy.backend("", "in_app", "root", "shizuku"))
        assertEquals("in_app", AgentToolConfigPolicy.backend("ROOT", "in_app", "root", "shizuku"))
        assertEquals("in_app", AgentToolConfigPolicy.backend("unknown", "in_app", "root", "shizuku"))
        assertEquals("root", AgentToolConfigPolicy.backend("root", "in_app", "root", "shizuku"))
        assertEquals("shizuku", AgentToolConfigPolicy.backend("shizuku", "in_app", "root", "shizuku"))
    }

    @Test
    fun permissionDefaultsToAllUnlessExactExecuteValue() {
        assertEquals("execute", AgentToolConfigPolicy.permission("execute", "execute", "all"))
        assertEquals("all", AgentToolConfigPolicy.permission(null, "execute", "all"))
        assertEquals("all", AgentToolConfigPolicy.permission("", "execute", "all"))
        assertEquals("all", AgentToolConfigPolicy.permission("EXECUTE", "execute", "all"))
        assertEquals("all", AgentToolConfigPolicy.permission("unknown", "execute", "all"))
    }

    @Test
    fun promptStrengthClampsAtBothEndsAndPreservesMiddleLevel() {
        assertEquals(1, AgentToolConfigPolicy.promptStrength(Int.MIN_VALUE, 1, 2, 3))
        assertEquals(1, AgentToolConfigPolicy.promptStrength(1, 1, 2, 3))
        assertEquals(2, AgentToolConfigPolicy.promptStrength(2, 1, 2, 3))
        assertEquals(3, AgentToolConfigPolicy.promptStrength(3, 1, 2, 3))
        assertEquals(3, AgentToolConfigPolicy.promptStrength(Int.MAX_VALUE, 1, 2, 3))
    }
}
