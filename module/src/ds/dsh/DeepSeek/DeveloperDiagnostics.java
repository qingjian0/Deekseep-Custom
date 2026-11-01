package ds.dsh.DeepSeek;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded, content-redacted diagnostics for compatibility and performance maintenance. */
final class DeveloperDiagnostics {
    private static final long STARTED_AT = SystemClock.elapsedRealtime();
    private static final DshDiagnosticsRecorder RECORDER = new DshDiagnosticsRecorder();

    private DeveloperDiagnostics() {}

    static void record(String raw) {
        RECORDER.record(raw);
    }

    static void showCompatibility(Activity activity) {
        StringBuilder report = new StringBuilder();
        report.append("YunSeek ").append(BuildInfo.MODULE_VERSION).append('\n');
        report.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            report.append("host=").append(info.versionName).append(" (").append(code).append(")\n");
        } catch (Throwable error) {
            report.append("host=unknown\n");
        }
        report.append(HostCompat.diagnosticSummary());
        showReport(activity, "兼容性诊断报告", report.toString());
    }

    static void showPerformance(Activity activity) {
        long uptime = Math.max(1L, SystemClock.elapsedRealtime() - STARTED_AT);
        String report = RECORDER.performanceSummary(uptime);
        report += "\n\n" + logStorageStatus(activity);
        showReport(activity, "Hook 性能统计", report);
    }

    /** Reads the module-owned dual-store counters through the authenticated provider. */
    private static String logStorageStatus(Context context) {
        try {
            Bundle status = context.getContentResolver().call(
                    Uri.parse("content://" + XposedActivationProvider.AUTHORITY),
                    XposedActivationProvider.METHOD_GET_DSH_LOG_STATUS, null, null);
            if (status == null || !status.getBoolean("accepted", false)) {
                return "logStorage=unavailable";
            }
            int privateDepth = status.getInt("private_queue_depth", -1);
            int privateCapacity = status.getInt("private_queue_capacity", -1);
            int publicDepth = status.getInt("public_queue_depth", -1);
            int publicCapacity = status.getInt("public_queue_capacity", -1);
            return "logStorage=active"
                    + "\nprivateWrites=" + status.getLong("private_writes", 0L)
                    + " privateFailures=" + status.getLong("private_failures", 0L)
                    + " privateQueue=" + privateDepth + "/" + privateCapacity
                    + "\npublicWrites=" + status.getLong("public_writes", 0L)
                    + " publicFailures=" + status.getLong("public_failures", 0L)
                    + " publicQueue=" + publicDepth + "/" + publicCapacity
                    + "\ningressRejections=" + status.getLong("ingress_rejections", 0L)
                    + " publicStorage=" + status.getString("public_storage_state", "unknown")
                    + " api=" + status.getInt("public_storage_api", 0);
        } catch (Throwable failure) {
            return "logStorage=unavailable errorType=" + failure.getClass().getName();
        }
    }

    static void exportTrace(Activity activity) {
        String report = traceReport(activity);
        File base = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = activity.getFilesDir();
        File dir = new File(base, "Deekseep");
        File output = new File(dir, "developer-trace-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
            FileWriter writer = new FileWriter(output, false);
            writer.write(report);
            writer.close();
            copy(activity, report);
            Toast.makeText(activity, "已导出并复制：" + output.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            copy(activity, report);
            Toast.makeText(activity, "文件保存失败，报告已复制", Toast.LENGTH_LONG).show();
        }
    }

    private static String traceReport(Activity activity) {
        StringBuilder out = new StringBuilder();
        out.append("YunSeek sanitized developer trace\n")
                .append(HostCompat.diagnosticSummary()).append("\n\n");
        for (String event : RECORDER.traceLines()) out.append(event).append('\n');
        return out.toString();
    }

    private static void showReport(final Activity activity, String title, final String report) {
        TextView body = new TextView(activity);
        body.setText(report);
        body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(12f);
        int padding = DeekseepUi.dp(activity, 20);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(body)
                .setNeutralButton("复制", (dialog, which) -> copy(activity, report))
                .setPositiveButton("完成", null)
                .show();
    }

    private static void copy(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("YunSeek diagnostics", text));
        } catch (Throwable ignored) {}
    }

}
