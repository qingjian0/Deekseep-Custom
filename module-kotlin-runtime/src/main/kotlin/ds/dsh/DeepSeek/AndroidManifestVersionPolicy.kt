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
    private const val MAX_XML_BYTES = 2 * 1024 * 1024

    @JvmStatic
    fun parse(xml: ByteArray?): String? {
        if (xml == null || xml.size < 8 || xml.size > MAX_XML_BYTES) return null
        var strings: Array<String?>? = null
        var offset = u16(xml, 2)
        if (offset < 8) offset = 8
        while (offset <= xml.size - 8) {
            val type = u16(xml, offset)
            val headerSize = u16(xml, offset + 2)
            val chunkSize = i32(xml, offset + 4)
            if (headerSize < 8 || chunkSize < headerSize || chunkSize > xml.size - offset) {
                return null
            }
            val chunkEnd = offset + chunkSize
            if (type == RES_STRING_POOL_TYPE) {
                strings = stringPool(xml, offset, headerSize, chunkSize)
            } else if (type == RES_XML_START_ELEMENT_TYPE && strings != null &&
                headerSize >= 16 && chunkSize - headerSize >= 20
            ) {
                val extension = offset + headerSize
                val element = stringAt(strings, i32(xml, extension + 4))
                if (element == "manifest") {
                    val attributeStart = u16(xml, extension + 8)
                    val attributeSize = u16(xml, extension + 10)
                    val attributeCount = u16(xml, extension + 12)
                    if (attributeStart < 20 || attributeSize < 20) return null
                    val attributes = extension.toLong() + attributeStart
                    val attributeEnd = attributes + attributeSize.toLong() * attributeCount
                    if (attributes > chunkEnd.toLong() || attributeEnd > chunkEnd.toLong()) {
                        return null
                    }
                    for (index in 0 until attributeCount) {
                        val attribute = (attributes + index.toLong() * attributeSize).toInt()
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
            offset = chunkEnd
        }
        return null
    }

    private fun stringPool(
        data: ByteArray,
        chunk: Int,
        headerSize: Int,
        chunkSize: Int
    ): Array<String?>? {
        if (headerSize < 28 || headerSize > chunkSize) return null
        val count = i32(data, chunk + 8)
        val flags = i32(data, chunk + 16)
        val start = i32(data, chunk + 20)
        val tableEnd = headerSize.toLong() + count.toLong() * 4L
        if (count < 0 || count > 200_000 || start < tableEnd || start > chunkSize ||
            tableEnd > chunkSize.toLong()
        ) return null
        val out = arrayOfNulls<String>(count)
        for (index in 0 until count) {
            val relative = i32(data, chunk + headerSize + index * 4)
            if (relative < 0) return null
            val atLong = chunk.toLong() + start + relative
            val chunkEnd = chunk.toLong() + chunkSize
            if (atLong < chunk.toLong() + start || atLong >= chunkEnd) return null
            val at = atLong.toInt()
            out[index] = if ((flags and UTF8_FLAG) != 0) {
                decodeUtf8(data, at, chunkEnd.toInt())
            } else {
                decodeUtf16(data, at, chunkEnd.toInt())
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
        var length = u16(data, at).toLong()
        var cursor = at + 2
        if ((length and 0x8000L) != 0L) {
            if (cursor.toLong() + 2 > end) return null
            length = ((length and 0x7fffL) shl 16) or u16(data, cursor).toLong()
            cursor += 2
        }
        val byteCount = length * 2L
        if (byteCount > end.toLong() - cursor || byteCount > Int.MAX_VALUE) return null
        return String(data, cursor, byteCount.toInt(), Charsets.UTF_16LE)
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
