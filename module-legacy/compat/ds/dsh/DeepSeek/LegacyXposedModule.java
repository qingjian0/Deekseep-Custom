package ds.dsh.DeepSeek;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Compatibility adapter that runs the stable core's around-hook contract on traditional Xposed.
 *
 * <p>The domestic and Google Play universal APKs compile the same Main.java. Traditional Xposed
 * supplies before/after callbacks through this adapter. This
 * bridge invokes the original member from a before callback and publishes the interceptor result,
 * preserving the core's fail-open and argument-replacement behavior without maintaining a second
 * several-thousand-line fork.</p>
 */
abstract class LegacyXposedModule {

    private final ConcurrentHashMap<Member, HookTarget> targets =
            new ConcurrentHashMap<Member, HookTarget>();

    // A stable installer scope lets a retried feature add only the registrations that did not
    // complete in its previous attempt. Unscoped callers keep the historical append behavior.
    private final ThreadLocal<HookRegistrationScope> hookRegistrationScope =
            new ThreadLocal<HookRegistrationScope>();

    static final class HookRegistrationScope {
        private final String id;
        private final HashMap<String, Integer> occurrenceCounts =
                new HashMap<String, Integer>();

        HookRegistrationScope(String id) { this.id = id; }

        String nextKey(Member member, Hooker hooker) {
            String base = member.toString() + "#" + hooker.getClass().getName();
            Integer previous = occurrenceCounts.get(base);
            int ordinal = previous == null ? 0 : previous.intValue();
            return id + "#" + base + "#" + ordinal;
        }

        void commitKey(Member member, Hooker hooker) {
            String base = member.toString() + "#" + hooker.getClass().getName();
            Integer previous = occurrenceCounts.get(base);
            int ordinal = previous == null ? 0 : previous.intValue();
            occurrenceCounts.put(base, Integer.valueOf(ordinal + 1));
        }
    }

    final HookRegistrationScope beginHookRegistrationScope(String scope) {
        HookRegistrationScope previous = hookRegistrationScope.get();
        if (scope == null || scope.trim().isEmpty()) {
            throw new IllegalArgumentException("hook registration scope must not be blank");
        }
        hookRegistrationScope.set(new HookRegistrationScope(scope));
        return previous;
    }

    final void endHookRegistrationScope(HookRegistrationScope previous) {
        if (previous == null) hookRegistrationScope.remove();
        else hookRegistrationScope.set(previous);
    }

    interface Hooker {
        Object intercept(Chain chain) throws Throwable;

        /** Required hooks fail visibly; optional hooks must not break the host when they fail. */
        default boolean isRequired() { return false; }
    }

    private static final class InvocationState {
        boolean originalInvoked;
        Object originalResult;
        Throwable originalThrowable;
        boolean requiredHookFailed;
    }

    static final class Chain {
        private final Member executable;
        private final XC_MethodHook.MethodHookParam param;
        private final List<Hooker> hookers;
        private final int nextHooker;
        private final Object[] args;
        private final Object[] originalArgs;
        private final InvocationState invocationState;
        private boolean proceeded;

        Chain(Member executable, XC_MethodHook.MethodHookParam param,
              List<Hooker> hookers, int nextHooker, Object[] args,
              Object[] originalArgs) {
            this(executable, param, hookers, nextHooker, args, originalArgs,
                    new InvocationState());
        }

        private Chain(Member executable, XC_MethodHook.MethodHookParam param,
              List<Hooker> hookers, int nextHooker, Object[] args,
              Object[] originalArgs, InvocationState invocationState) {
            this.executable = executable;
            this.param = param;
            this.hookers = hookers;
            this.nextHooker = nextHooker;
            this.args = args == null ? new Object[0] : args;
            this.originalArgs = originalArgs == null ? new Object[0] : originalArgs;
            this.invocationState = invocationState;
        }

        Object getThisObject() { return param.thisObject; }

        Object getArg(int index) { return args[index]; }

        List<Object> getArgs() { return Arrays.asList(args); }

        Member getExecutable() { return executable; }

        Object proceed() throws Throwable { return proceed(args); }

