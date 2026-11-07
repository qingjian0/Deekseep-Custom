package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

public final class RoiRuntimeInvocationRecorderTest {
    private static final String HOST = "apk-fingerprint-known";
    private static final String KEY = "opaque-request-1";

    public static void main(String[] args) throws Exception {
        testReadOnlyAndUnapprovedHostsReject();
        testCompleteChainAcrossThreads();
        testMissingPhaseDoesNotConfirm();
        testOutOfOrderPhasesDoNotConfirm();
        testParsedPhaseRequiresOrderedReceipt();
        testSameMillisecondPhasesRemainPossible();
        testSameMillisecondOutOfOrderPhasesDoNotConfirm();
        testTimestampRegressionDoesNotConfirm();
        testSameRequestKeyAcrossHostsDoesNotMerge();
        testSanitizedEvidenceSnapshot();
        testSameDescriptorDifferentLoaderScopesRemainDistinct();
        testCrossLoaderPhasesDoNotConfirm();
        testDisabledAndExpiredEvidenceSnapshotsAreEmpty();
        testCoverageDiagnosticsAreBoundedSanitizedAndExpire();
        testDisabledCoverageIsNotReported();
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

    private static void testOutOfOrderPhasesDoNotConfirm() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LResp;->receive()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 100, 1), 100);
        r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 110, 2), 110);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 120, 3), 120);
        List<RoiRuntimeInvocationRecorder.Chain> chains = r.snapshotChains(120);
        check(!chains.get(0).confirmsRequestTransportResponse(),
                "out-of-order phases incorrectly confirmed");
    }

    private static void testParsedPhaseRequiresOrderedReceipt() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 110, 2), 110);
        r.record(event(KEY, "LParser;->parse()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_PARSED, 120, 3), 120);
        check(!r.snapshotChains(120).get(0).confirmsParsedResponse(),
                "parser event before receipt incorrectly confirmed");
        r.record(event(KEY, "LResp;->receive()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 130, 4), 130);
        check(r.snapshotChains(130).get(0).confirmsRequestTransportResponse(),
                "ordered response chain not confirmed");
        check(!r.snapshotChains(130).get(0).confirmsParsedResponse(),
                "out-of-order parser event incorrectly confirmed");
        r.record(event(KEY, "LParser;->parse()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_PARSED, 140, 5), 140);
        check(r.snapshotChains(140).get(0).confirmsParsedResponse(),
                "ordered parser phase not confirmed");
    }

    private static void testSameMillisecondPhasesRemainPossible() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 100, 2), 100);
        r.record(event(KEY, "LResp;->receive()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 100, 3), 100);
        check(r.snapshotChains(100).get(0).confirmsRequestTransportResponse(),
                "same-millisecond callbacks should not be rejected due to clock granularity");
    }

    private static void testSameMillisecondOutOfOrderPhasesDoNotConfirm() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LResp;->receive()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 100, 1), 100);
        r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 100, 2), 100);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 3), 100);
        check(!r.snapshotChains(100).get(0).confirmsRequestTransportResponse(),
                "same-millisecond out-of-order callbacks incorrectly confirmed");
    }

    private static void testTimestampRegressionDoesNotConfirm() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        r.record(event(KEY, "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 99, 2), 100);
        r.record(event(KEY, "LResp;->receive()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 101, 3), 101);
        check(!r.snapshotChains(101).get(0).confirmsRequestTransportResponse(),
                "timestamp-regressing transport incorrectly confirmed");
    }

    private static void testSameRequestKeyAcrossHostsDoesNotMerge() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        r.record(new RoiRuntimeInvocationRecorder.Event(KEY, "other-approved-host",
                "LNet;->execute()V", RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED,
                110, 2, RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 110);
        List<RoiRuntimeInvocationRecorder.Chain> chains = r.snapshotChains(110);
        check(chains.size() == 2, "same request key merged across host identities");
        for (RoiRuntimeInvocationRecorder.Chain chain : chains) {
            check(!chain.confirmsRequestTransportResponse(),
                    "cross-host phases incorrectly confirmed a request chain");
        }
    }

    private static void testSanitizedEvidenceSnapshot() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        String privateKey = "secret-request-key-must-not-escape";
        String adapter = "adapter:generation-27:mainland";
        r.record(new RoiRuntimeInvocationRecorder.Event(privateKey, adapter, "loader:process-1:HostLoader",
                "LReq;->build()V", RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 100);
        r.record(new RoiRuntimeInvocationRecorder.Event(privateKey, adapter, "loader:process-1:HostLoader",
                "LNet;->execute()V", RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 110, 2,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 110);
        r.record(new RoiRuntimeInvocationRecorder.Event(privateKey, adapter, "loader:process-1:HostLoader",
                "LResp;->emit()V", RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 120, 3,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 120);
        List<RoiRuntimeInvocationRecorder.EvidenceSnapshot> snapshots = r.snapshotEvidence(120);
        check(snapshots.size() == 3, "expected one sanitized entry per observed descriptor");
        boolean exactResponse = false;
        for (Field field : RoiRuntimeInvocationRecorder.EvidenceSnapshot.class.getDeclaredFields()) {
            check(!field.getName().toLowerCase(java.util.Locale.ROOT).contains("requestkey"),
                    "snapshot type exposes a request-key field");
        }
        for (RoiRuntimeInvocationRecorder.EvidenceSnapshot item : snapshots) {
            check(adapter.equals(item.adapterIdentity), "adapter identity missing");
            check(item.sourceScope.startsWith("loader:process-"), "class-loader source scope missing");
            check(item.candidateDescriptor.length() > 0, "candidate descriptor missing");
            check(item.orderedRequestTransportResponse, "ordered chain flag missing");
            check(item.candidateDescriptor.indexOf(privateKey) < 0,
                    "request key leaked through descriptor");
            if ("LResp;->emit()V".equals(item.candidateDescriptor)
                    && item.phase == RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED) {
                exactResponse = true;
            }
        }
        check(exactResponse, "exact response descriptor not present");
        try {
            snapshots.clear();
            throw new AssertionError("snapshot list is mutable");
        } catch (UnsupportedOperationException expected) {
            // immutable snapshot contract
        }
    }

    private static void testSameDescriptorDifferentLoaderScopesRemainDistinct() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        String adapter = "adapter:generation-27:mainland";
        String descriptor = "LShared;->execute()V";
        r.record(new RoiRuntimeInvocationRecorder.Event("key-a", adapter,
                "loader:process-1:HostLoader", descriptor,
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 100, 1,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 100);
        r.record(new RoiRuntimeInvocationRecorder.Event("key-b", adapter,
                "loader:process-2:HostLoader", descriptor,
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 101, 2,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 101);
        List<RoiRuntimeInvocationRecorder.EvidenceSnapshot> snapshots = r.snapshotEvidence(101);
        check(snapshots.size() == 2, "same descriptor across loaders was collapsed");
        check(!snapshots.get(0).sourceScope.equals(snapshots.get(1).sourceScope),
                "loader scopes were merged");
    }

    private static void testCrossLoaderPhasesDoNotConfirm() {
        RoiRuntimeInvocationRecorder r = recorder(8, 1000);
        String key = "cross-loader-request";
        String adapter = "adapter:generation-27:mainland";
        r.record(new RoiRuntimeInvocationRecorder.Event(key, adapter,
                "loader:process-1:HostLoader", "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 100);
        r.record(new RoiRuntimeInvocationRecorder.Event(key, adapter,
                "loader:process-2:HostLoader", "LNet;->execute()V",
                RoiRuntimeInvocationRecorder.Phase.TRANSPORT_ENTERED, 110, 2,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 110);
        r.record(new RoiRuntimeInvocationRecorder.Event(key, adapter,
                "loader:process-1:HostLoader", "LResp;->emit()V",
                RoiRuntimeInvocationRecorder.Phase.RESPONSE_RECEIVED, 120, 3,
                RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 120);
        check(!r.snapshotChains(120).get(0).confirmsRequestTransportResponse(),
                "cross-loader phases incorrectly confirmed one scoped chain");
    }

    private static void testDisabledAndExpiredEvidenceSnapshotsAreEmpty() {
        RoiRuntimeInvocationRecorder disabled = new RoiRuntimeInvocationRecorder(
                RoiRuntimeInvocationRecorder.Policy.disabled(), 8, 1000);
        check(disabled.snapshotEvidence(100).isEmpty(),
                "disabled recorder exposed runtime evidence");
        RoiRuntimeInvocationRecorder r = recorder(8, 50);
        r.record(event(KEY, "LReq;->build()V",
                RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT, 100, 1), 100);
        check(r.snapshotEvidence(151).isEmpty(),
                "expired runtime evidence remained visible");
    }

    private static void testCoverageDiagnosticsAreBoundedSanitizedAndExpire() {
        RoiRuntimeInvocationRecorder r = recorder(3, 50);
        check(r.recordCoverage(HOST, "loader:process-1:HostLoader", "Lb41;->b(Lq03;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
                RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_SUSPEND_COLLECTOR, 100),
                "unsupported suspend coverage rejected");
        check(r.recordCoverage(HOST, "loader:process-1:HostLoader", "LParser;->rebuild(Ljava/lang/Object;)Lmv;",
                RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_RESPONSE_PARSER_CORRELATION, 101),
                "uncorrelated parser coverage rejected");
        check(r.recordCoverage(HOST, "loader:process-1:HostLoader", "Lb41;->b(Lq03;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
                RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_NON_SUSPEND_COLLECTOR, 102),
                "unsupported non-suspend coverage rejected");
        List<RoiRuntimeInvocationRecorder.CoverageSnapshot> items = r.snapshotCoverage(102);
        check(items.size() == 3, "coverage diagnostics missing");
        check(items.get(0).status
                == RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_SUSPEND_COLLECTOR,
                "unsupported suspend status lost");
        check(items.get(1).status
                == RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_RESPONSE_PARSER_CORRELATION,
                "uncorrelated parser status lost");
        check(items.get(2).status
                == RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_NON_SUSPEND_COLLECTOR,
                "unsupported non-suspend status lost");
        for (Field field : RoiRuntimeInvocationRecorder.CoverageSnapshot.class.getDeclaredFields()) {
            check(!field.getName().toLowerCase(java.util.Locale.ROOT).contains("requestkey"),
                    "coverage diagnostics expose request key");
        }
        try {
            items.clear();
            throw new AssertionError("coverage snapshot is mutable");
        } catch (UnsupportedOperationException expected) {
            // immutable coverage contract
        }
        check(r.snapshotCoverage(153).isEmpty(), "expired coverage diagnostics remained visible");
        r.recordCoverage(HOST, "loader:process-1:HostLoader", "LFlow;->emit()V",
                RoiRuntimeInvocationRecorder.CoverageStatus.RESPONSE_OBSERVED, 200);
        r.clear();
        check(r.snapshotCoverage(200).isEmpty(), "clear did not reset coverage diagnostics");
    }

    private static void testDisabledCoverageIsNotReported() {
        RoiRuntimeInvocationRecorder disabled = new RoiRuntimeInvocationRecorder(
                RoiRuntimeInvocationRecorder.Policy.disabled(), 8, 1000);
        check(!disabled.recordCoverage(HOST, "loader:process-1:HostLoader", "LFlow;->emit()V",
                RoiRuntimeInvocationRecorder.CoverageStatus.UNSUPPORTED_SUSPEND_COLLECTOR, 100),
                "disabled recorder accepted coverage diagnostic");
        check(disabled.snapshotCoverage(100).isEmpty(),
                "disabled recorder exposed coverage diagnostics");
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
        check(!r.record(new RoiRuntimeInvocationRecorder.Event(KEY, HOST, "",
                "LX;->x()V", RoiRuntimeInvocationRecorder.Phase.REQUEST_BUILT,
                10, 1, RoiRuntimeInvocationRecorder.Source.TRUSTED_EXISTING_CALLBACK), 10),
                "empty source scope accepted");
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
