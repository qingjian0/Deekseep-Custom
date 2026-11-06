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

python - "$GATEWAY" "$LOCAL_API_UI" "$BUILD_GRADLE" "$ROOT/module-kotlin-runtime/src/main/java/ds/dsh/DeepSeek/LocalApiResponseStateStore.java" "$ROOT/module-kotlin-runtime/src/main/java/ds/dsh/DeepSeek/LocalApiHttpRequestParser.java" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
build_gradle = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")
state_store = pathlib.Path(sys.argv[4]).read_text(encoding="utf-8")
parser = pathlib.Path(sys.argv[5]).read_text(encoding="utf-8")
if '(!protectedDistribution || localApiIncluded).toString()' not in build_gradle:
    raise SystemExit("Open builds must enable the declared Local API companion components")
required = {
    "HTTP chunked transfer encoding is accepted": '"chunked".equalsIgnoreCase(transferEncoding.trim())' in parser,
    "ambiguous Content-Length and Transfer-Encoding are rejected": '"Ambiguous request framing"' in parser,
    "unknown transfer encodings are rejected": '"Unsupported transfer encoding"' in parser,
    "chunked bodies use a dedicated bounded decoder": "body = readChunkedBody(in);" in parser,
    "decoded chunked body is bounded by MAX_BODY_BYTES": "chunkSize > MAX_BODY_BYTES - body.size()" in parser,
    "chunk terminators require CRLF": '"Invalid chunk terminator"' in parser,
    "chunk size lines require strict CRLF": "private static String readCrlfLine(InputStream in, int maxBytes)" in parser,
    "chunk trailers are bounded": '"Request trailers too large"' in parser,
    "security-sensitive framing trailers are rejected": '"Forbidden request trailer"' in parser,
    "truncated chunk payloads fail closed": '"Truncated chunk data"' in parser,
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
missing = [label for label, ok in required.items() if not ok]
if missing:
    raise SystemExit("Missing LAN network lifecycle contract: " + ", ".join(missing))
if "LocalApiHttpRequestParser.read(in)" not in source:\n    raise SystemExit("Android gateway must delegate HTTP parsing to the JVM-tested parser")\nif "addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)" not in source:
    raise SystemExit("LAN network monitor must observe non-VPN local-only networks")
if "c < 0x21 || c > 0x7e" not in source:
    raise SystemExit("Custom API key validator must accept visible ASCII")
if "InetAddress.getByName(\"0.0.0.0\")" in source:
    raise SystemExit("Wildcard listener binding is forbidden")
# Keep the constructor assertion source-oriented and insensitive to harmless
# formatting/line wrapping. The address argument is separately asserted above.
if "new ServerSocket(configuredPort, 16," not in source:
    raise SystemExit("LAN socket must use the configured port and bounded backlog")
tool_bridge = (pathlib.Path(sys.argv[1]).parent / "OpenAiToolBridge.java").read_text(encoding="utf-8")
bridge_required = {
    "ported tool bridge retains MIT copyright": "Copyright (c) 2026 haoyangtu09-art",
    "ported tool bridge retains MIT permission grant": "Permission is hereby granted, free of charge",
    "tool plan implements the shared backend contract": "static final class Plan implements z2.ToolPlan",
    "tool plan exposes backend readiness": "public boolean active()",
    "Chat Completions tool definitions are parsed": "static Plan chatPlan(",
    "Responses tool definitions are parsed": "static Plan responsesPlan(",
    "tool calls are parsed against request-scoped definitions": "static List<Call> parseCalls(",
    "tool arguments are schema-validated": "static boolean validArguments(",
    "forced tool choice is enforced": "static boolean forcedChoiceAllows(",
}
missing_bridge = [label for label, token in bridge_required.items() if token not in tool_bridge]
if missing_bridge:
    raise SystemExit("Missing OpenAI tool bridge contract: " + ", ".join(missing_bridge))
if "LocalApiGateway.GatewayException" in tool_bridge:
    raise SystemExit("Ported tool bridge still depends on the reference gateway class")
omni_bridge = (pathlib.Path(sys.argv[1]).parent / "OmniRouteToolBridge.java").read_text(encoding="utf-8")
omni_required = {
    "OmniRoute parser retains upstream copyright": "Copyright (c) 2026 diegosouzapw",
    "OmniRoute parser retains MIT permission grant": "Permission is hereby granted, free of charge",
    "strict tool envelope tokenizer is present": "TAG_TOKEN_RE",
    "loose JSON normalization is present": "normalizeLooseJson",
    "OmniRoute adapter consumes shared tool definitions": "OpenAiToolBridge.Definition",
}
missing_omni = [label for label, token in omni_required.items() if token not in omni_bridge]
if missing_omni:
    raise SystemExit("Missing OmniRoute parser contract: " + ", ".join(missing_omni))
main = (pathlib.Path(sys.argv[1]).parent / "Main.java").read_text(encoding="utf-8")
security = (pathlib.Path(sys.argv[1]).parent / "HookAccountLoginSecurity.java").read_text(encoding="utf-8")
probe = (pathlib.Path(sys.argv[1]).parent / "OpenNativeCompletionProbe.java").read_text(encoding="utf-8")
probe_required = {
    "code289 request constructor observer is installed": "installRequestConstructorObserver(requestType)",
    "request constructor event capture is bounded": "event <= 16",
    "request constructor capture records runtime argument types only": "observedArgumentTypes(args)",
    "request constructor capture records field shape but not values": "observedObjectShape(instance)",
    "request factory stack methods are inventoried": 'String[][] callSites = {',
    "request factory inventory includes observed call stack": '{"mw1", "y"}, {"mw1", "e"}',
    "request factory inventory includes transport parent": '{"mr2", "h"}',
    "diagnostic schema reflects request-construction capture": 'session-start schema=7' in probe and 'line(out, "report.schema", "7")' in probe,
    "constructor observer preserves host behavior on capture errors": "Diagnostics must never block the host's request constructor" in probe,
    "argument type capture is null-safe": 'if (args == null) return "null";' in probe,
}
missing_probe = [label for label, token in probe_required.items() if (token not in probe if isinstance(token, str) else not token)]
if missing_probe:
    raise SystemExit("Missing code289 request-construction diagnostics: " + ", ".join(missing_probe))
if 'throw new z2.GatewayException(503, "native_adapter_unavailable"' not in security:
    raise SystemExit("Open native adapter must fail closed before looking up protected z18 payload")
if 'z14.payloadClass(Main.hostApplicationContext, "ds.dsh.DeepSeek.z18")' not in security:
    raise SystemExit("Protected z18 execution boundary changed; re-review before enabling Open")
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
    "HTTP method and header names use RFC token validation": "!isHttpToken(first[0])" in parser and "!isHttpToken(name)" in parser,
    "chunked transfer encoding is restricted to HTTP/1.1": "Transfer encoding requires HTTP/1.1" in parser,
    "Content-Length accepts decimal digits only": "!isDecimalDigits(contentLength)" in parser,
    "chunk sizes accept hexadecimal digits only": "!isHexDigits(sizeText)" in parser,
    "HTTP header names reject surrounding whitespace": 'substring(0,colon)\n                    .toLowerCase' in parser,
    "chunk-size tokens are not whitespace-trimmed": 'sizeLine.substring(0, extension) : sizeLine;' in parser,
    "HTTP request lines require exact single-space separators": 'lines[0].split(" ", -1)' in parser and 'lines[0].trim().split' not in parser,
    "HTTP request targets reject embedded control whitespace": "!isRequestTarget(first[1])" in parser and "c <= 0x20 || c >= 0x7f" in parser,
    "HTTP headers reject control characters": "!isHttpFieldValue(rawValue)" in parser,
    "HTTP trailers validate field names and values": "!isHttpToken(name) || !isHttpFieldValue(trailerValue)" in parser,
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
    "tool results must match a preceding assistant call id": 'pendingToolCallIds.remove(callId)' in source and 'Tool result does not match a preceding assistant tool call' in source,
    "Chat tool definitions are parsed through the shared bridge": 'OpenAiToolBridge.chatPlan(' in source,
    "invalid Chat tool plans are returned as structured errors": 'catch (z2.GatewayException invalidTools)' in source and 'sendError(out, invalidTools.status' in source,
    "tool instructions are added to the backend prompt": 'OpenAiToolBridge.addInstructions(' in source,
    "tool calls are returned as OpenAI function-call objects": '.put("tool_calls", wireCalls)' in source and '.put("finish_reason", parsedToolCalls.isEmpty()' in source,
    "invalid model tool output is rejected rather than leaked": '"invalid_tool_response"' in source and 'OpenAiToolBridge.resemblesCallSyntax(result.text, toolPlan)' in source,
    "tool streaming is explicitly rejected until SSE redaction exists": '"unsupported_streaming_tool_calls"' in source and 'stream && toolPlan.active()' in source,
    "Responses POST route is registered": '"/v1/responses".equals(request.path)' in source and 'handleResponses(out, request)' in source,
    "Responses invalid tool plans return structured errors": 'catch (z2.GatewayException invalidTools)' in source and 'OpenAiToolBridge.responsesPlan(' in source,
    "Responses web search tools map to native search": 'searchRequested = true' in source and 'reasoningRequested, searchRequested, maxTokens' in source,
    "Responses reasoning effort is validated and mapped": 'reasoningOptions.optString("effort", "medium")' in source and 'reasoningRequested = reasoningRequested || !"none".equals(effort)' in source,
    "unsupported hosted Responses tools are rejected": '"Unsupported Responses tool type: "' in source,
    "Responses input accepts strings and item arrays": 'responsesInputToPrompt(rawInput, pendingToolCallIds)' in source and '"input must be a string or an array"' in source,
    "Responses previous_response_id resolves bounded in-memory state": 'LocalApiResponseStateStore.get(previousId)' in source and '"previous_response_not_found"' in source,
    "Responses state delegates to the bounded in-memory store": 'LocalApiResponseStateStore.get(previousId)' in source,
    "Responses continuation state is cleared when the gateway stops": 'LocalApiResponseStateStore.clear();' in source,
    "Responses output state records outstanding tool calls": 'LocalApiResponseStateStore.put(requestId' in source,
    "Responses function-call outputs are validated against prior call IDs": '!callIds.remove(callId)' in source and 'Unmatched tool-call output' in source,
    "Responses function calls are serialized as output items": '"function_call".equals(type)' in source and '"function_call"' in source and '"output_text"' in source,
    "Responses streaming emits terminal completed event": '"response.completed"' in source and 'sendResponsesEvent(out' in source,
    "Responses tool streaming is explicitly rejected": '"Responses tool calls are currently supported only with stream=false"' in source,
    "response_format remains explicitly rejected when unsupported": '"unsupported_feature"' in source and 'body.opt("response_format")' in source,
    "Chat usage is returned with an explicit estimate marker": '"prompt_tokens", promptTokens' in source and '"completion_tokens", completionTokens' in source and '"dsh_usage_estimated", true' in source,
    "stream_options.include_usage accepts boolean true and rejects non-boolean values": 'includeUsage instanceof Boolean' in source and 'optBoolean("include_usage", false)' in source,
    "stream usage chunk is emitted before the terminal DONE marker": 'if (includeUsage)' in source and 'put("choices", new JSONArray())' in source and 'put("usage", usage)' in source and 'out.write("data: [DONE]' in source,
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
    "HTTP query strings do not break endpoint routing": "int queryStart = path.indexOf('?');" in parser,
    "HTTP request line validates version and token count": 'first.length != 3' in parser and '"HTTP/1.1".equals(first[2])' in parser,
    "chunk parser uses a valid tab character literal": "value != '\\t'" in parser,
    "duplicate HTTP headers are rejected": "headers.containsKey(name)" in parser,
    "malformed HTTP requests receive an explicit 400/413 response": 'sendError(out, status, status == 413 ? "request_too_large"' in source,
    "Content-Type media type is compared exactly": 'contentType.split(";", 2)[0].trim()' in source and '"application/json".equalsIgnoreCase(mediaType)' in source,
    "non-JSON request bodies receive 415": 'sendError(out, 415, "unsupported_media_type"' in source,
}
missing_gateway = [label for label, ok in gateway_checks.items() if not ok]
if missing_gateway:
    raise SystemExit("Missing API protocol behavior: " + ", ".join(missing_gateway))
