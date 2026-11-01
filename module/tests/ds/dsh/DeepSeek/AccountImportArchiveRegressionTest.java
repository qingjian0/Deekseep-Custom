package ds.dsh.DeepSeek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AccountImportArchiveRegressionTest {
    public static void main(String[] args) throws Exception {
        readsSupportedEntriesAndSkipsOtherFiles();
        rejectsUnsafePaths();
        rejectsUnsafePathInsideArchive();
        rejectsDuplicateIdsAcrossEntries();
        rejectsMalformedUtf8();
        rejectsUnsupportedOnlyArchive();
        rejectsTooManyEntries();
        rejectsOversizedEntry();
        rejectsOversizedIgnoredEntry();
        System.out.println("AccountImportArchiveRegressionTest OK");
    }

    private static void readsSupportedEntriesAndSkipsOtherFiles() throws Exception {
        byte[] zip = archive(
                new String[]{"notes.md", "accounts/alice.json", "bob.DS-ACCOUNT"},
                new byte[][]{"ignore".getBytes(StandardCharsets.UTF_8),
                        credential("account-00000001", "Alice").getBytes(StandardCharsets.UTF_8),
                        credential("account-00000002", "Bob").getBytes(StandardCharsets.UTF_8)});
        List<AccountCredentialCodec.Entry> entries = AccountImportArchive.readZip(
                new ByteArrayInputStream(zip), "accounts.zip");
        check(entries.size() == 2, "supported entries must be imported");
        check("account-00000001".equals(entries.get(0).id), "first account id");
        check("account-00000002".equals(entries.get(1).id), "second account id");
    }

    private static void rejectsUnsafePaths() throws Exception {
        expectPathFailure("../escape.json");
        expectPathFailure("/absolute.json");
        expectPathFailure("C:\\temp\\account.json");
        expectPathFailure("nested/../../escape.json");
        expectPathFailure("bad" + ((char) 0) + "name.json");
    }

    private static void rejectsUnsafePathInsideArchive() throws Exception {
        byte[] zip = archive(new String[]{"../escape.json"},
                new byte[][]{credential("account-00000001", "Alice")
                        .getBytes(StandardCharsets.UTF_8)});
        expectArchiveFailure(zip, "unsafe path in archive");
    }

    private static void rejectsDuplicateIdsAcrossEntries() throws Exception {
        String account = credential("account-00000001", "Alice");
        byte[] zip = archive(new String[]{"one.json", "two.txt"},
                new byte[][]{account.getBytes(StandardCharsets.UTF_8),
                        account.getBytes(StandardCharsets.UTF_8)});
        expectArchiveFailure(zip, "duplicate id across ZIP entries");
    }

    private static void rejectsMalformedUtf8() throws Exception {
        byte[] zip = archive(new String[]{"broken.json"}, new byte[][]{
                new byte[]{(byte) 0xc3, (byte) 0x28}});
        boolean failed = false;
        try {
            AccountImportArchive.readZip(new ByteArrayInputStream(zip), "broken.zip");
        } catch (java.nio.charset.CharacterCodingException expected) {
            failed = true;
        }
        check(failed, "malformed UTF-8 must be rejected");
    }

    private static void rejectsUnsupportedOnlyArchive() throws Exception {
        byte[] zip = archive(new String[]{"readme.md"},
                new byte[][]{"not an account".getBytes(StandardCharsets.UTF_8)});
        expectArchiveFailure(zip, "archive without supported files");
    }

    private static void rejectsTooManyEntries() throws Exception {
        String[] names = new String[AccountImportArchive.MAX_ENTRIES + 1];
        byte[][] contents = new byte[names.length][];
        for (int i = 0; i < names.length; i++) {
            names[i] = "ignored-" + i + ".bin";
            contents[i] = new byte[]{1};
        }
        expectArchiveFailure(archive(names, contents), "entry count limit");
    }

    private static void rejectsOversizedEntry() throws Exception {
        byte[] oversized = new byte[AccountCredentialCodec.MAX_IMPORT_BYTES + 1];
        Arrays.fill(oversized, (byte) ' ');
        byte[] zip = archive(new String[]{"oversized.json"}, new byte[][]{oversized});
        expectArchiveFailure(zip, "single-entry size limit");
    }

    private static void rejectsOversizedIgnoredEntry() throws Exception {
        byte[] oversized = new byte[AccountImportArchive.MAX_ARCHIVE_UNCOMPRESSED_BYTES + 1];
        Arrays.fill(oversized, (byte) 'x');
        byte[] zip = archive(new String[]{"ignored.bin"}, new byte[][]{oversized});
        expectArchiveFailure(zip, "ignored entries must count toward the archive expansion limit");
    }

    private static void expectPathFailure(String path) throws Exception {
        boolean failed = false;
        try {
            AccountImportArchive.safePath(path);
        } catch (AccountCredentialCodec.FormatException expected) {
            failed = true;
        }
        check(failed, "unsafe path accepted: " + path);
    }

    private static void expectArchiveFailure(byte[] bytes, String label) throws Exception {
        boolean failed = false;
        try {
            AccountImportArchive.readZip(new ByteArrayInputStream(bytes), "test.zip");
        } catch (AccountCredentialCodec.FormatException expected) {
            failed = true;
        }
        check(failed, label);
    }

    private static byte[] archive(String[] names, byte[][] contents) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        for (int i = 0; i < names.length; i++) {
            zip.putNextEntry(new ZipEntry(names[i]));
            zip.write(contents[i]);
            zip.closeEntry();
        }
        zip.close();
        return bytes.toByteArray();
    }

    private static String credential(String id, String name) throws Exception {
        JSONObject credential = new JSONObject();
        credential.put("id", id);
        credential.put("token", "token-abcdefghijklmnopqrstuvwxyz-0123456789");
        credential.put("email", JSONObject.NULL);
        credential.put("mobile_number", JSONObject.NULL);
        credential.put("status", 1);
        credential.put("chat_status", new JSONObject().put("enabled", true));
        credential.put("id_profiles", new JSONArray().put(new JSONObject()
                .put("provider", "WECHAT").put("name", name)));
        credential.put("need_birthday", false);
        return credential.toString();
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
