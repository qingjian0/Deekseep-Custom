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

        Candidate(String owner, String methodName, String descriptor, String sourceScope,
                  Role role, boolean exactRuntimeMatch, boolean roleVerified,
                  boolean callGraphConfirmed) {
            this.owner = clean(owner);
            this.methodName = clean(methodName);
            this.descriptor = clean(descriptor);
            this.sourceScope = clean(sourceScope);
            this.role = role == null ? Role.UNRESOLVED : role;
            this.exactRuntimeMatch = exactRuntimeMatch;
            this.roleVerified = roleVerified;
            this.callGraphConfirmed = callGraphConfirmed;
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
        return candidate.owner.equals(runtime.owner)
                && candidate.descriptor.equals(runtime.descriptor)
                && candidate.sourceScope.equals(runtime.sourceScope);
    }

    private static boolean isCompleteDexMethodDescriptor(String value) {
        int ownerEnd = value.indexOf(";->");
        int open = value.indexOf('(', ownerEnd < 0 ? 0 : ownerEnd + 3);
        int close = value.indexOf(')', open < 0 ? 0 : open + 1);
        if (ownerEnd <= 1 || open <= ownerEnd + 3 || close < open) return false;
        if (value.charAt(0) != 'L' || value.indexOf(' ', 0) >= 0) return false;
        if (value.indexOf(';', ownerEnd + 1) >= 0) return false;
        String method = value.substring(ownerEnd + 3, open);
        if (method.length() == 0) return false;
        String result = value.substring(close + 1);
        if (result.length() == 0) return false;
        return isTypeDescriptor(result);
    }

    private static boolean isTypeDescriptor(String value) {
        if ("VZBSCIJFD".contains(value) && value.length() == 1) return true;
        int index = 0;
        while (index < value.length() && value.charAt(index) == '[') index++;
        if (index >= value.length()) return false;
        String type = value.substring(index);
        if (type.length() == 1) return "ZBSCIJFD".contains(type);
        return type.charAt(0) == 'L' && type.endsWith(";")
                && type.indexOf(';') == type.length() - 1;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
