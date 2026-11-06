#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
RESOLVER="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostChatRequestResolver.java"
POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostFeatureHookPolicy.java"
PIPELINE="$ROOT/module/src/ds/dsh/DeepSeek/HookChatPipeline.java"

for file in "$MAIN" "$DISCOVERY" "$RESOLVER" "$POLICY" "$PIPELINE"; do
  test -s "$file"
done

grep -Fq 'deekseep_roi_unknown_host_feature_hook_enabled' "$MAIN"
grep -Fq 'RoiUnknownHostFeatureHookPolicy.isExplicitlyEnabled' "$MAIN"
grep -Fq 'isUnknownHostFeatureHookEnabled()' "$MAIN"
grep -Fq 'Main.isUnknownHostFeatureHookEnabled()' "$PIPELINE"
grep -Fq 'ROI_UNKNOWN_HOST_CHAT_REQUEST_RESOLVER' "$MAIN"
grep -Fq '.observeLoadedClass((Class<?>) loaded, hostRoot,' "$MAIN"
grep -Fq 'Main.finalizeRoiUnknownHostChatRequestDiscovery()' "$DISCOVERY"
grep -Fq 'registerRoiUnknownHostChatRequestCandidate(candidate)' "$MAIN"
grep -Fq 'ROI_UNKNOWN_HOST_CHAT_REQUEST_RESOLVER.finalizeDiscovery()' "$MAIN"
grep -Fq 'resolver.observeDexCandidate(sourceScope, anchor, descriptor' "$DISCOVERY"
grep -Fq 'RoiDexKitExportReader.exportAndRead' "$DISCOVERY"
grep -Fq 'exportBridge.exportDexFile(outputDirectory)' "$DISCOVERY"
grep -Fq 'parsedDexMethods.contains(descriptor)' "$DISCOVERY"
grep -Fq 'parsedDexMethods.contains(reference.constructorDescriptor)' "$DISCOVERY"
grep -Fq 'inspectSerializerOwnerForRequestConstructors' "$DISCOVERY"
grep -Fq 'export_file_count_mismatch' "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitExportReader.java"
grep -Fq 'declaredClass(className)' "$DISCOVERY"
grep -Fq 'isChatRequestConstructor(invokedDescriptor)' "$DISCOVERY"
grep -Fq 'ownerFromMethodDescriptor(targetMethodDescriptor)' "$DISCOVERY"
grep -Fq 'callerMethodScans >= 2' "$DISCOVERY"
grep -Fq 'targetMethodDescriptor.equals(methodDescriptor)' "$DISCOVERY"
grep -Fq 'Cache per exact method' "$DISCOVERY"
grep -Fq 'candidate.fieldAnchors.contains("chat_session_id")' "$POLICY"
grep -Fq 'allowsChatRequestPromptHook' "$POLICY"
grep -Fq 'hasExpectedConstructorShape' "$RESOLVER"
grep -Fq 'constructorDescriptorForPolicy(constructor)' "$POLICY"
grep -Fq 'hookUnknownHostChatRequest(constructor)' "$MAIN"
grep -Fq 'HistoryBridge.wrapSystemPrompt(' "$PIPELINE"
grep -Fq 'combined, originalPrompt);' "$PIPELINE"
grep -Fq 'tlLocalApiRequest.get()' "$PIPELINE"
grep -Fq 'return modified == null ? chain.proceed() : chain.proceed(modified);' "$PIPELINE"
observer_line="$(grep -n 'installRoiClassLoadObserver(cl, true)' "$MAIN" | head -n 1 | cut -d: -f1)"
dex_scan_line="$(grep -n 'RoiDexKitDiscovery.schedule(cl' "$MAIN" | head -n 1 | cut -d: -f1)"
test -n "$observer_line" && test -n "$dex_scan_line" && test "$observer_line" -lt "$dex_scan_line"

# This first adapter is deliberately limited to the user/plugin prompt on the outgoing
# ChatFullCompletionRequest constructor; it must not synthesize tools or alter transport/auth.
start="$(grep -n 'boolean hookUnknownHostChatRequest(Constructor' "$PIPELINE" | cut -d: -f1)"
end="$(grep -n 'private static String combineSystemPrompts' "$PIPELINE" | cut -d: -f1)"
test -n "$start" && test -n "$end" && test "$end" -gt "$start"
section="$(sed -n "${start},$((end - 1))p" "$PIPELINE")"
if printf '%s\n' "$section" | grep -Eq 'dispatchChatProxy|AgentToolConfig|X-DS-PoW|Authorization|install.*Transport'; then
  echo "FAIL: unknown-host prompt adapter crossed its approved feature boundary" >&2
  exit 1
fi

echo "ROI unknown-host chat request feature wiring passed"
