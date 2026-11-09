package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Open, version-gated adapter for DeepSeek 2.7.5/code295.
 *
 * <p>This adapter deliberately reuses the host's own Ktor client, PoW manager, ChallengePlugin,
 * qy1 request template and nw3 transport. It does not construct auth headers or solve challenges
 * independently. Until all three runtime objects have been observed, it fails closed.</p>
 *
 * <p>The qy1 request has no max_tokens field. The host controls its native output budget; the
 * OpenAI max_tokens value is validated by the gateway but cannot currently be enforced by this
 * native request DTO.</p>
 */
final class Code295NativeCompletionAdapter {
    private static final long DEFAULT_FLOW_TIMEOUT_MS = 600_000L;

    private Code295NativeCompletionAdapter() {}

    static z2.CompletionResult executeLocalApiCompletion(
            z2.CompletionRequest request, z2.DeltaSink sink) throws Exception {
        if (request == null || request.prompt == null) {
            throw new z2.GatewayException(400, "invalid_request", "invalid_request_error",
                    "A non-empty completion request is required");
        }
        if (request.inputImages != null && !request.inputImages.isEmpty()) {
            throw new z2.GatewayException(501, "unsupported_input_images", "server_error",
                    "Image input is not yet supported by the code295 Open native adapter");
        }
        if (request.knownToolCalls != null && !request.knownToolCalls.isEmpty()) {
            throw new z2.GatewayException(501, "unsupported_tool_continuation", "server_error",
                    "Tool continuation is not yet supported by the code295 Open native adapter");
        }

        ClassLoader loader = Main.hostClassLoader;
        Object transport = Main.live275Transport;
        Object powManager = Main.live275PowManager;
        Object template = Main.live275RequestTemplate;
        if (loader == null || transport == null || powManager == null || template == null) {
            throw new z2.GatewayException(503, "host_not_ready", "server_error",
                    "Open DeepSeek and send one normal message before using the code295 Local API");
        }

        long timeoutMs = remainingBudget(request);
        String sessionId = null;
        try {
            String pow = mintPow(loader, powManager);
            sessionId = createThrowawaySession(loader, transport);
            if (sessionId == null) {
                throw new z2.GatewayException(502, "session_create_failed", "server_error",
                        "The native chat-session endpoint returned no usable session ID");
            }

            Object nativeRequest = buildRequest(template, request, sessionId, pow);
            Object flow = invokeTransport(loader, transport, nativeRequest,
                    Math.max(1L, Math.min(timeoutMs, DEFAULT_FLOW_TIMEOUT_MS)));
            if (flow == null) {
                throw new z2.GatewayException(502, "transport_failed", "server_error",
                        "The code295 native transport returned no Flow");
            }

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            collectFlow(loader, flow, sink, text, reasoning,
                    Math.max(1L, Math.min(timeoutMs, DEFAULT_FLOW_TIMEOUT_MS)));
            if (text.length() == 0 && reasoning.length() == 0) {
                throw new z2.GatewayException(502, "empty_native_response", "server_error",
                        "The native Flow completed without text or reasoning fragments");
            }
            return new z2.CompletionResult(text.toString(), reasoning.toString(), "stop");
        } finally {
            if (sessionId != null) {
                deleteThrowawaySession(loader, transport, sessionId);
            }
        }
    }

    private static long remainingBudget(z2.CompletionRequest request) {
        long deadline = request.deadlineAtMs;
        if (deadline <= 0L) return DEFAULT_FLOW_TIMEOUT_MS;
        return Math.max(1L, deadline - System.currentTimeMillis());
    }

    private static String mintPow(ClassLoader loader, Object powManager) throws Exception {
        try {
            Class<?> continuationType = Class.forName("ec3", false, loader);
            Method method = powManager.getClass().getDeclaredMethod("j", continuationType);
            method.setAccessible(true);
            Object result = HookAccountLoginSecurity.INSTANCE.driveSuspend(
                    loader, method, powManager, new Object[0]);
            Object value = MainReflectionSupport.fieldByName(result, "a");
            if (value instanceof String && ((String) value).length() > 0) {
                return (String) value;
            }
            throw new z2.GatewayException(502, "pow_failed", "server_error",
                    "The host PoW manager did not return a serialized solution");
        } catch (z2.GatewayException error) {
            throw error;
        } catch (Throwable error) {
            throw gatewayFailure(502, "pow_failed", "Native PoW generation failed", error);
        }
    }

