package ds.dsh.DeepSeek;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Fail-closed gate for diagnostic invocation probes on an unmaintained host.
 * Static role hints and class-load matches are not sufficient: callers must supply
 * independently verified role and call-graph evidence for the exact runtime method.
 */
final class RoiUnknownHostInvocationProbePolicy {
    static final String ENABLE_TOKEN = "ENABLE";

    enum Role {
        REQUEST_CONSTRUCTION,
        HTTP_TRANSPORT,
        RESPONSE_DECODING,
        UNRESOLVED
    }

    static final class Candidate {
        final String owner;
        final String methodName;
        final String descriptor;
        final String sourceScope;
        final Role role;
        final boolean exactRuntimeMatch;
        final boolean roleVerified;
        final boolean callGraphConfirmed;
        final boolean staticRoleHintPresent;
        final boolean staticCallGraphObserved;

        Candidate(String owner, String methodName, String descriptor, String sourceScope,
                  Role role, boolean exactRuntimeMatch, boolean roleVerified,
                  boolean callGraphConfirmed) {
            this(owner, methodName, descriptor, sourceScope, role, exactRuntimeMatch,
                    roleVerified, callGraphConfirmed, false, false);
        }

        Candidate(String owner, String methodName, String descriptor, String sourceScope,
                  Role role, boolean exactRuntimeMatch, boolean roleVerified,
                  boolean callGraphConfirmed, boolean staticRoleHintPresent,
                  boolean staticCallGraphObserved) {
            this.owner = clean(owner);
            this.methodName = clean(methodName);
            this.descriptor = clean(descriptor);
            this.sourceScope = clean(sourceScope);
            this.role = role == null ? Role.UNRESOLVED : role;
            this.exactRuntimeMatch = exactRuntimeMatch;
            this.roleVerified = roleVerified;
            this.callGraphConfirmed = callGraphConfirmed;
            this.staticRoleHintPresent = staticRoleHintPresent;
            this.staticCallGraphObserved = staticCallGraphObserved;
        }
    }

    static final class RuntimeMethod {
        final String owner;
        final String descriptor;
        final String sourceScope;

        RuntimeMethod(String owner, String descriptor, String sourceScope) {
            this.owner = clean(owner);
            this.descriptor = clean(descriptor);
            this.sourceScope = clean(sourceScope);
        }
    }

    private RoiUnknownHostInvocationProbePolicy() {}

    static boolean isExplicitlyEnabled(File switchFile) {
        if (switchFile == null || !switchFile.isFile()) return false;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(switchFile));
            String first = reader.readLine();
            String second = reader.readLine();
            return ENABLE_TOKEN.equals(first == null ? "" : first.trim())
                    && second == null;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Throwable ignored) {}
            }
        }
    }

    static boolean allows(boolean explicitOptIn, Candidate candidate, RuntimeMethod runtime) {
        if (!explicitOptIn || candidate == null || runtime == null) return false;
        if (!candidate.exactRuntimeMatch || !candidate.roleVerified
                || !candidate.callGraphConfirmed) return false;
        if (candidate.role == Role.UNRESOLVED) return false;
        if (candidate.owner.length() == 0 || candidate.methodName.length() == 0
                || candidate.sourceScope.length() == 0) return false;
        if (!isCompleteDexMethodDescriptor(candidate.descriptor)) return false;
        int ownerEnd = candidate.descriptor.indexOf(";->");
        if (!candidate.owner.equals(candidate.descriptor.substring(0, ownerEnd + 1))) return false;
        int methodStart = ownerEnd + 3;
        int methodEnd = candidate.descriptor.indexOf('(', methodStart);
        if (methodEnd <= methodStart
                || !candidate.methodName.equals(candidate.descriptor.substring(methodStart, methodEnd))) {
            return false;
        }
        return candidate.owner.equals(runtime.owner)
                && candidate.descriptor.equals(runtime.descriptor)
                && candidate.sourceScope.equals(runtime.sourceScope);
    }

    /**
     * Diagnostic-only probe gate. Static role/call-graph hints may trigger a transparent
     * pass-through observer after exact runtime method identity is confirmed; they never
     * satisfy the stronger functional-hook authorization gate above.
     */
    static boolean allowsProbe(boolean explicitOptIn, Candidate candidate, RuntimeMethod runtime) {
        if (!explicitOptIn || candidate == null || runtime == null) return false;
        if (!candidate.exactRuntimeMatch || !candidate.staticRoleHintPresent
                || !candidate.staticCallGraphObserved
                || candidate.role == Role.UNRESOLVED) return false;
        if (candidate.owner.length() == 0 || candidate.methodName.length() == 0
                || candidate.sourceScope.length() == 0
                || !isCompleteDexMethodDescriptor(candidate.descriptor)) return false;
        int ownerEnd = candidate.descriptor.indexOf(";->");
        if (ownerEnd < 0 || !candidate.owner.equals(candidate.descriptor.substring(0, ownerEnd + 1))) {
            return false;
        }
        int methodStart = ownerEnd + 3;
        int methodEnd = candidate.descriptor.indexOf('(', methodStart);
        if (methodEnd <= methodStart
                || !candidate.methodName.equals(candidate.descriptor.substring(methodStart, methodEnd))) {
            return false;
        }
        return candidate.owner.equals(runtime.owner)
                && candidate.descriptor.equals(runtime.descriptor)
                && candidate.sourceScope.equals(runtime.sourceScope);
    }

    private static boolean isCompleteDexMethodDescriptor(String value) {
        int ownerEnd = value.indexOf(";->");
        if (ownerEnd <= 1 || value.charAt(0) != 'L') return false;
        String owner = value.substring(0, ownerEnd + 1);
        if (owner.indexOf(' ') >= 0 || owner.indexOf('[') >= 0) return false;
        int open = value.indexOf('(', ownerEnd + 3);
        int close = value.indexOf(')', open < 0 ? 0 : open + 1);
        if (open <= ownerEnd + 3 || close < open) return false;
        String method = value.substring(ownerEnd + 3, open);
        if (method.length() == 0 || method.indexOf(';') >= 0 || method.indexOf('/') >= 0) {
            return false;
        }
        int cursor = open + 1;
        while (cursor < close) {
            int next = typeDescriptorEnd(value, cursor, false);
            if (next <= cursor || next > close) return false;
            cursor = next;
        }
        if (cursor != close) return false;
        int returnEnd = typeDescriptorEnd(value, close + 1, true);
        return returnEnd == value.length();
    }

    private static int typeDescriptorEnd(String value, int start, boolean allowVoid) {
        if (start < 0 || start >= value.length()) return -1;
        int cursor = start;
        while (cursor < value.length() && value.charAt(cursor) == '[') cursor++;
        if (cursor >= value.length()) return -1;
        char kind = value.charAt(cursor);
        if ("ZBSCIJFD".indexOf(kind) >= 0) return cursor + 1;
        if (kind == 'V') return allowVoid && cursor == start ? cursor + 1 : -1;
        if (kind != 'L') return -1;
        int semicolon = value.indexOf(';', cursor + 1);
        if (semicolon <= cursor + 1) return -1;
        String className = value.substring(cursor + 1, semicolon);
        if (className.indexOf('.') >= 0 || className.indexOf('[') >= 0
                || className.indexOf(' ') >= 0) return -1;
        return semicolon + 1;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
