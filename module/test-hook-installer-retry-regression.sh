#!/usr/bin/env bash
set -euo pipefail
trap 'status=$?; printf "[hook-installer-retry] failed at line %s: %s (exit %s)\n" "$LINENO" "$BASH_COMMAND" "$status" >&2' ERR

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT_DIR/module/src/ds/dsh/DeepSeek"

AGENT="$SRC/HookAgentPipeline.java"
CHAT="$SRC/HookChatPipeline.java"
UI="$SRC/HookUiSurface.java"
CONFIG="$SRC/HookConfigRollout.java"
MAIN="$SRC/Main.java"

for file in "$AGENT" "$CHAT" "$UI" "$CONFIG" "$MAIN"; do
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
nav_state_body="$(sed -n '/private int hookNavStateMethod(/,/private void rememberNavController/p' "$UI")"
grep -Fq 'if (count == 0 && installFailure != null) throw installFailure;' <<<"$nav_state_body"
grep -Fq 'installFailure.addSuppressed(error)' <<<"$nav_state_body"

# Config rollout hooks must use per-Method registration memory so a later installer
# retry cannot stack duplicate interceptors after a partial registration.
hot_update="$(sed -n '/void hookHotUpdateDialog(/,/void hookRemoteConfigHotUpdates/p' "$CONFIG")"
home_welcome="$(sed -n '/void hookHomeWelcomeRollout(/,/^    }/p' "$CONFIG")"
grep -Fq 'hookRolloutMethodOnce(method, new Hooker()' <<<"$hot_update"
grep -Fq 'hookRolloutMethodOnce(getter, new Hooker()' <<<"$home_welcome"
if grep -Fq 'Main.MODULE.hook(' <<<"$hot_update$home_welcome"; then
  echo "Config rollout installer bypasses retry-safe registration memory." >&2
  exit 1
fi

# Fake Mute must distinguish an unsupported mapping from a partial installation.
fake_mute="$(sed -n '/void hookNativeFakeMute(ClassLoader loader)/,/static boolean isGoogleLoginUnlock/p' "$SRC/HookAccountLoginSecurity.java")"
grep -Fq 'if (generationInstalled == 0)' <<<"$fake_mute"
grep -Fq 'if (generationInstalled > 0)' <<<"$fake_mute"
grep -Fq 'throw failure;' <<<"$fake_mute"
grep -Fq 'if (lastFailure != null) throw lastFailure;' <<<"$fake_mute"
# Image credential bridge must remember successful constructor hooks and surface partial failure.
attachment_src="$SRC/HookAttachmentPipeline.java"
grep -Fq 'IMAGE_CREDENTIAL_BRIDGE_HOOKED' "$attachment_src"
bridge_helper="$(sed -n '/private static boolean hookImageCredentialBridgeConstructor/,/void installImageCredentialBridge/p' "$attachment_src")"
grep -Fq 'synchronized (IMAGE_CREDENTIAL_BRIDGE_HOOKED)' <<<"$bridge_helper"
grep -Fq 'IMAGE_CREDENTIAL_BRIDGE_HOOKED.contains(constructor)' <<<"$bridge_helper"
grep -Fq 'IMAGE_CREDENTIAL_BRIDGE_HOOKED.add(constructor)' <<<"$bridge_helper"
bridge_body="$(sed -n '/void installImageCredentialBridge(/,/\/\*\* Removes the gateway/p' "$attachment_src")"
grep -Fq 'if (installFailure != null) throw installFailure;' <<<"$bridge_body"
grep -Fq 'installFailure.addSuppressed(t)' <<<"$bridge_body"
grep -Fq 'pv0 image credential bridge constructors' <<<"$bridge_body"
grep -Fq 'k31 image credential bridge constructors' <<<"$bridge_body"
grep -Fq 'ownerInstalled == 0' <<<"$bridge_body"

