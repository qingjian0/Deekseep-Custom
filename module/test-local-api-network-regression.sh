#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY="$ROOT/module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java"
LOCAL_API_UI="$ROOT/module/src/ds/dsh/DeepSeek/DeekseepUi.java"
COMPOSE_MANIFEST="$ROOT/compose-launcher/app/src/main/AndroidManifest.xml"
BUILD_GRADLE="$ROOT/compose-launcher/app/build.gradle.kts"
for manifest in "$ROOT/module-universal/AndroidManifest.xml" "$ROOT/module-legacy/AndroidManifest.xml" "$COMPOSE_MANIFEST"; do
    grep -Fq 'android.permission.ACCESS_NETWORK_STATE' "$manifest"
    grep -Fq 'android.permission.FOREGROUND_SERVICE' "$manifest"
    grep -Fq 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE' "$manifest"
    grep -Fq 'android:name="ds.dsh.DeepSeek.z20"' "$manifest"
    grep -Fq 'android:exported="true"' "$manifest"
    grep -Fq 'android:name="ds.dsh.DeepSeek.z21"' "$manifest"
    grep -Fq 'android:foregroundServiceType="specialUse"' "$manifest"
    grep -Fq 'android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"' "$manifest"
done

# The launcher APK is the installed ds.dsh.DeepSeek package in Open builds. Its manifest
# must declare the same trampoline/service as the standalone module manifests.
grep -Fq 'android:name="ds.dsh.DeepSeek.z20"' "$COMPOSE_MANIFEST"
grep -Fq 'public final class z20 extends Activity' "$ROOT/module/src/ds/dsh/DeepSeek/z20.java"
grep -Fq -- '-keep class ds.dsh.DeepSeek.** { *; }' "$ROOT/compose-launcher/app/proguard-rules.pro"
grep -Fq 'android:name="ds.dsh.DeepSeek.z21"' "$COMPOSE_MANIFEST"
grep -Fq 'android:enabled="${localApiComponentEnabled}"' "$COMPOSE_MANIFEST"

python - "$GATEWAY" "$LOCAL_API_UI" "$BUILD_GRADLE" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
build_gradle = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")
if '(!protectedDistribution || localApiIncluded).toString()' not in build_gradle:
    raise SystemExit("Open builds must enable the declared Local API companion components")
required = {
    "active Android network lookup": "manager.getActiveNetwork()",
    "active network address lookup": "lanAddress(manager, active)",
    "all-network callback registration": "manager.registerNetworkCallback(",
    "API-compatible network capability import": "import android.net.NetworkCapabilities;",
    "LAN network selection excludes VPN interfaces": "hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)",
    "LAN network monitor includes local-only non-VPN networks": "removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)",
    "all-network callback request": "addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)",
    "secondary network fallback": "manager.getAllNetworks()",
    "per-network link properties lookup": "manager.getLinkProperties(network)",
    "network available callback": "onAvailable(Network network)",
    "network lost callback": "onLost(Network network)",
    "link properties callback": "onLinkPropertiesChanged(",
    "callback cleanup": "manager.unregisterNetworkCallback(callback)",
    "exact interface address binding": "InetAddress.getByName(address)",
    "LAN-only address range": "(bytes[0] & 255) == 192 && (bytes[1] & 255) == 168",
    "LAN bind refresh": "refreshLanBinding()",
    "network-loss listener close": "current.close()",
    "generated API keys retain the sk prefix": '"sk-" + android.util.Base64.encodeToString(random',
    "custom API keys accept visible ASCII without an sk prefix": "private static boolean isValidApiKey(String value)",
    "persisted custom API keys use the same validation": 'if (isValidApiKey(existing)) return existing;',
    "custom API keys enforce the documented 8-256 length": 'value.length() < 8 || value.length() > 256',
    "base URL includes v1": 'return root.length() == 0 ? "" : root + "/v1";',
    "Chinese local address label": "本机地址：",
    "Chinese LAN status label": "局域网状态：",
    "LAN bind status is independent": '"局域网绑定状态：" + lanBindingStatus',
    "network monitor status is independently visible": '"网络变化监听：" + lanNetworkMonitorStatus',
    "monitor registration success is explicit": 'lanNetworkMonitorStatus = "已注册"',
    "monitor registration failure is isolated": 'lanNetworkMonitorStatus = "注册失败：" + error.getClass().getSimpleName()',
    "monitor unregister lifecycle is explicit": 'lanNetworkMonitorStatus = RUNNING.get() && lanEnabled ? "未注册" : "已停止"',
    "LAN binding failure does not overwrite monitor state": 'lanBindingStatus = "绑定失败：" + error.getClass().getSimpleName()',
    "unsupported model reports the actual requested model": "不支持的模型名称：",
    "unsupported model is logged without request content": 'Main.log("Open Local API rejected model id: " + reportedModel)',
    "unsupported model diagnostics are bounded": "reportedModel.length() > 128",
}
missing = [label for label, token in required.items() if token not in source]
if missing:
    raise SystemExit("Missing LAN network lifecycle contract: " + ", ".join(missing))
