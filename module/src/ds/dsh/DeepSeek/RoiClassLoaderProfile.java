package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only classification of a live ClassLoader chain.
 *
 * <p>This deliberately identifies loader mechanisms, not encrypted DEX contents.
 * A custom loader can be a plugin loader, obfuscation wrapper, or encrypted-DEX
 * loader; its class name alone is never proof of encryption or authorization to hook.</p>
 */
final class RoiClassLoaderProfile {
    enum Kind {
        BOOTSTRAP,
        PATH_DEX,
        DEX,
        IN_MEMORY_DEX,
        DELEGATE_LAST,
        CUSTOM,
        OTHER
    }

    static final int MAX_CHAIN_DEPTH = 8;

    final List<String> chainClassNames;
    final List<Kind> kinds;
    final boolean dynamicDexLoaderObserved;
    final boolean inMemoryDexLoaderObserved;
    final boolean customLoaderObserved;
    final String encryptedDexAssessment;

    private RoiClassLoaderProfile(List<String> names, List<Kind> kinds) {
        this.chainClassNames = Collections.unmodifiableList(new ArrayList<String>(names));
        this.kinds = Collections.unmodifiableList(new ArrayList<Kind>(kinds));
        boolean dynamic = false;
        boolean inMemory = false;
        boolean custom = false;
        for (Kind kind : kinds) {
            if (kind == Kind.DEX || kind == Kind.PATH_DEX || kind == Kind.IN_MEMORY_DEX) {
                dynamic = true;
            }
            if (kind == Kind.IN_MEMORY_DEX) inMemory = true;
            if (kind == Kind.CUSTOM) custom = true;
        }
        this.dynamicDexLoaderObserved = dynamic;
        this.inMemoryDexLoaderObserved = inMemory;
        this.customLoaderObserved = custom;
        this.encryptedDexAssessment = custom
                ? "unconfirmed_custom_loader_requires_runtime_dex_evidence"
                : "not_observable_from_loader_type";
    }

    static RoiClassLoaderProfile inspect(ClassLoader loader) {
        List<String> names = new ArrayList<String>();
        List<Kind> kinds = new ArrayList<Kind>();
        if (loader == null) {
            names.add("bootstrap:null");
            kinds.add(Kind.BOOTSTRAP);
            return new RoiClassLoaderProfile(names, kinds);
        }
        ClassLoader current = loader;
        for (int depth = 0; current != null && depth < MAX_CHAIN_DEPTH; depth++) {
            String name = current.getClass().getName();
            names.add(name);
            kinds.add(classifyName(name));
            try {
                current = current.getParent();
            } catch (SecurityException denied) {
                names.add("parent:unavailable");
                kinds.add(Kind.OTHER);
                break;
            }
        }
        if (current != null && names.size() == MAX_CHAIN_DEPTH) {
            names.add("chain:truncated");
            kinds.add(Kind.OTHER);
        }
        return new RoiClassLoaderProfile(names, kinds);
    }

    static Kind classifyName(String name) {
        if (name == null || name.length() == 0) return Kind.OTHER;
        if ("dalvik.system.PathClassLoader".equals(name)) return Kind.PATH_DEX;
        if ("dalvik.system.DexClassLoader".equals(name)) return Kind.DEX;
        if ("dalvik.system.InMemoryDexClassLoader".equals(name)) return Kind.IN_MEMORY_DEX;
        if ("dalvik.system.DelegateLastClassLoader".equals(name)) return Kind.DELEGATE_LAST;
        if ("java.lang.BootClassLoader".equals(name)) return Kind.BOOTSTRAP;
        if (name.startsWith("java.") || name.startsWith("dalvik.")) return Kind.OTHER;
        return Kind.CUSTOM;
    }
}
