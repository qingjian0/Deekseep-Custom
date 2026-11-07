package ds.dsh.DeepSeek;

public final class RoiHostBuildIdentityTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static String repeat(char value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 64; i++) out.append(value);
        return out.toString();
    }
    private static RoiHostBuildIdentity identity(String packageName, long versionCode,
            String versionName, String channel, String artifact, String signer, String scope) {
        RoiHostArtifactFingerprint.Result result = new RoiHostArtifactFingerprint.Result(
                true, repeat('a'), artifact, 2, 4096L, "none");
        return RoiHostBuildIdentity.create(packageName, versionCode, versionName, channel,
                result, signer, scope);
    }
    public static void main(String[] args) {
        check(RoiHostBuildIdentity.manifestVersionMatches("2.7.2", "2.7.2"),
                "matching PackageManager and base manifest versions should pass");
        check(!RoiHostBuildIdentity.manifestVersionMatches("2.7.2", "2.7.1"),
                "mismatched PackageManager and base manifest versions must fail closed");
        check(!RoiHostBuildIdentity.manifestVersionMatches("2.7.2", null),
                "missing base manifest version must fail closed");
        check(!RoiHostBuildIdentity.manifestVersionMatches(null, "2.7.2"),
                "missing PackageManager version must fail closed");
        check(!RoiHostBuildIdentity.manifestVersionMatches("", ""),
                "empty version metadata must fail closed");

        ClassLoader loader = RoiHostBuildIdentityTest.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(loader);
        RoiHostBuildIdentity baseline = identity("com.deepseek.chat", 42L,
                "2.7.2", "mainland", repeat('b'), repeat('c'), scope);
        check(baseline.isComplete(), "complete target identity should pass");
        String activationFingerprint = baseline.activationFingerprint();
        check(activationFingerprint.matches("[0-9a-f]{64}"),
                "complete host identity must produce a canonical SHA-256 activation fingerprint");
        check(activationFingerprint.equals(baseline.activationFingerprint()),
                "activation fingerprint must be deterministic");
        check(!activationFingerprint.equals(identity("com.deepseek.chat", 42L,
                "2.7.2", "mainland", repeat('d'), repeat('c'), scope).activationFingerprint()),
                "APK-set digest changes must change activation fingerprint");
        check(!activationFingerprint.equals(identity("com.deepseek.chat", 42L,
                "2.7.2", "mainland", repeat('b'), repeat('d'), scope).activationFingerprint()),
                "signer changes must change activation fingerprint");
        check(!activationFingerprint.equals(identity("com.deepseek.chat", 43L,
                "2.7.2", "mainland", repeat('b'), repeat('c'), scope).activationFingerprint()),
                "Android version-code changes must change activation fingerprint");
        check(!identity("com.example.fake", 42L, "2.7.2", "mainland",
                repeat('b'), repeat('c'), scope).isComplete(), "wrong package must fail closed");
        check(!identity("com.deepseek.chat", 0L, "2.7.2", "mainland",
                repeat('b'), repeat('c'), scope).isComplete(), "zero version code must fail closed");
        check(!identity("com.deepseek.chat", 42L, "", "mainland",
                repeat('b'), repeat('c'), scope).isComplete(), "missing version name must fail closed");
        check(!identity("com.deepseek.chat", 42L, "2.7.2", "",
                repeat('b'), repeat('c'), scope).isComplete(), "missing channel must fail closed");
        check(!identity("com.deepseek.chat", 42L, "2.7.2", "mainland",
                "not-a-digest", repeat('c'), scope).isComplete(), "malformed APK digest must fail closed");
        check(!identity("com.deepseek.chat", 42L, "2.7.2", "mainland",
                repeat('b'), "not-a-digest", scope).isComplete(), "malformed signer digest must fail closed");

        RoiHostBuildIdentity otherLoader = identity("com.deepseek.chat", 42L,
                "2.7.2", "mainland", repeat('b'), repeat('c'),
                RoiCandidateIdentity.forClassLoader(new ClassLoader(loader) {}));
        check(!baseline.matches(otherLoader), "ClassLoader scope mismatch must fail closed");
        RoiHostBuildIdentity otherSigner = identity("com.deepseek.chat", 42L,
                "2.7.2", "mainland", repeat('b'), repeat('d'), scope);
        check(!baseline.matches(otherSigner), "signer mismatch must fail closed");
        System.out.println("ROI host build identity tests passed");
    }
}