if "addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)" not in source:
    raise SystemExit("LAN network monitor must observe non-VPN local-only networks")
if "c < 0x21 || c > 0x7e" not in source:
    raise SystemExit("Custom API key validator must accept visible ASCII")
if "InetAddress.getByName(\"0.0.0.0\")" in source:
    raise SystemExit("Wildcard listener binding is forbidden")
# Keep the constructor assertion source-oriented and insensitive to harmless
# formatting/line wrapping. The address argument is separately asserted above.
if "new ServerSocket(configuredPort, 16," not in source:
    raise SystemExit("LAN socket must use the configured port and bounded backlog")
main = (pathlib.Path(sys.argv[1]).parent / "Main.java").read_text(encoding="utf-8")
security = (pathlib.Path(sys.argv[1]).parent / "HookAccountLoginSecurity.java").read_text(encoding="utf-8")
if 'localApiFloatingWindowError = "";' not in security:
    raise SystemExit("Missing independent floating-window error storage")
if 'localApiFloatingWindowError = "小窗保活启动失败：" + safeThrowableMessage(error);' not in main:
    raise SystemExit("Floating-window launch failure is not stored independently")
service = (pathlib.Path(sys.argv[1]).parent / "z21.java").read_text(encoding="utf-8")
timeout_start = service.index("if (startedElapsed > 0L && now - startedElapsed >= ACK_TIMEOUT_MS")
timeout_end = service.index("try {\n                Intent ping", timeout_start)
timeout_block = service[timeout_start:timeout_end]
if "stopSelf()" in timeout_block or "putBoolean(KEY_REQUESTED, false)" in timeout_block:
    raise SystemExit("Missing host heartbeat must not disable the user-requested keeper after boot")
if "前台服务继续重试心跳" not in timeout_block:
    raise SystemExit("Missing host heartbeat must retain and retry the foreground keeper")
if "15 秒内未收到 DeepSeek 心跳确认" not in main or "sinceLaunch > 15_000L" not in main:
    raise SystemExit("Keepalive status must fail visibly when launch has no host heartbeat acknowledgement")
if "最近心跳已过期" not in main or "age > 15_000L" not in main:
    raise SystemExit("Keepalive status must detect stale host heartbeat")
if "power.isIgnoringBatteryOptimizations(TARGET)" not in main or "power.isIgnoringBatteryOptimizations(SELF)" not in main:
    raise SystemExit("Battery policy must be checked independently for host and module service packages")
if "moduleDozeExempt" not in main or "hostDozeExempt" not in main:
    raise SystemExit("Keepalive background status must report host and module battery policy separately")
if "EXTRA_NOTIFICATION_ENABLED" not in service or "areNotificationsEnabled()" not in service or "getNotificationChannel(CHANNEL_ID)" not in service or "NotificationManager.IMPORTANCE_NONE" not in service:
    raise SystemExit("Foreground keeper must report module notification permission")
if "localApiKeepAliveNotificationEnabled" not in security or "notificationWarning" not in main:
    raise SystemExit("Host UI must surface missing module notification permission")
