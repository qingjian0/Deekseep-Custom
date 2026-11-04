package ds.dsh.DeepSeek;

/**
 * Per-scan soft budget for read-only DexKit discovery.
 * The deadline is checked before native queries and while iterating candidates.
 * It cannot interrupt a DexKit JNI query that has already started.
 */
final class RoiDiscoveryBudget {
    private final int maxQueries;
    private final int maxCandidates;
    private final long deadlineNanos;
    private int queriesStarted;
    private int candidatesAccepted;
    private String stopReason = "none";

    RoiDiscoveryBudget(int maxQueries, int maxCandidates, long deadlineNanos) {
        if (maxQueries < 0 || maxCandidates < 0) {
            throw new IllegalArgumentException("budget limits must be non-negative");
        }
        this.maxQueries = maxQueries;
        this.maxCandidates = maxCandidates;
        this.deadlineNanos = deadlineNanos;
    }

    synchronized boolean tryStartQuery(long nowNanos) {
        if (!canContinueLocked(nowNanos)) return false;
        if (queriesStarted >= maxQueries) {
            stopReason = "query_limit";
            return false;
        }
        queriesStarted++;
        return true;
    }

    synchronized boolean tryAcceptCandidate(long nowNanos) {
        if (!canContinueLocked(nowNanos)) return false;
        if (candidatesAccepted >= maxCandidates) {
            stopReason = "candidate_limit";
            return false;
        }
        candidatesAccepted++;
        return true;
    }

    synchronized boolean canContinue(long nowNanos) {
        return canContinueLocked(nowNanos);
    }

    private boolean canContinueLocked(long nowNanos) {
        if (!"none".equals(stopReason)) return false;
        if (nowNanos >= deadlineNanos) {
            stopReason = "soft_deadline";
            return false;
        }
        if (queriesStarted >= maxQueries) {
            stopReason = "query_limit";
            return false;
        }
        if (candidatesAccepted >= maxCandidates) {
            stopReason = "candidate_limit";
            return false;
        }
        return true;
    }

    synchronized boolean isExhausted(long nowNanos) {
        return !canContinueLocked(nowNanos);
    }

    synchronized int queriesStarted() { return queriesStarted; }
    synchronized int candidatesAccepted() { return candidatesAccepted; }
    synchronized String stopReason() { return stopReason; }
}
