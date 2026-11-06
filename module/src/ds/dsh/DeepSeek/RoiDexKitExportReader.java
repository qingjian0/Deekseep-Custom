package ds.dsh.DeepSeek;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded hand-off from a DexKit runtime-loader bridge to the dependency-free DEX parser.
 *
 * The caller owns the DexKit bridge. This class owns only a fresh temporary export directory
 * and always removes it before returning. Exported DEX images are parsed one at a time so the
 * full multidex byte set is never retained in memory.
 */
final class RoiDexKitExportReader {
    static final int MAX_DEX_FILES = 256;
    static final long MAX_DEX_BYTES = 64L * 1024L * 1024L;
    static final long MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
    private static final Pattern DEX_NAME = Pattern.compile("classes_([0-9]+)\\.dex");

    interface Exporter {
        void exportTo(String outputDirectory) throws Exception;
    }

    interface DexConsumer {
        void accept(File exportedFile, RoiDexFileReader.DexFile dex) throws Exception;
    }

    static final class ExportException extends Exception {
        ExportException(String message) { super(message); }
        ExportException(String message, Throwable cause) { super(message, cause); }
    }

    static final class Result {
        final int dexFiles;
        final int definedClasses;
        final int definedMethods;
        final int codeMethods;
        final long bytesRead;

        Result(int files, int classes, int methods, int code, long bytes) {
            dexFiles = files;
            definedClasses = classes;
            definedMethods = methods;
            codeMethods = code;
            bytesRead = bytes;
        }
    }

    private RoiDexKitExportReader() {}

    static Result exportAndRead(File privateParent, int expectedDexCount,
            Exporter exporter, DexConsumer consumer) throws ExportException {
        if (privateParent == null || exporter == null || consumer == null)
            throw new ExportException("export_arguments_missing");
        if (expectedDexCount <= 0 || expectedDexCount > MAX_DEX_FILES)
            throw new ExportException("dex_count_out_of_bounds");
        if (!privateParent.exists() && !privateParent.mkdirs())
            throw new ExportException("private_parent_create_failed");
        if (!privateParent.isDirectory())
            throw new ExportException("private_parent_not_directory");

        File work = null;
        try {
            work = File.createTempFile("roi-dex-", ".tmp", privateParent);
            if (!work.delete() || !work.mkdir())
                throw new ExportException("export_directory_create_failed");
            File canonicalParent = privateParent.getCanonicalFile();
            File canonicalWork = work.getCanonicalFile();
            if (!canonicalParent.equals(canonicalWork.getParentFile()))
                throw new ExportException("export_directory_parent_mismatch");

            exporter.exportTo(canonicalWork.getAbsolutePath());
            File[] entries = canonicalWork.listFiles();
            if (entries == null || entries.length != expectedDexCount)
                throw new ExportException("export_file_count_mismatch");

            Arrays.sort(entries, new Comparator<File>() {
                @Override public int compare(File left, File right) {
                    return left.getName().compareTo(right.getName());
                }
            });

            long totalBytes = 0L;
            int classes = 0;
            int methods = 0;
            int codeMethods = 0;
            for (File file : entries) {
                if (file == null || !file.isFile())
                    throw new ExportException("export_entry_not_regular_file");
                File canonicalFile = file.getCanonicalFile();
                if (!canonicalWork.equals(canonicalFile.getParentFile()))
                    throw new ExportException("export_file_escaped_directory");
                Matcher matcher = DEX_NAME.matcher(file.getName());
                if (!matcher.matches())
                    throw new ExportException("export_filename_invalid");
                long namedLength;
                try {
                    namedLength = Long.parseLong(matcher.group(1));
                } catch (NumberFormatException invalid) {
                    throw new ExportException("export_filename_length_invalid", invalid);
                }
                long fileLength = file.length();
                if (namedLength != fileLength)
                    throw new ExportException("export_filename_length_mismatch");
                if (fileLength < RoiDexFileReader.HEADER_SIZE
                        || fileLength > MAX_DEX_BYTES)
                    throw new ExportException("export_file_size_out_of_bounds");
                totalBytes += fileLength;
                if (totalBytes > MAX_TOTAL_BYTES)
                    throw new ExportException("export_total_size_out_of_bounds");

                byte[] bytes = readExact(file, (int) fileLength);
                final RoiDexFileReader.DexFile dex;
                try {
                    dex = RoiDexFileReader.read(bytes);
                } catch (RoiDexFileReader.DexException invalidDex) {
                    throw new ExportException("exported_dex_invalid", invalidDex);
                }
                if (dex.header.fileSize != fileLength)
                    throw new ExportException("parsed_dex_file_size_mismatch");
                consumer.accept(file, dex);
                classes += dex.classes.length;
                methods += dex.definedMethods.length;
                for (RoiDexFileReader.ResolvedMethod method : dex.definedMethods) {
                    if (method.codeItem != null) codeMethods++;
                }
            }
            return new Result(entries.length, classes, methods, codeMethods, totalBytes);
        } catch (ExportException error) {
            throw error;
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            if (error instanceof ThreadDeath) throw (ThreadDeath) error;
            throw new ExportException("dex_export_or_parse_failed:"
                    + error.getClass().getName(), error);
        } finally {
            if (work != null) deleteTree(work);
        }
    }

    private static byte[] readExact(File file, int length) throws ExportException {
        byte[] bytes = new byte[length];
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            int offset = 0;
            while (offset < length) {
                int count = input.read(bytes, offset, length - offset);
                if (count < 0) throw new ExportException("export_file_truncated");
                if (count == 0) continue;
                offset += count;
            }
            if (input.read() != -1) throw new ExportException("export_file_grew_during_read");
            return bytes;
        } catch (IOException error) {
            throw new ExportException("export_file_read_failed", error);
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        try { file.delete(); } catch (Throwable ignored) {}
    }
}