# Expert history preserver registers each Constructor/Method once and aggregates owner failures.
grep -Fq 'HISTORY_PRESERVER_HOOKED_MEMBERS' "$attachment_src"
history_helper="$(sed -n '/private static void hookHistoryPreserverMember/,/void installExpertHistoryImagePreserver/p' "$attachment_src")"
grep -Fq 'synchronized (HISTORY_PRESERVER_HOOKED_MEMBERS)' <<<"$history_helper"
grep -Fq 'HISTORY_PRESERVER_HOOKED_MEMBERS.contains(member)' <<<"$history_helper"
grep -Fq 'HISTORY_PRESERVER_HOOKED_MEMBERS.add(member)' <<<"$history_helper"
history_body="$(sed -n '/void installExpertHistoryImagePreserver(/,/private static void scheduleFrozenEditReapply/p' "$attachment_src")"
grep -Fq 'hookHistoryPreserverMember(ctor, new Hooker()' <<<"$history_body"
grep -Fq 'hookHistoryPreserverMember(writer, new Hooker()' <<<"$history_body"
grep -Fq 'installFailure.addSuppressed(error)' <<<"$history_body"
if grep -Fq 'catch (Throwable ignored) {}' <<<"$history_body"; then
  echo "History preserver silently ignores an installation failure." >&2
  exit 1
fi

# Attachment file-link and image-preview boundary retries reuse successful Member hooks.
grep -Fq 'ATTACHMENT_PIPELINE_HOOKED_MEMBERS' "$attachment_src"
attachment_helper="$(sed -n '/private static void hookAttachmentMember/,/private static boolean hookImageCredentialBridgeConstructor/p' "$attachment_src")"
grep -Fq 'synchronized (ATTACHMENT_PIPELINE_HOOKED_MEMBERS)' <<<"$attachment_helper"
grep -Fq 'ATTACHMENT_PIPELINE_HOOKED_MEMBERS.contains(member)' <<<"$attachment_helper"
grep -Fq 'ATTACHMENT_PIPELINE_HOOKED_MEMBERS.add(member)' <<<"$attachment_helper"
file_link_body="$(sed -n '/void hookModelFileLinks()/,/private static Activity activityFromContext/p' "$attachment_src")"
grep -Fq 'hookAttachmentMember(startActivity, new Hooker()' <<<"$file_link_body"
if grep -Fq 'Main.MODULE.hook(startActivity)' <<<"$file_link_body"; then
  echo "File-link installer bypasses attachment Member de-duplication." >&2
  exit 1
fi
preview_body="$(sed -n '/void hookNativeImagePreviewBoundary/,/void hookNativeModelFileCards/p' "$attachment_src")"
grep -Fq 'hookAttachmentMember(boundary, new Hooker()' <<<"$preview_body"
grep -Fq 'hookAttachmentMember(method, new Hooker()' <<<"$preview_body"
# Remaining attachment installers share the same Member-level idempotent registrar.
for spec in \
  'void hookLocalEditorImageUris(:resolve' \
  'void hookNativeModelFileCards(:method' \
  'void hookExpertUnlock(:ctor' \
  'void hookExpertUnlock(:mtd' \
  'void installExpertUploadGate(:mtd' \
  'void f9(:ctor' \
  'void hookSendPointFps234(:send' \
  'void hookSendPointFps(final:y' \
  'void installExpertFlowCollectHook(:bColl'; do
  IFS=: read -r method target <<<"$spec"
  body="$(sed -n "/$method/,/^    }/p" "$attachment_src")"
  grep -Fq "hookAttachmentMember($target, new Hooker()" <<<"$body"
  if grep -Fq "Main.MODULE.hook($target)" <<<"$body"; then
    echo "$method bypasses attachment retry-safe registration." >&2
    exit 1
  fi
done


# Login-risk diagnostic retries must not register the same host Member more than once.
login_src="$SRC/HookAccountLoginSecurity.java"
grep -Fq 'LOGIN_RISK_HOOKED_MEMBERS' "$login_src"
login_helper="$(sed -n '/private static void hookLoginRiskMember/,/void installLoginRiskLogger/p' "$login_src")"
grep -Fq 'synchronized (LOGIN_RISK_HOOKED_MEMBERS)' <<<"$login_helper"
grep -Fq 'LOGIN_RISK_HOOKED_MEMBERS.contains(member)' <<<"$login_helper"
grep -Fq 'LOGIN_RISK_HOOKED_MEMBERS.add(member)' <<<"$login_helper"
login_body="$(sed -n '/void installLoginRiskLogger(/,/private static Throwable appendOptionalHookFailure/p' "$login_src")"
grep -Fq 'hookLoginRiskMember(method, new Hooker()' <<<"$login_body"
grep -Fq 'hookLoginRiskMember(ctor, new Hooker()' <<<"$login_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$login_body"; then
  echo "Login-risk installer bypasses retry-safe registration." >&2
  exit 1
