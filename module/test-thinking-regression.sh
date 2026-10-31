#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
OUT="build/thinking-test"
JSON_JAR="$OUT/lib/json-20240303.jar"
DEPS_DIR="build/test-deps"
JSON_CACHE="$DEPS_DIR/json-20240303.jar"
JSON_SHA256="3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/lib" "$DEPS_DIR"

# Tests use the already-built universal Main/adapter classes; this keeps
# the regression suite aligned with the APK that is actually distributed.
UNIVERSAL_CLASSES="../module-universal/build/classes"
if [[ ! -f "$UNIVERSAL_CLASSES/ds/dsh/DeepSeek/Main.class" ]]; then
    echo "Universal classes are missing; build module-universal before running tests" >&2
    exit 1
fi

if [[ ! -f "$JSON_CACHE" ]] || ! printf '%s  %s\n' "$JSON_SHA256" "$JSON_CACHE" \
        | sha256sum -c - >/dev/null 2>&1; then
    tmp="$JSON_CACHE.tmp"
    rm -f "$tmp"
    downloaded=false
    for base in \
        https://repo.maven.apache.org/maven2 \
        https://repo1.maven.org/maven2 \
        https://maven.aliyun.com/repository/public; do
        if curl -fsSL --connect-timeout 15 --max-time 60 \
                "$base/org/json/json/20240303/json-20240303.jar" -o "$tmp" \
                && printf '%s  %s\n' "$JSON_SHA256" "$tmp" \
                    | sha256sum -c - >/dev/null 2>&1; then
            mv "$tmp" "$JSON_CACHE"
            downloaded=true
            break
        fi
        rm -f "$tmp"
    done
    if [[ "$downloaded" != true ]]; then
        echo "Could not download a verified org.json test dependency" >&2
        exit 1
    fi
fi
cp "$JSON_CACHE" "$JSON_JAR"