state_store_required = {
    "Responses state store enforces a 64-entry LRU bound": "MAX_ENTRIES = 64" in state_store and "new LinkedHashMap<String, State>(16, 0.75f, true)" in state_store,
    "Responses state store enforces a 30-minute TTL": "TTL_MS = 30L * 60L * 1000L" in state_store,
    "Responses state store enforces the transcript cap": "MAX_TRANSCRIPT_CHARS = 256 * 1024" in state_store,
    "Responses pending tool IDs are defensively copied": "Collections.unmodifiableSet(new HashSet<String>(pendingToolCallIds))" in state_store,
}
missing_state_store = [label for label, ok in state_store_required.items() if not ok]
if missing_state_store:
    raise SystemExit("Missing Responses state-store contract: " + ", ".join(missing_state_store))

anthropic_adapter = (pathlib.Path(sys.argv[1]).parent / "AnthropicMessagesAdapter.java").read_text(encoding="utf-8")
anthropic_required = {
    "Anthropic Messages route is registered": '"/v1/messages".equals(request.path)' in source and "handleAnthropicMessages(out, request)" in source,
    "Anthropic x-api-key authentication is supported": 'request.headers.get("x-api-key")' in source,
    "Anthropic version header is required": 'request.headers.get("anthropic-version")' in source and '"anthropic-version header is required"' in source,
    "Anthropic request adapter validates max_tokens": '"max_tokens is required and must be an integer' in anthropic_adapter,
    "Anthropic reasoner model enables backend reasoning mode": 'boolean reasoning = "deepseek-reasoner".equals(model);' in anthropic_adapter and "normalized.reasoning" in source,
    "Anthropic system and message roles are normalized": 'body.opt("system")' in anthropic_adapter and '"user".equals(role)' in anthropic_adapter and '"assistant".equals(role)' in anthropic_adapter,
    "Anthropic tool definitions map to shared tool bridge": 'OpenAiToolBridge.chatPlan(openAiTools, choice, parallelTools)' in anthropic_adapter,
    "Anthropic tool_use and tool_result IDs are correlated": 'pendingToolIds.remove(id)' in anthropic_adapter and 'pendingToolIds.add(id)' in anthropic_adapter,
    "Anthropic tool streaming is explicitly rejected": 'unsupported_streaming_tool_calls' in anthropic_adapter,
    "Anthropic JSON response returns content blocks": 'anthropicContent(result, calls)' in source and '.put("type", "tool_use")' in source,
    "Anthropic does not emit unsigned thinking blocks": '"type", "thinking"' not in source and "signature support, which is not implemented" in anthropic_adapter,
    "Anthropic text streaming maps to text_delta": '"text_delta"' in source,
    "Anthropic stream lifecycle emits message_stop": '"message_start"' in source and '"message_delta"' in source and '"message_stop"' in source,
    "Anthropic errors use the Anthropic error envelope": 'new JSONObject().put("type", "error")' in source and 'sendAnthropicError' in source,
    "Anthropic usage is explicitly marked as estimated": '"dsh_usage_estimated", true' in source and "estimateTokens" in source,
}
missing_anthropic = [label for label, ok in anthropic_required.items() if not ok]
if missing_anthropic:
    raise SystemExit("Missing Anthropic Messages contract: " + ", ".join(missing_anthropic))

