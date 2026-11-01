package ds.dsh.DeepSeek

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostNavigationCatalogTest {
    @Test
    fun extractsUniqueRoutesInSortedOrder() {
        val dexText = listOf(
            "prefix com.deepseek.chat.ui.pages.settings.SettingsRoute suffix",
            "com.deepseek.chat.ui.pages.chat.ChatRoute",
            "com.deepseek.chat.ui.pages.chat.ChatRoute",
            "com.deepseek.chat.ui.pages.account.AccountDeletionRoute",
        ).joinToString("|")
        val routes = HostNavigationCatalog.extractRoutes(
            ByteArrayInputStream(dexText.toByteArray(StandardCharsets.ISO_8859_1)),
        )

        assertEquals(
            listOf(
                "com.deepseek.chat.ui.pages.account.AccountDeletionRoute",
                "com.deepseek.chat.ui.pages.chat.ChatRoute",
                "com.deepseek.chat.ui.pages.settings.SettingsRoute",
            ),
            routes,
        )
    }

    @Test
    fun classifiesRoutesAndTitlesWithoutHostRuntime() {
        assertEquals(HostNavigationCatalog.BLOCKED, HostNavigationCatalog.policy(null))
        assertEquals(
            HostNavigationCatalog.HIGH_RISK,
            HostNavigationCatalog.policy("com.deepseek.chat.ui.pages.AccountDeletionRoute"),
        )
        assertEquals(
            HostNavigationCatalog.PARAMETERS,
            HostNavigationCatalog.policy("com.deepseek.chat.ui.pages.WebViewRoute"),
        )
        assertEquals(
            HostNavigationCatalog.INTERNAL,
            HostNavigationCatalog.policy("com.deepseek.chat.ui.pages.WelcomeRoute"),
        )
        assertEquals(
            HostNavigationCatalog.DIRECT,
            HostNavigationCatalog.policy("com.deepseek.chat.ui.pages.ChatRoute"),
        )
        assertEquals("聊天主页", HostNavigationCatalog.title("com.deepseek.chat.ui.pages.ChatRoute"))
        assertEquals("未知页面", HostNavigationCatalog.title(null))
    }

    @Test
    fun mapsOnlyVerifiedHostGenerations() {
        val chat = "com.deepseek.chat.ui.pages.ChatRoute"
        assertEquals("oa1", HostNavigationCatalog.obfuscatedRouteClass(chat, true, false))
        assertEquals("id1", HostNavigationCatalog.obfuscatedRouteClass(chat, false, true))
        assertNull(HostNavigationCatalog.obfuscatedRouteClass(chat, false, false))
        assertEquals(
            "gv7",
            HostNavigationCatalog.obfuscatedRouteClass257(
                "com.deepseek.chat.ui.pages.MinorModeBirthdayModifyRoute",
            ),
        )
    }

    @Test
    fun nullInputProducesNoRoutes() {
        assertTrue(HostNavigationCatalog.extractRoutes(null).isEmpty())
    }
}