if "DeepSeek 宿主后台活动" not in main or "DeepSeek host background activity" not in main:
    raise SystemExit("Background restriction status must identify the host package it actually measures")
if "localApiXiaomiBackgroundGuide" not in main or "Autostart" not in main or "自启动管理" not in main:
    raise SystemExit("Missing Xiaomi MIUI/HyperOS autostart and background policy guidance")
if 'String error = HookAccountLoginSecurity.localApiKeepAliveError;' not in main:
    raise SystemExit("Foreground status no longer reads its own error field")
if 'String error = HookAccountLoginSecurity.localApiFloatingWindowError;' not in main:
    raise SystemExit("Floating-window status does not read its own error field")
if 'HookAccountLoginSecurity.localApiKeepAliveError = "小窗保活启动失败：' in main:
    raise SystemExit("Floating-window failure still contaminates foreground status")
keepalive_start = main.index("private static boolean requestLocalApiKeepAlive(\n            Context context, boolean enabled, boolean forceControl)")
floating_start = main.index("private static boolean requestLocalApiFloatingWindowExact(\n            Context context, boolean enabled, String adapter)")
keepalive_method = main[keepalive_start:floating_start]
floating_method = main[floating_start:main.index("static String localApiKeepAliveStatus()", floating_start)]
if 'if (context == null) {\n            HookAccountLoginSecurity.localApiKeepAliveError = "DeepSeek 上下文尚未就绪";' not in keepalive_method:
    raise SystemExit("Foreground keepalive null-context failure is not stored independently")
if 'if (context == null) {\n            HookAccountLoginSecurity.localApiFloatingWindowError = "DeepSeek 上下文尚未就绪";' not in floating_method:
    raise SystemExit("Floating-window null-context failure is not stored independently")
ui = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
ui_required = {
    "OpenAI base URL copy remains valid while the gateway is stopped": "z13.preferredPort(hostApplicationContext)" in main and 'value = "http://127.0.0.1:" + preferred;' in main,
    "connection details allow long-press text selection": "connectionInfo.setTextIsSelectable(true);",
    "OpenAI URL copy includes /v1": "Main.localApiCopyEndpoint()",
    "Anthropic URL copy retains root endpoint": "Main.localApiRootEndpoint()",
}
missing_ui = [label for label, token in ui_required.items() if token not in ui]
if missing_ui:
    raise SystemExit("Missing local API UI contract: " + ", ".join(missing_ui))
