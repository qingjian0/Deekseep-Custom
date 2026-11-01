package ds.dsh.DeepSeek

/**
 * Pure, bounded parser for the binary Android XML manifest versionName attribute.
 * APK/ZIP I/O remains in the Java facade; this policy only examines the supplied byte array.
 */
object AndroidManifestVersionPolicy {
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val UTF8_FLAG = 0x00000100
    private const val TYPE_STRING = 0x03

    @JvmStatic
    fun parse(xml: ByteArray?): String? {
        if (xml == null || xml.size < 8) return null
        var strings: Array<String?>? = null
        var offset = u16(xml, 2)
        if (offset < 8) offset = 8
        while (offset + 8 <= xml.size) {
            val type = u16(xml, offset)
            val headerSize = u16(xml, offset + 2)
            val chunkSize = i32(xml, offset + 4)
            if (headerSize < 8 || chunkSize < headerSize || offset + chunkSize > xml.size) {
                return null
            }
            if (type == RES_STRING_POOL_TYPE) {
                strings = stringPool(xml, offset, headerSize, chunkSize)
            } else if (type == RES_XML_START_ELEMENT_TYPE && strings != null &&
                headerSize >= 16 && offset + headerSize + 20 <= xml.size
            ) {
                val extension = offset + headerSize
                val element = stringAt(strings, i32(xml, extension + 4))
                if (element == "manifest") {
                    val attributeStart = u16(xml, extension + 8)
                    val attributeSize = u16(xml, extension + 10)
                    val attributeCount = u16(xml, extension + 12)
                    val attributes = extension + attributeStart
                    if (attributeSize < 20 || attributes < extension ||
                        attributes.toLong() + attributeSize.toLong() * attributeCount >
                        offset.toLong() + chunkSize
                    ) return null
                    for (index in 0 until attributeCount) {
                        val attribute = attributes + index * attributeSize
                        val name = stringAt(strings, i32(xml, attribute + 4))
                        if (name != "versionName") continue
                        val rawValue = i32(xml, attribute + 8)
                        if (rawValue >= 0) return stringAt(strings, rawValue)
                        val valueType = xml[attribute + 15].toInt() and 0xff
                        if (valueType == TYPE_STRING) {
                            return stringAt(strings, i32(xml, attribute + 16))
                        }
                        return null
                    }
                }
            }
            offset += chunkSize
        }
        return null
    }

    private fun stringPool(
        data: ByteArray,
        chunk: Int,
        headerSize: Int,
        chunkSize: Int
    ): Array<String?>? {
        if (headerSize < 28 || chunk + headerSize > data.size) return null
        val count = i32(data, chunk + 8)
        val flags = i32(data, chunk + 16)
        val start = i32(data, chunk + 20)
        if (count < 0 || count > 200_000 || start < headerSize ||
            chunk + headerSize.toLong() + count.toLong() * 4 > chunk.toLong() + chunkSize
        ) return null
        val out = arrayOfNulls<String>(count)
        for (index in 0 until count) {
            val relative = i32(data, chunk + headerSize + index * 4)
            val at = chunk + start + relative
            if (at < chunk || at >= chunk + chunkSize) return null
            out[index] = if ((flags and UTF8_FLAG) != 0) {
                decodeUtf8(data, at, chunk + chunkSize)
            } else {
                decodeUtf16(data, at, chunk + chunkSize)
            }
        }
        return out
    }

    private fun decodeUtf8(data: ByteArray, at: Int, end: Int): String? {
        val first = length8(data, at, end) ?: return null
        val second = length8(data, first[1], end) ?: return null
        if (second[0] < 0 || second[1].toLong() + second[0] > end) return null
        return String(data, second[1], second[0], Charsets.UTF_8)
    }

    private fun length8(data: ByteArray, at: Int, end: Int): IntArray? {
        if (at >= end) return null
        var cursor = at + 1
        var value = data[at].toInt() and 0xff
        if ((value and 0x80) != 0) {
            if (cursor >= end) return null
            value = ((value and 0x7f) shl 8) or (data[cursor].toInt() and 0xff)
            cursor++
        }
        return intArrayOf(value, cursor)
    }

    private fun decodeUtf16(data: ByteArray, at: Int, end: Int): String? {
        if (at.toLong() + 2 > end) return null
        var length = u16(data, at)
        var cursor = at + 2
        if ((length and 0x8000) != 0) {
            if (cursor.toLong() + 2 > end) return null
            length = ((length and 0x7fff) shl 16) or u16(data, cursor)
            cursor += 2
        }
        val byteCount = length * 2
        if (length < 0 || byteCount < 0 || cursor.toLong() + byteCount > end) return null
        return String(data, cursor, byteCount, Charsets.UTF_16LE)
    }

    private fun stringAt(values: Array<String?>, index: Int): String? =
        if (index >= 0 && index < values.size) values[index] else null

    private fun u16(data: ByteArray, at: Int): Int {
        if (at < 0 || at.toLong() + 2 > data.size) return -1
        return (data[at].toInt() and 0xff) or
            ((data[at + 1].toInt() and 0xff) shl 8)
    }

    private fun i32(data: ByteArray, at: Int): Int {
        if (at < 0 || at.toLong() + 4 > data.size) return -1
        return (data[at].toInt() and 0xff) or
            ((data[at + 1].toInt() and 0xff) shl 8) or
            ((data[at + 2].toInt() and 0xff) shl 16) or
            ((data[at + 3].toInt() and 0xff) shl 24)
    }
}
