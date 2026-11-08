package ds.dsh.DeepSeek;

/**
 * Pure lifecycle accounting for a single collected Flow.
 *
 * This class is deliberately not wired to Xposed hooks. A synchronous suspend-method return
 * is not treated as continuation completion or Flow termination. Callers must report each
 * lifecycle signal from an independently verified observation point.
 */
final class NativeFlowLifecycle {
    enum Phase { NEW, COLLECTING, COMPLETED, FAILED, CANCELLED }

    static final class Snapshot {
        final Phase phase;
        final int activeCallbacks;
        final int pendingContinuations;
        final long callbacksEntered;
        final long callbacksReturned;
        final long suspendedCallbacks;
        final long continuationsResumed;
        final long continuationFailures;

        Snapshot(Phase phase, int activeCallbacks, int pendingContinuations,
                long callbacksEntered, long callbacksReturned, long suspendedCallbacks,
                long continuationsResumed, long continuationFailures) {
            this.phase = phase;
            this.activeCallbacks = activeCallbacks;
            this.pendingContinuations = pendingContinuations;
            this.callbacksEntered = callbacksEntered;
            this.callbacksReturned = callbacksReturned;
            this.suspendedCallbacks = suspendedCallbacks;
            this.continuationsResumed = continuationsResumed;
            this.continuationFailures = continuationFailures;
        }
    }

    private Phase phase = Phase.NEW;
    private int activeCallbacks;
    private int pendingContinuations;
    private long callbacksEntered;
    private long callbacksReturned;
    private long suspendedCallbacks;
    private long continuationsResumed;
    private long continuationFailures;

    synchronized void collectionStarted() {
        require(phase == Phase.NEW, "collection can start only once");
        phase = Phase.COLLECTING;
    }

    synchronized void callbackEntered() {
        require(phase == Phase.COLLECTING, "callback requires an active collection");
        activeCallbacks++;
        callbacksEntered++;
    }

    /**
     * Records the synchronous return from an observed callback boundary.
     * suspended=true means the boundary returned a suspension marker; it does not mean
     * that the continuation has resumed or that the Flow has completed.
     */
    synchronized void callbackReturned(boolean suspended) {
        require(phase == Phase.COLLECTING, "callback return requires an active collection");
        require(activeCallbacks > 0, "callback return has no matching entry");
        activeCallbacks--;
        callbacksReturned++;
        if (suspended) {
            pendingContinuations++;
            suspendedCallbacks++;
        }
    }

    /**
     * Records a separately observed continuation resume. A failed resume is not itself
     * promoted to Flow failure; the Flow terminal signal must be observed independently.
     */
    synchronized void continuationResumed(boolean success) {
        require(phase == Phase.COLLECTING, "continuation resume requires an active collection");
        require(pendingContinuations > 0, "continuation resume has no pending suspension");
        pendingContinuations--;
        continuationsResumed++;
        if (!success) continuationFailures++;
    }

    synchronized void flowCompleted() {
        require(phase == Phase.COLLECTING, "Flow completion requires an active collection");
        require(activeCallbacks == 0, "Flow cannot complete while a callback is active");
        require(pendingContinuations == 0, "Flow cannot complete with pending continuations");
        phase = Phase.COMPLETED;
    }

    synchronized void flowFailed() {
        terminate(Phase.FAILED);
    }

    synchronized void flowCancelled() {
        terminate(Phase.CANCELLED);
    }

    private void terminate(Phase terminal) {
        require(phase == Phase.COLLECTING, "terminal signal requires an active collection");
        phase = terminal;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(phase, activeCallbacks, pendingContinuations, callbacksEntered,
                callbacksReturned, suspendedCallbacks, continuationsResumed,
                continuationFailures);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