gateway_checks = {
    "model discovery is not blocked by engine readiness": (
        source.index('if ("GET".equals(request.method) && "/v1/models".equals(request.path)) {')
        < source.index("Model discovery describes the configured API surface")
        and "modelBackend.isReady()" not in source.split(
            'if ("GET".equals(request.method) && "/v1/models".equals(request.path)) {', 1
        )[1].split('if (!"POST".equals(request.method)', 1)[0]
    ),
    "max_completion_tokens alias is supported": 'body.has("max_completion_tokens")' in source,
    "max_tokens values are validated instead of silently clamped": 'numericMaxTokens != Math.rint(numericMaxTokens)' in source and '"invalid_max_tokens"' in source,
    "reasoning is returned in non-stream completions": 'message.put("reasoning_content", result.reasoning)' in source,
    "SSE text sink propagates client disconnect cancellation": 'send("content", delta); return !disconnected.get();' in source,
    "reasoning deltas are emitted in SSE": 'send("reasoning_content", delta)' in source,
    "text deltas are emitted in SSE": 'send("content", delta)' in source,
    "plain text and text-part message content are supported": "private static String messageText(Object content)" in source and '"input_text".equals(type)' in source,
    "unsupported multimodal parts are rejected instead of silently discarded": '"Only text content parts are supported"' in source,
    "unsupported generation controls are rejected instead of ignored": "unsupportedChatOption(body)" in source and '"unsupported_parameter"' in source,
    "only one completion choice is accepted": 'if (body.has("n"))' in source and 'return "n";' in source,
    "named messages are rejected rather than silently flattened": 'message.has("name")' in source and '"Named messages, tool messages, and tool calls are not supported"' in source,
    "empty message content is rejected": 'text.trim().length() == 0' in source and '"Message content is empty"' in source,
    "assistant tool call history is not silently discarded": 'message.has("tool_calls")' in source and '"Tool messages and tool calls are not supported"' in source,
    "tool calling and response_format are explicitly rejected": '"unsupported_feature"' in source and 'body.has("tool_choice")' in source and 'body.has("response_format")' in source,
    "usage streaming is explicitly rejected": 'optBoolean("include_usage", false)' in source and "usage streaming" in source,
    "stream must be a boolean": 'body.opt("stream") instanceof Boolean' in source and '"invalid_stream"' in source,
    "model must be a string when supplied": 'body.opt("model") instanceof String' in source and '"invalid_model"' in source,
    "gateway startup failures are visible in connection info": '最近启动错误：' in source and 'lastStartError = error.getClass().getSimpleName();' in source,
    "gateway runtime status distinguishes startup failure": 'Stopped (startup failed: ' in source,
    "port restart restores previous port after failure": "新端口启动失败，已恢复原端口" in source and "previousPort" in source,
    "HTTP error type matches authentication and server status": 'status >= 500 ? "server_error"' in source and 'status == 401 ? "authentication_error"' in source and 'status == 403 ? "permission_error"' in source,
    "gateway preserves structured upstream HTTP errors": "sendError(out, rejected.status, rejected.code, rejected.type" in source,
    "null upstream result returns 502": '"empty_upstream_response"' in source,
    "HTTP query strings do not break endpoint routing": "int queryStart = path.indexOf('?');" in source,
    "HTTP request line validates version and token count": 'first.length != 3' in source and '"HTTP/1.1".equals(first[2])' in source,
    "duplicate HTTP headers are rejected": "headers.containsKey(name)" in source,
    "malformed HTTP requests receive an explicit 400/413 response": 'sendError(out, status, status == 413 ? "request_too_large"' in source,
    "non-JSON request bodies receive 415": 'sendError(out, 415, "unsupported_media_type"' in source,
}
missing_gateway = [label for label, ok in gateway_checks.items() if not ok]
if missing_gateway:
    raise SystemExit("Missing API protocol behavior: " + ", ".join(missing_gateway))
ui_open = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
if "Responses API、Anthropic Messages、工具调用和多模态尚未实现" not in ui_open:
    raise SystemExit("Open UI must not advertise unimplemented API protocols")
if "原生执行适配器尚未接通" not in ui_open:
    raise SystemExit("Open UI must disclose that native completion is not connected")
overlay = (pathlib.Path(sys.argv[1]).parent / "LocalApiFloatingOverlay.java").read_text(encoding="utf-8")
hook = (pathlib.Path(sys.argv[1]).parent / "HookAccountLoginSecurity.java").read_text(encoding="utf-8")
if 'if ("Open".equals(BuildInfo.BUILD_EDITION)) {' not in overlay or 'httpsSwitch = null;' not in overlay:
    raise SystemExit("Open floating overlay must hide unavailable HTTPS controls")
if 'protocolValue.setClickable(false);' not in overlay or 'OpenAI（固定）' not in overlay:
    raise SystemExit("Open floating overlay must keep the protocol fixed to OpenAI")
if '!"Open".equals(BuildInfo.BUILD_EDITION)' not in hook or 'Open 版当前仅支持 HTTP + OpenAI Chat Completions' not in hook:
    raise SystemExit("Open host control receiver must reject unsupported HTTPS/protocol changes")
if '((operation == null || operation.length() == 0)' not in hook or 'z2.PROTOCOL_ANTHROPIC.equals(protocol)' not in hook:
    raise SystemExit("Open host control receiver must guard the legacy protocol broadcast path")
if 'protocolValue.setText(UiLanguage.text(act, "OpenAI（仅 Chat Completions）"' not in ui_open or 'protocolValue.setClickable(false)' not in ui_open:
    raise SystemExit("Open UI must not expose the unavailable Anthropic protocol picker")
print("LAN lifecycle, key, URL, selectable UI, API protocol, and truthful Open capability checks passed")
PY
