package ds.dsh.DeepSeek

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidManifestVersionTest {
    @Test
    fun parsesUtf8StringPoolAndRawAttributeValue() {
        assertEquals("2.7.1", AndroidManifestVersion.parse(manifestFixture(utf8 = true, rawValue = true)))
    }

    @Test
    fun parsesUtf16StringPoolAndTypedAttributeValue() {
        assertEquals("2.7.1", AndroidManifestVersion.parse(manifestFixture(utf8 = false, rawValue = false)))
    }

    @Test
    fun rejectsTruncatedAndMalformedBinaryXml() {
        assertNull(AndroidManifestVersion.parse(null))
        assertNull(AndroidManifestVersion.parse(ByteArray(7)))
        val malformed = ByteArray(16)
        putU16(malformed, 2, 8)
        putU16(malformed, 8, 1)
        putU16(malformed, 10, 28)
        putI32(malformed, 12, 4)
        assertNull(AndroidManifestVersion.parse(malformed))
    }

    @Test
    fun readsManifestFromApkZipEntry() {
        val apk = File.createTempFile("dsh-manifest", ".apk")
        try {
            ZipOutputStream(apk.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(manifestFixture(utf8 = true, rawValue = true))
                zip.closeEntry()
            }
            assertEquals("2.7.1", AndroidManifestVersion.readFromApk(apk.absolutePath))
        } finally {
            apk.delete()
        }
    }

    private fun manifestFixture(utf8: Boolean, rawValue: Boolean): ByteArray {
        val strings = listOf("manifest", "versionName", "2.7.1")
        val stringData = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        strings.forEach { value ->
            offsets += stringData.size()
            if (utf8) {
                val bytes = value.toByteArray(StandardCharsets.UTF_8)
                stringData.write(value.length)
                stringData.write(bytes.size)
                stringData.write(bytes)
                stringData.write(0)
            } else {
                stringData.write(value.length)
                stringData.write(0)
                value.forEach { char ->
                    stringData.write(char.code and 0xff)
                    stringData.write((char.code ushr 8) and 0xff)
                }
                stringData.write(0)
                stringData.write(0)
            }
        }

        val stringHeaderSize = 28
        val stringsStart = stringHeaderSize + strings.size * 4
        val stringChunkSize = stringsStart + stringData.size()
        val elementHeaderSize = 16
        val extensionSize = 20
        val attributeSize = 20
        val elementChunkSize = elementHeaderSize + extensionSize + attributeSize
        val result = ByteArray(8 + stringChunkSize + elementChunkSize)

        // Top-level XML chunk header; the parser starts at this header's declared size.
        putU16(result, 0, 0x0003)
        putU16(result, 2, 8)
        putI32(result, 4, result.size)

        val pool = 8
        putU16(result, pool, 0x0001)
        putU16(result, pool + 2, stringHeaderSize)
        putI32(result, pool + 4, stringChunkSize)
        putI32(result, pool + 8, strings.size)
        putI32(result, pool + 12, 0)
        putI32(result, pool + 16, if (utf8) 0x100 else 0)
        putI32(result, pool + 20, stringsStart)
        putI32(result, pool + 24, 0)
        offsets.forEachIndexed { index, relative ->
            putI32(result, pool + stringHeaderSize + index * 4, relative)
        }
        stringData.toByteArray().copyInto(result, pool + stringsStart)

        val element = pool + stringChunkSize
        putU16(result, element, 0x0102)
        putU16(result, element + 2, elementHeaderSize)
        putI32(result, element + 4, elementChunkSize)
        val extension = element + elementHeaderSize
        putI32(result, extension, -1)
        putI32(result, extension + 4, 0)
        putU16(result, extension + 8, extensionSize)
        putU16(result, extension + 10, attributeSize)
        putU16(result, extension + 12, 1)
        val attribute = extension + extensionSize
        putI32(result, attribute, -1)
        putI32(result, attribute + 4, 1)
        putI32(result, attribute + 8, if (rawValue) 2 else -1)
        putU16(result, attribute + 12, 8)
        result[attribute + 14] = 0
        result[attribute + 15] = 0x03
        putI32(result, attribute + 16, 2)
        return result
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putI32(target: ByteArray, offset: Int, value: Int) {
        for (index in 0 until 4) {
            target[offset + index] = (value ushr (index * 8)).toByte()
        }
    }
}