    private static String createThrowawaySession(ClassLoader loader, Object transport)
            throws Exception {
        try {
            Object httpEngine = MainReflectionSupport.fieldByName(transport, "d");
            Object client = MainReflectionSupport.fieldByName(httpEngine, "a");
            if (client == null) {
                throw new IllegalStateException("code295 Ktor client unavailable");
            }
            Class<?> continuationType = Class.forName("dc3", false, loader);
            Class<?> coroutine = Class.forName("sz0", false, loader);
            Constructor<?> constructor = coroutine.getDeclaredConstructor(
                    int.class, continuationType, int.class);
            constructor.setAccessible(true);
            // sz0 branch 2 is the host's POST /api/v0/chat_session/create coroutine.
            Object block = constructor.newInstance(2, null, 2);
            Method invoke = coroutine.getDeclaredMethod("q", Object.class, Object.class);
            invoke.setAccessible(true);
            Object response = HookAccountLoginSecurity.INSTANCE.driveSuspend(
                    loader, invoke, block, new Object[]{client});
            return HookAttachmentPipeline.extractSessionId(response);
        } catch (Throwable error) {
            throw gatewayFailure(502, "session_create_failed",
                    "Native chat-session creation failed", error);
        }
    }

    private static void deleteThrowawaySession(ClassLoader loader, Object transport,
                                               String sessionId) {
        try {
            Object httpEngine = MainReflectionSupport.fieldByName(transport, "d");
            Class<?> engineType = Class.forName("gi5", false, loader);
            Class<?> serviceType = Class.forName("hh2", false, loader);
            Class<?> requestType = Class.forName("al2", false, loader);
            Class<?> continuationType = Class.forName("dc3", false, loader);
            Constructor<?> serviceConstructor = serviceType.getDeclaredConstructor(engineType);
            serviceConstructor.setAccessible(true);
            Object service = serviceConstructor.newInstance(httpEngine);
            Constructor<?> requestConstructor = requestType.getDeclaredConstructor(
                    int.class, String.class, ArrayList.class);
            requestConstructor.setAccessible(true);
            // Bit 2 defaults the optional second field to null while retaining chat_session_id.
            Object deleteRequest = requestConstructor.newInstance(2, sessionId, null);
            Method delete = serviceType.getDeclaredMethod("d", requestType, continuationType);
            delete.setAccessible(true);
            HookAccountLoginSecurity.INSTANCE.driveSuspend(
                    loader, delete, service, new Object[]{deleteRequest});
        } catch (Throwable ignored) {
            // Session cleanup is best-effort and must not mask the completion result.
        }
    }

    private static Object buildRequest(Object template, z2.CompletionRequest request,
                                       String sessionId, String pow) throws Exception {
        Object clone = HookAttachmentPipeline.cloneNativeRequestTemplate(template);
        if (clone == null || !"qy1".equals(clone.getClass().getName())) {
            throw new z2.GatewayException(500, "request_template_invalid", "server_error",
                    "The captured code295 request template could not be cloned");
        }
        List<String> fileIds = request.nativeFileIds == null
                ? new ArrayList<String>() : new ArrayList<String>(request.nativeFileIds);
        String nativeModel = request.nativeModel == null || request.nativeModel.length() == 0
                ? "default" : request.nativeModel;

        setAndVerify(clone, "a", sessionId);
        setAndVerify(clone, "b", null);
        setAndVerify(clone, "c", request.prompt);
        setAndVerify(clone, "d", new ArrayList<String>(fileIds));
        setAndVerify(clone, "e", Boolean.valueOf(request.reasoning));
        setAndVerify(clone, "f", Boolean.valueOf(request.search));
        setAndVerify(clone, "g", null);
        setAndVerify(clone, "h", Boolean.FALSE);
        setAndVerify(clone, "i", nativeModel);
        setAndVerify(clone, "j", null);
        setAndVerify(clone, "k", pow);
        // Field l (Shumei device identity) is deliberately preserved from the host template.
        return clone;
    }

    private static void setAndVerify(Object target, String field, Object value) throws Exception {
        HookAttachmentPipeline.setNativeRequestField(target, field, value);
        Object actual = MainReflectionSupport.fieldByName(target, field);
        if (value instanceof List) {
            if (!(actual instanceof List) || !((List<?>) actual).equals(value)) {
                throw new IllegalStateException("Native request field update failed: " + field);
            }
        } else if (value == null ? actual != null : !value.equals(actual)) {
            throw new IllegalStateException("Native request field update failed: " + field);
        }
    }

