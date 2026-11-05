package ds.dsh.DeepSeek;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded metadata-only sink for classes returned by an explicitly enabled host-loader callback.
 * It never asks a loader to resolve a class and never retains Class or ClassLoader references.
 */
final class RoiClassLoadObserver {
    static final int DEFAULT_MAX_EVENTS = 128;
    static final int DEFAULT_MAX_EVENTS_PER_WINDOW = 16;
    static final long DEFAULT_WINDOW_MILLIS = 1000L;
    static final long DEFAULT_TTL_MILLIS = 120000L;
    static final int MAX_LOADER_DEPTH = 16;

    static final class EventSnapshot {
        final String className;
        final String classDescriptor;
        final String sourceScope;
        final String definingLoaderType;
        final String superclassName;
        final List<String> interfaces;
        final List<String> methods;
        final List<String> fields;
        final int methodCountReported;
        final int fieldCountReported;
        final boolean methodsTruncated;
        final boolean fieldsTruncated;
        final String inspectionStatus;
        final long observedAtMillis;

        EventSnapshot(RoiLoadedClassInspector.Snapshot source,
                      String definingLoaderType, long observedAtMillis) {
            this.className = source.className;
            this.classDescriptor = source.classDescriptor;
            this.sourceScope = source.loaderScope;
            this.definingLoaderType = definingLoaderType;
            this.superclassName = source.superclassName;
            this.interfaces = immutable(source.interfaces);
            this.methods = immutable(source.methods);
            this.fields = immutable(source.fields);
            this.methodCountReported = source.methodCountReported;
            this.fieldCountReported = source.fieldCountReported;
            this.methodsTruncated = source.methodsTruncated;
            this.fieldsTruncated = source.fieldsTruncated;
            this.inspectionStatus = source.inspectionStatus;
            this.observedAtMillis = observedAtMillis;
        }

        String toReport() {
            StringBuilder out = new StringBuilder(512);
            out.append("class=").append(className)
                    .append(" descriptor=").append(classDescriptor)
                    .append(" sourceScope=").append(sourceScope)
                    .append(" loaderType=").append(definingLoaderType)
                    .append(" superclass=").append(superclassName)
                    .append(" interfaces=").append(interfaces)
                    .append(" methodCount=").append(methodCountReported)
                    .append(" methodsTruncated=").append(methodsTruncated)
                    .append(" methods=").append(methods)
                    .append(" fieldCount=").append(fieldCountReported)
                    .append(" fieldsTruncated=").append(fieldsTruncated)
                    .append(" fields=").append(fields)
                    .append(" status=").append(inspectionStatus);
            return out.toString();
        }
    }

    private final int maxEvents;
    private final int maxEventsPerWindow;
    private final long windowMillis;
    private final long ttlMillis;
    private final WeakHashMap<Class<?>, Long> seen =
            new WeakHashMap<Class<?>, Long>();
    private final ArrayDeque<EventSnapshot> events =
            new ArrayDeque<EventSnapshot>();
    private long windowStartedAt = Long.MIN_VALUE;
    private int windowEventCount;

    RoiClassLoadObserver() {
        this(DEFAULT_MAX_EVENTS, DEFAULT_MAX_EVENTS_PER_WINDOW,
                DEFAULT_WINDOW_MILLIS, DEFAULT_TTL_MILLIS);
    }

    RoiClassLoadObserver(int maxEvents, int maxEventsPerWindow,
                         long windowMillis, long ttlMillis) {
        if (maxEvents < 1 || maxEventsPerWindow < 1
                || windowMillis < 1L || ttlMillis < 1L) {
            throw new IllegalArgumentException("observer bounds must be positive");
        }
        this.maxEvents = maxEvents;
        this.maxEventsPerWindow = maxEventsPerWindow;
        this.windowMillis = windowMillis;
        this.ttlMillis = ttlMillis;
    }

    synchronized EventSnapshot observe(ClassLoader definingLoader, Class<?> loadedClass,
                                       ClassLoader hostRoot, boolean adapterApproved,
                                       boolean optInEnabled, long nowMillis) {
        prune(nowMillis);
        if (!adapterApproved || !optInEnabled || definingLoader == null
                || loadedClass == null || hostRoot == null) return null;
        if (!isWithinHostScope(definingLoader, hostRoot)) return null;

        try {
            if (loadedClass.getClassLoader() != definingLoader) return null;
            if (seen.containsKey(loadedClass)) return null;
            if (!admitRate(nowMillis)) return null;

            RoiLoadedClassInspector.Snapshot metadata =
                    RoiLoadedClassInspector.inspect(loadedClass);
            EventSnapshot event = new EventSnapshot(metadata,
                    definingLoader.getClass().getName(), nowMillis);
            seen.put(loadedClass, Long.valueOf(nowMillis));
            while (events.size() >= maxEvents) events.removeFirst();
            events.addLast(event);
            return event;
        } catch (LinkageError | SecurityException error) {
            // Observation is fail-open: callers must preserve the original class-load result.
            return null;
        }
    }

    synchronized List<EventSnapshot> snapshot(long nowMillis) {
        prune(nowMillis);
        return Collections.unmodifiableList(new ArrayList<EventSnapshot>(events));
    }

    synchronized void clear() {
        seen.clear();
        events.clear();
        windowStartedAt = Long.MIN_VALUE;
        windowEventCount = 0;
    }

    static boolean isWithinHostScope(ClassLoader candidate, ClassLoader hostRoot) {
        if (candidate == null || hostRoot == null) return false;
        ClassLoader current = candidate;
        for (int depth = 0; current != null && depth < MAX_LOADER_DEPTH; depth++) {
            if (current == hostRoot) return true;
            try {
                current = current.getParent();
            } catch (SecurityException denied) {
                return false;
            }
        }
        return false;
    }

    private boolean admitRate(long nowMillis) {
        if (windowStartedAt == Long.MIN_VALUE
                || nowMillis < windowStartedAt
                || nowMillis - windowStartedAt >= windowMillis) {
            windowStartedAt = nowMillis;
            windowEventCount = 0;
        }
        if (windowEventCount >= maxEventsPerWindow) return false;
        windowEventCount++;
        return true;
    }

    private void prune(long nowMillis) {
        while (!events.isEmpty()) {
            EventSnapshot first = events.peekFirst();
            if (nowMillis >= first.observedAtMillis
                    && nowMillis - first.observedAtMillis > ttlMillis) {
                events.removeFirst();
            } else {
                break;
            }
        }
        Iterator<Map.Entry<Class<?>, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            Long timestamp = iterator.next().getValue();
            if (timestamp == null || (nowMillis >= timestamp.longValue()
                    && nowMillis - timestamp.longValue() > ttlMillis)) {
                iterator.remove();
            }
        }
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
