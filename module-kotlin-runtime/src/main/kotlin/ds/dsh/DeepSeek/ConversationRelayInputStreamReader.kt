package ds.dsh.DeepSeek

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Reads a response body as UTF-8 and always releases the supplied stream.
 *
 * The caller transfers ownership of [input] to this method. A null stream is an empty body.
 */
object ConversationRelayInputStreamReader {
    @JvmStatic
    @Throws(IOException::class)
    fun readUtf8(input: InputStream?): String {
        if (input == null) return ""
        return input.use { stream ->
            String(stream.readBytes(), StandardCharsets.UTF_8)
        }
    }
}
