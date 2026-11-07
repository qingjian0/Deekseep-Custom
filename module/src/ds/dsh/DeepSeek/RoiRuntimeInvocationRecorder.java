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
    enum CoverageStatus { RESPONSE_OBSERVED, UNSUPPORTED_SUSPEND_COLLECTOR, UNSUPPORTED_COLLECTOR_TYPE, UNSUPPORTED_NON_SUSPEND_COLLECTOR, UNSUPPORTED_RESPONSE_PARSER_CORRELATION }

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
        final String sourceScope;
        final String candidateDescriptor;
        final Phase phase;
        final long timestampMillis;
        final long threadId;
        final Source source;

        Event(String requestKey, String hostFingerprint, String candidateDescriptor,
              Phase phase, long timestampMillis, long threadId, Source source) {
            this(requestKey, hostFingerprint, "loader:test", candidateDescriptor, phase,
                    timestampMillis, threadId, source);
        }

        Event(String requestKey, String hostFingerprint, String sourceScope,
              String candidateDescriptor, Phase phase, long timestampMillis,
              long threadId, Source source) {
            this.requestKey = clean(requestKey, 128);
            this.hostFingerprint = clean(hostFingerprint, 256);
            this.sourceScope = clean(sourceScope, 256);
            this.candidateDescriptor = clean(candidateDescriptor, 512);
            this.phase = phase;
            this.timestampMillis = timestampMillis;
            this.threadId = threadId;
            this.source = source;
        }

        boolean isValid() {
            return requestKey.length() > 0 && hostFingerprint.length() > 0
                    && sourceScope.length() > 0 && candidateDescriptor.length() > 0 && phase != null
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
        final boolean orderedRequestTransportResponse;
        final boolean orderedParsedResponse;

        Chain(String requestKey, String hostFingerprint, Set<String> requestBuilders,
              Set<String> transports, Set<String> responseReceivers,
              Set<String> responseParsers, long firstTimestampMillis,
              long lastTimestampMillis, boolean orderedRequestTransportResponse,
              boolean orderedParsedResponse) {
            this.requestKey = requestKey;
            this.hostFingerprint = hostFingerprint;
            this.requestBuilders = immutable(requestBuilders);
            this.transports = immutable(transports);
            this.responseReceivers = immutable(responseReceivers);
            this.responseParsers = immutable(responseParsers);
            this.firstTimestampMillis = firstTimestampMillis;
            this.lastTimestampMillis = lastTimestampMillis;
            this.orderedRequestTransportResponse = orderedRequestTransportResponse;
            this.orderedParsedResponse = orderedParsedResponse;
        }

        boolean confirmsRequestTransportResponse() {
            return orderedRequestTransportResponse;
        }

        boolean confirmsParsedResponse() {
            return orderedParsedResponse;
        }
    }

    /** Sanitized per-descriptor evidence; deliberately excludes request keys. */
    static final class EvidenceSnapshot {
        final String adapterIdentity;
        final String sourceScope;
        final String candidateDescriptor;
        final Phase phase;
        final boolean orderedRequestTransportResponse;
        final boolean orderedParsedResponse;

        EvidenceSnapshot(String adapterIdentity, String sourceScope,
                         String candidateDescriptor, Phase phase,
                         boolean orderedRequestTransportResponse,
                         boolean orderedParsedResponse) {
            this.adapterIdentity = adapterIdentity;
            this.sourceScope = sourceScope;
            this.candidateDescriptor = candidateDescriptor;
            this.phase = phase;
            this.orderedRequestTransportResponse = orderedRequestTransportResponse;
            this.orderedParsedResponse = orderedParsedResponse;
        }
    }

    /** Payload-free explanation of a response-observation capability boundary. */
    static final class CoverageSnapshot {
        final String adapterIdentity;
        final String sourceScope;
        final String candidateDescriptor;
        final CoverageStatus status;
        final long timestampMillis;

        CoverageSnapshot(String adapterIdentity, String sourceScope, String candidateDescriptor,
                         CoverageStatus status, long timestampMillis) {
            this.adapterIdentity = adapterIdentity;
            this.sourceScope = sourceScope;
            this.candidateDescriptor = candidateDescriptor;
            this.status = status;
            this.timestampMillis = timestampMillis;
        }
    }

    private final Policy policy;
    private final int capacity;
    private final long ttlMillis;
    private final List<Event> events = new ArrayList<Event>();
    private final List<CoverageSnapshot> coverage = new ArrayList<CoverageSnapshot>();

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
            int builtIndex = -1;
            int transportIndex = -1;
            int receivedIndex = -1;
            int parsedIndex = -1;
            long builtAt = Long.MAX_VALUE;
            long transportAt = Long.MAX_VALUE;
            long receivedAt = Long.MAX_VALUE;
            String builtScope = "";
            String transportScope = "";
            for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {
                Event event = events.get(eventIndex);
                if (!event.hostFingerprint.equals(seed.hostFingerprint)
                        || !event.requestKey.equals(seed.requestKey)) continue;
                first = Math.min(first, event.timestampMillis);
                last = Math.max(last, event.timestampMillis);
                switch (event.phase) {
                    case REQUEST_BUILT:
                        builders.add(event.candidateDescriptor);
                        if (builtIndex < 0) {
                            builtIndex = eventIndex;
                            builtAt = event.timestampMillis;
                            builtScope = event.sourceScope;
                        }
                        break;
                    case TRANSPORT_ENTERED:
                        transports.add(event.candidateDescriptor);
                        if (builtIndex >= 0 && transportIndex < 0
                                && builtScope.equals(event.sourceScope)
                                && eventIndex > builtIndex && event.timestampMillis >= builtAt) {
                            transportIndex = eventIndex;
                            transportAt = event.timestampMillis;
                            transportScope = event.sourceScope;
                        }
                        break;
                    case RESPONSE_RECEIVED:
                        receivers.add(event.candidateDescriptor);
                        if (transportIndex >= 0 && receivedIndex < 0
                                && transportScope.equals(event.sourceScope)
                                && eventIndex > transportIndex
                                && event.timestampMillis >= transportAt) {
                            receivedIndex = eventIndex;
                            receivedAt = event.timestampMillis;
                        }
                        break;
                    case RESPONSE_PARSED:
                        parsers.add(event.candidateDescriptor);
                        if (receivedIndex >= 0 && parsedIndex < 0
                                && transportScope.equals(event.sourceScope)
                                && eventIndex > receivedIndex
                                && event.timestampMillis >= receivedAt) {
                            parsedIndex = eventIndex;
                        }
                        break;
                    default: break;
                }
            }
            boolean orderedResponse = builtIndex >= 0
                    && transportIndex > builtIndex && receivedIndex > transportIndex;
            boolean orderedParsed = orderedResponse && parsedIndex > receivedIndex;
            chains.add(new Chain(seed.requestKey, seed.hostFingerprint, builders,
                    transports, receivers, parsers, first, last,
                    orderedResponse, orderedParsed));
        }
        return Collections.unmodifiableList(chains);
    }

    synchronized List<EvidenceSnapshot> snapshotEvidence(long nowMillis) {
        if (nowMillis <= 0 || !policy.allowsRecording()) return Collections.emptyList();
        List<Chain> chains = snapshotChains(nowMillis);
        List<EvidenceSnapshot> result = new ArrayList<EvidenceSnapshot>();
        for (Chain chain : chains) {
            for (Event event : events) {
                if (!chain.hostFingerprint.equals(event.hostFingerprint)
                        || !chain.requestKey.equals(event.requestKey)) continue;
                result.add(new EvidenceSnapshot(event.hostFingerprint, event.sourceScope,
                        event.candidateDescriptor, event.phase,
                        chain.confirmsRequestTransportResponse(), chain.confirmsParsedResponse()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized boolean recordCoverage(String adapterIdentity, String sourceScope,
                                        String candidateDescriptor, CoverageStatus status,
                                        long nowMillis) {
        if (!policy.allowsRecording() || nowMillis <= 0 || adapterIdentity == null
                || adapterIdentity.length() == 0 || sourceScope == null || sourceScope.length() == 0
                || candidateDescriptor == null || candidateDescriptor.length() == 0
                || status == null) return false;
        prune(nowMillis);
        while (coverage.size() >= capacity) coverage.remove(0);
        coverage.add(new CoverageSnapshot(clean(adapterIdentity, 256), clean(sourceScope, 256),
                clean(candidateDescriptor, 512), status, nowMillis));
        return true;
    }

    synchronized List<CoverageSnapshot> snapshotCoverage(long nowMillis) {
        if (nowMillis <= 0 || !policy.allowsRecording()) return Collections.emptyList();
        prune(nowMillis);
        return Collections.unmodifiableList(new ArrayList<CoverageSnapshot>(coverage));
    }

    synchronized int size(long nowMillis) {
        prune(nowMillis);
        return events.size();
    }

    synchronized void clear() { events.clear(); coverage.clear(); }

    private void prune(long nowMillis) {
        for (int i = coverage.size() - 1; i >= 0; i--) {
            CoverageSnapshot item = coverage.get(i);
            if (nowMillis < item.timestampMillis
                    || nowMillis - item.timestampMillis > ttlMillis) coverage.remove(i);
        }
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
