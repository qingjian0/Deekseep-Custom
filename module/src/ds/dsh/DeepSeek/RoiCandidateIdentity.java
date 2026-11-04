package ds.dsh.DeepSeek;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Process-local source identity for read-only ROI observations. */
final class RoiCandidateIdentity {
    private static final Object LOCK = new Object();
    private static final List<Entry> LOADERS = new ArrayList<Entry>();
    private static long nextId = 1L;

    private static final class Entry {
        final WeakReference<ClassLoader> reference;
        final long id;
        Entry(ClassLoader loader, long id) {
            this.reference = new WeakReference<ClassLoader>(loader);
            this.id = id;
        }
    }

    private RoiCandidateIdentity() {}

    static String forClassLoader(ClassLoader loader) {
        if (loader == null) return "loader:none";
        synchronized (LOCK) {
            long id = 0L;
            Iterator<Entry> it = LOADERS.iterator();
            while (it.hasNext()) {
                Entry entry = it.next();
                ClassLoader current = entry.reference.get();
                if (current == null) it.remove();
                else if (current == loader) id = entry.id;
            }
            if (id == 0L) {
                id = nextId++;
                LOADERS.add(new Entry(loader, id));
            }
            return "loader:process-" + id + ":" + loader.getClass().getName();
        }
    }

    static String forApkPath(String path) {
        if (path == null || path.trim().isEmpty()) return "apk:unknown";
        try {
            return "apk:" + new File(path).getCanonicalPath();
        } catch (Exception ignored) {
            return "apk:" + new File(path).getAbsolutePath();
        }
    }

    static String candidateId(String scope, String owner, String method, String descriptor) {
        return safe(scope) + "|" + safe(owner) + "->" + safe(method) + safe(descriptor);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
