package de.robv.android.xposed;

import java.lang.reflect.Member;

// compileOnly stub — provided by the Xposed framework at runtime
public final class XposedBridge {

    public static int XPOSED_BRIDGE_VERSION;
    public static XC_MethodHook callbackForTest;
    public static Member memberForTest;
    public static int hookCountForTest;
    public static Error hookFailureForTest;
    public static OriginalMethodInvoker originalMethodInvokerForTest;

    public interface OriginalMethodInvoker {
        Object invoke(Member member, Object thisObject, Object[] args) throws Throwable;
    }

    private XposedBridge() {}

    public static void log(String text) {}

    public static void log(Throwable t) {}

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        if (hookFailureForTest != null) {
            Error failure = hookFailureForTest;
            hookFailureForTest = null;
            throw failure;
        }
        memberForTest = hookMethod;
        callbackForTest = callback;
        hookCountForTest++;
        return null;
    }

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws Throwable {
        return originalMethodInvokerForTest == null ? null
                : originalMethodInvokerForTest.invoke(method, thisObject, args);
    }

    public static void resetTestState() {
        callbackForTest = null;
        memberForTest = null;
        hookCountForTest = 0;
        hookFailureForTest = null;
        originalMethodInvokerForTest = null;
    }
}
