package ds.dsh.DeepSeek;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Android-side capture of the currently installed host identity.
 *
 * <p>Call only from a worker thread: this reads PackageManager metadata and hashes the installed
 * base/split APK set. A null result means the identity is incomplete and must deny activation.</p>
 */
final class RoiAndroidHostIdentity {
    private RoiAndroidHostIdentity() {}

    static RoiHostBuildIdentity capture(Context context, ClassLoader loader, String channel) {
        if (context == null || loader == null || channel == null || channel.trim().isEmpty()) {
            return null;
        }
        try {
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            ApplicationInfo appInfo = app.getApplicationInfo();
            if (appInfo == null || !"com.deepseek.chat".equals(appInfo.packageName)
                    || appInfo.sourceDir == null || appInfo.sourceDir.length() == 0) return null;

            PackageManager manager = app.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo packageInfo = manager.getPackageInfo(appInfo.packageName, flags);
            if (packageInfo == null || !appInfo.packageName.equals(packageInfo.packageName)) return null;

            RoiHostArtifactFingerprint.Result artifacts =
                    RoiHostArtifactFingerprint.inspect(appInfo.sourceDir, appInfo.splitSourceDirs);
            if (!artifacts.verified) return null;

            String signerDigest = signerSetSha256(packageInfo);
            if (signerDigest.length() != 64) return null;

            long versionCode = Build.VERSION.SDK_INT >= 28
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            String versionName = packageInfo.versionName;
            RoiHostBuildIdentity identity = RoiHostBuildIdentity.create(
                    appInfo.packageName, versionCode, versionName, channel, artifacts,
                    signerDigest, RoiCandidateIdentity.forClassLoader(loader));
            return identity.isComplete() ? identity : null;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static String signerSetSha256(PackageInfo info) throws Exception {
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return "";
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return "";

        List<String> digests = new ArrayList<String>();
        for (Signature signature : signatures) {
            if (signature == null) return "";
            digests.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        }
        Collections.sort(digests);
        MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
        aggregate.update("roi-host-signers-v1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String digest : digests) {
            aggregate.update(digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            aggregate.update((byte) '\n');
        }
        return hex(aggregate.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned < 16) out.append('0');
            out.append(Integer.toHexString(unsigned));
        }
        return out.toString();
    }
}
