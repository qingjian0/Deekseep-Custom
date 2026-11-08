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
    "active client sockets are tracked": "activeClients = Collections.newSetFromMap(",
    "stop closes active client sockets": "closeActiveClients();",
    "client registration is serialized with stop": "synchronized (activeClients)",
    "accepted sockets are rejected after stop": "if (!RUNNING.get() || client == null || client.isClosed()) return false;",
    "handler always unregisters and closes its socket": "closeTrackedClient(socket);",
    "executor saturation closes tracked sockets": "closeTrackedClient(client);",
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
if main.index("String error = HookAccountLoginSecurity.localApiKeepAliveError;") > main.index("if (age >= 0L && age <= 15_000L)"):
    raise SystemExit("Foreground keepalive errors must take precedence over a recent stale heartbeat")
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
if 'Main.log("local-api incoming known_tool_calls=" + request.knownToolCalls);' in security:
    raise SystemExit("Local API log must not dump known tool call payloads")
if 'sb.append(" [").append(tc).append("]");' in security:
    raise SystemExit("Local API log must not dump tool call objects or arguments")
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
main_required = {
    "OpenAI base URL copy remains valid while the gateway is stopped": "z13.preferredPort(hostApplicationContext)",
    "stopped gateway URL retains the configured loopback port": 'value = "http://127.0.0.1:" + preferred;',
}
missing_main = [label for label, token in main_required.items() if token not in main]
if missing_main:
    raise SystemExit("Missing local API Main contract: " + ", ".join(missing_main))
ui = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
ui_required = {
    "connection details allow long-press text selection": "connectionInfo.setTextIsSelectable(true);",
    "OpenAI URL copy includes /v1": "Main.localApiCopyEndpoint()",
    "Anthropic URL copy retains root endpoint": "Main.localApiRootEndpoint()",
}
missing_ui = [label for label, token in ui_required.items() if token not in ui]
if missing_ui:
    raise SystemExit("Missing local API UI contract: " + ", ".join(missing_ui))
overlay = (pathlib.Path(sys.argv[1]).parent / "LocalApiFloatingOverlay.java").read_text(encoding="utf-8")
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
    "named messages are rejected while valid tool messages are preserved": 'message.has("name")' in source and '"tool".equals(role)' in source and 'Tool result does not match a preceding assistant tool call' in source,
    "empty message content is rejected": 'text.trim().length() == 0' in source and '"Message content is empty"' in source,
    "assistant tool-call history is validated for client-side continuation": 'message.optJSONArray("tool_calls")' in source and 'Invalid assistant tool-call history' in source,
    "tool plans are parsed through the shared bridge": "OpenAiToolBridge.chatPlan(" in source and "OpenAiToolBridge.responsesPlan(" in source,
    "stream usage is supported with explicit estimate marker": '"dsh_usage_estimated"' in source and 'includeUsage instanceof Boolean' in source and 'put("usage", usage)' in source,
    "stream must be a boolean": 'body.opt("stream") instanceof Boolean' in source and '"invalid_stream"' in source,
    "model must be a string when supplied": 'body.opt("model") instanceof String' in source and '"invalid_model"' in source,
    "legacy deepseek-flash model ID is advertised as a compatibility alias": 'new JSONObject().put("id", "deepseek-flash")' in source and "Compatibility alias retained" in source,
    "legacy deepseek-flash requests are canonicalized to deepseek-chat": '"deepseek-flash".equals(requestedModel)' in source and '"deepseek-chat" : requestedModel' in source,
    "gateway startup failures are visible in connection info": '最近启动错误：' in source and 'lastStartError = error.getClass().getSimpleName();' in source,
    "gateway runtime status distinguishes startup failure": 'Stopped (startup failed: ' in source,
    "floating overlay does not report stale gateway heartbeat as listening": "age <= 15_000L" in overlay and "boolean running = z21.gatewayRunning() && heartbeatFresh;" in overlay and '"保活异常"' in overlay,
    "port restart restores previous port after failure": "新端口启动失败，已恢复原端口" in source and "previousPort" in source,
    "HTTP error type matches authentication and server status": 'status >= 500 ? "server_error"' in source and 'status == 401 ? "authentication_error"' in source and 'status == 403 ? "permission_error"' in source,
    "gateway preserves structured upstream HTTP errors": "sendError(out, rejected.status, rejected.code, rejected.type" in source,
    "null upstream result returns 502": '"empty_upstream_response"' in source,
    "HTTP query strings do not break endpoint routing": "int queryStart = path.indexOf('?');" in source,
    "HTTP request line validates version and token count": 'first.length != 3' in source and '"HTTP/1.1".equals(first[2])' in source,
    "duplicate HTTP headers are rejected": "headers.containsKey(name)" in source,
    "malformed HTTP requests receive an explicit 400/413 response": 'sendError(out, status, status == 413 ? "request_too_large"' in source,
    "Content-Type media type is compared exactly": 'contentType.split(";", 2)[0].trim()' in source and '"application/json".equalsIgnoreCase(mediaType)' in source,
    "non-JSON request bodies receive 415": 'sendError(out, 415, "unsupported_media_type"' in source,
}
missing_gateway = [label for label, ok in gateway_checks.items() if not ok]
if missing_gateway:
    raise SystemExit("Missing API protocol behavior: " + ", ".join(missing_gateway))
