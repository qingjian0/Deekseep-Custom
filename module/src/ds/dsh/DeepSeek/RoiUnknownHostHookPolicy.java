package ds.dsh.DeepSeek;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Explicit opt-in gate for experimental ROI observation on an unmaintained host.
 * This gate never approves a feature adapter or authorizes a functional hook.
 */
final class RoiUnknownHostHookPolicy {
    static final String ENABLE_TOKEN = "ENABLE";

    private RoiUnknownHostHookPolicy() {}

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

    static boolean allowsMetadataObservation(boolean maintainedHost, File switchFile) {
        return maintainedHost || isExplicitlyEnabled(switchFile);
    }

    static boolean authorizesFunctionalHook(boolean maintainedHost, File switchFile) {
        // Unknown-host opt-in opens only the bounded metadata observation lane.
        // Functional Hook authorization still requires a reviewed adapter and resolver proof.
        return false;
    }
}
