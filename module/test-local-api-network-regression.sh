#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY="$ROOT/module/src/ds/dsh/DeepSeek/OpenLocalApiGateway.java"
for manifest in "$ROOT/module-universal/AndroidManifest.xml" "$ROOT/module-legacy/AndroidManifest.xml"; do
    grep -Fq 'android.permission.ACCESS_NETWORK_STATE' "$manifest"
done

python - "$GATEWAY" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
required = {
    "active Android network lookup": "manager.getActiveNetwork()",
    "active network link properties": "manager.getLinkProperties(active)",
    "default network callback registration": "manager.registerDefaultNetworkCallback(callback)",
    "network available callback": "onAvailable(Network network)",
    "network lost callback": "onLost(Network network)",
    "link properties callback": "onLinkPropertiesChanged(",
    "callback cleanup": "manager.unregisterNetworkCallback(callback)",
    "exact interface address binding": "InetAddress.getByName(address)",
    "LAN-only address range": "(bytes[0] & 255) == 192 && (bytes[1] & 255) == 168",
    "LAN bind refresh": "refreshLanBinding()",
    "network-loss listener close": "current.close()",
}
missing = [label for label, token in required.items() if token not in source]
if missing:
    raise SystemExit("Missing LAN network lifecycle contract: " + ", ".join(missing))
if "InetAddress.getByName(\"0.0.0.0\")" in source:
    raise SystemExit("Wildcard listener binding is forbidden")
if "new ServerSocket(configuredPort, 16, InetAddress.getByName(address))" not in source:
    raise SystemExit("LAN socket must bind the discovered exact IPv4 address")
print("LAN network callback, exact-address binding, and lifecycle source checks passed")
PY
