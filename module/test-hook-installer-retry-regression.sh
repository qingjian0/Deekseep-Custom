#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT_DIR/module/src/ds/dsh/DeepSeek"

AGENT="$SRC/HookAgentPipeline.java"
CHAT="$SRC/HookChatPipeline.java"
UI="$SRC/HookUiSurface.java"
MAIN="$SRC/Main.java"

for file in "$AGENT" "$CHAT" "$UI" "$MAIN"; do
  test -s "$file"
done

# Every successful Member registration is recorded only after the framework accepts it.
for spec in \
  "$AGENT:hookAgentToolOnce:AGENT_TOOL_INSTALLED_HOOKS" \
  "$CHAT:hookChatRequestOnce:CHAT_REQUEST_INSTALLED_MEMBERS" \
  "$UI:hookSettingsNavigationOnce:SETTINGS_NAV_INSTALLED_MEMBERS"; do
  IFS=: read -r file helper set_name <<<"$spec"
  body="$(sed -n "/boolean $helper(/,/^    }/p" "$file")"
  test -n "$body"
  grep -Fq "synchronized ($set_name)" <<<"$body"
  grep -Fq "if ($set_name.contains(member)) return false;" <<<"$body"
  hook_line="$(grep -nF 'Main.MODULE.hook(member).intercept(hooker);' <<<"$body" | cut -d: -f1)"
  add_line="$(grep -nF "$set_name.add(member);" <<<"$body" | cut -d: -f1)"
  test -n "$hook_line" && test -n "$add_line"
  test "$hook_line" -lt "$add_line"
done

# Agent multi-target installers route their Member registrations through the retry-safe helper.
agent_236="$(sed -n '/void hookAgentToolLogRoundedRect(ClassLoader cl)/,/private void hookV241AgentToolLogRoundedRect/p' "$AGENT")"
agent_241="$(sed -n '/private void hookV241AgentToolLogRoundedRect/,/private void hookAgentToolLogTouchSurface/p' "$AGENT")"
grep -Fq 'hookAgentToolOnce(constructor, new Hooker()' <<<"$agent_236"
grep -Fq 'hookAgentToolOnce(draw, new Hooker()' <<<"$agent_236"
grep -Fq 'hookAgentToolOnce(frameDraw, new Hooker()' <<<"$agent_236"
grep -Fq 'hookAgentToolOnce(constructor, new Hooker()' <<<"$agent_241"
grep -Fq 'hookAgentToolOnce(draw, new Hooker()' <<<"$agent_241"

# Chat request constructor installation uses the helper and reports an empty/failed install.
chat_body="$(sed -n '/boolean hookChatRequest(ClassLoader cl)/,/private static String combineSystemPrompts/p' "$CHAT")"
grep -Fq 'hookChatRequestOnce(ctor, new Hooker()' <<<"$chat_body"
if grep -Fq 'Main.MODULE.hook(ctor)' <<<"$chat_body"; then
  echo "Chat request installer bypasses the retry-safe registration helper." >&2
  exit 1
fi
grep -Fq 'return n > 0;' <<<"$chat_body"
grep -Fq 'return false;' <<<"$chat_body"
main_chat="$(sed -n '/installHookStep("chat.request-construction"/,/ensureOptionalHooksForEnabledFeatures/p' "$MAIN")"
grep -Fq 'throw new IllegalStateException("chat request constructors were not hooked")' <<<"$main_chat"

# Settings navigation route and pop registrations share the same retry-safe helper;
# a failure from either half must be propagated to the feature install registry.
nav_body="$(sed -n '/void hookSettingsNavigation(ClassLoader cl)/,/private void rememberNavController/p' "$UI")"
grep -Fq 'hookSettingsNavigationOnce(m, new Hooker()' <<<"$nav_body"
grep -Fq 'if (installFailure != null) throw installFailure;' <<<"$nav_body"
grep -Fq 'private int hookNavStateMethod(Class<?> nav, String name) throws Throwable' "$UI"

# Main's install wrapper must preserve failure visibility instead of silently swallowing it.
install_body="$(sed -n '/private Throwable runHookInstallStep/,/void installHookStep/p' "$MAIN")"
grep -Fq 'FEATURE_HOOK_INSTALL_REGISTRY.install(id' <<<"$install_body"
grep -Fq 'return error;' <<<"$install_body"

echo "Hook installer retry and failure-propagation regression checks passed."
