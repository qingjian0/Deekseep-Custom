package ds.dsh.DeepSeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class AccountValidationPolicyTest {
    @Test
    fun userAgentKeepsVersionFallbackAndSdkFloor() {
        assertEquals("DeepSeek/2.2.2 Android/1", AccountValidationPolicy.userAgent(" ", 0))
        assertEquals("DeepSeek/2.4.1 Android/35", AccountValidationPolicy.userAgent(" 2.4.1 ", 35))
        assertEquals("DeepSeek/\u20032.4.1\u2003 Android/35", AccountValidationPolicy.userAgent("\u20032.4.1\u2003", 35))
    }

    @Test
    fun localePreservesLanguageCountryFallbacks() {
        assertEquals("zh_CN", AccountValidationPolicy.locale(null))
        assertEquals("zh", AccountValidationPolicy.locale(Locale("", "CN")))
        assertEquals("en_US", AccountValidationPolicy.locale(Locale.US))
    }

    @Test
    fun headersMatchHostContractAndOmitBlankRangersId() {
        val headers = AccountValidationPolicy.headers("secret-token", " 2.4.1 ", 35, " en_US ", " device-1 ", 19_800)
        assertEquals("application/json", headers["Accept"])
        assertEquals("DeepSeek/2.4.1 Android/35", headers["User-Agent"])
        assertEquals("https://chat.deepseek.com", headers["Referer"])
        assertEquals("Bearer secret-token", headers["Authorization"])
        assertEquals("android", headers["x-client-platform"])
        assertEquals("com.deepseek.chat", headers["x-client-bundle-id"])
        assertEquals("2.4.1", headers["x-client-version"])
        assertEquals("en_US", headers["x-client-locale"])
        assertEquals("19800", headers["x-client-timezone-offset"])
        assertEquals("device-1", headers["x-rangers-id"])

        val withoutDeviceId = AccountValidationPolicy.headers("t", null, -1, null, "  ", 0)
        assertEquals("DeepSeek/2.2.2 Android/1", withoutDeviceId["User-Agent"])
        assertEquals("zh_CN", withoutDeviceId["x-client-locale"])
        assertFalse(withoutDeviceId.containsKey("x-rangers-id"))
        assertNull(withoutDeviceId["x-auth-token"])
        assertEquals("Bearer null", AccountValidationPolicy.headers(null, null, 0, null, null, 0)["Authorization"])
    }

    @Test
    fun timezoneOffsetUsesInstantSpecificOffsetIncludingDst() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals(-28_800, AccountValidationPolicy.timezoneOffsetSeconds(zone, 1_704_067_200_000L))
        assertEquals(-25_200, AccountValidationPolicy.timezoneOffsetSeconds(zone, 1_720_000_000_000L))
    }

    @Test
    fun retryAfterRetainsDefaultMinimumAndUpperBound() {
        assertEquals(2_500L, AccountValidationPolicy.retryDelayMillis(null))
        assertEquals(2_500L, AccountValidationPolicy.retryDelayMillis(" "))
        assertEquals(1_500L, AccountValidationPolicy.retryDelayMillis("0"))
        assertEquals(1_500L, AccountValidationPolicy.retryDelayMillis("1"))
        assertEquals(10_000L, AccountValidationPolicy.retryDelayMillis("10"))
        assertEquals(-1L, AccountValidationPolicy.retryDelayMillis("-1"))
        assertEquals(-1L, AccountValidationPolicy.retryDelayMillis("11"))
        assertEquals(-1L, AccountValidationPolicy.retryDelayMillis("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals(-1L, AccountValidationPolicy.retryDelayMillis("not-a-number"))
        assertEquals(-1L, AccountValidationPolicy.retryDelayMillis("\u2003"))
    }
}
