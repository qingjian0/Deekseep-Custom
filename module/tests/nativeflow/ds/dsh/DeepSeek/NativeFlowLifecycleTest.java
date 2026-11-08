package ds.dsh.DeepSeek;

public final class NativeFlowLifecycleTest {
    public static void main(String[] args) {
        testSynchronousReturnIsNotFlowCompletion();
        testSuspensionAndResumeAreSeparateFromTerminalSignal();
        testContinuationFailureIsNotPromotedToFlowFailure();
        testInvalidTransitionsDoNotMutateState();
        testTerminalSignalsRemainDistinct();
        System.out.println("NativeFlowLifecycle regression checks passed");
    }

    private static void testSynchronousReturnIsNotFlowCompletion() {
        NativeFlowLifecycle flow = new NativeFlowLifecycle();
        flow.collectionStarted();
        flow.callbackEntered();
        flow.callbackReturned(false);
        NativeFlowLifecycle.Snapshot state = flow.snapshot();
        equal(NativeFlowLifecycle.Phase.COLLECTING, state.phase, "sync return phase");
        equal(0, state.activeCallbacks, "sync return active callbacks");
        equal(0, state.pendingContinuations, "sync return pending continuations");
        flow.flowCompleted();
        equal(NativeFlowLifecycle.Phase.COMPLETED, flow.snapshot().phase, "explicit completion");
    }

    private static void testSuspensionAndResumeAreSeparateFromTerminalSignal() {
        NativeFlowLifecycle flow = new NativeFlowLifecycle();
        flow.collectionStarted();
        flow.callbackEntered();
        flow.callbackReturned(true);
        NativeFlowLifecycle.Snapshot suspended = flow.snapshot();
        equal(NativeFlowLifecycle.Phase.COLLECTING, suspended.phase, "suspended phase");
        equal(1, suspended.pendingContinuations, "pending suspension");
        expectFailure(new Runnable() { public void run() { flow.flowCompleted(); } },
                "completion with pending continuation");
        flow.continuationResumed(true);
        NativeFlowLifecycle.Snapshot resumed = flow.snapshot();
        equal(NativeFlowLifecycle.Phase.COLLECTING, resumed.phase, "resume is not terminal");
        equal(0, resumed.pendingContinuations, "resume clears pending suspension");
        flow.flowCompleted();
        equal(NativeFlowLifecycle.Phase.COMPLETED, flow.snapshot().phase, "terminal after completion signal");
    }

    private static void testContinuationFailureIsNotPromotedToFlowFailure() {
        NativeFlowLifecycle flow = new NativeFlowLifecycle();
        flow.collectionStarted();
        flow.callbackEntered();
        flow.callbackReturned(true);
        flow.continuationResumed(false);
        NativeFlowLifecycle.Snapshot state = flow.snapshot();
        equal(NativeFlowLifecycle.Phase.COLLECTING, state.phase, "failed resume remains nonterminal");
        equal(1L, state.continuationFailures, "continuation failure count");
        flow.flowFailed();
        equal(NativeFlowLifecycle.Phase.FAILED, flow.snapshot().phase, "explicit Flow failure");
    }

    private static void testInvalidTransitionsDoNotMutateState() {
        final NativeFlowLifecycle flow = new NativeFlowLifecycle();
        expectFailure(new Runnable() { public void run() { flow.callbackEntered(); } }, "callback before collection");
        equal(NativeFlowLifecycle.Phase.NEW, flow.snapshot().phase, "phase after rejected callback");
        flow.collectionStarted();
        expectFailure(new Runnable() { public void run() { flow.callbackReturned(false); } }, "return without entry");
        equal(0, flow.snapshot().callbacksReturned, "return count after rejected return");
        expectFailure(new Runnable() { public void run() { flow.continuationResumed(true); } }, "resume without suspension");
        equal(0, flow.snapshot().continuationsResumed, "resume count after rejected resume");
    }

    private static void testTerminalSignalsRemainDistinct() {
        NativeFlowLifecycle failed = new NativeFlowLifecycle();
        failed.collectionStarted();
        failed.flowFailed();
        equal(NativeFlowLifecycle.Phase.FAILED, failed.snapshot().phase, "failed terminal");
        expectFailure(new Runnable() { public void run() { failed.flowCancelled(); } }, "second terminal signal");

        NativeFlowLifecycle cancelled = new NativeFlowLifecycle();
        cancelled.collectionStarted();
        cancelled.flowCancelled();
        equal(NativeFlowLifecycle.Phase.CANCELLED, cancelled.snapshot().phase, "cancelled terminal");
    }

    private static void expectFailure(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label + " should fail");
        } catch (IllegalStateException expected) {
            // Expected fail-closed transition.
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void equal(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
