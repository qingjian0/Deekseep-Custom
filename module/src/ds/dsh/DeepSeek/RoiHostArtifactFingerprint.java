package ds.dsh.DeepSeek;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content identity for the installed host APK set.
 *
 * <p>This is deliberately separate from adapter generation labels and process-local ClassLoader
 * scopes. It hashes the base APK and every declared split APK, then hashes a canonical manifest
 * containing their role/name/content digests. It does not itself authorize a Hook.</p>
 */
final class RoiHostArtifactFingerprint {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int MAX_APK_FILES = 16;
    private static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024L * 1024L;

    static final class Result {
        final boolean verified;
        final String algorithm;
        final String baseSha256;
        final String aggregateSha256;
        final int splitCount;
        final long totalBytes;
        final String reason;

        Result(boolean verified, String baseSha256, String aggregateSha256,
               int splitCount, long totalBytes, String reason) {
            this.verified = verified;
            this.algorithm = "SHA-256";
            this.baseSha256 = safe(baseSha256);
            this.aggregateSha256 = safe(aggregateSha256);
            this.splitCount = splitCount;
            this.totalBytes = totalBytes;
            this.reason = safe(reason);
        }

        String toReport() {
            StringBuilder out = new StringBuilder(256);
            line(out, "artifactIdentity.status=" + (verified ? "verified" : "unavailable"));
            line(out, "artifactIdentity.algorithm=" + algorithm);
            line(out, "artifactIdentity.base.sha256=" + baseSha256);
            line(out, "artifactIdentity.split.count=" + splitCount);
            line(out, "artifactIdentity.aggregate.sha256=" + aggregateSha256);
            line(out, "artifactIdentity.totalBytes=" + totalBytes);
            line(out, "artifactIdentity.reason=" + reason);
            return out.toString();
        }
    }

    private static final class Part {
        final String role;
        final String name;
        final String digest;
        final long bytes;

        Part(String role, String name, String digest, long bytes) {
            this.role = role;
            this.name = name;
            this.digest = digest;
            this.bytes = bytes;
        }
    }

    private RoiHostArtifactFingerprint() {}

    static Result inspect(String baseApkPath, String[] splitApkPaths) {
        if (baseApkPath == null || baseApkPath.trim().isEmpty()) {
            return unavailable("base_apk_path_missing");
        }
        List<Part> parts = new ArrayList<Part>();
        Set<String> canonicalPaths = new HashSet<String>();
        Set<String> splitNames = new HashSet<String>();
        long totalBytes = 0L;
        try {
            File base = canonicalFile(baseApkPath);
            if (!canonicalPaths.add(base.getPath())) return unavailable("duplicate_base_apk_path");
            Part basePart = hash(base, "base");
            parts.add(basePart);
            totalBytes = checkedAdd(totalBytes, basePart.bytes);

            String[] splits = splitApkPaths == null ? new String[0] : splitApkPaths.clone();
            if (splits.length + 1 > MAX_APK_FILES) return unavailable("apk_file_count_limit_exceeded");
            List<File> splitFiles = new ArrayList<File>();
            for (String path : splits) {
                if (path == null || path.trim().isEmpty()) return unavailable("split_apk_path_missing");
                File split = canonicalFile(path);
                if (!canonicalPaths.add(split.getPath())) return unavailable("duplicate_apk_path");
                if (!splitNames.add(split.getName())) return unavailable("duplicate_split_name");
                splitFiles.add(split);
            }
            Collections.sort(splitFiles, new Comparator<File>() {
                @Override public int compare(File left, File right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (File split : splitFiles) {
                Part part = hash(split, "split");
                parts.add(part);
                totalBytes = checkedAdd(totalBytes, part.bytes);
            }

            MessageDigest aggregate = sha256();
            update(aggregate, "roi-host-artifact-set-v1\n");
            for (Part part : parts) {
                update(aggregate, part.role);
                update(aggregate, "\t");
                update(aggregate, part.name);
                update(aggregate, "\t");
                update(aggregate, part.digest);
                update(aggregate, "\t");
                update(aggregate, Long.toString(part.bytes));
                update(aggregate, "\n");
            }
            return new Result(true, basePart.digest, hex(aggregate.digest()),
                    splitFiles.size(), totalBytes, "none");
        } catch (LimitException limited) {
            return unavailable(limited.getMessage());
        } catch (IOException failure) {
            return unavailable("apk_read_failed");
        } catch (NoSuchAlgorithmException unavailable) {
            return unavailable("sha256_unavailable");
        } catch (SecurityException denied) {
            return unavailable("apk_access_denied");
        } catch (RuntimeException invalid) {
            return unavailable("invalid_apk_input");
        }
    }

    private static File canonicalFile(String path) throws IOException, LimitException {
        File file = new File(path).getCanonicalFile();
        if (!file.isFile() || !file.canRead()) throw new IOException("unreadable");
        long length = file.length();
        if (length <= 0L) throw new LimitException("empty_apk_file");
        if (length > MAX_FILE_BYTES) throw new LimitException("apk_file_size_limit_exceeded");
        return file;
    }

    private static Part hash(File file, String role)
            throws IOException, NoSuchAlgorithmException, LimitException {
        long declaredLength = file.length();
        if (declaredLength <= 0L || declaredLength > MAX_FILE_BYTES) {
            throw new LimitException("apk_file_size_limit_exceeded");
        }
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_BYTES];
        long bytes = 0L;
        FileInputStream input = new FileInputStream(file);
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes = checkedAdd(bytes, read);
                if (bytes > MAX_FILE_BYTES) throw new LimitException("apk_file_size_limit_exceeded");
                digest.update(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        if (bytes != declaredLength) throw new IOException("file_changed_during_hash");
        return new Part(role, file.getName(), hex(digest.digest()), bytes);
    }

    private static long checkedAdd(long current, long amount) throws LimitException {
        if (amount < 0L || current > MAX_TOTAL_BYTES - amount) {
            throw new LimitException("apk_set_size_limit_exceeded");
        }
        return current + amount;
    }

    private static MessageDigest sha256() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
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

    private static Result unavailable(String reason) {
        return new Result(false, "", "", 0, 0L, reason);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void line(StringBuilder out, String value) {
        out.append(value).append('\n');
    }

    private static final class LimitException extends Exception {
        LimitException(String message) { super(message); }
    }
}
