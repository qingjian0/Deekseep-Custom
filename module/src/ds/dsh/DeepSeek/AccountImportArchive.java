package ds.dsh.DeepSeek;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded in-memory ZIP parser used only by the Closed code257 account importer. */
final class AccountImportArchive {
    static final int MAX_ENTRIES = 64;
    static final int MAX_AGGREGATE_BYTES = 8 * 1024 * 1024;
    static final int MAX_ARCHIVE_UNCOMPRESSED_BYTES = 8 * 1024 * 1024;

    private AccountImportArchive() {}

    static List<AccountCredentialCodec.Entry> readZip(InputStream raw, String archiveName)
            throws Exception {
        int entryCount = 0;
        int aggregate = 0;
        int expanded = 0;
        List<AccountCredentialCodec.Entry> out = new ArrayList<AccountCredentialCodec.Entry>();
        Set<String> ids = new LinkedHashSet<String>();
        ZipInputStream zip = new ZipInputStream(raw);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (++entryCount > MAX_ENTRIES) {
                throw new AccountCredentialCodec.FormatException("ZIP 文件项超过 64 个上限");
            }
            String path = safePath(entry.getName());
            if (entry.isDirectory()) {
                expanded += drainBounded(zip, MAX_ARCHIVE_UNCOMPRESSED_BYTES - expanded);
                zip.closeEntry();
                continue;
            }
            boolean supported = AccountImportArchivePolicy.isSupportedAccountFile(path);
            if (!supported) {
                expanded += drainBounded(zip, MAX_ARCHIVE_UNCOMPRESSED_BYTES - expanded);
                zip.closeEntry();
                continue;
            }
            long declared = entry.getSize();
            if (declared > AccountCredentialCodec.MAX_IMPORT_BYTES) {
                throw new AccountCredentialCodec.FormatException(
                        "ZIP 内单个账号文件超过 1 MiB：" + path);
            }
            byte[] bytes = readBounded(zip, AccountCredentialCodec.MAX_IMPORT_BYTES);
            expanded += bytes.length;
            if (expanded > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                throw new AccountCredentialCodec.FormatException(
                        "ZIP 解压后总内容超过 8 MiB");
            }
            aggregate += bytes.length;
            if (aggregate > MAX_AGGREGATE_BYTES) {
                throw new AccountCredentialCodec.FormatException("ZIP 解压后账号文件超过 8 MiB");
            }
            String text = decodeStrictUtf8(bytes);
            try {
                List<AccountCredentialCodec.Entry> parsed = AccountCredentialCodec.parseImport(text);
                for (AccountCredentialCodec.Entry value : parsed) {
                    if (!ids.add(value.id)) {
                        throw new AccountCredentialCodec.FormatException(
                                "ZIP 内包含重复账号：" + value.id);
                    }
                    out.add(value);
                }
            } catch (AccountCredentialCodec.FormatException failure) {
                throw new AccountCredentialCodec.FormatException("ZIP 内 " + path + "："
                        + failure.getMessage());
            }
            zip.closeEntry();
        }
        if (out.isEmpty()) {
            throw new AccountCredentialCodec.FormatException(
                    (archiveName == null ? "ZIP" : archiveName)
                            + " 中没有受支持的账号 JSON/TXT");
        }
        return out;
    }

    static String safePath(String raw) throws AccountCredentialCodec.FormatException {
        return AccountImportArchivePolicy.safePath(raw);
    }

    private static byte[] readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > limit) {
                throw new AccountCredentialCodec.FormatException(
                        "ZIP 内单个账号文件超过 1 MiB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** Drain ignored entries without allowing them to bypass the archive expansion budget. */
    private static int drainBounded(InputStream input, int remaining) throws Exception {
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total + read > remaining) {
                throw new AccountCredentialCodec.FormatException(
                        "ZIP 解压后总内容超过 8 MiB");
            }
            total += read;
        }
        return total;
    }

    private static String decodeStrictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
}
