package ds.dsh.DeepSeek;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Compile-time and runtime guard for Java/Xposed callers of policies migrated into the
 * Kotlin-only runtime JAR. This protects the existing static Java call sites during migration.
 */
public final class KotlinPolicyJavaInteropRegression {
    public static void main(String[] args) throws Exception {
        require(LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(true, 15_000L, true),
                "Java caller could not use the Kotlin heartbeat policy");
        require(!LocalApiKeepAliveDecision.throttleSameDirection(
                        13_000L, 10_000L, true, true),
                "Java caller observed a changed launch-throttle boundary");
        require(NativeSettingsEntryPolicy.showFloating(true, true, false),
                "Java caller could not use the Kotlin settings fallback policy");
        require(!NativeSettingsEntryPolicy.showFloating(true, true, true),
                "Java caller observed a changed native-entry visibility decision");
        require(AutoContinuePolicy.shouldResume(true, "WIP", "INCOMPLETE", "ASSISTANT"),
                "Java caller could not use the migrated Kotlin auto-continue policy");
        require(!AutoContinuePolicy.shouldResume(true, "INCOMPLETE", "INCOMPLETE", "ASSISTANT"),
                "Java caller observed a changed auto-continue transition boundary");
        require(ReplyReadyPolicy.shouldNotify("WIP", "FINISHED", "ASSISTANT", false),
                "Java caller could not use the migrated Kotlin reply-ready policy");
        require(!ReplyReadyPolicy.shouldNotify("WIP", "FINISHED", "ASSISTANT", true),
                "Java caller observed a changed reply-ready foreground boundary");
        require(ds.dsh.DeepSeek.relay.ExpertRelayGate.matches("expert", null, true),
                "Java caller could not use the migrated Kotlin expert relay gate");
        require(!ds.dsh.DeepSeek.relay.ExpertRelayGate.matches("vision", "expert", true),
                "Java caller observed a changed expert relay model precedence rule");
        require(HostNavigationCatalog.DIRECT.equals(HostNavigationCatalog.policy(
                        "com.deepseek.chat.ui.pages.ChatRoute")),
                "Java caller could not use the migrated navigation policy");
        require("oa1".equals(HostNavigationCatalog.obfuscatedRouteClass(
                        "com.deepseek.chat.ui.pages.ChatRoute", true, false)),
                "Java caller observed a changed code249 route mapping");
        require("id1".equals(HostNavigationCatalog.obfuscatedRouteClass(
                        "com.deepseek.chat.ui.pages.ChatRoute", false, true)),
                "Java caller observed a changed code257 route mapping");
        require(HostNavigationCatalog.extractRoutes(new ByteArrayInputStream(
                        "com.deepseek.chat.ui.pages.ChatRoute".getBytes(StandardCharsets.ISO_8859_1)))
                        .contains("com.deepseek.chat.ui.pages.ChatRoute"),
                "Java caller could not use the migrated DEX route parser");
        require("闪电".equals(ModelDisplayNamePolicy.mapDisplay(
                        " 快速 ", true, java.util.Collections.singletonMap("快速", "闪电"))),
                "Java caller could not use the Kotlin display-label policy");
        require(ModelDisplayNamePolicy.clean(" alpha\nbeta ").equals("alpha beta"),
                "Java caller observed a changed display-label cleanup rule");
        require(ModelDisplayNamePolicy.mapDisplay(
                        "普通聊天里提到快速", true, java.util.Collections.singletonMap("快速", "闪电")) == null,
                "Kotlin display-label policy changed exact-match semantics");
        require("DeepSeek/2.4.1 Android/35".equals(
                        AccountValidationPolicy.userAgent(" 2.4.1 ", 35)),
                "Java caller could not use the migrated account-validation User-Agent policy");
        require("zh_CN".equals(AccountValidationPolicy.locale(null)),
                "Java caller could not use the migrated account-validation locale policy");
        require("Bearer interop-token".equals(AccountValidationPolicy.headers(
                        "interop-token", null, 0, null, " ", 0).get("Authorization")),
                "Java caller could not use the migrated account-validation headers");
        require("com.deepseek.chat".equals(AccountValidationPolicy.headers(
                        "interop-token", null, 0, null, null, 0).get("x-client-bundle-id")),
                "Kotlin account-validation policy changed the host bundle identifier");
        require(AccountValidationPolicy.retryDelayMillis("1") == 1500L
                        && AccountValidationPolicy.retryDelayMillis("11") == -1L,
                "Java caller observed changed account-validation retry boundaries");
        require(AndroidManifestVersion.parse(new byte[7]) == null,
                "Java caller could not use the migrated Kotlin AXML parser");
        require(AgentRateLimiter.MIN_GAP_MS == 8_000L
                        && AgentRateLimiter.PER_MINUTE == 5
                        && AgentRateLimiter.PER_HOUR == 60,
                "Java caller could not read the migrated Kotlin rate-limit constants");
        AgentRateLimiter.recordSent(1_000L);
        require(AgentRateLimiter.waitMs(1_000L) == 8_000L,
                "Java caller observed a changed Agent minimum-gap policy");
        AgentManualGateState.markPending("interop-call");
        AgentManualGateState.approve("interop-call");
        require(AgentManualGateState.consumeApproval("interop-call")
                        && !AgentManualGateState.consumeApproval("interop-call"),
                "Java caller could not use the migrated Kotlin manual-gate state");
        require(ZipArchivePathPolicy.isSafePath("accounts/account.json")
                        && !ZipArchivePathPolicy.isSafePath("../account.json"),
                "Java caller could not use the migrated ZIP path policy");
        AgentManualGateState.markPending("interop-call");
        AgentManualGateState.approve("interop-call");
        require(AgentManualGateState.consumeApproval("interop-call")
                        && !AgentManualGateState.consumeApproval("interop-call"),
                "Java caller could not use the migrated Kotlin manual-gate state");
        DshDiagnosticsRecorder diagnostics = new DshDiagnosticsRecorder(2);
        diagnostics.record("hook installed cost=16ms");
        diagnostics.record("server request Cookie: sid=interop-secret-123");
        require(diagnostics.performanceSummary(1_000L).contains("averageCost=16ms"),
                "Java caller could not use the Kotlin diagnostics recorder");
        require(diagnostics.traceLines().size() == 2,
                "Java caller observed an unexpected diagnostics trace size");
        require(!diagnostics.traceLines().toString().contains("interop-secret-123"),
                "Kotlin diagnostics recorder exposed a cookie to Java");

        System.out.println("Kotlin policy Java interop regression passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