fi
biz_body="$(sed -n '/private boolean installBizCodeLogger(/,/private Object proceedWithRegionalLoginOptions/p' "$login_src")"
grep -Fq 'hookLoginRiskMember(ctor, new Hooker()' <<<"$biz_body"

# Local API routing preserves successful v2.4.1 Member hooks across partial-install retries.
grep -Fq 'LOCAL_API_ROUTING_HOOKED_MEMBERS' "$login_src"
routing_helper="$(sed -n '/private static void hookLocalApiRoutingMember/,/void hookLocalApiAccountRouting/p' "$login_src")"
grep -Fq 'synchronized (LOCAL_API_ROUTING_HOOKED_MEMBERS)' <<<"$routing_helper"
grep -Fq 'LOCAL_API_ROUTING_HOOKED_MEMBERS.contains(member)' <<<"$routing_helper"
grep -Fq 'LOCAL_API_ROUTING_HOOKED_MEMBERS.add(member)' <<<"$routing_helper"
routing_body="$(sed -n '/void hookLocalApiAccountRouting(/,/private void hookV241LocalApiUploadCoroutineRouting/p' "$login_src")"
grep -Fq 'hookLocalApiRoutingMember(method, new Hooker()' <<<"$routing_body"
grep -Fq 'hookLocalApiRoutingMember(constructor, new Hooker()' <<<"$routing_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$routing_body"; then
  echo "Local API routing bypasses retry-safe registration." >&2
  exit 1
fi

# Heartbeat live/static response constructors and methods are retry-idempotent.
grep -Fq 'HEARTBEAT_RESPONSE_HOOKED_MEMBERS' "$AGENT"
heartbeat_helper="$(sed -n '/private static void hookHeartbeatResponseMember/,/void hookHeartbeatToolResponses/p' "$AGENT")"
grep -Fq 'synchronized (HEARTBEAT_RESPONSE_HOOKED_MEMBERS)' <<<"$heartbeat_helper"
grep -Fq 'HEARTBEAT_RESPONSE_HOOKED_MEMBERS.contains(member)' <<<"$heartbeat_helper"
grep -Fq 'HEARTBEAT_RESPONSE_HOOKED_MEMBERS.add(member)' <<<"$heartbeat_helper"
heartbeat_body="$(sed -n '/void hookHeartbeatToolResponses(/,/void hookHeartbeatPatchDispatcher/p' "$AGENT")"
grep -Fq 'hookHeartbeatResponseMember(ctor, new Hooker()' <<<"$heartbeat_body"
grep -Fq 'hookHeartbeatResponseMember(method, new Hooker()' <<<"$heartbeat_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$heartbeat_body"; then
  echo "Heartbeat response installer bypasses retry-safe registration." >&2
  exit 1
fi

# Heartbeat patch dispatcher and mutable State write targets share retry-safe registration.
grep -Fq 'HEARTBEAT_PIPELINE_HOOKED_MEMBERS' "$AGENT"
pipeline_helper="$(sed -n '/private static void hookHeartbeatPipelineMember/,/void hookHeartbeatPatchDispatcher/p' "$AGENT")"
grep -Fq 'synchronized (HEARTBEAT_PIPELINE_HOOKED_MEMBERS)' <<<"$pipeline_helper"
grep -Fq 'HEARTBEAT_PIPELINE_HOOKED_MEMBERS.contains(member)' <<<"$pipeline_helper"
grep -Fq 'HEARTBEAT_PIPELINE_HOOKED_MEMBERS.add(member)' <<<"$pipeline_helper"
patch_body="$(sed -n '/void hookHeartbeatPatchDispatcher(/,/private static void registerHeartbeatFragmentState/p' "$AGENT")"
grep -Fq 'hookHeartbeatPipelineMember(method, new Hooker()' <<<"$patch_body"
state_body="$(sed -n '/void hookTrackedHeartbeatStateWrites(/,/void hookHeartbeat/p' "$AGENT")"
grep -Fq 'hookHeartbeatPipelineMember(method, new Hooker()' <<<"$state_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$patch_body$state_body"; then
  echo "Heartbeat patch/state installer bypasses retry-safe registration." >&2
  exit 1
fi

