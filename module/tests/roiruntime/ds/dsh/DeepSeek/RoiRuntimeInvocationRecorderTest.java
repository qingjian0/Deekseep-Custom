package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class RoiRuntimeInvocationRecorderTest {
    private static final String HOST = "apk-fingerprint-known";
    private static final String KEY = "opaque-request-1";

    public static void main(String[] args) throws Exception {
        testReadOnlyAndUnapprovedHostsReject();
        testCompleteChainAcrossThreads();
        testMissingPhaseDoesNotConfirm();
        testCapacityAndTtl();
        testInvalidAndWrongSourceRejected();
        testConcurrentRecording();
        System.out.println("RoiRuntimeInvocationRecorderTest: OK");
    }

    private static RoiRuntimeInvocationRecorder recorder(int cap, long ttl) {
        return new RoiRuntimeInvocationRecorder(
                RoiRuntimeInvocationRecorder.Policy.trustedKnownHost(true, true), cap, ttl);
    }

    private static RoiRuntimeInvocationRecorder.Event event(String key, String descriptor,
            RoiRuntimeInvocationRecorder.Phase phase, long time, long thread) {
        return new RoiRuntimeInvocationRecorder.Event(key, HOST, descriptor, phase, time,
                thread, RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK);
    }

    private static void testReadOnlyAndUnapprovedHostsReject() {
        RoiRuntimeInvocationRecorder readOnly = new RoiRuntimeInvocationRecorder(
                RoiRuntimeInvocationRecorder.Policy.disabled(), 8, 1000);
        check(!readOnly.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 10, 1), 10),
                "read-only policy accepted event");
        RoiRuntimeInvocationRecorder unapproved = new RoiRuntimeInvocationRecorder(
                RoiRuntimeInvocationRecorder.Policy.trustedKnownHost(false, true), 8, 1000);
        check(!unapproved.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 10, 1), 10),
                "unapproved host accepted event");
    }

    private static void testCompleteChainAcrossThreads() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        check(r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100), "request");
        check(r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 110, 2), 110), "transport");
        check(r.record(event(KEY, "LResp;->receive()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 120, 3), 120), "response");
        List<RoiRuntimeInvocationRecorder.Chain> chains = r.snapshotChains(120);
        check(chains.size() == 1, "expected one chain");
        check(chains.get(0).confirmsRequestTransportResponse(), "complete chain not confirmed");
        check(chains.get(0).requestBuilders.contains("LReq;->build()V"), "request descriptor lost");
        check(chains.get(0).transports.contains("LNet;->execute()V"), "transport descriptor lost");
    }

    private static void testMissingPhaseDoesNotConfirm() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 110, 1), 110);
        check(!r.snapshotChains(110).get(0).confirmsRequestTransportResponse(),
                "missing response phase confirmed");
    }

    private static void testCapacityAndTtl() {
        RoiRuntimeInvocationRecorder r = recorder(2, 50);
        r.record(event("a", "LA;->a()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        r.record(event("b", "LB;->b()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 101, 1), 101);
        r.record(event("c", "LC;->c()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 102, 1), 102);
        check(r.size(102) == 2, "capacity not enforced");
        check(r.size(153) == 0, "TTL did not expire events");
    }

    private static void testInvalidAndWrongSourceRejected() {
        RoiRuntimeInvocationRecorder r = recorder(4, 1000);
        RoiRuntimeInvocationRecorder.Event invalid = new RoiRuntimeInvocationRecorder.Event(
                "", HOST, "LX;->x()V", RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT,
                10, 1, RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK);
        check(!r.record(invalid, 10), "invalid request key accepted");
        check(!r.record(new RoiRuntimeInvocationRecorder.Event(KEY, HOST, "LX;->x()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 10, 1, null), 10),
                "untrusted source accepted");
    }

    private static void testConcurrentRecording() throws Exception {
        final RoiRuntimeInvocationRecorder r = recorder(512, 10000);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < 8; t++) {
            final int id = t;
            Thread thread = new Thread(new Runnable() {
                @Override public void run() {
                    try { start.await(); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); return;
                    }
                    for (int i = 0; i < 40; i++) {
                        String key = "r-" + id + "-" + i;
                        r.record(event(key, "LNet;->execute()V",
                                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED,
                                1000 + i, id), 1000 + i);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        check(r.size(1100) <= 512, "concurrent capacity overflow");
        check(r.size(1100) > 0, "concurrent events lost");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
