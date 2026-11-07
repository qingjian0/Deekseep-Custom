package ds.dsh.DeepSeek;

import java.util.Locale;

/**
 * Immutable installed-host build identity used by the reviewed Hook activation boundary.
 * A content digest alone is insufficient: package/version/channel, signer and loader scope
 * are part of the identity tuple.
 */
final class RoiHostBuildIdentity {
    final String packageName;
    final long versionCode;
    final String versionName;
    final String channel;
    final String artifactSetSha256;
    final String signerCertificateSha256;
    final String classLoaderScope;

    private RoiHostBuildIdentity(String packageName, long versionCode, String versionName,
            String channel, String artifactSetSha256, String signerCertificateSha256,
            String classLoaderScope) {
        this.packageName = safe(packageName);
        this.versionCode = versionCode;
        this.versionName = safe(versionName);
        this.channel = safe(channel);
        this.artifactSetSha256 = normalizeDigest(artifactSetSha256);
        this.signerCertificateSha256 = normalizeDigest(signerCertificateSha256);
        this.classLoaderScope = safe(classLoaderScope);
    }

    static RoiHostBuildIdentity create(String packageName, long versionCode, String versionName,
            String channel, RoiHostArtifactFingerprint.Result artifacts,
            String signerCertificateSha256, String classLoaderScope) {
        String artifactDigest = artifacts != null && artifacts.verified
                ? artifacts.aggregateSha256 : "";
        return new RoiHostBuildIdentity(packageName, versionCode, versionName, channel,
                artifactDigest, signerCertificateSha256, classLoaderScope);
    }

    boolean isComplete() {
        return "com.deepseek.chat".equals(packageName)
                && versionCode > 0L
                && versionName.length() > 0
                && channel.length() > 0
                && isSha256(artifactSetSha256)
                && isSha256(signerCertificateSha256)
                && classLoaderScope.length() > 0;
    }

    boolean matches(RoiHostBuildIdentity other) {
        return other != null && isComplete() && other.isComplete()
                && packageName.equals(other.packageName)
                && versionCode == other.versionCode
                && versionName.equals(other.versionName)
                && channel.equals(other.channel)
                && artifactSetSha256.equals(other.artifactSetSha256)
                && signerCertificateSha256.equals(other.signerCertificateSha256)
                && classLoaderScope.equals(other.classLoaderScope);
    }

    private static String normalizeDigest(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
