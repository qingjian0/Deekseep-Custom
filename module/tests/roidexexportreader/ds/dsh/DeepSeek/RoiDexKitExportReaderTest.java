package ds.dsh.DeepSeek;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class RoiDexKitExportReaderTest {
    public static void main(String[] args) throws Exception {
        testExportAndParse();
        testDexCountMismatch();
        testFilenameLengthMismatch();
        testMalformedDexRejected();
        testOversizedIdTableRejected();
        System.out.println("RoiDexKitExportReaderTest: PASS");
    }

    private static void testExportAndParse() throws Exception {
        File parent = testParent("success");
        final byte[] fixture = fixture(112);
        final int[] consumed = {0};
        RoiDexKitExportReader.Result result = RoiDexKitExportReader.exportAndRead(
                parent, 1, directory -> write(new File(directory, "classes_112.dex"), fixture),
                (file, dex) -> {
                    consumed[0]++;
                    eq("039", dex.header.version);
                    if (!file.isFile()) throw new AssertionError("exported file missing during callback");
                });
        eq(1, result.dexFiles);
        eq(0, result.definedClasses);
        eq(0, result.definedMethods);
        eq(112L, result.bytesRead);
        eq(1, consumed[0]);
        assertEmpty(parent);
        delete(parent);
    }

    private static void testDexCountMismatch() throws Exception {
        File parent = testParent("count");
        expectFailure(parent, 2,
                directory -> write(new File(directory, "classes_112.dex"), fixture(112)),
                "export_file_count_mismatch");
        assertEmpty(parent);
        delete(parent);
    }

    private static void testFilenameLengthMismatch() throws Exception {
        File parent = testParent("length");
        expectFailure(parent, 1,
                directory -> write(new File(directory, "classes_113.dex"), fixture(112)),
                "export_filename_length_mismatch");
        assertEmpty(parent);
        delete(parent);
    }

    private static void testMalformedDexRejected() throws Exception {
        File parent = testParent("malformed");
        byte[] invalid = fixture(112);
        invalid[0] = 'x';
        expectFailure(parent, 1,
                directory -> write(new File(directory, "classes_112.dex"), invalid),
                "exported_dex_invalid");
        assertEmpty(parent);
        delete(parent);
    }

    private static void testOversizedIdTableRejected() throws Exception {
        File parent = testParent("oversized-table");
        byte[] oversized = fixture(112);
        put32(oversized, 88, 65_536);
        expectFailure(parent, 1,
                directory -> write(new File(directory, "classes_112.dex"), oversized),
                "dex_id_table_count_out_of_bounds");
        assertEmpty(parent);
        delete(parent);
    }

    private static void expectFailure(File parent, int count,
            RoiDexKitExportReader.Exporter exporter, String expected) throws Exception {
        try {
            RoiDexKitExportReader.exportAndRead(parent, count, exporter,
                    (file, dex) -> { throw new AssertionError("invalid DEX reached consumer"); });
            throw new AssertionError("expected export failure: " + expected);
        } catch (RoiDexKitExportReader.ExportException failure) {
            if (!failure.getMessage().contains(expected))
                throw new AssertionError("expected " + expected + " but got " + failure.getMessage());
        }
    }

    private static File testParent(String name) {
        return new File(System.getProperty("java.io.tmpdir"),
                "roi-dex-export-" + name + "-" + System.nanoTime());
    }

    private static void write(File file, byte[] bytes) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try { output.write(bytes); } finally { output.close(); }
    }

    private static byte[] fixture(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = 'd'; bytes[1] = 'e'; bytes[2] = 'x'; bytes[3] = '\n';
        bytes[4] = '0'; bytes[5] = '3'; bytes[6] = '9';
        put32(bytes, 32, size);
        put32(bytes, 36, 112);
        put32(bytes, 40, 0x12345678);
        return bytes;
    }

    private static void put32(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }

    private static void assertEmpty(File directory) {
        File[] entries = directory.listFiles();
        if (entries == null || entries.length != 0)
            throw new AssertionError("temporary export directory was not cleaned");
    }

    private static void delete(File file) {
        File[] entries = file.listFiles();
        if (entries != null) for (File entry : entries) delete(entry);
        if (file.exists() && !file.delete()) throw new AssertionError("test directory cleanup failed");
    }

    private static void eq(int expected, int actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + " got " + actual);
    }
    private static void eq(long expected, long actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + " got " + actual);
    }
    private static void eq(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " got " + actual);
    }
}
