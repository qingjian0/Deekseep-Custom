package ds.dsh.DeepSeek;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Exact host identities for a thin base APK that passively loads the universal module APK. */
final class RuntimeEmbeddingMode {
    private static final String HOST = RuntimeEmbeddingPolicy.HOST_PACKAGE;

    private static volatile boolean passiveHostLoader;

    private RuntimeEmbeddingMode() {}

    private static volatile String sLastFailureReason = "unknown";

    static String getLastFailureReason() {
        return sLastFailureReason;
    }

    static boolean activatePassiveHostLoader(Context context) {
        if (!isExactSupportedHost(context)) return false;
        passiveHostLoader = true;
        return true;
    }

    static boolean isPassiveHostLoader() {
        // HostCompat is initialized by Main before any component routing occurs. Keeping the
        // exact version check here prevents an embedded-loader marker from leaking into an older
        // host path when the same universal module APK is reused by a patching framework.
        return passiveHostLoader && (HostCompat.isV236() || HostCompat.isV241());
    }

    static String componentPackage(String normalModulePackage) {
        return isPassiveHostLoader() ? HOST : normalModulePackage;
    }

    private static boolean isExactSupportedHost(Context context) {
        if (context == null) {
            sLastFailureReason = "context_null";
            return false;
        }
        if (!HOST.equals(context.getPackageName())) {
            sLastFailureReason = "pkg_mismatch:" + context.getPackageName();
            return false;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(HOST, 0);
            if (info == null) {
                sLastFailureReason = "info_null";
                return false;
            }
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            String failure = RuntimeEmbeddingPolicy.failureReason(
                    info.versionName, code,
                    HostCompat.isV236(), HostCompat.isV241(),
                    HostCompat.isGooglePlay(), HostCompat.isV250());
            if (failure != null) {
                sLastFailureReason = failure;
                return false;
            }
            return true;
        } catch (Throwable t) {
            sLastFailureReason = "exception:" + HostRemap.simpleName(t.getClass()) + ":" + t.getMessage();
            return false;
        }
    }
}