# Chat diagnostic constructor hooks preserve successful registrations across retries.
chat_src="$SRC/HookChatPipeline.java"
grep -Fq 'CHAT_DIAGNOSTIC_HOOKED_MEMBERS' "$chat_src"
chat_helper="$(sed -n '/private static void hookChatDiagnosticMember/,/void hookSafetyRetraction/p' "$chat_src")"
grep -Fq 'synchronized (CHAT_DIAGNOSTIC_HOOKED_MEMBERS)' <<<"$chat_helper"
grep -Fq 'CHAT_DIAGNOSTIC_HOOKED_MEMBERS.contains(member)' <<<"$chat_helper"
grep -Fq 'CHAT_DIAGNOSTIC_HOOKED_MEMBERS.add(member)' <<<"$chat_helper"
safety_body="$(sed -n '/void hookSafetyRetraction(/,/诊断：抓取服务器返回的 SSE 原始事件/p' "$chat_src")"
grep -Fq 'hookChatDiagnosticMember(ctor, new Hooker()' <<<"$safety_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$safety_body"; then
  echo "Safety-retraction installer bypasses retry-safe registration." >&2
  exit 1
fi
server_capture_body="$(sed -n '/void installServerCapture(/,/Sanitizes 2.3.4/p' "$chat_src")"
grep -Fq 'hookChatDiagnosticMember(ctor, new Hooker()' <<<"$server_capture_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$server_capture_body"; then
  echo "Server-capture installer bypasses retry-safe registration." >&2
  exit 1
fi

# UI text-wave and host-theme installers preserve successful Member hooks across retries.
ui_src="$SRC/HookUiSurface.java"
grep -Fq 'UI_SURFACE_HOOKED_MEMBERS' "$ui_src"
ui_helper="$(sed -n '/private static void hookUiSurfaceMember/,/void hookTextWaveMotion/p' "$ui_src")"
grep -Fq 'synchronized (UI_SURFACE_HOOKED_MEMBERS)' <<<"$ui_helper"
grep -Fq 'UI_SURFACE_HOOKED_MEMBERS.contains(member)' <<<"$ui_helper"
grep -Fq 'UI_SURFACE_HOOKED_MEMBERS.add(member)' <<<"$ui_helper"
text_wave_body="$(sed -n '/void hookTextWaveMotion(/,/Replaces DeepSeek/p' "$ui_src")"
grep -Fq 'hookUiSurfaceMember(onDraw, new Hooker()' <<<"$text_wave_body"
grep -Fq 'hookUiSurfaceMember(candidate, new Hooker()' <<<"$text_wave_body"
grep -Fq 'hookUiSurfaceMember(textDraw, new Hooker()' <<<"$text_wave_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$text_wave_body"; then
  echo "Text-wave installer bypasses retry-safe registration." >&2
  exit 1
fi
theme_body="$(sed -n '/void hookHostThemeColor(/,/private static long remapHostDrawColor/p' "$ui_src")"
grep -Fq 'hookUiSurfaceMember(localProvider, new Hooker()' <<<"$theme_body"
grep -Fq 'hookUiSurfaceMember(method, new Hooker()' <<<"$theme_body"
grep -Fq 'hookUiSurfaceMember(background, new Hooker()' <<<"$theme_body"
if grep -Fq 'Main.MODULE.hook(' <<<"$theme_body"; then
  echo "Host-theme installer bypasses retry-safe registration." >&2
  exit 1
fi

# Agent feature-specific installers keep separate Member sets and retry without duplicate hooks.
grep -Fq 'AGENT_FEATURE_HOOKED_MEMBERS' "$AGENT"
agent_feature_helper="$(sed -n '/private static void hookAgentFeatureMember/,/void hookAgentToolLogRoundedRect/p' "$AGENT")"
grep -Fq 'synchronized (AGENT_FEATURE_HOOKED_MEMBERS)' <<<"$agent_feature_helper"
grep -Fq 'installed.contains(member)' <<<"$agent_feature_helper"
grep -Fq 'installed.add(member)' <<<"$agent_feature_helper"
for method in \
  hookHeartbeatFragmentRenderBoundary \
  hookHeartbeatMarkdownInputBoundary \
  hookHeartbeatNativeMarkdownParser \
  hookHeartbeatToolStatusStyle \
  hookHeartbeatToolStatusBasicText \
  hookProactiveVisibleThreadFilter \
  hookNativeUiHeartbeatCompletion; do
  body="$(sed -n "/void $method(/,/^    }/p" "$AGENT")"
  grep -Fq "hookAgentFeatureMember(\"$method\"," <<<"$body"
  if grep -Fq 'Main.MODULE.hook(' <<<"$body"; then
    echo "$method bypasses Agent retry-safe registration." >&2
    exit 1
  fi