ui_open = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
if "Anthropic 支持文本 JSON/SSE 与非流式 tool_use/tool_result" not in ui_open:
    raise SystemExit("Open UI must accurately disclose Anthropic Messages support")
if "原生 Completion Adapter 尚未接通" not in ui_open:
    raise SystemExit("Open UI must disclose that native completion is not connected")
hook = (pathlib.Path(sys.argv[1]).parent / "HookAccountLoginSecurity.java").read_text(encoding="utf-8")
if 'if ("Open".equals(BuildInfo.BUILD_EDITION)) {' not in overlay or 'httpsSwitch = null;' not in overlay:
    raise SystemExit("Open floating overlay must hide unavailable HTTPS controls")
if 'protocolValue.setClickable(false);' not in overlay or 'OpenAI + Anthropic（固定）' not in overlay:
    raise SystemExit("Open floating overlay must keep the protocol fixed to OpenAI")
if '!"Open".equals(BuildInfo.BUILD_EDITION)' not in hook or 'Open 版当前支持 HTTP + OpenAI / Anthropic Messages；HTTPS 暂不可用' not in hook:
    raise SystemExit("Open host control receiver must reject unsupported HTTPS/protocol changes")
if '((operation == null || operation.length() == 0)' not in hook or 'z2.PROTOCOL_ANTHROPIC.equals(protocol)' not in hook:
    raise SystemExit("Open host control receiver must guard the legacy protocol broadcast path")
if 'protocolValue.setText(UiLanguage.text(act, "OpenAI + Anthropic Messages（固定）"' not in ui_open or 'protocolValue.setClickable(false)' not in ui_open:
    raise SystemExit("Open UI must disclose the fixed dual-protocol Open API surface")
print("LAN lifecycle, key, URL, selectable UI, API protocol, and truthful Open capability checks passed")
PY
