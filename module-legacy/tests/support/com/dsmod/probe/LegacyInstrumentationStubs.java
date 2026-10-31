package com.dsmod.probe;

import java.lang.reflect.Member;

/**
 * No-op instrumentation seams for the isolated LegacyXposedModule adapter regression test.
 * Production implementations live in module/src and are intentionally not compiled here:
 * they depend on Android runtime APIs and the plugin/JSON subsystem.
 */
final class RawHookTrace {
    private RawHookTrace() {}
    static long enter(Member member, Object receiver, Object[] args) { return 0L; }
    static void exit(long id, Member member, Object result, long durationNanos) {}
    static void failed(long id, Member member, Throwable error, long durationNanos) {}
    static void registered(Member member) {}
}

final class JavaPluginRuntimeBridge {
    private JavaPluginRuntimeBridge() {}
    static void before(Member member, Object receiver, Object[] args) {}
    static void after(Member member, Object result, long durationNanos) {}
    static void error(Member member, Throwable error, long durationNanos) {}
    static void registered(Member member) {}
}