done

# Login feature installers use per-feature Member sets, so unrelated interceptors do not mask one another.
grep -Fq 'LOGIN_FEATURE_HOOKED_MEMBERS' "$login_src"
login_feature_helper="$(sed -n '/private static void hookLoginFeatureMember/,/void installLoginRiskLogger/p' "$login_src")"
grep -Fq 'synchronized (LOGIN_FEATURE_HOOKED_MEMBERS)' <<<"$login_feature_helper"
grep -Fq 'installed.contains(member)' <<<"$login_feature_helper"
grep -Fq 'installed.add(member)' <<<"$login_feature_helper"
for method in \
  hookTrainingOptOutControl \
  hookNativeFakeMute \
  hookRegionalLoginUnlock \
  hookLoginEntryPasswordUnlock \
  hookCandidateLoginErrorMessages \
  hookCandidatePasswordLoginCapture \
  hookCandidateRegistrationCaptchaCallbacks \
  hookRegionOverride \
  installRiskSdkNeutralizer \
  installSmsdkForge \
  hookAndroidIdSpoof \
  hookOaidSpoof \
  hookConcreteOaidPreferencesV241 \
  neutralizeRiskIoClass \
  fa \
  hookV241LocalApiUploadCoroutineRouting; do
  body="$(sed -n "/void $method(/,/^    }/p" "$login_src")"
  if [ -z "$body" ]; then
    body="$(sed -n "/private .* $method(/,/^    }/p" "$login_src")"
  fi
  grep -Fq "hookLoginFeatureMember(\"$method\"," <<<"$body"
  if grep -Fq 'Main.MODULE.hook(' <<<"$body"; then
    echo "$method bypasses login retry-safe registration." >&2
    exit 1
  fi
done

# Chat feature installers use isolated Member sets and do not stack interceptors on retry.
grep -Fq 'CHAT_FEATURE_HOOKED_MEMBERS' "$CHAT"
chat_feature_helper="$(sed -n '/private static void hookChatFeatureMember/,/private static String combineSystemPrompts/p' "$CHAT")"
grep -Fq 'synchronized (CHAT_FEATURE_HOOKED_MEMBERS)' <<<"$chat_feature_helper"
grep -Fq 'installed.contains(member)' <<<"$chat_feature_helper"
grep -Fq 'installed.add(member)' <<<"$chat_feature_helper"
for method in \
  hookContentFilterApply \
  installMsgRebuildCapture \
  hookStatusWrite \
  hookTemplateProbe \
  hookFinalMessageMerge \
  hookFinalMessageApply \
  hookS11RenderFilter \
  hookCs1RenderFilter \
  hookTransport \
  installPowManagerCapture; do
  body="$(sed -n "/void $method(/,/^    }/p" "$CHAT")"
  if [ -z "$body" ]; then
    body="$(sed -n "/private .* $method(/,/^    }/p" "$CHAT")"
  fi
  grep -Fq "hookChatFeatureMember(\"$method\"," <<<"$body"
  if grep -Fq 'Main.MODULE.hook(' <<<"$body"; then
    echo "$method bypasses chat retry-safe registration." >&2
    exit 1
  fi
done

# UI feature installers use isolated Member sets; settings navigation keeps its dedicated registrar.
grep -Fq 'UI_FEATURE_HOOKED_MEMBERS' "$UI"
ui_feature_helper="$(sed -n '/private static void hookUiFeatureMember/,/private static boolean hookSettingsNavigationOnce/p' "$UI")"
grep -Fq 'synchronized (UI_FEATURE_HOOKED_MEMBERS)' <<<"$ui_feature_helper"
grep -Fq 'installed.contains(member)' <<<"$ui_feature_helper"
grep -Fq 'installed.add(member)' <<<"$ui_feature_helper"
for method in \
  hookNativeSettingsEntry \
  prepareNativeSettingsDecoration \
  maybeInstallAdaptedSettingsEntry \
  hookAssistantAvatarPainter \
  hookChatBubbleCustomization \
  hookWelcomeWhaleMotion \
  hookHomeGreeting \
  hookNativeDualChatRoot; do
  body="$(sed -n "/void $method(/,/^    }/p" "$UI")"
  grep -Fq "hookUiFeatureMember(\"$method\"," <<<"$body"
  if [ "$method" = "hookChatBubbleCustomization" ]; then
    grep -Fq 'hookUiFeatureMember("hookChatBubbleCustomization", runtime.clipMethod' <<<"$body"
    grep -Fq 'hookUiFeatureMember("hookChatBubbleCustomization", runtime.backgroundMethod' <<<"$body"
  fi
  if grep -Fq 'Main.MODULE.hook(' <<<"$body"; then
    echo "$method bypasses UI retry-safe registration." >&2
    exit 1
  fi
