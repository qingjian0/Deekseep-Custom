#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY="$ROOT/module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java"
LOCAL_API_UI="$ROOT/module/src/ds/dsh/DeepSeek/DeekseepUi.java"
for manifest in "$ROOT/module-universal/AndroidManifest.xml" "$ROOT/module-legacy/AndroidManifest.xml"; do
    grep -Fq 'android.permission.ACCESS_NETWORK_STATE' "$manifest"
    grep -Fq 'android.permission.FOREGROUND_SERVICE' "$manifest"
    grep -Fq 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE' "$manifest"
    grep -Fq 'android:name="ds.dsh.DeepSeek.z20"' "$manifest"
    grep -Fq 'android:exported="true"' "$manifest"
    grep -Fq 'android:name="ds.dsh.DeepSeek.z21"' "$manifest"
    grep -Fq 'android:foregroundServiceType="specialUse"' "$manifest"
    grep -Fq 'android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"' "$manifest"
done

python - "$GATEWAY" "$LOCAL_API_UI" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
required = {
    "active Android network lookup": "manager.getActiveNetwork()",
    "active network link properties": "manager.getLinkProperties(active)",
    "all-network callback registration": "manager.registerNetworkCallback(",
    "API-compatible network capability import": "import android.net.NetworkCapabilities;",
    "API-compatible VPN matching": "removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)",
    "all-network callback request": "removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)",
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
    "local API key prefix": '"sk-" + android.util.Base64.encodeToString(random',
    "custom key requires official-style prefix": 'sk-[A-Za-z0-9_-]{40,80}',
    "persisted key migration accepts only prefixed keys": 'existing.matches("sk-[A-Za-z0-9_-]{40,80}")',
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
if 'String error = HookAccountLoginSecurity.localApiKeepAliveError;' not in main:
    raise SystemExit("Foreground status no longer reads its own error field")
if 'String error = HookAccountLoginSecurity.localApiFloatingWindowError;' not in main:
    raise SystemExit("Floating-window status does not read its own error field")
if 'HookAccountLoginSecurity.localApiKeepAliveError = "小窗保活启动失败：' in main:
    raise SystemExit("Floating-window failure still contaminates foreground status")
keepalive_start = main.index("private static boolean requestLocalApiKeepAlive(\\n            Context context, boolean enabled, boolean forceControl)")
floating_start = main.index("private static boolean requestLocalApiFloatingWindowExact(\\n            Context context, boolean enabled, String adapter)")
keepalive_method = main[keepalive_start:floating_start]
floating_method = main[floating_start:main.index("static String localApiKeepAliveStatus()", floating_start)]
if 'if (context == null) {\\n            HookAccountLoginSecurity.localApiKeepAliveError = "DeepSeek 上下文尚未就绪";' not in keepalive_method:
    raise SystemExit("Foreground keepalive null-context failure is not stored independently")
if 'if (context == null) {\\n            HookAccountLoginSecurity.localApiFloatingWindowError = "DeepSeek 上下文尚未就绪";' not in floating_method:
    raise SystemExit("Floating-window null-context failure is not stored independently")
ui = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
ui_required = {
    "connection details allow long-press text selection": "connectionInfo.setTextIsSelectable(true);",
    "OpenAI URL copy includes /v1": "Main.localApiCopyEndpoint()",
    "Anthropic URL copy retains root endpoint": "Main.localApiRootEndpoint()",
}
missing_ui = [label for label, token in ui_required.items() if token not in ui]
if missing_ui:
    raise SystemExit("Missing local API UI contract: " + ", ".join(missing_ui))
print("LAN lifecycle, key, URL, and selectable-connection UI source checks passed")
PY
