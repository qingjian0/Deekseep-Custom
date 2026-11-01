package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPagePolicyTest {
    @Test
    fun normalizesOnlyTheSettingsPageToSettings() {
        assertEquals(SettingsPagePolicy.HOME, SettingsPagePolicy.normalize(-1))
        assertEquals(SettingsPagePolicy.HOME, SettingsPagePolicy.normalize(0))
        assertEquals(SettingsPagePolicy.SETTINGS, SettingsPagePolicy.normalize(1))
        assertEquals(SettingsPagePolicy.HOME, SettingsPagePolicy.normalize(2))
        assertEquals(SettingsPagePolicy.HOME, SettingsPagePolicy.normalize(Int.MAX_VALUE))
    }

    @Test
    fun recreatesShellOnlyForFirstRenderOrUiQualifierChanges() {
        assertTrue(SettingsPagePolicy.shouldRecreateShell(false, false, false, false, false))
        assertTrue(SettingsPagePolicy.shouldRecreateShell(true, false, true, false, false))
        assertTrue(SettingsPagePolicy.shouldRecreateShell(true, false, false, false, true))
        assertFalse(SettingsPagePolicy.shouldRecreateShell(true, false, false, false, false))
    }
}
