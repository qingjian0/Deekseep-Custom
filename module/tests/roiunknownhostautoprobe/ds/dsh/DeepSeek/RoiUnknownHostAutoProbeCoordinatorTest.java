package ds.dsh.DeepSeek;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoiUnknownHostAutoProbeCoordinatorTest {
    private static final class RuntimeFixture {
        private String build(String input) { return input; }
    }

    public static void main(String[] args) throws Throwable {
        testLateCandidateReconciliationAndExactInstall();
        testCandidateGates();
        System.out.println("ROI unknown-host auto probe coordinator tests passed");
    }

    private static void testLateCandidateReconciliationAndExactInstall() throws Throwable {
        RoiUnknownHostAutoProbeCoordinator coordinator =
                new RoiUnknownHostAutoProbeCoordinator();
        ClassLoader root = RuntimeFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String owner = "L" + RuntimeFixture.class.getName().replace('.', '/') + ";";
        String descriptor = owner + "->build(Ljava/lang/String;)Ljava/lang/String;";
        AtomicInteger installs = new AtomicInteger();

        check(coordinator.observeLoadedClass(RuntimeFixture.class, root,
                (candidate, method) -> false) == 0, "no candidate means no install");

        int installed = coordinator.registerCandidate(owner, "build", descriptor, scope,
                RoiUnknownHostInvocationProbePolicy.Role.REQUEST_CONSTRUCTION, true, root,
                (candidate, method) -> {
                    check(candidate.exactRuntimeMatch, "runtime identity must be exact");
                    check(candidate.staticRoleHintPresent, "static role hint retained");
                    check(candidate.staticCallGraphObserved, "static graph hint retained");
                    check(!candidate.roleVerified, "static role hint is not runtime role proof");
                    check(!candidate.callGraphConfirmed, "static graph is not runtime graph proof");
                    check(method.getName().equals("build"), "exact reflected method");
                    installs.incrementAndGet();
                    return true;
                });
        equal(1, installed, "late candidate must reconcile with observed class");
        equal(1, installs.get(), "installer called once");
        equal(1, coordinator.candidateCount(), "candidate retained");
        equal(1, coordinator.installedCount(), "installation retained");
        check(coordinator.observeLoadedClass(RuntimeFixture.class, root,
                (candidate, method) -> { installs.incrementAndGet(); return true; }) == 0,
                "duplicate class event must not reinstall");
        equal(1, installs.get(), "duplicate event suppressed");
    }

    private static void testCandidateGates() throws Throwable {
        RoiUnknownHostAutoProbeCoordinator coordinator =
                new RoiUnknownHostAutoProbeCoordinator();
        ClassLoader root = RuntimeFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String owner = "L" + RuntimeFixture.class.getName().replace('.', '/') + ";";
        AtomicInteger installs = new AtomicInteger();
        RoiUnknownHostAutoProbeCoordinator.Installer installer = (candidate, method) -> {
            installs.incrementAndGet();
            return true;
        };

        coordinator.observeLoadedClass(RuntimeFixture.class, root, installer);
        equal(0, coordinator.registerCandidate(owner, "build",
                owner + "->build(Ljava/lang/String;)Ljava/lang/String;", scope,
                RoiUnknownHostInvocationProbePolicy.Role.UNRESOLVED, true, root, installer),
                "unresolved role rejected");
        equal(0, coordinator.registerCandidate(owner, "build",
                owner + "->build(Ljava/lang/String;)Ljava/lang/String;", "apk:/base.apk",
                RoiUnknownHostInvocationProbePolicy.Role.HTTP_TRANSPORT, true, root, installer),
                "APK-only scope rejected");
        equal(0, coordinator.registerCandidate(owner, "missing",
                owner + "->missing(Ljava/lang/String;)Ljava/lang/String;", scope,
                RoiUnknownHostInvocationProbePolicy.Role.HTTP_TRANSPORT, true, root, installer),
                "missing runtime method rejected");
        equal(0, coordinator.registerCandidate(owner, "build",
                owner + "->build(Ljava/lang/String;)Ljava/lang/String;", scope,
                RoiUnknownHostInvocationProbePolicy.Role.HTTP_TRANSPORT, false, root, installer),
                "missing static call graph rejected");
        equal(0, installs.get(), "rejected candidates never install");

        RoiUnknownHostInvocationProbePolicy.Candidate diagnostic =
                new RoiUnknownHostInvocationProbePolicy.Candidate(owner, "build",
                        owner + "->build(Ljava/lang/String;)Ljava/lang/String;", scope,
                        RoiUnknownHostInvocationProbePolicy.Role.HTTP_TRANSPORT,
                        true, false, false, true, true);
        RoiUnknownHostInvocationProbePolicy.RuntimeMethod runtime =
                new RoiUnknownHostInvocationProbePolicy.RuntimeMethod(owner,
                        diagnostic.descriptor, scope);
        check(RoiUnknownHostInvocationProbePolicy.allowsProbe(true, diagnostic, runtime),
                "exact candidate may enter diagnostic-only probe lane");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true, diagnostic, runtime),
                "diagnostic proof must not authorize functional hooks");
        check(!RoiUnknownHostInvocationProbePolicy.allowsProbe(false, diagnostic, runtime),
                "probe lane requires explicit opt-in");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void equal(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected="
                + expected + " actual=" + actual);
    }
}
