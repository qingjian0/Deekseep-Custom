package ds.dsh.DeepSeek

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.regex.Pattern

/**
 * Pure parser and launch policy for the independently verified code249/code257 catalogs.
 *
 * Host-version detection stays in the Android adapter. Passing the two verified generation
 * flags keeps this runtime independent from Android and Xposed classes.
 */
class HostNavigationCatalog private constructor() {
    companion object {
        private const val MAX_DEX_BYTES = 48 * 1024 * 1024
        private val ROUTE = Pattern.compile(
            "com\\\\.deepseek\\\\.chat\\\\.ui\\\\.pages\\\\.[A-Za-z0-9_.$]{1,160}(?:Route|Graph)"
        )

        const val DIRECT = "direct"
        const val HIGH_RISK = "confirm"
        const val PARAMETERS = "parameters"
        const val INTERNAL = "internal"
        const val BLOCKED = "blocked"

        @JvmStatic
        @Throws(IOException::class)
        fun extractRoutes(input: InputStream?): List<String> {
            if (input == null) return emptyList()
            val bytes = ByteArrayOutputStream(512 * 1024)
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DEX_BYTES) {
                    throw IOException("DEX exceeds navigation scan limit")
                }
                bytes.write(buffer, 0, read)
            }
            val raw = String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            val unique = LinkedHashSet<String>()
            val matcher = ROUTE.matcher(raw)
            while (matcher.find()) unique.add(matcher.group())
            return unique.toList().sorted()
        }

        @JvmStatic
        fun policy(route: String?): String {
            if (route == null) return BLOCKED
            if (route.endsWith("AccountDeletionRoute")) return HIGH_RISK
            if (route.contains("AuthNestedGraph") || route.endsWith("WelcomeRoute")) {
                return INTERNAL
            }
            if (route.endsWith("AgeScreenRoute") || route.endsWith("MobileRebindRoute")
                || route.endsWith("MobileVerificationRoute")
                || route.endsWith("ResetPasswordRoute")
                || route.endsWith("WebViewRoute") || route.endsWith("TableRoute")
            ) {
                return PARAMETERS
            }
            if (route.endsWith("ChatRoute") || route.endsWith("SettingsNestedGraph")
                || route.endsWith("SettingsRoute")
                || route.endsWith("AccountManagementRoute")
                || route.endsWith("DataControlsSettingsRoute")
                || route.endsWith("FontSizeRoute")
                || route.endsWith("MinorModeBirthdayModifyRoute")
                || route.endsWith("ShareLinksManagementRoute")
                || route.endsWith("TermsOfServiceRoute")
            ) {
                return DIRECT
            }
            return INTERNAL
        }

        @JvmStatic
        fun title(route: String?): String {
            if (route == null) return "未知页面"
            if (route.endsWith("ChatRoute")) return "聊天主页"
            if (route.endsWith("SettingsNestedGraph") || route.endsWith("SettingsRoute")) return "设置"
            if (route.endsWith("AccountManagementRoute")) return "账号管理"
            if (route.endsWith("AccountDeletionRoute")) return "删除账号"
            if (route.endsWith("DataControlsSettingsRoute")) return "数据控制"
            if (route.endsWith("FontSizeRoute")) return "字体大小"
            if (route.endsWith("MinorModeBirthdayModifyRoute")) return "生日/未成年人模式"
            if (route.endsWith("ShareLinksManagementRoute")) return "分享链接管理"
            if (route.endsWith("TermsOfServiceRoute")) return "用户协议与隐私"
            if (route.endsWith("PasswordLoginRoute")) return "密码登录"
            if (route.endsWith("RegisterRoute")) return "注册"
            if (route.endsWith("SmsLoginRoute")) return "短信登录"
            if (route.endsWith("WelcomeRoute")) return "欢迎页"
            if (route.endsWith("WebViewRoute")) return "网页页面"
            if (route.endsWith("TableRoute")) return "表格预览"
            val dot = route.lastIndexOf('.')
            return if (dot >= 0) route.substring(dot + 1) else route
        }

        @JvmStatic
        fun obfuscatedRouteClass(route: String?, isV236: Boolean, isV241: Boolean): String? {
            if (isV236) return obfuscatedRouteClass249(route)
            if (!isV241) return null
            return obfuscatedRouteClass257(route)
        }

        @JvmStatic
        fun obfuscatedRouteClass249(route: String?): String? {
            if (route == null) return null
            if (route.endsWith("ChatRoute")) return "oa1"
            if (route.endsWith("SettingsNestedGraph")) return "kn7"
            if (route.endsWith("SettingsNestedGraph.SettingsRoute")) return "hn7"
            if (route.endsWith("AccountManagementRoute")) return "en7"
            if (route.endsWith("AccountDeletionRoute")) return "dn7"
            if (route.endsWith("DataControlsSettingsRoute")) return "fn7"
            if (route.endsWith("FontSizeRoute")) return "gn7"
            if (route.endsWith("ShareLinksManagementRoute")) return "in7"
            if (route.endsWith("TermsOfServiceRoute")) return "jn7"
            return null
        }

        @JvmStatic
        fun obfuscatedRouteClass257(route: String?): String? {
            if (route == null) return null
            if (route.endsWith("ChatRoute")) return "id1"
            if (route.endsWith("SettingsNestedGraph")) return "kv7"
            if (route.endsWith("SettingsNestedGraph.SettingsRoute")) return "hv7"
            if (route.endsWith("AccountManagementRoute")) return "dv7"
            if (route.endsWith("AccountDeletionRoute")) return "cv7"
            if (route.endsWith("DataControlsSettingsRoute")) return "ev7"
            if (route.endsWith("FontSizeRoute")) return "fv7"
            if (route.endsWith("MinorModeBirthdayModifyRoute")) return "gv7"
            if (route.endsWith("ShareLinksManagementRoute")) return "iv7"
            if (route.endsWith("TermsOfServiceRoute")) return "jv7"
            return null
        }
    }
}
