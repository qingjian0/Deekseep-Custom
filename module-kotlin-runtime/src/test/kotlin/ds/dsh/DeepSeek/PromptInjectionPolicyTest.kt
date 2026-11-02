package ds.dsh.DeepSeek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptInjectionPolicyTest {
    @Test
    fun normalizesConfiguredSkipCountToSupportedRange() {
        assertEquals(1, PromptInjectionPolicy.normalizeSkippedTurns(Int.MIN_VALUE))
        assertEquals(1, PromptInjectionPolicy.normalizeSkippedTurns(0))
        assertEquals(1, PromptInjectionPolicy.normalizeSkippedTurns(1))
        assertEquals(30, PromptInjectionPolicy.normalizeSkippedTurns(30))
        assertEquals(30, PromptInjectionPolicy.normalizeSkippedTurns(31))
        assertEquals(30, PromptInjectionPolicy.normalizeSkippedTurns(Int.MAX_VALUE))
    }

    @Test
    fun firstTurnAlwaysReceivesPrompt() {
        for (skippedTurns in 1..30) {
            assertTrue(PromptInjectionPolicy.shouldInjectTurn(1, skippedTurns))
        }
    }

    @Test
    fun injectsAtConfiguredSkipInterval() {
        assertEquals(
            listOf(true, false, false, false, true, false, false, false, true),
            (1..9).map { PromptInjectionPolicy.shouldInjectTurn(it, 3) }
        )
        assertEquals(
            listOf(true, false, true, false, true, false),
            (1..6).map { PromptInjectionPolicy.shouldInjectTurn(it, 1) }
        )
    }

    @Test
    fun clampsSkipCountBeforeScheduling() {
        assertFalse(PromptInjectionPolicy.shouldInjectTurn(2, 0))
        assertFalse(PromptInjectionPolicy.shouldInjectTurn(2, 30))
        assertTrue(PromptInjectionPolicy.shouldInjectTurn(32, 30))
    }
}
