package ds.dsh.DeepSeek;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public final class RoiUnknownHostInvocationProbeTest {
    private static final String OWNER = "Lx/y/Transport;";
    private static final String DESCRIPTOR = "Lx/y/Transport;->send(Lx/y/Request;)Lx/y/Flow;";
    private static final String SCOPE = "loader:host:42";

    public static void main(String[] args) throws Throwable {
        testPolicyRequiresEveryGate();
        testSwitchIsExactAndDefaultOff();
        testProbeExactlyOnceAndPayloadFree();
        testProbeBounds();
        System.out.println("ROI unknown-host invocation probe tests passed");
    }

    private static void testPolicyRequiresEveryGate() {
        RoiUnknownHostInvocationProbePolicy.Candidate candidate = candidate(true, true, true);
        RoiUnknownHostInvocationProbePolicy.RuntimeMethod runtime =
                new RoiUnknownHostInvocationProbePolicy.RuntimeMethod(OWNER, DESCRIPTOR, SCOPE);
        check(!RoiUnknownHostInvocationProbePolicy.allows(false, candidate, runtime),
                "probe must be disabled without opt-in");
        check(RoiUnknownHostInvocationProbePolicy.allows(true, candidate, runtime),
                "fully evidenced exact method should be eligible");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true,
                candidate(false, true, true), runtime), "exact runtime match is mandatory");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true,
                candidate(true, false, true), runtime), "verified semantic role is mandatory");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true,
                candidate(true, true, false), runtime), "confirmed call graph is mandatory");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true,
                new RoiUnknownHostInvocationProbePolicy.Candidate(OWNER, "send", DESCRIPTOR,
                        SCOPE, RoiUnknownHostInvocationProbePolicy.Role.UNRESOLVED,
                        true, true, true), runtime), "unresolved role must be denied");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true, candidate(true, true, true),
                new RoiUnknownHostInvocationProbePolicy.RuntimeMethod(
                        OWNER, DESCRIPTOR, "loader:other")), "loader mismatch must be denied");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true, candidate(true, true, true),
                new RoiUnknownHostInvocationProbePolicy.RuntimeMethod(
                        OWNER, DESCRIPTOR.replace("Request;", "Other;"), SCOPE)),
                "descriptor mismatch must be denied");
        check(!RoiUnknownHostInvocationProbePolicy.allows(true,
                new RoiUnknownHostInvocationProbePolicy.Candidate(OWNER, "send", "send(?)",
                        SCOPE, RoiUnknownHostInvocationProbePolicy.Role.HTTP_TRANSPORT,
                        true, true, true), runtime), "incomplete descriptor must be denied");
    }

    private static RoiUnknownHostInvocationProbePolicy.Candidate candidate(
            boolean exact, boolean role, boolean graph) {
        return new RoiUnknownHostInvocationProbePolicy.Candidate(OWNER, "send", DESCRIPTOR, SCOPE,
                RoiUnknownHostInvocationProbePolicy.Role.HTTP_TRANSPORT, exact, role, graph);
    }

    private static void testSwitchIsExactAndDefaultOff() throws Exception {
        File file = File.createTempFile("roi-invocation-probe", ".flag");
        try {
            check(!RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled(file),
                    "empty switch must be off");
            write(file, "ENABLE\n");
            check(RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled(file),
                    "exact ENABLE token must turn switch on");
            write(file, "true\n");
            check(!RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled(file),
                    "truthy alias must be rejected");
            write(file, "ENABLE\nEXTRA\n");
            check(!RoiUnknownHostInvocationProbePolicy.isExplicitlyEnabled(file),
                    "extra content must be rejected");
        } finally {
            file.delete();
        }
    }

    private static void testProbeExactlyOnceAndPayloadFree() throws Throwable {
        RoiUnknownHostInvocationProbe probe = new RoiUnknownHostInvocationProbe();
        check(probe.register(DESCRIPTOR), "exact descriptor should register");
        final int[] calls = {0};
        Object marker = new Object();
        Object result = probe.invoke(DESCRIPTOR, new RoiUnknownHostInvocationProbe.ProceedingCall() {
            @Override public Object proceed() {
                calls[0]++;
                return marker;
            }
        });
        check(calls[0] == 1, "original call must execute exactly once");
        check(result == marker, "return object identity must be preserved");
        List<RoiUnknownHostInvocationProbe.Snapshot> snapshot = probe.snapshot();
        check(snapshot.size() == 1 && snapshot.get(0).invocationCount == 1,
                "successful invocation should be counted");
        check(snapshot.get(0).descriptor.equals(DESCRIPTOR),
                "only method descriptor should be retained");
        check(snapshot.get(0).exceptionCounts.isEmpty(),
                "successful invocation should not report exceptions");

        final int[] failedCalls = {0};
        final IllegalStateException expected = new IllegalStateException("sensitive message must not be retained");
        try {
            probe.invoke(DESCRIPTOR, new RoiUnknownHostInvocationProbe.ProceedingCall() {
                @Override public Object proceed() {
                    failedCalls[0]++;
                    throw expected;
                }
            });
            throw new AssertionError("original exception must propagate");
        } catch (IllegalStateException actual) {
            check(actual == expected, "original throwable identity must be preserved");
        }
        check(failedCalls[0] == 1, "throwing original call must execute exactly once");
        snapshot = probe.snapshot();
        check(snapshot.get(0).invocationCount == 2, "throwing invocation should be counted");
        check(snapshot.get(0).exceptionCounts.containsKey(IllegalStateException.class.getName()),
                "only exception class should be retained");
        check(!snapshot.get(0).exceptionCounts.containsKey(expected.getMessage()),
                "exception message must not be retained");
    }

    private static void testProbeBounds() {
        RoiUnknownHostInvocationProbe probe = new RoiUnknownHostInvocationProbe();
        for (int i = 0; i < RoiUnknownHostInvocationProbe.MAX_PROBED_METHODS; i++) {
            check(probe.register("Lx/y/C" + i + ";->m()V"), "probe slot should be available");
        }
        check(!probe.register("Lx/y/Overflow;->m()V"), "method probe cap must be enforced");
        check(probe.snapshot().size() == RoiUnknownHostInvocationProbe.MAX_PROBED_METHODS,
                "snapshot must remain bounded");
        probe.clear();
        check(probe.snapshot().isEmpty(), "clear must release probe state");
    }

    private static void write(File file, String value) throws Exception {
        FileWriter writer = new FileWriter(file);
        try { writer.write(value); } finally { writer.close(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
