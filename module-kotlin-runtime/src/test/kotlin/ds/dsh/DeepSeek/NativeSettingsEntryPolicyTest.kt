package ds.dsh.DeepSeek

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSettingsEntryPolicyTest {
    @Test
    fun floatingEntryRemainsUntilNativeRowIsActuallyEmitted() {
        assertTrue(NativeSettingsEntryPolicy.showFloating(false, false, false))
        assertTrue(NativeSettingsEntryPolicy.showFloating(true, false, false))
        assertTrue(NativeSettingsEntryPolicy.showFloating(true, true, false))
        assertFalse(NativeSettingsEntryPolicy.showFloating(true, true, true))
    }

    @Test
    fun missingNativeHookOrRowKeepsRecoveryEntryVisible() {
        assertTrue(NativeSettingsEntryPolicy.showFloating(false, true, true))
        assertTrue(NativeSettingsEntryPolicy.showFloating(true, false, true))
    }
}
