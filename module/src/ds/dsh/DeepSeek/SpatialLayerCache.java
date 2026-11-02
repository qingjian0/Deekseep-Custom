package ds.dsh.DeepSeek;

import android.graphics.Bitmap;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

/**
 * Builds two derived planes from a simple imported wallpaper without adding an ML dependency.
 *
 * <p>The existing edge-connected cutout mask supplies the subject plane. Removed subject pixels
 * in the back plate are reconstructed from a coarse field of known background samples. This is
 * deliberately a derived cache: the original user image is never modified.</p>
 */
final class SpatialLayerCache {
    private static final String CACHE_VERSION = "v2";
    private static final String CACHE_DIR =
            ChatAppearance.ROOT_DIR + "/spatial_cache";
    private static final Object LOCK = new Object();
    private static final HashSet<String> IN_FLIGHT = new HashSet<>();

    interface Completion {
        void onComplete(boolean ready);
    }

    static final class Layers {
        final String key;
        final File backplate;
        final File midground;

        Layers(String key, File backplate, File midground) {
            this.key = key;
            this.backplate = backplate;
            this.midground = midground;
        }

        boolean ready() {
            return backplate.isFile() && backplate.length() > 0L
                    && midground.isFile() && midground.length() > 0L;
        }
    }

    private SpatialLayerCache() {}

    static Layers filesFor(File source) {
        String identity = source == null ? CACHE_VERSION + "|missing"
                : CACHE_VERSION + "|" + source.getAbsolutePath() + "|" + source.length()
                + "|" + source.lastModified();
        String key = Integer.toHexString(identity.hashCode())
                + "_" + Long.toHexString(source == null ? 0L : source.length());
        File directory = new File(CACHE_DIR);
        return new Layers(
                key,
                new File(directory, "back_" + key + ".png"),
                new File(directory, "mid_" + key + ".png"));
    }

    static void generateAsync(
            final File source, final Completion completion) {
        final Layers layers = filesFor(source);
        if (layers.ready()) {
            if (completion != null) completion.onComplete(true);
            return;
        }
        synchronized (LOCK) {
            if (!IN_FLIGHT.add(layers.key)) return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                boolean ready = false;
                try {
                    ready = generate(source, layers);
                } catch (Throwable t) {
                    Main.log("spatial layer generation failed: " + t);
                } finally {
                    synchronized (LOCK) {
                        IN_FLIGHT.remove(layers.key);
                    }
                    if (completion != null) {
                        try {
                            completion.onComplete(ready);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }, "Deekseep-spatial-layers");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean generate(File source, Layers layers)
            throws Exception {
        if (source == null || !source.isFile()) return false;
        File directory = layers.backplate.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) return false;

        Bitmap sourceBitmap = ChatAppearance.loadBitmap(source, 1800, 1800);
        if (sourceBitmap == null) return false;
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();
        int count = width * height;
        int[] sourcePixels = new int[count];
        sourceBitmap.getPixels(
                sourcePixels, 0, width, 0, 0, width, height);
        byte[] keepMask = ImageCutoutUi.detectBackgroundMask(
                sourcePixels, width, height);
        if (keepMask == null || keepMask.length != count) {
            sourceBitmap.recycle();
            return false;
        }

        int kept = 0;
        for (byte value : keepMask) {
            if ((value & 0xFF) >= 128) kept++;
        }
        float keptFraction = kept / (float) Math.max(1, count);
        // A failed segmentation that keeps almost nothing/everything is worse than a flat plane.
        if (keptFraction < 0.02f || keptFraction > 0.92f) {
            sourceBitmap.recycle();
            Main.log("spatial layer mask rejected fraction=" + keptFraction);
            return false;
        }

        int[] midgroundPixels = buildMidgroundPixels(
                sourcePixels, keepMask);
        int[] backplatePixels = buildBackplatePixels(
                sourcePixels, keepMask, width, height);

        Bitmap midground = Bitmap.createBitmap(
                midgroundPixels, width, height, Bitmap.Config.ARGB_8888);
        Bitmap backplate = Bitmap.createBitmap(
                backplatePixels, width, height, Bitmap.Config.ARGB_8888);
        boolean wroteBack = writePng(backplate, layers.backplate);
        boolean wroteMid = writePng(midground, layers.midground);
        backplate.recycle();
        midground.recycle();
        sourceBitmap.recycle();
        boolean ready = wroteBack && wroteMid && layers.ready();
        if (ready) {
            Main.log("spatial optical planes ready key=" + layers.key
                    + " subject=" + Math.round(keptFraction * 100f) + "%");
        }
        return ready;
    }

    static int[] buildMidgroundPixels(int[] pixels, byte[] keepMask) {
        return SpatialLayerPixelPolicy.buildMidgroundPixels(pixels, keepMask);
    }

    static int[] buildBackplatePixels(
            int[] pixels, byte[] keepMask, int width, int height) {
        return SpatialLayerPixelPolicy.buildBackplatePixels(
                pixels, keepMask, width, height);
    }

    static int[] buildBackgroundField(
            int[] pixels, byte[] keepMask, int width, int height) {
        return SpatialLayerPixelPolicy.buildBackgroundField(
                pixels, keepMask, width, height);
    }

    private static boolean writePng(Bitmap bitmap, File target) {
        File temporary = new File(
                target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temporary, false);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
            output.flush();
            try {
                output.getFD().sync();
            } catch (Throwable ignored) {}
            output.close();
            output = null;
            // Same-directory rename atomically replaces an existing cache file. Keep the
            // previous valid image intact unless the fully-written temporary file is ready.
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            return target.isFile();
        } catch (Throwable t) {
            Main.log("spatial cache write failed: " + t);
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {}
            }
            // rename consumes the temporary path on success; on every failure path,
            // remove any partial file without touching the previous target.
            if (temporary.exists()) {
                try {
                    temporary.delete();
                } catch (Throwable ignored) {}
            }
        }
    }
}