done

# Session installers propagate installation failures to Main/Registry.
session_src="$SRC/HookSessionManagement.java"
for method in \
  hookLocalSessionDirectoryMerge \
  hookLocalNativeSessionRefresh \
  hookLocalSessionRemoteReload \
  hookLocalSessionDeletedFlow \
  hookLocalSessionDeletedResponse \
  hookActiveChatSessionCapture \
  hookHistoryLoadDiagnostics \
  hookNativeSessionNavigator; do
  grep -Fq "void $method(" "$session_src"
done
for spec in \
  'hookLocalSessionDirectoryMerge:t' \
  'hookLocalNativeSessionRefresh:t' \
  'hookLocalSessionRemoteReload:t' \
  'hookLocalSessionDeletedFlow:t' \
  'hookLocalSessionDeletedResponse:t' \
  'hookActiveChatSessionCapture:error' \
  'hookHistoryLoadDiagnostics:t' \
  'hookNativeSessionNavigator:t'; do
  IFS=: read -r method error_name <<<"$spec"
  body="$(sed -n "/void $method(/,/^    }/p" "$session_src")"
  grep -Fq "throw $error_name;" <<<"$body"
done
for marker in \
  'hookLocalSessionDirectoryMerge failed:' \
  'hookLocalNativeSessionRefresh failed:' \
  'hookLocalSessionRemoteReload failed:' \
  'hookLocalSessionDeletedFlow failed:' \
  'hookLocalSessionDeletedResponse failed:' \
  'hook active chat session capture failed:' \
  'hook history-load diagnostics failed:' \
  'hookNativeSessionNavigator failed:'; do
  grep -Fq "$marker" "$session_src"
done

# Lazy native click callbacks must not be marked complete after partial installation;
# successful Members remain tracked so a later callback retries only the failed ones.
click_body="$(sed -n '/private void hookNativeSessionClickCallback(/,/static List copyListForHook/p' "$session_src")"
grep -Fq 'private static final HashSet<Method> NATIVE_CLICK_HOOKED_METHODS = new HashSet<>();' "$session_src"
grep -Fq 'if (NATIVE_CLICK_HOOKED_METHODS.contains(method)) continue;' <<<"$click_body"
grep -Fq 'NATIVE_CLICK_HOOKED_METHODS.add(method);' <<<"$click_body"
grep -Fq 'if (complete)' <<<"$click_body"
grep -Fq 'NATIVE_CLICK_HOOKED_CLASSES.add(callbackClass);' <<<"$click_body"
grep -Fq 'complete = false;' <<<"$click_body"
grep -Fq 'NATIVE_CLICK_INSTALLING_CLASSES.remove(callbackClass);' <<<"$click_body"
outer_catch_line="$(awk '/^        } catch (Throwable error) \{/ { line = NR } END { print line }' <<<"$click_body")"
outer_finally_line="$(awk '/^        } finally \{/ { line = NR } END { print line }' <<<"$click_body")"
test -n "$outer_catch_line" && test -n "$outer_finally_line"
outer_failure_path="$(sed -n "${outer_catch_line},${outer_finally_line}p" <<<"$click_body")"
if grep -Fq 'NATIVE_CLICK_HOOKED_CLASSES.add(callbackClass);' <<<"$outer_failure_path"; then
  echo "Session click callback may be marked installed on its outer failure path." >&2
  exit 1
fi

# Main's install wrapper must preserve failure visibility instead of silently swallowing it.
install_body="$(sed -n '/private Throwable runHookInstallStep/,/void installHookStep/p' "$MAIN")"
grep -Fq 'FEATURE_HOOK_INSTALL_REGISTRY.install(id' <<<"$install_body"
grep -Fq 'return error;' <<<"$install_body"

echo "Hook installer retry and failure-propagation regression checks passed."
