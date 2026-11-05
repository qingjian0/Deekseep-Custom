package ds.dsh.DeepSeek;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public final class RoiUnknownHostHookPolicyTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("roi-unknown-host-policy").toFile();
        File toggle = new File(dir, "unknown-host-hook");
        check(!RoiUnknownHostHookPolicy.isExplicitlyEnabled(toggle), "missing defaults off");
        check(!RoiUnknownHostHookPolicy.allowsMetadataObservation(false, toggle),
                "unknown host defaults read-only");
        write(toggle, "");
        check(!RoiUnknownHostHookPolicy.isExplicitlyEnabled(toggle), "empty file stays off");
        write(toggle, "true");
        check(!RoiUnknownHostHookPolicy.isExplicitlyEnabled(toggle), "truthy alias rejected");
        write(toggle, "ENABLE\n");
        check(RoiUnknownHostHookPolicy.isExplicitlyEnabled(toggle), "explicit token enables");
        check(RoiUnknownHostHookPolicy.allowsMetadataObservation(false, toggle),
                "unknown opt-in allows metadata observation");
        check(!RoiUnknownHostHookPolicy.authorizesFunctionalHook(false, toggle),
                "unknown opt-in never authorizes functional hooks");
        check(RoiUnknownHostHookPolicy.allowsMetadataObservation(true, null),
                "maintained observation remains separately gated by its caller");
        write(toggle, "ENABLE\nextra\n");
        check(!RoiUnknownHostHookPolicy.isExplicitlyEnabled(toggle),
                "extra content rejected");
        System.out.println("ROI unknown-host hook policy tests passed");
    }

    private static void write(File file, String value) throws Exception {
        FileWriter writer = new FileWriter(file, false);
        try { writer.write(value); } finally { writer.close(); }
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