        Object proceed(Object... args) throws Throwable {
            if (proceeded) {
                throw new IllegalStateException("Xposed chain proceeded more than once");
            }
            proceeded = true;
            Object[] actual = args == null ? new Object[0] : args;
            if (nextHooker < hookers.size()) {
                Hooker hooker = hookers.get(nextHooker);
                try {
                    return hooker.intercept(new Chain(
                            executable, param, hookers, nextHooker + 1, actual,
                            originalArgs, invocationState));
                } catch (Throwable error) {
                    if (hooker.isRequired() || invocationState.requiredHookFailed
                            || error instanceof VirtualMachineError
                            || error instanceof ThreadDeath) {
                        invocationState.requiredHookFailed = true;
                        throw error;
                    }
                    // Optional interceptors are isolated individually. If the original member
                    // already ran, keep its unmodified result/exception; otherwise continue the
                    // remaining chain with the current arguments and invoke the original once.
                    try {
                        XposedBridge.log("Deekseep universal: optional hook failed open for "
                                + executable + ": " + error);
                    } catch (RuntimeException ignored) {}
                    try {
                        JavaPluginRuntimeBridge.error(executable, error, 0L);
                    } catch (RuntimeException ignored) {}
                    if (invocationState.originalInvoked) {
                        if (invocationState.originalThrowable != null) {
                            throw invocationState.originalThrowable;
                        }
                        return invocationState.originalResult;
                    }
                    return new Chain(executable, param, hookers, nextHooker + 1,
                            actual, originalArgs, invocationState).proceed(actual);
                }
            }
            // Keep MethodHookParam consistent for traditional after callbacks registered by
            // other modules. invokeOriginalMethod bypasses hook dispatch but uses these exact
            // arguments and thisObject, including for constructors.
            Object[] invokeArgs = actual;
            if (!argumentsCompatible(executable, actual)
                    && argumentsCompatible(executable, originalArgs)) {
                // A few traditional bridges expose synthetic Compose parameters in a different
                // order when an around hook supplies a replacement array. Never crash the host
                // for such a bridge mismatch: preserve the unmodified invocation and emit enough
                // framework-log detail to diagnose the affected member.
                XposedBridge.log("Deekseep universal: rejected incompatible replacement for "
                        + executable + " actual=" + argumentTypes(actual)
                        + " original=" + argumentTypes(originalArgs));
                invokeArgs = originalArgs.clone();
            }
            param.args = invokeArgs;
            invocationState.originalInvoked = true;
            try {
                Object result = XposedBridge.invokeOriginalMethod(
                        executable, param.thisObject, invokeArgs);
                invocationState.originalResult = result;
                return result;
            } catch (InvocationTargetException wrapped) {
                // Some traditional bridges implement invokeOriginalMethod with Method.invoke().
                // Preserve the exception contract seen by the host instead of leaking the
                // reflection wrapper. This matters for Kotlin coroutine cancellation signals.
                Throwable cause = wrapped.getCause();
                Throwable original = cause != null ? cause : wrapped;
                invocationState.originalThrowable = original;
                throw original;
            } catch (Throwable original) {
                invocationState.originalThrowable = original;
                throw original;
            }
        }

        private static boolean argumentsCompatible(Member member, Object[] values) {
            Class<?>[] types;
            if (member instanceof Method) {
                types = ((Method) member).getParameterTypes();
            } else if (member instanceof Constructor) {
                types = ((Constructor<?>) member).getParameterTypes();
            } else {
                return true;
            }
            if (values == null || types.length != values.length) return false;
            for (int i = 0; i < types.length; i++) {
                Object value = values[i];
                if (value == null) {
                    if (types[i].isPrimitive()) return false;
                    continue;
                }
                Class<?> wanted = boxed(types[i]);
                if (!wanted.isInstance(value)) return false;
            }
            return true;
        }

        private static Class<?> boxed(Class<?> type) {
            if (!type.isPrimitive()) return type;
            if (type == boolean.class) return Boolean.class;
            if (type == byte.class) return Byte.class;
            if (type == char.class) return Character.class;
            if (type == short.class) return Short.class;
            if (type == int.class) return Integer.class;
            if (type == long.class) return Long.class;
            if (type == float.class) return Float.class;
            if (type == double.class) return Double.class;
            return Void.class;
        }

