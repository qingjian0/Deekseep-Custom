package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded diagnostic-only invocation recorder. It never sees arguments or return values.
 * A caller must first pass RoiUnknownHostInvocationProbePolicy before registering a method.
 */
final class RoiUnknownHostInvocationProbe {
    static final int MAX_PROBED_METHODS = 8;
    static final int MAX_EVENTS_PER_SECOND = 16;
    static final int MAX_EXCEPTION_TYPES = 8;

    interface ProceedingCall {
        Object proceed() throws Throwable;
    }

    static final class Snapshot {
        final String descriptor;
        final long invocationCount;
        final long totalElapsedNanos;
        final long maxElapsedNanos;
        final Map<String, Long> exceptionCounts;

        Snapshot(String descriptor, long invocationCount, long totalElapsedNanos,
                 long maxElapsedNanos, Map<String, Long> exceptionCounts) {
            this.descriptor = descriptor;
            this.invocationCount = invocationCount;
            this.totalElapsedNanos = totalElapsedNanos;
            this.maxElapsedNanos = maxElapsedNanos;
            this.exceptionCounts = Collections.unmodifiableMap(
                    new LinkedHashMap<String, Long>(exceptionCounts));
        }
    }

    private static final class Aggregate {
        final String descriptor;
        Aggregate(String descriptor) { this.descriptor = descriptor; }
        long count;
        long totalNanos;
        long maxNanos;
        final Map<String, Long> exceptions = new LinkedHashMap<String, Long>();
    }

    private final Set<String> approvedDescriptors = new LinkedHashSet<String>();
    private final Map<String, Aggregate> aggregates = new LinkedHashMap<String, Aggregate>();
    private long rateWindowStartNanos;
    private int rateWindowEvents;

    synchronized boolean register(String descriptor) {
        String value = clean(descriptor);
        return register(value, value);
    }

    synchronized boolean register(String key, String descriptor) {
        String identity = clean(key);
        String value = clean(descriptor);
        if (identity.length() == 0 || value.length() == 0) return false;
        if (approvedDescriptors.contains(identity)) return true;
        if (approvedDescriptors.size() >= MAX_PROBED_METHODS) return false;
        approvedDescriptors.add(identity);
        aggregates.put(identity, new Aggregate(value));
        return true;
    }

    /** The supplied call is invoked exactly once; observer failures never replace its result. */
    Object invoke(String descriptor, ProceedingCall call) throws Throwable {
        if (call == null) throw new IllegalArgumentException("call is required");
        long started = System.nanoTime();
        try {
            Object result = call.proceed();
            recordSafely(descriptor, elapsed(started), null, System.nanoTime());
            return result;
        } catch (Throwable failure) {
            recordSafely(descriptor, elapsed(started), failure.getClass().getName(), System.nanoTime());
            throw failure;
        }
    }

    private void recordSafely(String descriptor, long elapsedNanos, String exceptionType, long nowNanos) {
        try { record(descriptor, elapsedNanos, exceptionType, nowNanos); }
        catch (Throwable ignored) { /* Diagnostics must never replace the host result or failure. */ }
    }

    private synchronized void record(String descriptor, long elapsedNanos,
                                     String exceptionType, long nowNanos) {
        String value = clean(descriptor);
        Aggregate aggregate = aggregates.get(value);
        if (aggregate == null || nowNanos <= 0) return;
        if (rateWindowStartNanos == 0 || nowNanos - rateWindowStartNanos >= 1_000_000_000L
                || nowNanos < rateWindowStartNanos) {
            rateWindowStartNanos = nowNanos;
            rateWindowEvents = 0;
        }
        if (rateWindowEvents >= MAX_EVENTS_PER_SECOND) return;
        rateWindowEvents++;
        long safeElapsed = Math.max(0L, elapsedNanos);
        aggregate.count++;
        aggregate.totalNanos = saturatedAdd(aggregate.totalNanos, safeElapsed);
        aggregate.maxNanos = Math.max(aggregate.maxNanos, safeElapsed);
        if (exceptionType != null && aggregate.exceptions.size() < MAX_EXCEPTION_TYPES
                || (exceptionType != null && aggregate.exceptions.containsKey(exceptionType))) {
            Long previous = aggregate.exceptions.get(exceptionType);
            aggregate.exceptions.put(exceptionType, previous == null ? 1L : previous + 1L);
        }
    }

    synchronized boolean unregister(String key) {
        String identity = clean(key);
        approvedDescriptors.remove(identity);
        aggregates.remove(identity);
        return true;
    }

    synchronized List<Snapshot> snapshot() {
        List<Snapshot> result = new ArrayList<Snapshot>();
        for (Map.Entry<String, Aggregate> entry : aggregates.entrySet()) {
            Aggregate value = entry.getValue();
            result.add(new Snapshot(value.descriptor, value.count, value.totalNanos,
                    value.maxNanos, value.exceptions));
        }
        return Collections.unmodifiableList(result);
    }

    synchronized void clear() {
        approvedDescriptors.clear();
        aggregates.clear();
        rateWindowStartNanos = 0L;
        rateWindowEvents = 0;
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }

    private static long saturatedAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
