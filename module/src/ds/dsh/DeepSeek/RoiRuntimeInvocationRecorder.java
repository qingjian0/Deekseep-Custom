package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded, payload-free runtime observations from explicitly trusted existing callbacks.
 * This class does not install hooks, inspect host objects, or infer evidence from static hints.
 */
final class RoiRuntimeInvocationRecorder {
    enum Phase { REQUEST_BUILT, TRANSPORT_ENTERED, RESPONSE_RECEIVED, RESPONSE_PARSED }
    enum Source { TRUSTED_EXISTING_CALLBACK }

    static final class Policy {
        final boolean hostIdentityApproved;
        final boolean trustedObservationEnabled;
        final boolean readOnly;

        private Policy(boolean hostIdentityApproved, boolean trustedObservationEnabled,
                       boolean readOnly) {
            this.hostIdentityApproved = hostIdentityApproved;
            this.trustedObservationEnabled = trustedObservationEnabled;
            this.readOnly = readOnly;
        }

        static Policy disabled() { return new Policy(false, false, true); }

        static Policy trustedKnownHost(boolean hostIdentityApproved,
                                       boolean trustedObservationEnabled) {
            return new Policy(hostIdentityApproved, trustedObservationEnabled, false);
        }

        boolean allowsRecording() {
            return !readOnly && hostIdentityApproved && trustedObservationEnabled;
        }
    }

    static final class Event {
        final String requestKey;
        final String hostFingerprint;
        final String candidateDescriptor;
        final Phase phase;
        final long timestampMillis;
        final long threadId;
        final Source source;

        Event(String requestKey, String hostFingerprint, String candidateDescriptor,
              Phase phase, long timestampMillis, long threadId, Source source) {
            this.requestKey = clean(requestKey, 128);
            this.hostFingerprint = clean(hostFingerprint, 256);
            this.candidateDescriptor = clean(candidateDescriptor, 512);
            this.phase = phase;
            this.timestampMillis = timestampMillis;
            this.threadId = threadId;
            this.source = source;
        }

        boolean isValid() {
            return requestKey.length() > 0 && hostFingerprint.length() > 0
                    && candidateDescriptor.length() > 0 && phase != null
                    && timestampMillis > 0 && threadId >= 0
                    && source == Source.TRUSTED_EXISTING_CALLBACK;
        }
    }

    static final class Chain {
        final String requestKey;
        final String hostFingerprint;
        final Set<String> requestBuilders;
        final Set<String> transports;
        final Set<String> responseReceivers;
        final Set<String> responseParsers;
        final long firstTimestampMillis;
        final long lastTimestampMillis;

        Chain(String requestKey, String hostFingerprint, Set<String> requestBuilders,
              Set<String> transports, Set<String> responseReceivers,
              Set<String> responseParsers, long firstTimestampMillis,
              long lastTimestampMillis) {
            this.requestKey = requestKey;
            this.hostFingerprint = hostFingerprint;
            this.requestBuilders = immutable(requestBuilders);
            this.transports = immutable(transports);
            this.responseReceivers = immutable(responseReceivers);
            this.responseParsers = immutable(responseParsers);
            this.firstTimestampMillis = firstTimestampMillis;
            this.lastTimestampMillis = lastTimestampMillis;
        }

        boolean confirmsRequestTransportResponse() {
            return !requestBuilders.isEmpty() && !transports.isEmpty()
                    && !responseReceivers.isEmpty();
        }
    }

    private final Policy policy;
    private final int capacity;
    private final long ttlMillis;
    private final List<Event> events = new ArrayList<Event>();

    RoiRuntimeInvocationRecorder(Policy policy, int capacity, long ttlMillis) {
        if (policy == null || capacity < 1 || ttlMillis < 1) {
            throw new IllegalArgumentException("invalid recorder configuration");
        }
        this.policy = policy;
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
    }

    synchronized boolean record(Event event, long nowMillis) {
        if (!policy.allowsRecording() || event == null || !event.isValid()
                || nowMillis <= 0 || event.timestampMillis > nowMillis
                || nowMillis - event.timestampMillis > ttlMillis) return false;
        prune(nowMillis);
        while (events.size() >= capacity) events.remove(0);
        events.add(event);
        return true;
    }

    synchronized List<Chain> snapshotChains(long nowMillis) {
        if (nowMillis <= 0 || !policy.allowsRecording()) return Collections.emptyList();
        prune(nowMillis);
        List<Chain> chains = new ArrayList<Chain>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Event seed : events) {
            String key = seed.hostFingerprint + "\u0000" + seed.requestKey;
            if (!seen.add(key)) continue;
            Set<String> builders = new LinkedHashSet<String>();
            Set<String> transports = new LinkedHashSet<String>();
            Set<String> receivers = new LinkedHashSet<String>();
            Set<String> parsers = new LinkedHashSet<String>();
            long first = Long.MAX_VALUE;
            long last = 0;
            for (Event event : events) {
                if (!event.hostFingerprint.equals(seed.hostFingerprint)
                        || !event.requestKey.equals(seed.requestKey)) continue;
                first = Math.min(first, event.timestampMillis);
                last = Math.max(last, event.timestampMillis);
                switch (event.phase) {
                    case REQUEST_BUILT: builders.add(event.candidateDescriptor); break;
                    case TRANSPORT_ENTERED: transports.add(event.candidateDescriptor); break;
                    case RESPONSE_RECEIVED: receivers.add(event.candidateDescriptor); break;
                    case RESPONSE_PARSED: parsers.add(event.candidateDescriptor); break;
                    default: break;
                }
            }
            chains.add(new Chain(seed.requestKey, seed.hostFingerprint, builders,
                    transports, receivers, parsers, first, last));
        }
        return Collections.unmodifiableList(chains);
    }

    synchronized int size(long nowMillis) {
        prune(nowMillis);
        return events.size();
    }

    synchronized void clear() { events.clear(); }

    private void prune(long nowMillis) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (nowMillis < event.timestampMillis
                    || nowMillis - event.timestampMillis > ttlMillis) events.remove(i);
        }
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String result = value.trim();
        return result.length() > maxLength ? "" : result;
    }

    private static Set<String> immutable(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(source));
    }
}
