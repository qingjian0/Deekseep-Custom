#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT_DIR/module/src/ds/dsh/DeepSeek"

AGENT="$SRC/HookAgentPipeline.java"
CHAT="$SRC/HookChatPipeline.java"
UI="$SRC/HookUiSurface.java"
ATTACHMENT="$SRC/HookAttachmentPipeline.java"
SESSION="$SRC/HookSessionManagement.java"
MAIN="$SRC/Main.java"

# This is intentionally a lightweight source guard, not a substitute for runtime/device tests.
for file in "$AGENT" "$CHAT" "$UI" "$ATTACHMENT" "$SESSION" "$MAIN"; do
  test -s "$file"
done

# Shared registration helpers must serialize registration and remember only successful hooks.
check_member_helper() {
  local file="$1" helper="$2" set_name="$3"
  local body
  body="$(sed -n "/boolean $helper(/,/^    }/p" "$file")"
  test -n "$body"
  grep -Fq "synchronized ($set_name)" <<<"$body"
  grep -Fq "$set_name.contains(member)" <<<"$body"
  grep -Fq "Main.MODULE.hook(member)" <<<"$body"
  grep -Fq "$set_name.add(member)" <<<"$body"
}

check_member_helper "$AGENT" hookAgentToolOnce AGENT_TOOL_INSTALLED_HOOKS
check_member_helper "$CHAT" hookChatRequestOnce CHAT_REQUEST_INSTALLED_MEMBERS
check_member_helper "$UI" hookSettingsNavigationOnce SETTINGS_NAV_INSTALLED_MEMBERS

# Main must route installation through the registry and preserve thrown failures.
install_body="$(sed -n '/private Throwable runHookInstallStep/,/void installHookStep/p' "$MAIN")"
grep -Fq 'FEATURE_HOOK_INSTALL_REGISTRY.install(id' <<<"$install_body"
grep -Fq 'return error;' <<<"$install_body"

# Attachment credential bridge: retain successful constructor registrations and expose failures.
grep -Fq 'IMAGE_CREDENTIAL_BRIDGE_HOOKED' "$ATTACHMENT"
bridge_body="$(sed -n '/void installImageCredentialBridge(/,/\/\*\* Removes the gateway/p' "$ATTACHMENT")"
grep -Fq 'installFailure' <<<"$bridge_body"
grep -Fq 'addSuppressed(' <<<"$bridge_body"
grep -Fq 'throw installFailure' <<<"$bridge_body"

# Session click installation must track successful Methods and clear in-progress state.
grep -Fq 'NATIVE_CLICK_HOOKED_METHODS' "$SESSION"
click_body="$(sed -n '/private void hookNativeSessionClickCallback(/,/static List copyListForHook/p' "$SESSION")"
grep -Fq 'NATIVE_CLICK_HOOKED_METHODS.contains(method)' <<<"$click_body"
grep -Fq 'NATIVE_CLICK_HOOKED_METHODS.add(method)' <<<"$click_body"
grep -Fq 'NATIVE_CLICK_INSTALLING_CLASSES.remove(callbackClass)' <<<"$click_body"

echo "Hook installer retry smoke checks passed."
