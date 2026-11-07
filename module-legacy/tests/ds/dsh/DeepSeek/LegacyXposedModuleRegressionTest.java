package ds.dsh.DeepSeek;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class LegacyXposedModuleRegressionTest {
    private static final class Module extends LegacyXposedModule {}

    private static final class Target {
        int joinCalls;
        String join(String left, String right) {
            joinCalls++;
            return left + ":" + right;
        }
        String fail(RuntimeException signal) { throw signal; }
    }

    public static void main(String[] args) throws Throwable {
        for (int api = 82; api <= 102; api++) {
            runApiContract(api);
        }
        runUniversalBackendAdapterTest();
        System.out.println("Legacy Xposed API 82-102 matrix OK (21 versions)");
    }

    
    private static void runUniversalBackendAdapterTest() throws Throwable {
        XposedBridge.resetTestState();
        final Method method = Target.class.getDeclaredMethod("join", String.class, String.class);
        method.setAccessible(true);
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver, Object[] args)
                    throws Throwable {
                return ((Method) member).invoke(receiver, args);
            }
        };

        Module module = new Module();
        RoiUniversalHookRuntime.Callback callback = new RoiUniversalHookRuntime.Callback() {
            @Override public void before(RoiUniversalHookRuntime.Invocation invocation) {
                invocation.args[0] = "hooked";
            }
            @Override public void after(RoiUniversalHookRuntime.Invocation invocation) {
                invocation.result = invocation.result + ":after";
            }
        };
        RoiUniversalHookRuntime.Spec spec = RoiUniversalHookRuntime.Spec.builder("legacy.backend.join")
                .role("test").className(Target.class.getName()).method("join")
                .params("java.lang.String", "java.lang.String")
                .returns("java.lang.String").fingerprint("test-fingerprint")
                .callback(callback).build();
        RoiUniversalHookRuntime.Adapters adapters = new RoiUniversalHookRuntime.Adapters();
        adapters.approve("test-fingerprint", java.util.Collections.singleton("test"));
        RoiUniversalHookRuntime.Installer installer = new RoiUniversalHookRuntime.Installer(
                new RoiUniversalHookRuntime.Resolver(), module.universalHookBackend(),
                new RoiUniversalHookRuntime.Health() {
                    @Override public boolean verify(RoiUniversalHookRuntime.Target target,
                                                    RoiUniversalHookRuntime.Handle handle) {
                        return target != null && handle != null;
                    }
                },
                new RoiUniversalHookRuntime.ActivationGate() {
                    @Override public boolean authorized(RoiUniversalHookRuntime.Spec value) {
                        return true;
                    }
                });
        RoiUniversalHookRuntime.Result installed = installer.install(
                java.util.Collections.singletonList(spec), Target.class.getClassLoader(),
                "test-fingerprint", adapters);
        check(installed.success, "universal backend failed to register through HookTarget: "
                + installed.message);
        check(XposedBridge.hookCountForTest == 1,
                "universal backend registered a competing native callback");

        Target target = new Target();
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        param.thisObject = target;
        param.args = new Object[] {"before", "right"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);
        check("hooked:right:after".equals(param.getResult()),
                "universal callback argument/result contract changed: " + param.getResult());
        check(target.joinCalls == 1, "universal backend must invoke original exactly once");

        check(installer.uninstall("legacy.backend.join"),
                "logical unhook should disable the universal interceptor");
        XC_MethodHook.MethodHookParam afterUnhook = new XC_MethodHook.MethodHookParam();
        afterUnhook.method = method;
        afterUnhook.thisObject = target;
        afterUnhook.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(afterUnhook);
        check("safe:original".equals(afterUnhook.getResult()),
                "disabled logical interceptor must pass through the shared callback chain");
        check(target.joinCalls == 2, "disabled interceptor must still invoke original exactly once");
        check(XposedBridge.hookCountForTest == 1,
                "logical unhook must not remove or duplicate the shared native callback");
        XposedBridge.resetTestState();
    }

    private static void runApiContract(int api) throws Throwable {
        XposedBridge.XPOSED_BRIDGE_VERSION = api;
        XposedBridge.resetTestState();
        final Method method = Target.class.getDeclaredMethod(
                "join", String.class, String.class);
        method.setAccessible(true);
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };

        Module module = new Module();
        module.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                check("left".equals(chain.getArg(0)), "outer hook saw wrong initial args");
                return chain.proceed(new Object[] {"changed", "middle"}) + ":outer";
            }
        });
        module.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                check("changed".equals(chain.getArg(0)),
                        "inner hook did not receive replaced args");
                Object[] next = chain.getArgs().toArray();
                next[1] = "final";
                return chain.proceed(next) + ":inner";
            }
        });

        check(XposedBridge.hookCountForTest == 1,
                "the same Member registered more than one traditional callback");
        check(XposedBridge.callbackForTest != null, "traditional callback was not registered");
        check(XposedBridge.callbackForTest.priority == Integer.MIN_VALUE,
                "adapter should run after ordinary before callbacks");

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        param.thisObject = new Target();
        param.args = new Object[] {"left", "right"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);

        check(!param.hasThrowable(), "adapter returned an unexpected throwable");
        check("changed:final:inner:outer".equals(param.getResult()),
                "around-hook order or return transforms changed: " + param.getResult());
        check("changed".equals(param.args[0]) && "final".equals(param.args[1]),
                "final arguments were not exposed to traditional after callbacks");

        XposedBridge.resetTestState();
        final Method failing = Target.class.getDeclaredMethod("fail", RuntimeException.class);
        failing.setAccessible(true);
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module exceptionModule = new Module();
        exceptionModule.hook(failing).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed();
            }
        });
        RuntimeException cancellation = new RuntimeException("flow finished");
        XC_MethodHook.MethodHookParam failingParam = new XC_MethodHook.MethodHookParam();
        failingParam.method = failing;
        failingParam.thisObject = new Target();
        failingParam.args = new Object[] {cancellation};
        XposedBridge.callbackForTest.dispatchBeforeForTest(failingParam);
        check(failingParam.getThrowable() == cancellation,
                "reflection wrapper changed the host exception contract: "
                        + failingParam.getThrowable());

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module failOpenModule = new Module();
        failOpenModule.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed(new Object[] {"invalid", Integer.valueOf(7)});
            }
        });
        XC_MethodHook.MethodHookParam failOpenParam = new XC_MethodHook.MethodHookParam();
        failOpenParam.method = method;
        failOpenParam.thisObject = new Target();
        failOpenParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(failOpenParam);
        check(!failOpenParam.hasThrowable(),
                "API " + api
                        + " incompatible replacement should fail open to the original arguments");
        check("safe:original".equals(failOpenParam.getResult()),
                "API " + api + " fail-open invocation did not preserve the original arguments");

        runFailureIsolationTests(method, api);
        runScopedRegistrationRetryTest(method, api);
        runScopedRegistrationNullRetryTest(method, api);
        runPartialSameClassScopeRetryTest(method, api);
        runScopedRegistrationErrorRetryTest(method, api);
        runScopedRegistrationErrorAfterNativeInstallTest(method, api);
        runScopedRegistrationMultiplicityTest(method, api);
        runNestedRegistrationScopeRestoreTest(method, api);
    }

    private static void registerScopedRetryHook(Module module, Method method) {
        LegacyXposedModule.HookRegistrationScope previous =
                module.beginHookRegistrationScope("test.retryable.feature");
        try {
            module.hook(method).intercept(new LegacyXposedModule.Hooker() {
                @Override public Object intercept(LegacyXposedModule.Chain chain)
                        throws Throwable {
                    return chain.proceed() + ":scoped";
                }
            });
        } finally {
            module.endHookRegistrationScope(previous);
        }
    }

    private static void runScopedRegistrationRetryTest(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module module = new Module();
        registerScopedRetryHook(module, method);
        registerScopedRetryHook(module, method);
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " retry duplicated the underlying Xposed callback");

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Target target = new Target();
        param.thisObject = target;
        param.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);
        check("safe:original:scoped".equals(param.getResult()),
                "API " + api + " retry duplicated or lost the scoped interceptor: "
                        + param.getResult());
        check(target.joinCalls == 1,
                "API " + api + " retry must invoke the original exactly once");
    }

    private static void runScopedRegistrationNullRetryTest(
            final Method method, int api) throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module module = new Module();
        XposedBridge.returnNullForTest = true;
        boolean failed = false;
        try {
            registerScopedRetryHook(module, method);
        } catch (IllegalStateException expected) {
            failed = expected.getMessage().contains("returned null");
        }
        check(failed, "API " + api + " null Unhook result was treated as success");
        check(!XposedBridge.nativeHookInstalledForTest,
                "API " + api + " null Unhook result must not mark native hook installed");

        registerScopedRetryHook(module, method);
        registerScopedRetryHook(module, method);
        check(XposedBridge.nativeHookInstalledForTest,
                "API " + api + " retry after explicit null result did not install");
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " explicit null retry duplicated native installation");

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Target target = new Target();
        param.thisObject = target;
        param.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);
        check("safe:original:scoped".equals(param.getResult()),
                "API " + api + " explicit null retry duplicated the logical Hooker: "
                        + param.getResult());
        check(target.joinCalls == 1,
                "API " + api + " explicit null retry must invoke the original exactly once");
    }

    private static void registerScopedSuffix(
            Module module, Method method, final String suffix) {
        module.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed() + suffix;
            }
        });
    }

    private static void registerPartialScopedSameClassHooks(
            Module module, Method method, boolean failAfterFirst) {
        LegacyXposedModule.HookRegistrationScope previous =
                module.beginHookRegistrationScope("test.partial.same.class");
        try {
            registerScopedSuffix(module, method, ":first");
            if (failAfterFirst) throw new IllegalStateException("simulated partial installer failure");
            registerScopedSuffix(module, method, ":second");
        } finally {
            module.endHookRegistrationScope(previous);
        }
    }

    private static void runPartialSameClassScopeRetryTest(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module module = new Module();
        boolean failed = false;
        try {
            registerPartialScopedSameClassHooks(module, method, true);
        } catch (IllegalStateException expected) {
            failed = "simulated partial installer failure".equals(expected.getMessage());
        }
        check(failed, "API " + api + " partial same-class installer failure was not injected");

        registerPartialScopedSameClassHooks(module, method, false);
        registerPartialScopedSameClassHooks(module, method, false);

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Target target = new Target();
        param.thisObject = target;
        param.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);
        check("safe:original:second:first".equals(param.getResult()),
                "API " + api + " retry lost or duplicated same-class occurrence: "
                        + param.getResult());
        check(target.joinCalls == 1,
                "API " + api + " partial same-class retry must invoke original exactly once");
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " partial same-class retry duplicated native callback");
    }

    private static void runScopedRegistrationErrorRetryTest(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module module = new Module();
        XposedBridge.hookFailureForTest = new LinkageError("first registration attempt");
        boolean failed = false;
        try {
            registerScopedRetryHook(module, method);
        } catch (LinkageError expected) {
            failed = true;
        }
        check(failed, "API " + api + " injected registration Error was not propagated");

        boolean retryRefused = false;
        try {
            registerScopedRetryHook(module, method);
        } catch (IllegalStateException expected) {
            retryRefused = expected.getCause() instanceof LinkageError;
        }
        check(retryRefused,
                "API " + api + " retry must expose the poisoned Xposed registration state");
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " retry must not pretend to reinstall a poisoned callback");
        check(XposedBridge.callbackForTest != null,
                "API " + api + " test must model callback publication before native failure");
        check(!XposedBridge.nativeHookInstalledForTest,
                "API " + api + " injected native failure must remain uninstalled");
    }

    private static void runScopedRegistrationErrorAfterNativeInstallTest(
            final Method method, int api) throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module module = new Module();
        XposedBridge.hookFailureForTest =
                new LinkageError("failure after native callback installation");
        XposedBridge.hookFailureAfterNativeInstallForTest = true;
        boolean failed = false;
        try {
            registerScopedRetryHook(module, method);
        } catch (LinkageError expected) {
            failed = true;
        }
        check(failed, "API " + api + " post-install failure was not propagated");
        check(XposedBridge.nativeHookInstalledForTest,
                "API " + api + " test did not simulate a live native callback");

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Target target = new Target();
        param.thisObject = target;
        param.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);
        check(!param.hasThrowable(),
                "API " + api + " live callback lost its staged interceptor after failure");
        check("safe:original:scoped".equals(param.getResult()),
                "API " + api + " live callback did not retain the staged interceptor");
        check(target.joinCalls == 1,
                "API " + api + " live callback must invoke the original exactly once");

        boolean retryRefused = false;
        try {
            registerScopedRetryHook(module, method);
        } catch (IllegalStateException expected) {
            retryRefused = expected.getCause() instanceof LinkageError;
        }
        check(retryRefused,
                "API " + api + " must refuse retry after an ambiguous post-install failure");
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " post-install retry duplicated the native callback");
    }

    private static void registerTwoScopedSameClassHooks(Module module, Method method) {
        LegacyXposedModule.HookRegistrationScope previous =
                module.beginHookRegistrationScope("test.multiple.same.class");
        try {
            for (final String suffix : new String[] {":first", ":second"}) {
                module.hook(method).intercept(new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable {
                        return chain.proceed() + suffix;
                    }
                });
            }
        } finally {
            module.endHookRegistrationScope(previous);
        }
    }

    private static void runScopedRegistrationMultiplicityTest(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module module = new Module();
        registerTwoScopedSameClassHooks(module, method);
        registerTwoScopedSameClassHooks(module, method);

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Target target = new Target();
        param.thisObject = target;
        param.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);

        check(!param.hasThrowable(), "API " + api + " repeated same-class hooks failed");
        check("safe:original:second:first".equals(param.getResult()),
                "API " + api + " same-class hook occurrences were not preserved exactly: "
                        + param.getResult());
        check(target.joinCalls == 1,
                "API " + api + " same-class hooks must invoke the original exactly once");
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " retry must not duplicate the underlying Xposed callback");
    }

    private static void registerNestedScopeSequence(Module module, Method method) {
        LegacyXposedModule.HookRegistrationScope outer =
                module.beginHookRegistrationScope("test.nested.outer");
        try {
            registerScopedLabel(module, method, ":outer-first");
            LegacyXposedModule.HookRegistrationScope inner =
                    module.beginHookRegistrationScope("test.nested.inner");
            try {
                registerScopedLabel(module, method, ":inner");
            } finally {
                module.endHookRegistrationScope(inner);
            }
            // Ending the inner scope must restore the original outer occurrence counter.
            registerScopedLabel(module, method, ":outer-second");
        } finally {
            module.endHookRegistrationScope(outer);
        }
    }

    private static void registerScopedLabel(Module module, Method method, final String suffix) {
        module.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed() + suffix;
            }
        });
    }

    private static void runNestedRegistrationScopeRestoreTest(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module module = new Module();
        registerNestedScopeSequence(module, method);
        registerNestedScopeSequence(module, method);

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Target target = new Target();
        param.thisObject = target;
        param.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);

        check(!param.hasThrowable(), "API " + api + " nested registration scope failed");
        check("safe:original:outer-second:inner:outer-first".equals(param.getResult()),
                "API " + api + " nested scope restore duplicated or lost registrations: "
                        + param.getResult());
        check(target.joinCalls == 1,
                "API " + api + " nested scopes must invoke the original exactly once");
        check(XposedBridge.hookCountForTest == 1,
                "API " + api + " nested scopes duplicated the underlying Xposed callback");
    }

    private static void runFailureIsolationTests(final Method method, int api) throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module optionalBefore = new Module();
        optionalBefore.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) {
                throw new IllegalStateException("optional pre-hook failure");
            }
        });
        XC_MethodHook.MethodHookParam beforeParam = new XC_MethodHook.MethodHookParam();
        beforeParam.method = method;
        Target beforeTarget = new Target();
        beforeParam.thisObject = beforeTarget;
        beforeParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(beforeParam);
        check(!beforeParam.hasThrowable(),
                "API " + api + " optional pre-hook failure escaped to the host");
        check("safe:original".equals(beforeParam.getResult()),
                "API " + api + " optional pre-hook failure did not invoke the original member");
        check(beforeTarget.joinCalls == 1,
                "API " + api + " optional pre-hook failure must invoke the original exactly once");

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module optionalAfter = new Module();
        optionalAfter.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                chain.proceed();
                throw new IllegalStateException("optional post-hook failure");
            }
        });
        XC_MethodHook.MethodHookParam afterParam = new XC_MethodHook.MethodHookParam();
        afterParam.method = method;
        Target afterTarget = new Target();
        afterParam.thisObject = afterTarget;
        afterParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(afterParam);
        check(!afterParam.hasThrowable(),
                "API " + api + " optional post-hook failure escaped to the host");
        check("safe:original".equals(afterParam.getResult()),
                "API " + api + " optional post-hook failure lost the original result");
        check(afterTarget.joinCalls == 1,
                "API " + api + " optional post-hook failure must not invoke the original twice");

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module required = new Module();
        required.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public boolean isRequired() { return true; }
            @Override public Object intercept(LegacyXposedModule.Chain chain) {
                throw new IllegalStateException("required hook failure");
            }
        });
        XC_MethodHook.MethodHookParam requiredParam = new XC_MethodHook.MethodHookParam();
        requiredParam.method = method;
        Target requiredTarget = new Target();
        requiredParam.thisObject = requiredTarget;
        requiredParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(requiredParam);
        check(requiredParam.getThrowable() instanceof IllegalStateException,
                "API " + api + " required hook failure was incorrectly swallowed");
        check(requiredTarget.joinCalls == 0,
                "API " + api + " required hook failure must not silently invoke the original");

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module nestedRequired = new Module();
        nestedRequired.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed();
            }
        });
        nestedRequired.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public boolean isRequired() { return true; }
            @Override public Object intercept(LegacyXposedModule.Chain chain) {
                throw new IllegalStateException("nested required hook failure");
            }
        });
        XC_MethodHook.MethodHookParam nestedParam = new XC_MethodHook.MethodHookParam();
        nestedParam.method = method;
        nestedParam.thisObject = new Target();
        nestedParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(nestedParam);
        check(nestedParam.getThrowable() instanceof IllegalStateException,
                "API " + api + " outer optional hook swallowed a nested required failure");

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module fatal = new Module();
        fatal.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) {
                throw new ThreadDeath();
            }
        });
        XC_MethodHook.MethodHookParam fatalParam = new XC_MethodHook.MethodHookParam();
        fatalParam.method = method;
        fatalParam.thisObject = new Target();
        fatalParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(fatalParam);
        check(fatalParam.getThrowable() instanceof ThreadDeath,
                "API " + api + " fatal VM control error must not be swallowed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
