package ds.dsh.DeepSeek;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Minimal read-only AXML parser for the manifest package versionName attribute. */
final class AndroidManifestVersion {
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int UTF8_FLAG = 0x00000100;
    private static final int TYPE_STRING = 0x03;

    private AndroidManifestVersion() {}

    static String readFromApk(String apkPath) {
        if (apkPath == null) return null;
        ZipFile apk = null;
        try {
            apk = new ZipFile(new File(apkPath));
            ZipEntry manifest = apk.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.getSize() > 2 * 1024 * 1024L) return null;
            InputStream input = apk.getInputStream(manifest);
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    manifest.getSize() > 0 ? (int) manifest.getSize() : 32 * 1024);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (output.size() + read > 2 * 1024 * 1024) return null;
                    output.write(buffer, 0, read);
                }
            } finally { input.close(); }
            return parse(output.toByteArray());
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (apk != null) try { apk.close(); } catch (Throwable ignored) {}
        }
    }

    static String parse(byte[] xml) {
        return AndroidManifestVersionPolicy.parse(xml);
    }
}