ui_open = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
if "Open 版提供 OpenAI Chat Completions、Responses 与 Anthropic Messages：" not in ui_open:
    raise SystemExit("Open UI must describe the implemented protocol surface")
if "原生执行适配器尚未接通" not in ui_open:
    raise SystemExit("Open UI must disclose that native completion is not connected")
hook = (pathlib.Path(sys.argv[1]).parent / "HookAccountLoginSecurity.java").read_text(encoding="utf-8")
if 'if ("Open".equals(BuildInfo.BUILD_EDITION)) {' not in overlay or 'httpsSwitch = null;' not in overlay:
    raise SystemExit("Open floating overlay must hide unavailable HTTPS controls")
if 'protocolValue.setClickable(false);' not in overlay or 'OpenAI + Anthropic（固定）' not in overlay:
    raise SystemExit("Open floating overlay must keep the supported protocols fixed")
if '!"Open".equals(BuildInfo.BUILD_EDITION)' not in hook or 'Open 版当前支持 HTTP + OpenAI / Anthropic Messages；HTTPS 暂不可用' not in hook:
    raise SystemExit("Open host control receiver must reject unsupported HTTPS/protocol changes")
if '((operation == null || operation.length() == 0)' not in hook or 'z2.PROTOCOL_ANTHROPIC.equals(protocol)' not in hook:
    raise SystemExit("Open host control receiver must guard the legacy protocol broadcast path")
if 'protocolValue.setText(UiLanguage.text(act, "OpenAI + Anthropic Messages（固定）"' not in ui_open or 'protocolValue.setClickable(false)' not in ui_open:
    raise SystemExit("Open UI must keep the supported protocol selection fixed")

tool_bridge = (pathlib.Path(sys.argv[1]).parent / "OpenAiToolBridge.java").read_text(encoding="utf-8")
bridge_required = {
    "ported OpenAI tool bridge retains MIT copyright": "Copyright (c) 2026 haoyangtu09-art",
    "ported OpenAI tool bridge retains MIT permission grant": "Permission is hereby granted, free of charge",
    "tool plan implements shared backend contract": "static final class Plan implements z2.ToolPlan",
    "Chat tool definitions are parsed": "static Plan chatPlan(",
    "Responses tool definitions are parsed": "static Plan responsesPlan(",
    "tool calls are validated against request definitions": "static List<Call> parseCalls(",
    "tool definition type fields are checked without coercion": 'optionalString(raw, "type", FUNCTION, "invalid_tool_type")',
    "tool definition descriptions are checked without coercion": 'optionalString(source, "description", "", "invalid_tool_description")',
    "function parameters must be an object": 'optionalObject(source, "parameters", "invalid_tool_schema")',
    "tool schemas validate object/properties/required shapes": "validateSchemaShape(schema, \"invalid_tool_schema\")",
    "tool choice object fields are checked without coercion": 'optionalString(object, "type", FUNCTION, "invalid_tool_choice")',
    "tool choice nested function must be an object": 'optionalObject(object, "function", "invalid_tool_choice")',
    "model-produced tool names are not coerced from non-strings": 'if (!(rawName instanceof String)) continue;',
    "model-produced tool namespaces are not coerced from non-strings": '!(rawNamespace instanceof String)) continue;',
    "custom tool input must be a string": 'if (CUSTOM.equals(definition.kind)) return object.opt("input") instanceof String;',
    "function arguments are checked against the declared schema": "return matchesSchema(object, definition.schema, 0);",
    "nested schema property types are enforced": "private static boolean matchesJsonType(Object value, String type)",
    "required schema values are checked as strings": "Object rawKey = required.opt(i);",
}
missing_bridge = [label for label, token in bridge_required.items() if token not in tool_bridge]
if missing_bridge:
    raise SystemExit("Missing OpenAI tool bridge contract: " + ", ".join(missing_bridge))
