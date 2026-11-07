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
        System.out.println("Legacy Xposed API 82-102 matrix OK (21 versions)");
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
        runManagedHookRegistrationTests(method, api);
        runManagedLifecycleBridgeTests(method, api);
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


    private static void runManagedHookRegistrationTests(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };

        Module managedOnly = new Module();
        LegacyXposedModule.HookRegistrationHandle onlyHandle = managedOnly.registerManaged(
                method, new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable {
                        return chain.proceed() + ":managed";
                    }
                });
        XC_MethodHook.MethodHookParam managedParam = new XC_MethodHook.MethodHookParam();
        managedParam.method = method;
        managedParam.thisObject = new Target();
        managedParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(managedParam);
        check("safe:original:managed".equals(managedParam.getResult()),
                "API " + api + " managed Hooker was not dispatched");
        onlyHandle.unhook();
        onlyHandle.unhook();
        check(XposedBridge.unhookCountForTest == 1,
                "API " + api + " managed-only target should physically unhook exactly once");
        check(XposedBridge.callbackForTest == null && !XposedBridge.nativeHookInstalledForTest,
                "API " + api + " managed-only target left the Xposed callback installed");

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module sharedTarget = new Module();
        sharedTarget.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed() + ":legacy";
            }
        });
        LegacyXposedModule.HookRegistrationHandle sharedHandle = sharedTarget.registerManaged(
                method, new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable {
                        return chain.proceed() + ":managed";
                    }
                });
        XC_MethodHook.MethodHookParam beforeRemoval = new XC_MethodHook.MethodHookParam();
        beforeRemoval.method = method;
        beforeRemoval.thisObject = new Target();
        beforeRemoval.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(beforeRemoval);
        check("safe:original:managed:legacy".equals(beforeRemoval.getResult()),
                "API " + api + " managed Hooker changed shared callback order");
        sharedHandle.unhook();
        check(XposedBridge.unhookCountForTest == 0,
                "API " + api + " removing one Hooker must not unhook a shared callback");
        XC_MethodHook.MethodHookParam afterRemoval = new XC_MethodHook.MethodHookParam();
        afterRemoval.method = method;
        afterRemoval.thisObject = new Target();
        afterRemoval.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(afterRemoval);
        check("safe:original:legacy".equals(afterRemoval.getResult()),
                "API " + api + " managed unhook removed another module's Hooker");

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver,
                                           Object[] invokeArgs) throws Throwable {
                return ((Method) member).invoke(receiver, invokeArgs);
            }
        };
        Module retryModule = new Module();
        LegacyXposedModule.HookRegistrationHandle retryHandle = retryModule.registerManaged(
                method, new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable {
                        return chain.proceed();
                    }
                });
        XposedBridge.unhookFailureForTest = new IllegalStateException("transient unhook failure");
        boolean failed = false;
        try {
            retryHandle.unhook();
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed, "API " + api + " failed physical unhook was not surfaced");
        check(XposedBridge.callbackForTest != null,
                "API " + api + " failed unhook must retain the callback for retry");
        retryHandle.unhook();
        check(XposedBridge.unhookCountForTest == 2,
                "API " + api + " managed unhook retry did not call the framework again");
        check(XposedBridge.callbackForTest == null,
                "API " + api + " managed unhook retry did not remove the callback");
    }

    private static void runManagedLifecycleBridgeTests(final Method method, int api)
            throws Throwable {
        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest = new XposedBridge.OriginalMethodInvoker() {
            @Override public Object invoke(Member member, Object receiver, Object[] args)
                    throws Throwable { return ((Method) member).invoke(receiver, args); }
        };
        Module module = new Module();
        final java.util.concurrent.atomic.AtomicInteger checks =
                new java.util.concurrent.atomic.AtomicInteger();
        String id = "roi.integration.api" + api;
        HookLifecycleManager.Status active = RoiManagedHookInstaller.install(id, module, method,
                new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable { return chain.proceed() + ":managed"; }
                }, new RoiManagedHookInstaller.Authorization() {
                    @Override public boolean isAuthorized() { checks.incrementAndGet(); return true; }
                });
        check(active.getState() == HookLifecycleManager.State.ACTIVE,
                "API " + api + " ROI-managed install did not reach ACTIVE");
        check(checks.get() == 2,
                "API " + api + " authorization was not rechecked after registration");
        check(XposedBridge.nativeHookInstalledForTest && XposedBridge.hookCountForTest == 1,
                "API " + api + " managed bridge did not install exactly one callback");

        HookLifecycleManager.Status duplicate = RoiManagedHookInstaller.install(id, module, method,
                new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable { return chain.proceed() + ":duplicate"; }
                }, new RoiManagedHookInstaller.Authorization() {
                    @Override public boolean isAuthorized() { return true; }
                });
        check(duplicate.getState() == HookLifecycleManager.State.ACTIVE
                        && XposedBridge.hookCountForTest == 1,
                "API " + api + " duplicate lifecycle registration was not idempotent");
        HookLifecycleManager.Status removed = RoiManagedHookInstaller.uninstall(id);
        check(removed.getState() == HookLifecycleManager.State.ROLLED_BACK
                        && !XposedBridge.nativeHookInstalledForTest
                        && XposedBridge.unhookCountForTest == 1,
                "API " + api + " lifecycle bridge failed physical unhook");

        XposedBridge.resetTestState();
        final java.util.concurrent.atomic.AtomicInteger revokedChecks =
                new java.util.concurrent.atomic.AtomicInteger();
        HookLifecycleManager.Status revoked = RoiManagedHookInstaller.install(id + ".revoked",
                module, method, new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable { return chain.proceed(); }
                }, new RoiManagedHookInstaller.Authorization() {
                    @Override public boolean isAuthorized() { return revokedChecks.incrementAndGet() == 1; }
                });
        check(revoked.getState() == HookLifecycleManager.State.ROLLED_BACK
                        && !XposedBridge.nativeHookInstalledForTest
                        && XposedBridge.unhookCountForTest == 1,
                "API " + api + " post-registration authorization revocation was not rolled back");

        XposedBridge.resetTestState();
        HookLifecycleManager.Status denied = RoiManagedHookInstaller.install(id + ".denied",
                module, method, new LegacyXposedModule.Hooker() {
                    @Override public Object intercept(LegacyXposedModule.Chain chain)
                            throws Throwable { return chain.proceed(); }
                }, new RoiManagedHookInstaller.Authorization() {
                    @Override public boolean isAuthorized() { return false; }
                });
        check(denied.getState() == HookLifecycleManager.State.SKIPPED
                        && !XposedBridge.nativeHookInstalledForTest,
                "API " + api + " unauthorized ROI target reached Xposed installation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
