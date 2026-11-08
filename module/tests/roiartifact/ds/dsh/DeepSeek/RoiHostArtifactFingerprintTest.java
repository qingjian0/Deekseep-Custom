package ds.dsh.DeepSeek;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class RoiHostArtifactFingerprintTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static File write(File dir, String name, String value) throws Exception {
        File file = new File(dir, name);
        FileOutputStream output = new FileOutputStream(file);
        try { output.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { output.close(); }
        return file;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "roi-artifact-fingerprint-" + System.nanoTime());
        check(dir.mkdirs(), "temporary fixture directory was not created");
        try {
            File base = write(dir, "base.apk", "base-content-v1");
            File splitA = write(dir, "split_config.arm64.apk", "arm64-content");
            File splitB = write(dir, "split_config.xxhdpi.apk", "density-content");

            RoiHostArtifactFingerprint.Result first = RoiHostArtifactFingerprint.inspect(
                    base.getAbsolutePath(),
                    new String[] { splitA.getAbsolutePath(), splitB.getAbsolutePath() });
            check(first.verified, "complete base + split set must hash");
            check(first.baseSha256.length() == 64, "base SHA-256 must be 64 hex chars");
            check(first.aggregateSha256.length() == 64, "aggregate SHA-256 must be 64 hex chars");
            check(first.splitCount == 2, "both split APKs must be included");
            check(first.totalBytes > 0L, "total bytes must be recorded");

            RoiHostArtifactFingerprint.Result reordered = RoiHostArtifactFingerprint.inspect(
                    base.getAbsolutePath(),
                    new String[] { splitB.getAbsolutePath(), splitA.getAbsolutePath() });
            check(reordered.verified, "reordered split set must hash");
            check(first.aggregateSha256.equals(reordered.aggregateSha256),
                    "split ordering must not change the aggregate identity");

            write(dir, "split_config.arm64.apk", "arm64-content-changed");
            RoiHostArtifactFingerprint.Result changed = RoiHostArtifactFingerprint.inspect(
                    base.getAbsolutePath(),
                    new String[] { splitA.getAbsolutePath(), splitB.getAbsolutePath() });
            check(changed.verified, "changed but readable split should hash");
            check(!first.aggregateSha256.equals(changed.aggregateSha256),
                    "changing any split content must change the aggregate identity");

            RoiHostArtifactFingerprint.Result missingBase =
                    RoiHostArtifactFingerprint.inspect(new File(dir, "missing.apk").getAbsolutePath(), null);
            check(!missingBase.verified && "apk_read_failed".equals(missingBase.reason),
                    "missing base APK must fail closed");

            RoiHostArtifactFingerprint.Result missingSplit = RoiHostArtifactFingerprint.inspect(
                    base.getAbsolutePath(), new String[] { new File(dir, "missing-split.apk").getAbsolutePath() });
            check(!missingSplit.verified && "apk_read_failed".equals(missingSplit.reason),
                    "missing split APK must fail closed");

            RoiHostArtifactFingerprint.Result duplicateSplit = RoiHostArtifactFingerprint.inspect(
                    base.getAbsolutePath(), new String[] {
                        splitA.getAbsolutePath(), new File(dir, "nested/../split_config.arm64.apk").getAbsolutePath()
                    });
            check(!duplicateSplit.verified && "duplicate_apk_path".equals(duplicateSplit.reason),
                    "duplicate canonical APK paths must fail closed");

            check(first.toReport().contains("artifactIdentity.algorithm=SHA-256"),
                    "diagnostic report must declare the digest algorithm");
            System.out.println("ROI host artifact fingerprint tests passed");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) file.delete();
            dir.delete();
        }
    }
}