        private static String argumentTypes(Object[] values) {
            if (values == null) return "null";
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(',');
                out.append(values[i] == null ? "null" : values[i].getClass().getName());
            }
            return out.append(']').toString();
        }
    }

    final class HookTarget {
        private final Member member;
        private final CopyOnWriteArrayList<Hooker> hookers =
                new CopyOnWriteArrayList<Hooker>();
        private final Set<String> scopedRegistrations = new HashSet<String>();
        private final java.util.IdentityHashMap<Hooker, String> scopedRegistrationKeys =
                new java.util.IdentityHashMap<Hooker, String>();
        private boolean registered;
        // XposedBridge implementations may publish a callback in their per-Member callback
        // registry before native hook installation completes. If hookMethod then throws, a
        // second call can observe that stale registry entry and return without retrying the
        // native hook. The public API exposes no portable way to distinguish or repair that
        // partially completed state, so this target must fail closed rather than report success.
        private Throwable registrationFailure;

        HookTarget(Member member) { this.member = member; }

        void intercept(final Hooker hooker) {
            interceptInternal(hooker, false);
        }

        synchronized boolean interceptAndReport(final Hooker hooker) {
            return interceptInternal(hooker, true);
        }

        private synchronized boolean interceptInternal(final Hooker hooker,
                                                        boolean removable) {
            if (hooker == null) throw new NullPointerException("hooker");
            if (registrationFailure != null) {
                throw new IllegalStateException(
                        "Xposed registration previously failed; unsafe retry refused for "
                                + member, registrationFailure);
            }
            HookRegistrationScope scope = hookRegistrationScope.get();
            String registrationKey = scope == null ? null : scope.nextKey(member, hooker);
            if (registrationKey != null && scopedRegistrations.contains(registrationKey)) {
                // Replaying a completed occurrence in a fresh retry scope must still advance
                // that scope's ordinal; otherwise a later same-class occurrence aliases it.
                scope.commitKey(member, hooker);
                return false;
            }
            hookers.add(hooker);
            if (registered) {
                if (registrationKey != null) {
                    scopedRegistrations.add(registrationKey);
                    if (removable) scopedRegistrationKeys.put(hooker, registrationKey);
                    scope.commitKey(member, hooker);
                }
                return true;
            }
            // Run after ordinary before callbacks, then publish our result so their matching
            // after callbacks still execute. A highest-priority replacement would silently
            // suppress every other module hooked to the same host member.
            XC_MethodHook.Unhook unhook;
            try {
                unhook = XposedBridge.hookMethod(member, new XC_MethodHook(Integer.MIN_VALUE) {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        long started = System.nanoTime();
                        long traceId = RawHookTrace.enter(member, param.thisObject, param.args);
                        JavaPluginRuntimeBridge.before(member, param.thisObject, param.args);
                        try {
                            List<Hooker> snapshot = Arrays.asList(
                                    hookers.toArray(new Hooker[hookers.size()]));
                            Object[] original = param.args == null
                                    ? new Object[0] : param.args.clone();
                            Chain chain = new Chain(
                                    member, param, snapshot, 0, param.args, original);
                            Object result = chain.proceed();
                            RawHookTrace.exit(traceId, member, result,
                                    System.nanoTime() - started);
                            JavaPluginRuntimeBridge.after(member, result,
                                    System.nanoTime() - started);
                            param.setResult(result);
                        } catch (Throwable error) {
                            RawHookTrace.failed(traceId, member, error,
                                    System.nanoTime() - started);
                            JavaPluginRuntimeBridge.error(member, error,
                                    System.nanoTime() - started);
                            param.setThrowable(error);
                        }
                    }
                });
            } catch (RuntimeException | Error error) {
                // Keep the staged Hooker: hookMethod may already have published its callback
                // before a native/linkage failure. Removing it could orphan a live callback.
                // Do not retry this Member: standard Xposed callback registries retain the
                // partially published entry and may falsely accept a later registration.
                registrationFailure = error;
                throw error;
            }
            if (unhook == null) {
                // LSPosed's legacy bridge returns null when its native HookBridge reports that
                // installation failed. Unlike an exception after callback publication, this is
                // an explicit unsuccessful result and can be retried. Roll back both the staged
                // Hooker and the scope ordinal so the same logical registration keeps its key.
                hookers.remove(hooker);
                throw new IllegalStateException(
                        "XposedBridge.hookMethod returned null for " + member);
            }
            registered = true;
            if (registrationKey != null) {
                scopedRegistrations.add(registrationKey);
                if (removable) scopedRegistrationKeys.put(hooker, registrationKey);
                scope.commitKey(member, hooker);
            }
            RawHookTrace.registered(member);
            JavaPluginRuntimeBridge.registered(member);
            return true;
        }

        synchronized void remove(Hooker hooker) {
            if (hooker == null) return;
            String registrationKey = scopedRegistrationKeys.get(hooker);
            if (registrationKey != null) scopedRegistrations.remove(registrationKey);
            scopedRegistrationKeys.remove(hooker);
            hookers.remove(hooker);
        }
    }

    /**
     * Adapts the universal runtime to the existing per-Member callback chain. This deliberately
     * does not call XposedBridge.hookMethod directly: HookTarget owns the single native callback
     * and preserves ordering, original-call, argument and exception semantics for all features.
     *
     * Unhook disables this logical callback. The shared native Xposed callback remains installed
     * while the process lives because removing it would also remove unrelated callbacks on the
     * same Member.
     */
    final RoiUniversalHookRuntime.Backend universalHookBackend() {
        return new RoiUniversalHookRuntime.Backend() {
            @Override public RoiUniversalHookRuntime.Handle install(
                    final RoiUniversalHookRuntime.Target target,
                    final RoiUniversalHookRuntime.Callback callback) {
                if (target == null || target.member == null || callback == null) {
                    throw new IllegalArgumentException("target and callback are required");
                }
                final java.util.concurrent.atomic.AtomicBoolean enabled =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                final HookTarget hookTarget = hook(target.member);
                final Hooker universalHooker = new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (!enabled.get()) return chain.proceed();
                        RoiUniversalHookRuntime.Invocation invocation =
                                new RoiUniversalHookRuntime.Invocation(
                                        target.member, chain.getThisObject(),
                                        chain.getArgs().toArray(new Object[0]));
                        callback.before(invocation);
                        if (!invocation.skipOriginal && invocation.throwable == null) {
                            try {
                                invocation.result = chain.proceed(invocation.args);
                            } catch (Throwable originalFailure) {
                                invocation.throwable = originalFailure;
                            }
                        }
                        callback.after(invocation);
                        if (invocation.throwable != null) throw invocation.throwable;
                        return invocation.result;
                    }
                    @Override public boolean isRequired() { return target.spec.required; }
                };
                final RoiUniversalHookRuntime.Handle handle =
                        new RoiUniversalHookRuntime.Handle() {
                            @Override public void unhook() {
                                enabled.set(false);
                                hookTarget.remove(universalHooker);
                            }
                        };
                if (!hookTarget.interceptAndReport(universalHooker)) {
                    throw new IllegalStateException(
                            "universal interceptor registration was already present for " + target.member);
                }
                // Activate only after HookTarget confirms successful registration.
                // A partially published Xposed callback remains a disabled pass-through.
                enabled.set(true);
                return handle;
            }
        };
    }

    HookTarget hook(Member member) {
        if (member == null) throw new NullPointerException("member");
        HookTarget existing = targets.get(member);
        if (existing != null) return existing;
        HookTarget created = new HookTarget(member);
        existing = targets.putIfAbsent(member, created);
        return existing == null ? created : existing;
    }

    boolean deoptimize(Member member) {
        try {
            Method method = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            method.setAccessible(true);
            method.invoke(null, member);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    void log(int priority, String tag, String message) {
        XposedBridge.log(tag + " [" + priority + "] " + message);
    }
}