javac -source 8 -target 8 -cp "$JSON_JAR:$ANDROID_JAR:$UNIVERSAL_CLASSES:build/classes" \
    -d "$OUT/classes" \
    tests/ds/dsh/DeepSeek/ChatEditorThinkingRegressionTest.java \
    tests/ds/dsh/DeepSeek/ChatEditorHistoryImageRegressionTest.java \
    tests/ds/dsh/DeepSeek/HistoryBridgeRegressionTest.java \
    tests/ds/dsh/DeepSeek/NativeSessionDeleteRegressionTest.java \
    tests/ds/dsh/DeepSeek/NativeSessionRefreshRegressionTest.java \
    tests/ds/dsh/DeepSeek/ResponsePreserverRegressionTest.java \
    tests/ds/dsh/DeepSeek/AccountCredentialCodecRegressionTest.java \
    tests/ds/dsh/DeepSeek/AccountServerValidationRegressionTest.java \
    tests/ds/dsh/DeepSeek/GoogleLoginUnlockRegressionTest.java \
    tests/ds/dsh/DeepSeek/NativeApiPatchDecoderRegressionTest.java \
    tests/ds/dsh/DeepSeek/HostCompatRegressionTest.java \
    tests/ds/dsh/DeepSeek/UiLanguagePolicyRegressionTest.java \
    tests/ds/dsh/DeepSeek/ChatAppearanceConfigRegressionTest.java \
    tests/ds/dsh/DeepSeek/ImageCutoutRegressionTest.java \
    tests/ds/dsh/DeepSeek/HeartbeatToolProtocolRegressionTest.java \
    tests/ds/dsh/DeepSeek/RichPanelRendererRegressionTest.java \
    tests/ds/dsh/DeepSeek/AgentRunStoreRegressionTest.java \
    tests/ds/dsh/DeepSeek/ChatBackupFormatRegressionTest.java \
    tests/ds/dsh/DeepSeek/DeepSeekCacheCleanerRegressionTest.java \
    tests/ds/dsh/DeepSeek/RemoteFeatureFlagsRegressionTest.java \
    tests/ds/dsh/DeepSeek/ProcessManagerRegressionTest.java \
    tests/ds/dsh/DeepSeek/ReplyReadyPolicyRegressionTest.java \
    tests/ds/dsh/DeepSeek/AutoContinuePolicyRegressionTest.java \
    src/ds/dsh/DeepSeek/NativeApiPatchDecoder.java \
    src/ds/dsh/DeepSeek/SpatialMotionController.java \
    src/ds/dsh/DeepSeek/SpatialLayerCache.java \
    src/ds/dsh/DeepSeek/ChatAppearance.java \
    src/ds/dsh/DeepSeek/ImageCutoutUi.java \
    src/ds/dsh/DeepSeek/HeartbeatToolProtocol.java \
    src/ds/dsh/DeepSeek/RichPanelRenderer.java \
    src/ds/dsh/DeepSeek/AgentRunStore.java \
    src/ds/dsh/DeepSeek/ChatBackupStore.java \
    src/ds/dsh/DeepSeek/DeekseepTools.java \
    src/ds/dsh/DeepSeek/AgentToolConfig.java \
    src/ds/dsh/DeepSeek/AgentQuestionUi.java \
    src/ds/dsh/DeepSeek/AgentDeviceBridge.java \
    src/ds/dsh/DeepSeek/ProactiveHeartbeatReceiver.java \
    src/ds/dsh/DeepSeek/QqMusicPlaybackService.java \
    src/ds/dsh/DeepSeek/LocalAudioPlaybackService.java \
    src/ds/dsh/DeepSeek/LocalAudioControlActivity.java \
    src/ds/dsh/DeepSeek/QqMusicSessionAccessService.java \
    src/ds/dsh/DeepSeek/HistoryBridge.java \
    src/ds/dsh/DeepSeek/XposedActivationProvider.java \
    src/ds/dsh/DeepSeek/XposedActivationReceiver.java \
    src/ds/dsh/DeepSeek/AccountCredentialCodec.java \
    src/ds/dsh/DeepSeek/AccountManager.java \
    src/ds/dsh/DeepSeek/RemoteFeatureFlags.java \
    src/ds/dsh/DeepSeek/ProcessManagerActivity.java \
    src/ds/dsh/DeepSeek/ReplyReadyPolicy.java \
    src/ds/dsh/DeepSeek/AutoContinuePolicy.java \
    src/ds/dsh/DeepSeek/GoogleLoginUnlock.java \
    src/ds/dsh/DeepSeek/ResponsePreserver.java \
    src/ds/dsh/DeepSeek/HostCompat.java \
    tests/tp.java \
    tests/h61.java \
    tests/sl8.java \
    tests/kv.java \
    tests/lq.java \
    tests/kv0.java \
    tests/iu0.java \
    tests/td1.java \
    tests/hv.java \
    tests/x94.java \
    tests/n02.java \
    tests/p64.java \
    tests/c74.java

# Current sources compiled by this run must precede the previous universal APK classes.
# Otherwise Java loads a stale HostCompat/Main from module-universal/build/classes and the
# compatibility regression tests validate yesterday's APK instead of the pending build.
TEST_CP="$JSON_JAR:$ANDROID_JAR:$OUT/classes:$UNIVERSAL_CLASSES:build/classes"

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ChatEditorThinkingRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ChatEditorHistoryImageRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.HistoryBridgeRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.NativeSessionDeleteRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.NativeSessionRefreshRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ResponsePreserverRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.AccountCredentialCodecRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.AccountServerValidationRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.GoogleLoginUnlockRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.NativeApiPatchDecoderRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.HostCompatRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.UiLanguagePolicyRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ChatAppearanceConfigRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ImageCutoutRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.HeartbeatToolProtocolRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.RichPanelRendererRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.AgentRunStoreRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ChatBackupFormatRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.DeepSeekCacheCleanerRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.RemoteFeatureFlagsRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ProcessManagerRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.ReplyReadyPolicyRegressionTest

java -cp "$TEST_CP" \
    ds.dsh.DeepSeek.AutoContinuePolicyRegressionTest

./test-language-catalog.sh
