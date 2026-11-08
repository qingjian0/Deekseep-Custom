package ds.dsh.DeepSeek;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit-opt-in, read-only runtime call-boundary probe for mainland DeepSeek 2.7.5/code295.
 *
 * It records method roles, argument/result runtime class names and exception classes only.
 * It never reads fields, serializes arguments, invokes host methods itself, or changes arguments.
 */
final class NativeV275RuntimeProbe {
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean();
    private static final AtomicBoolean ENABLED = new AtomicBoolean();

    private NativeV275RuntimeProbe() {}

    static void install(ClassLoader loader) {
        if (!Main.isOpenNativeCompletionProbeEnabled()
                || !HostCompat.isV275StaticOnly()
                || loader == null
                || Main.MODULE == null
                || !ATTEMPTED.compareAndSet(false, true)) {
            return;
        }
        ENABLED.set(true);
        log("install-start profile=2.7.5/code295 mode=read-only");
        int installed = 0;
        installed += installExact(loader, "nw3", "e", "b70", false,
                "yu1", "java.lang.Long", "request-flow");
        installed += installExact(loader, "r3", "c", "java.lang.Object", false,
                "fv1", "dc3", "event-dispatch");
        installed += installExact(loader, "pv1", "a", "java.lang.Object", true,
                "pv1", "bia", "ec3", "sse-event-adapter");
        installed += installExact(loader, "pv1", "e", "java.lang.Object", true,
                "pv1", "int", "wt3", "ec3", "delta-apply");
        installed += installExact(loader, "iv1", "c", "void", false,
                "fda", "lifecycle");
        log("install-finished hooked-boundaries=" + installed
                + " runtime-state=observation-only native-completion=disabled");
    }

    private static int installExact(ClassLoader loader, String ownerName, String methodName,
            String returnName, boolean expectStatic, String... signatureAndRole) {
        String role = signatureAndRole[signatureAndRole.length - 1];
        try {
            Class<?> owner = Class.forName(ownerName, false, loader);
            Class<?>[] parameters = new Class<?>[signatureAndRole.length - 1];
            for (int i = 0; i < parameters.length; i++) {
                String name = signatureAndRole[i];
                parameters[i] = "int".equals(name) ? Integer.TYPE : Class.forName(name, false, loader);
            }
            final Method target = owner.getDeclaredMethod(methodName, parameters);
            if (!returnName.equals(target.getReturnType().getName())
                    || Modifier.isStatic(target.getModifiers()) != expectStatic) {
                log("boundary-unavailable role=" + role + " reason=descriptor-mismatch");
                return 0;
            }
            target.setAccessible(true);
            Main.MODULE.hook(target).intercept(new LegacyXposedModule.Hooker() {
                @Override public Object intercept(LegacyXposedModule.Chain chain)
                        throws Throwable {
                    if (!Main.isOpenNativeCompletionProbeEnabled() || !ENABLED.get()) {
                        return chain.proceed();
                    }
                    safeLog("enter role=" + role + " args=" + argumentTypes(chain.getArgs()));
                    final Object result;
                    try {
                        result = chain.proceed();
                    } catch (VirtualMachineError fatal) {
                        throw fatal;
                    } catch (ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable failure) {
                        safeLog("throw role=" + role + " exception=" + failure.getClass().getName());
                        throw failure;
                    }
                    safeLog("return role=" + role + " result="
                            + (result == null ? "null" : result.getClass().getName()));
                    return result;
                }
            });
            log("boundary-installed role=" + role + " signature=" + target.getName());
            return 1;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            log("boundary-unavailable role=" + role + " reason="
                    + error.getClass().getSimpleName());
            return 0;
        }
    }

    private static String argumentTypes(List<?> args) {
        if (args == null || args.isEmpty()) return "[]";
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) out.append(',');
            Object value = args.get(i);
            out.append(value == null ? "null" : value.getClass().getName());
        }
        return out.append(']').toString();
    }

    private static void log(String event) {
        if (Main.isOpenNativeCompletionProbeEnabled()) {
            OpenNativeCompletionProbe.apiTrace("[NATIVE_V275_RUNTIME_PROBE] " + event);
        }
    }

    private static void safeLog(String event) {
        try {
            log(event);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            // Diagnostics must never affect the original host call.
        }
    }
}
