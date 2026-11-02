package ds.dsh.DeepSeek

/**
 * Pure prompt-injection scheduling rules.
 *
 * Conversation identity, turn counting, duplicate suppression, locking, and persistence remain
 * owned by the Android/Java host adapter. This policy only normalizes the configured skip count
 * and decides whether a one-based turn receives the prompt.
 */
object PromptInjectionPolicy {
    const val DEFAULT_SKIPPED_TURNS = 3
    const val MIN_SKIPPED_TURNS = 1
    const val MAX_SKIPPED_TURNS = 30

    @JvmStatic
    fun normalizeSkippedTurns(value: Int): Int =
        value.coerceIn(MIN_SKIPPED_TURNS, MAX_SKIPPED_TURNS)

    @JvmStatic
    fun shouldInjectTurn(turn: Int, skippedTurns: Int): Boolean {
        val period = normalizeSkippedTurns(skippedTurns) + 1
        return ((turn - 1) % period) == 0
    }
}
