package com.dsmod.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local state for independently installed feature-hook groups.
 *
 * <p>The registry deliberately tracks installer execution, not reflected members. An installer
 * should represent one stable, idempotent feature boundary. A FAILED/DEGRADED boundary may be
 * retried; INSTALLED and SKIPPED boundaries are terminal for the lifetime of this process.</p>
 */
final class FeatureHookInstallRegistry {
    enum State {
        NEW,
        INSTALLING,
        INSTALLED,
        SKIPPED,
        DEGRADED,
        FAILED
    }

    interface Installer {
        void install() throws Throwable;
    }

    static final class Status {
        final String id;
        final State state;
        final int attempts;
        final long startedAtMillis;
        final long completedAtMillis;
        final String detail;

        Status(String id, State state, int attempts, long startedAtMillis,
                long completedAtMillis, String detail) {
            this.id = id;
            this.state = state;
            this.attempts = attempts;
            this.startedAtMillis = startedAtMillis;
            this.completedAtMillis = completedAtMillis;
            this.detail = detail;
        }
    }

    private static final class Entry {
        State state = State.NEW;
        int attempts;
        long startedAtMillis;
        long completedAtMillis;
        String detail;
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Runs an installer once per stable ID. A failed installer is eligible for another attempt.
     * The entry lock is held across installation so concurrent callers cannot double-register it.
     */
    void install(String id, Installer installer) throws Throwable {
        requireId(id);
        if (installer == null) throw new IllegalArgumentException("installer == null");

        Entry entry = entries.computeIfAbsent(id, ignored -> new Entry());
        synchronized (entry) {
            if (entry.state == State.INSTALLED || entry.state == State.SKIPPED) return;
            if (entry.state == State.INSTALLING) return;

            entry.state = State.INSTALLING;
            entry.attempts++;
            entry.startedAtMillis = System.currentTimeMillis();
            entry.completedAtMillis = 0L;
            entry.detail = null;

            try {
                installer.install();
                entry.state = State.INSTALLED;
                entry.completedAtMillis = System.currentTimeMillis();
            } catch (Throwable failure) {
                entry.state = State.FAILED;
                entry.completedAtMillis = System.currentTimeMillis();
                entry.detail = describe(failure);
                throw failure;
            }
        }
    }

    /** Records an explicit host/version gate; skipped hooks are not retried in this process. */
    void markSkipped(String id, String reason) {
        requireId(id);
        Entry entry = entries.computeIfAbsent(id, ignored -> new Entry());
        synchronized (entry) {
            if (entry.state == State.INSTALLED) return;
            entry.state = State.SKIPPED;
            entry.completedAtMillis = System.currentTimeMillis();
            entry.detail = reason == null ? "" : reason;
        }
    }

    /** Records a best-effort installer that returned control but could not complete fully. */
    void markDegraded(String id, Throwable failure) {
        requireId(id);
        Entry entry = entries.computeIfAbsent(id, ignored -> new Entry());
        synchronized (entry) {
            if (entry.state == State.INSTALLED || entry.state == State.SKIPPED) return;
            entry.state = State.DEGRADED;
            entry.completedAtMillis = System.currentTimeMillis();
            entry.detail = failure == null ? "best-effort installation incomplete" : describe(failure);
        }
    }

    boolean hasFailures() {
        for (Entry entry : entries.values()) {
            synchronized (entry) {
                if (entry.state == State.FAILED || entry.state == State.DEGRADED) return true;
            }
        }
        return false;
    }

    List<Status> snapshot() {
        ArrayList<Status> result = new ArrayList<>();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            Entry entry = item.getValue();
            synchronized (entry) {
                result.add(new Status(item.getKey(), entry.state, entry.attempts,
                        entry.startedAtMillis, entry.completedAtMillis, entry.detail));
            }
        }
        Collections.sort(result, (left, right) -> left.id.compareTo(right.id));
        return Collections.unmodifiableList(result);
    }

    private static void requireId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("hook installation ID must not be blank");
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
        return value.length() > 320 ? value.substring(0, 320) : value;
    }
}
