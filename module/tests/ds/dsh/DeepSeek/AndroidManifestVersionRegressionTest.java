package ds.dsh.DeepSeek;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class AndroidManifestVersionRegressionTest {
    public static void main(String[] args) throws Exception {
        readsRawVersionName();
        readsTypedStringVersionName();
        returnsNullWhenManifestHasNoVersionName();
        rejectsTruncatedAndMalformedChunks();
        rejectsInvalidStringPoolOffsets();
        rejectsNullAndShortInput();
        System.out.println("AndroidManifestVersionRegressionTest OK");
    }

    private static void readsRawVersionName() throws Exception {
        byte[] xml = binaryManifest("manifest", "versionName", "2.4.1", false);
        check("2.4.1".equals(AndroidManifestVersion.parse(xml)), "raw versionName");
        check("2.4.1".equals(AndroidManifestVersionPolicy.parse(xml)), "Kotlin raw versionName");
    }

    private static void readsTypedStringVersionName() throws Exception {
        byte[] xml = binaryManifest("manifest", "versionName", "2.7.0", true);
        check("2.7.0".equals(AndroidManifestVersion.parse(xml)), "typed versionName");
    }

    private static void returnsNullWhenManifestHasNoVersionName() throws Exception {
        byte[] xml = binaryManifest("manifest", "package", "com.deepseek.chat", false);
        check(AndroidManifestVersion.parse(xml) == null, "missing versionName");
        byte[] wrongElement = binaryManifest("application", "versionName", "2.4.1", false);
        check(AndroidManifestVersion.parse(wrongElement) == null, "non-manifest element");
    }

    private static void rejectsTruncatedAndMalformedChunks() throws Exception {
        byte[] valid = binaryManifest("manifest", "versionName", "2.4.1", false);
        byte[] truncated = new byte[valid.length - 1];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);
        check(AndroidManifestVersion.parse(truncated) == null, "truncated document");
        byte[] badSize = valid.clone();
        put32(badSize, 12, Integer.MAX_VALUE);
        check(AndroidManifestVersion.parse(badSize) == null, "oversized first chunk");
    }

    private static void rejectsInvalidStringPoolOffsets() throws Exception {
        byte[] xml = binaryManifest("manifest", "versionName", "2.4.1", false);
        byte[] invalid = xml.clone();
        // The first string offset follows the 8-byte XML header and 28-byte pool header.
        put32(invalid, 8 + 28, Integer.MAX_VALUE);
        check(AndroidManifestVersion.parse(invalid) == null, "invalid string offset");
    }

    private static void rejectsNullAndShortInput() {
        check(AndroidManifestVersion.parse(null) == null, "null input");
        check(AndroidManifestVersion.parse(new byte[7]) == null, "short input");
    }

    private static byte[] binaryManifest(String element, String attribute,
                                         String value, boolean typed) throws Exception {
        List<String> strings = new ArrayList<>();
        strings.add(element);
        strings.add(attribute);
        strings.add(value);

        ByteArrayOutputStream poolData = new ByteArrayOutputStream();
        int[] offsets = new int[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            offsets[i] = poolData.size();
            byte[] bytes = strings.get(i).getBytes("UTF-8");
            poolData.write(bytes.length);
            poolData.write(bytes.length);
            poolData.write(bytes);
            poolData.write(0);
        }

        int poolHeader = 28;
        int stringsStart = poolHeader + offsets.length * 4;
        int poolSize = stringsStart + poolData.size();
        ByteArrayOutputStream pool = new ByteArrayOutputStream();
        put16(pool, 0x0001);
        put16(pool, poolHeader);
        put32(pool, poolSize);
        put32(pool, strings.size());
        put32(pool, 0);
        put32(pool, 0x00000100);
        put32(pool, stringsStart);
        put32(pool, 0);
        for (int offset : offsets) put32(pool, offset);
        pool.write(poolData.toByteArray());

        int nodeHeader = 16;
        int extensionSize = 20;
        int attributeSize = 20;
        int nodeSize = nodeHeader + extensionSize + attributeSize;
        ByteArrayOutputStream node = new ByteArrayOutputStream();
        put16(node, 0x0102);
        put16(node, nodeHeader);
        put32(node, nodeSize);
        put32(node, 1);
        put32(node, -1);
        put32(node, -1);
        put32(node, 0);
        put16(node, extensionSize);
        put16(node, attributeSize);
        put16(node, 1);
        put16(node, 0);
        put16(node, 0);
        put16(node, 0);
        put32(node, -1);
        put32(node, 1);
        put32(node, typed ? -1 : 2);
        put16(node, 8);
        node.write(0);
        node.write(typed ? 0x03 : 0);
        put32(node, typed ? 2 : 0);

        int rootHeader = 8;
        int rootSize = rootHeader + pool.size() + node.size();
        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        put16(xml, 0x0003);
        put16(xml, rootHeader);
        put32(xml, rootSize);
        xml.write(pool.toByteArray());
        xml.write(node.toByteArray());
        return xml.toByteArray();
    }

    private static void put16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void put32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private static void put32(byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >>> 8);
        out[offset + 2] = (byte) (value >>> 16);
        out[offset + 3] = (byte) (value >>> 24);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
