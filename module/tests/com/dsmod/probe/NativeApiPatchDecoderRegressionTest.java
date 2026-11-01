package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Open edition deliberately ships a Closed-only decoder stub. These tests assert that the
 * facade fails closed and never leaks patch fragments into either public text or reasoning when
 * that protected payload is unavailable. Channel-splitting behavior belongs to the Closed
 * payload and cannot be truthfully tested by the Open source-only build.
 */
public final class NativeApiPatchDecoderRegressionTest {
    public static void main(String[] args) throws Exception {
        failsClosedWhenProtectedPayloadIsUnavailable();
        preservesEmptyOutputAcrossPatchShapes();
        System.out.println("NativeApiPatchDecoder Open fallback regression OK");
    }

    private static void failsClosedWhenProtectedPayloadIsUnavailable() {
        check(!z14.available(), "Open edition unexpectedly exposes the protected decoder");
        check(z14.payloadClass(null, "com.dsmod.probe.z12decoder") == null,
                "Open edition unexpectedly resolves the Closed-only decoder");

        NativeApiPatchDecoder decoder = new NativeApiPatchDecoder();
        NativeApiPatchDecoder.Delta result = decoder.decode(new JSONObject()
                .put("v", new JSONObject().put("response",
                        new JSONObject().put("fragments", new JSONArray().put(new JSONObject()
                                .put("id", 2).put("type", "THINK").put("content", "private"))))));
        checkEmpty(result, "snapshot");
    }

    private static void preservesEmptyOutputAcrossPatchShapes() throws Exception {
        NativeApiPatchDecoder decoder = new NativeApiPatchDecoder();
        NativeApiPatchDecoder.Delta append = decoder.decode(new JSONObject()
                .put("p", "response/fragments/-1/content")
                .put("o", "APPEND").put("v", "private reasoning"));
        NativeApiPatchDecoder.Delta bare = decoder.decode(new JSONObject().put("v", "private response"));
        NativeApiPatchDecoder.Delta switchFragment = decoder.decode(new JSONObject()
                .put("p", "response/fragments").put("o", "APPEND")
                .put("v", new JSONArray().put(new JSONObject().put("id", 3)
                        .put("type", "RESPONSE").put("content", "private response"))));
        checkEmpty(append, "append");
        checkEmpty(bare, "bare continuation");
        checkEmpty(switchFragment, "fragment switch");
    }

    private static void checkEmpty(NativeApiPatchDecoder.Delta delta, String label) {
        check(delta != null, label + " returned a null result");
        check(empty(delta.text) && empty(delta.reasoning)
                        && empty(delta.textSet) && empty(delta.reasoningSet),
                label + " leaked protected payload content into Open edition output");
    }

    private static boolean empty(String value) {
        return value == null || value.length() == 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
