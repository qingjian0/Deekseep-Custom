package ds.dsh.DeepSeek

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecyclePoliciesTest {
    @Test
    fun autoContinuePreservesExactTransitionRules() {
        assertTrue(AutoContinuePolicy.shouldResume(true, "WIP", "INCOMPLETE", "ASSISTANT"))
        assertTrue(AutoContinuePolicy.shouldResume(true, "CHECKING", "INCOMPLETE", "ASSISTANT"))
        assertFalse(AutoContinuePolicy.shouldResume(false, "WIP", "INCOMPLETE", "ASSISTANT"))
        assertFalse(AutoContinuePolicy.shouldResume(true, "INCOMPLETE", "INCOMPLETE", "ASSISTANT"))
        assertFalse(AutoContinuePolicy.shouldResume(true, "WIP", "INCOMPLETE", "USER"))
        assertFalse(AutoContinuePolicy.shouldResume(true, "WIP", "FINISHED", "ASSISTANT"))
        assertFalse(AutoContinuePolicy.shouldResume(true, "WIP", "INTERRUPTED", "ASSISTANT"))
        assertFalse(AutoContinuePolicy.shouldResume(true, null, "INCOMPLETE", "ASSISTANT"))
    }

    @Test
    fun replyReadyPreservesNormalizationAndTransitionRules() {
        assertTrue(ReplyReadyPolicy.shouldNotify("WIP", "FINISHED", "ASSISTANT", false))
        assertTrue(ReplyReadyPolicy.shouldNotify(" checking ", " finished ", " assistant ", false))
        assertTrue(ReplyReadyPolicy.shouldNotify("INCOMPLETE", "FINISHED", "ASSISTANT", false))
        assertFalse(ReplyReadyPolicy.shouldNotify("WIP", "FINISHED", "ASSISTANT", true))
        assertFalse(ReplyReadyPolicy.shouldNotify("WIP", "FINISHED", "USER", false))
        assertFalse(ReplyReadyPolicy.shouldNotify("FINISHED", "FINISHED", "ASSISTANT", false))
        assertFalse(ReplyReadyPolicy.shouldNotify(null, "FINISHED", "ASSISTANT", false))
        assertFalse(ReplyReadyPolicy.shouldNotify("WIP", "CONTENT_FILTER", "ASSISTANT", false))
    }

    @Test
    fun replyReadyRecognizesOnlyDocumentedStates() {
        listOf("WIP", "CHECKING", "INCOMPLETE", "STREAMING", "GENERATING", "RESPONDING")
            .forEach { assertTrue("expected generating state: $it", ReplyReadyPolicy.isGenerating(it)) }
        listOf("FINISHED", "COMPLETE", "COMPLETED", "SUCCESS", "SUCCEEDED", "DONE")
            .forEach { assertTrue("expected completed state: $it", ReplyReadyPolicy.isCompleted(it)) }
        assertFalse(ReplyReadyPolicy.isGenerating("CONTENT_FILTER"))
        assertFalse(ReplyReadyPolicy.isCompleted("INTERRUPTED"))
    }
}