omni_bridge = (pathlib.Path(sys.argv[1]).parent / "OmniRouteToolBridge.java").read_text(encoding="utf-8")
if "Copyright (c) 2026 diegosouzapw" not in omni_bridge or "normalizeLooseJson" not in omni_bridge:
    raise SystemExit("Missing attributed OmniRoute tool parser")
anthropic = (pathlib.Path(sys.argv[1]).parent / "AnthropicMessagesAdapter.java").read_text(encoding="utf-8")
if "static Request parse(JSONObject body)" not in anthropic or "tool_use" not in anthropic or "tool_result" not in anthropic:
    raise SystemExit("Missing Anthropic Messages request adapter")
if 'block.opt("is_error") instanceof Boolean' not in anthropic:
    raise SystemExit("Anthropic tool_result.is_error must be type-checked as a boolean")
if 'Message role must be a string' not in anthropic or 'Content block type must be a string' not in anthropic or 'tool_use id and name must be strings' not in anthropic or 'tool_use_id must be a string' not in anthropic:
    raise SystemExit("Anthropic message and tool block fields must be validated without coercion")
if 'Set<String> seenToolIds = new HashSet<>()' not in anthropic or '!seenToolIds.add(id)' not in anthropic:
    raise SystemExit("Anthropic history must reject reused tool_use IDs even after a prior result")
if "if (!pendingToolIds.isEmpty())" not in anthropic or "Every tool_use must have a matching tool_result" not in anthropic:
    raise SystemExit("Anthropic history must reject tool_use blocks without matching tool_result blocks")
if '"/v1/responses".equals(request.path)' not in source or "handleResponses(out, request)" not in source:
    raise SystemExit("Responses endpoint is not registered")
if "Object rawCallId = item.opt(\"call_id\");" not in source or "Object rawName = item.opt(\"name\");" not in source:
    raise SystemExit("Responses tool-call history must reject non-string IDs and names")
if "if (!callIds.add(callId))" not in source or "Duplicate tool-call id" not in source:
    raise SystemExit("Responses tool-call history must reject duplicate call IDs")
if "rawArguments instanceof String" not in source:
    raise SystemExit("Responses tool-call arguments must be strings")
if 'basePrompt = "system: " + instructions' not in source:
    raise SystemExit("Responses instructions must remain system context before prior transcript")
if 'Responses input exceeds the prompt size limit' not in source or 'Message content exceeds the prompt size limit' not in source:
    raise SystemExit("Oversized Responses content must be rejected, never silently truncated")
if 'Input item type must be a string' not in source or 'Message role must be a string' not in source or 'Content part type must be a string' not in source:
    raise SystemExit("Responses item, role, and content-part types must be validated without coercion")
if 'Chat messages exceed the prompt size limit' not in source or 'tool_calls must be an array' not in source or 'Tool result call id must be a string' not in source:
    raise SystemExit("Chat history must reject oversized content and malformed tool history")
if 'seenToolCallIds.add(id)' not in source:
    raise SystemExit("Chat history must reject reused tool-call IDs")
if 'Object rawCallId = item.opt("call_id");' not in source or "Tool-call output id must be a string" not in source:
    raise SystemExit("Responses tool-call output IDs must be validated as strings")
if '"/v1/messages".equals(request.path)' not in source or "handleAnthropicMessages(out, request)" not in source:
    raise SystemExit("Anthropic Messages endpoint is not registered")
if 'Responses streaming tool calls are not implemented yet; use stream=false' not in source:
    raise SystemExit("Responses streaming must reject tool calls until structured tool events are implemented")
if 'Streaming tool_use is not implemented yet; use stream=false' not in source:
    raise SystemExit("Anthropic streaming must reject tool_use until structured tool events are implemented")
if 'throw new z2.GatewayException(503, "engine_missing"' not in security:
    raise SystemExit("Open native completion must report engine_missing when the execution payload is absent")
if 'native_adapter_unavailable' in security:
    raise SystemExit("Open native completion must not be blocked by an edition-level gate")

print("LAN lifecycle, key, URL, selectable UI, API protocol, and truthful Open capability checks passed")
PY