    private static Object invokeTransport(ClassLoader loader, Object transport,
                                          Object nativeRequest, long timeoutMs)
            throws Exception {
        try {
            Class<?> requestMarker = Class.forName("yu1", false, loader);
            Method method = transport.getClass().getDeclaredMethod(
                    "e", requestMarker, Long.class);
            method.setAccessible(true);
            return method.invoke(transport, nativeRequest, Long.valueOf(timeoutMs));
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause() == null ? wrapped : wrapped.getCause();
            throw gatewayFailure(502, "transport_failed",
                    "The code295 native transport rejected the request", cause);
        } catch (Throwable error) {
            throw gatewayFailure(502, "transport_failed",
                    "The code295 native transport could not be invoked", error);
        }
    }

    private static void collectFlow(ClassLoader loader, Object flow, z2.DeltaSink sink,
                                    StringBuilder text, StringBuilder reasoning,
                                    long timeoutMs) throws Exception {
        Method collect = null;
        for (Class<?> type : MainReflectionSupport.allInterfaces(flow.getClass())) {
            Method candidate = null;
            int twoArgumentMethods = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 2) {
                    candidate = method;
                    twoArgumentMethods++;
                }
            }
            if (twoArgumentMethods == 1 && candidate != null
                    && candidate.getParameterTypes()[1].isInterface()) {
                collect = candidate;
                break;
            }
        }
        if (collect == null) {
            throw new z2.GatewayException(502, "flow_unrecognized", "server_error",
                    "The code295 native Flow interface was not recognized");
        }

        final Class<?> collectorType = collect.getParameterTypes()[0];
        final Class<?> continuationType = collect.getParameterTypes()[1];
        Class<?> contextType = null;
        for (Method method : continuationType.getMethods()) {
            if (method.getParameterTypes().length == 0
                    && method.getReturnType().isInterface()) {
                contextType = method.getReturnType();
                break;
            }
        }
        final Object cancellationJob = contextType == null
                ? null : Main.newLocalApiCancellationJob(loader, contextType);
        final Object context = contextType == null ? null
                : (cancellationJob == null
                        ? Main.emptyContextProxy(loader, contextType) : cancellationJob);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final FragmentState fragmentState = new FragmentState();

        InvocationHandler continuationHandler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                if (MainReflectionSupport.isObjectMethod(method)) {
                    return MainReflectionSupport.objectMethod(proxy, method, args);
                }
                if (method.getParameterTypes().length == 0) return context;
                if (method.getParameterTypes().length == 1) {
                    if (args != null && args.length > 0 && args[0] != null
                            && args[0].getClass().getName().contains("Result$Failure")) {
                        Object exception = MainReflectionSupport.fieldByName(args[0], "exception");
                        if (exception instanceof Throwable) failure.compareAndSet(null,
                                (Throwable) exception);
                    }
                    done.countDown();
                }
                return null;
            }
        };
        Object continuation = Proxy.newProxyInstance(loader,
                new Class<?>[]{continuationType}, continuationHandler);

        InvocationHandler collectorHandler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                if (MainReflectionSupport.isObjectMethod(method)) {
                    return MainReflectionSupport.objectMethod(proxy, method, args);
                }
                if (method.getParameterTypes().length == 2 && args != null && args.length == 2) {
                    if (sink != null && sink.isCancelled()) {
                        Main.cancelLocalApiCancellationJob(cancellationJob);
                        throw new java.util.concurrent.CancellationException(
                                "local API client disconnected");
                    }
                    try {
                        emitDelta(args[0], sink, text, reasoning, fragmentState);
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                        throw error;
                    }
                }
                return Main.ui8Unit(loader);
            }
        };
        Object collector = Proxy.newProxyInstance(loader,
                new Class<?>[]{collectorType}, collectorHandler);

        collect.setAccessible(true);
        Main.ACTIVE_LOCAL_API_CANCEL_JOB = cancellationJob;
        try {
            Object returned = collect.invoke(flow, collector, continuation);
            // The native cold Flow starts its upstream work when collect() is entered.
            if (sink != null) sink.onUpstreamStarted();
            if (!isCoroutineSuspended(loader, returned)) done.countDown();

            long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMs);
            while (done.getCount() != 0L) {
                if (sink != null && sink.isCancelled()) {
                    Main.cancelLocalApiCancellationJob(cancellationJob);
                    throw new java.util.concurrent.CancellationException(
                            "local API client disconnected");
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    Main.cancelLocalApiCancellationJob(cancellationJob);
                    throw new z2.GatewayException(504, "native_flow_timeout", "server_error",
                            "The code295 native completion Flow exceeded the request deadline");
                }
                done.await(Math.min(100L, remaining), TimeUnit.MILLISECONDS);
            }
            Throwable error = failure.get();
            if (error != null) {
                throw gatewayFailure(502, "flow_failed", "Native Flow delivery failed", error);
            }
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause() == null ? wrapped : wrapped.getCause();
            if (cause instanceof java.util.concurrent.CancellationException) throw (Exception) cause;
            throw gatewayFailure(502, "flow_failed", "Native Flow collection failed", cause);
        } finally {
            Main.cancelLocalApiCancellationJob(
                    sink != null && sink.isCancelled() ? cancellationJob : null);
            if (Main.ACTIVE_LOCAL_API_CANCEL_JOB == cancellationJob) {
                Main.ACTIVE_LOCAL_API_CANCEL_JOB = null;
            }
        }
    }

    private static boolean isCoroutineSuspended(ClassLoader loader, Object value) {
        try {
            Class<?> marker = Class.forName("hd3", false, loader);
            Field field = marker.getDeclaredField("a");
            field.setAccessible(true);
            return value == field.get(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void emitDelta(Object value, z2.DeltaSink sink, StringBuilder text,
                                  StringBuilder reasoning, FragmentState state) throws Exception {
        Object event = MainReflectionSupport.fieldByName(value, "a");
        Object payload = event == null ? null : MainReflectionSupport.fieldByName(event, "b");
        if (!(payload instanceof String)) return;
        String json = (String) payload;
        if (json.length() == 0 || json.charAt(0) != '{') return;
        JSONObject object;
        try {
            object = new JSONObject(json);
        } catch (Throwable ignored) {
            return;
        }

        String path = object.optString("p", "");
        if (path.length() > 0) {
            if (path.endsWith("fragments")) {
                emitFragments(object.optJSONArray("v"), sink, text, reasoning, state);
                return;
            }
            if (path.contains("content")) {
                String delta = object.optString("v", "");
                if (delta.length() > 0) {
                    deliver(sink, text, reasoning, delta,
                            isReasoningPath(path) || "THINK".equals(state.lastType));
                }
            }
            return;
        }

        if (json.startsWith("{\"v\":\"")) {
            String delta = object.optString("v", "");
            if (delta.length() > 0) {
                deliver(sink, text, reasoning, delta, "THINK".equals(state.lastType));
            }
            return;
        }

        JSONObject root = object.optJSONObject("v");
        JSONObject response = root == null ? null : root.optJSONObject("response");
        emitFragments(response == null ? null : response.optJSONArray("fragments"),
                sink, text, reasoning, state);
    }

    private static void emitFragments(JSONArray fragments, z2.DeltaSink sink,
                                      StringBuilder text, StringBuilder reasoning,
                                      FragmentState state) throws Exception {
        if (fragments == null) return;
        for (int i = 0; i < fragments.length(); i++) {
            JSONObject fragment = fragments.optJSONObject(i);
            if (fragment == null) continue;
            String type = fragment.optString("type", "RESPONSE");
            state.lastType = type;
            String value = fragment.optString("content", "");
            if (value.length() > 0) deliver(sink, text, reasoning, value, "THINK".equals(type));
        }
    }

    private static void deliver(z2.DeltaSink sink, StringBuilder text,
                                StringBuilder reasoning, String delta, boolean think)
            throws Exception {
        boolean keepGoing = true;
        if (think) {
            reasoning.append(delta);
            if (sink != null) keepGoing = sink.onReasoning(delta);
        } else {
            text.append(delta);
            if (sink != null) keepGoing = sink.onText(delta);
        }
        if (!keepGoing) {
            throw new java.util.concurrent.CancellationException(
                    "local API client disconnected");
        }
    }

    private static boolean isReasoningPath(String path) {
        String value = path.toLowerCase(java.util.Locale.US);
        return value.contains("think") || value.contains("reason");
    }

    private static z2.GatewayException gatewayFailure(int status, String code,
                                                       String message, Throwable error) {
        String type = error == null ? "server_error" : error.getClass().getSimpleName();
        return new z2.GatewayException(status, code, "server_error",
                message + " (" + type + ")");
    }

    private static final class FragmentState {
        String lastType = "RESPONSE";
    }
}
